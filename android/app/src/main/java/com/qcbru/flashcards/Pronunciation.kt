package com.qcbru.flashcards

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class Pronunciation(context: Context, private val notice: (String) -> Unit) {
    private var ready = false
    private val engine: TextToSpeech = TextToSpeech(context) { ready = it == TextToSpeech.SUCCESS }
    fun voices(): List<Pair<String,String>> = if (!ready) emptyList() else engine.voices.orEmpty().filter { it.locale.language in listOf("en","es") }.sortedBy { it.name }.map { it.name to "${it.name}${if (it.isNetworkConnectionRequired) " (online)" else " (offline)"}" }
    fun speak(text: String, language: String, rate: Float, voiceName: String?) {
        if (!ready) { notice("Speech is still starting. Try again."); return }
        val locale = Locale.forLanguageTag(if (language == "es-419") "es-MX" else language)
        if (engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) { notice("Download a $language voice in Android text-to-speech settings."); return }
        engine.language = locale
        val selected = engine.voices?.find { it.name == voiceName && it.locale.language == locale.language }
        if (selected != null) engine.voice = selected
        engine.setSpeechRate(rate)
        if (engine.speak(text,TextToSpeech.QUEUE_FLUSH,null,newId()) == TextToSpeech.ERROR) notice("Speech unavailable. Check installed voices or connection.")
    }
    fun stop() { engine.stop() }
    fun close() { engine.stop(); engine.shutdown() }
}
