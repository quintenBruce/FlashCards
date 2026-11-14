package com.qcbru.flashcards

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val model: FlashViewModel by viewModels()
    private lateinit var speech: Pronunciation
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (!it) model.notice("Notifications are disabled. You can enable them in Android settings.") }
    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(model.state.value.exportText) } ?: error("Cannot open export file") } }
                .onSuccess { model.notice("Export saved") }.onFailure { model.notice("Could not save export") }
        }
    }
    private val openFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer); if (count < 0) break
                        require(output.size()+count <= 20_000_000) { "File exceeds 20 MB" }
                        output.write(buffer,0,count)
                    }
                    output.toByteArray().toString(Charsets.UTF_8)
                } ?: error("Cannot read file")
            } }.onSuccess { model.imported(it) }.onFailure { model.notice(it.message ?: "Could not read file") }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        speech = Pronunciation(this,model::notice)
        handleIntent(intent)
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            FlashTheme(state.theme) {
                FlashApp(state,model,onSignIn=::signIn,onExport={ text,name -> model.prepareExport(text); saveFile.launch(name) },
                    onOpenFile={ openFile.launch("*/*") },onNotifications={ if(Build.VERSION.SDK_INT>=33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onSpeak={ text,language -> speech.speak(text,language,state.rate,state.voice) },onVoices={ speech.voices() })
            }
        }
    }
    private fun signIn() {
        lifecycleScope.launch {
            try {
                val clientId = getString(resources.getIdentifier("default_web_client_id","string",packageName))
                check(!clientId.startsWith("placeholder")) { "Configure the real FlashCards google-services.json first" }
                val result = CredentialManager.create(this@MainActivity).getCredential(this@MainActivity,
                    GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build())
                model.signedIn(GoogleIdTokenCredential.createFrom(result.credential.data).idToken)
            } catch (e: Exception) { model.notice(e.message ?: "Google sign-in did not finish") }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    private fun handleIntent(intent: Intent) {
        val uid = intent.getStringExtra("uid") ?: return
        if (uid == model.repository.uid) intent.getStringExtra("reminderId")?.let(model::openReminder)
    }
    override fun onStop() { speech.stop(); super.onStop() }
    override fun onDestroy() { speech.close(); super.onDestroy() }
}
