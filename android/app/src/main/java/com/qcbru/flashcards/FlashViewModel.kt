package com.qcbru.flashcards

import android.app.Application
import android.net.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.GoogleAuthProvider
import com.qcbru.flashcards.storage.StoredDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString

data class FlashState(
    val uid: String? = null, val email: String = "", val loading: Boolean = false, val busy: Boolean = false,
    val online: Boolean = true, val notice: String = "", val decks: List<Deck> = emptyList(), val cards: List<FlashCard> = emptyList(),
    val progress: List<Progress> = emptyList(), val sessions: List<StudySession> = emptyList(),
    val reminders: List<StudyReminder> = emptyList(), val turns: List<Turn> = emptyList(), val proposals: List<Proposal> = emptyList(), val conversations: List<Conversation> = emptyList(),
    val usage: Usage = Usage(), val theme: String = "system", val rate: Float = .85f, val voice: String? = null,
    val importText: String = "", val exportText: String = "", val pendingReminder: String? = null,
)
class FlashViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as FlashApplication).repository
    private val mutable = MutableStateFlow(FlashState(theme = repository.preferences.getString("theme","system") ?: "system",
        rate = repository.preferences.getFloat("rate",.85f),voice = repository.preferences.getString("voice",null)))
    val state = mutable.asStateFlow()
    private var localJob: Job? = null
    private val network = application.getSystemService(ConnectivityManager::class.java)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { updateNetwork() }
        override fun onLost(network: Network) { updateNetwork() }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { updateNetwork() }
    }
    init {
        network.registerDefaultNetworkCallback(callback)
        repository.auth.currentUser?.let { observe(it.uid); action { repository.initialize() } }
        updateNetwork()
    }
    private fun updateNetwork() {
        mutable.update { it.copy(online = repository.online()) }
        if (repository.online()) SyncWorker.enqueue(getApplication())
    }
    private fun observe(uid: String) {
        localJob?.cancel()
        mutable.update { it.copy(uid = uid,email = repository.auth.currentUser?.email ?: "",loading = true) }
        localJob = viewModelScope.launch(Dispatchers.Default) {
            repository.observeLocal(uid).collect { rows ->
                fun <T> read(kind: String, decode: (String) -> T): List<T> = rows.filter { it.kind == kind }.mapNotNull { row ->
                    runCatching { decode(row.body) }.onFailure { notice("A saved $kind record could not be read. Export a backup before clearing storage.") }.getOrNull()
                }
                val downloads = rows.filter { it.kind == "downloads" }.map { it.id }.toSet()
                mutable.update { it.copy(loading = false,
                    decks = read("decks") { Codec.decodeFromString<Deck>(it) }.map { it.copy(downloaded = it.id in downloads) },
                    cards = read("cards") { Codec.decodeFromString<FlashCard>(it) }, progress = read("progress") { Codec.decodeFromString<Progress>(it) },
                    sessions = read("sessions") { Codec.decodeFromString<StudySession>(it) }.sortedByDescending { it.updatedAt },
                    reminders = read("reminders") { Codec.decodeFromString<StudyReminder>(it) }, turns = read("turns") { Codec.decodeFromString<Turn>(it) },
                    proposals = read("proposals") { Codec.decodeFromString<Proposal>(it) },
                    conversations = read("conversations") { Codec.decodeFromString<Conversation>(it) }.filter { it.deletedAt == null },
                    usage = read("usage") { Codec.decodeFromString<Usage>(it) }.maxByOrNull { it.month } ?: Usage()) }
            }
        }
    }
    fun notice(message: String) { mutable.update { it.copy(notice = message) } }
    fun clearNotice() = notice("")
    fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutable.update { it.copy(busy = true) }
            try { block() } catch (e: Exception) { notice(e.localizedMessage ?: "Operation failed. Retry safely.") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun signedIn(token: String) = action {
        val user = repository.auth.signInWithCredential(GoogleAuthProvider.getCredential(token,null)).await().user ?: error("Sign-in failed")
        try { repository.initialize(); observe(user.uid) }
        catch (e: Exception) { repository.signOut(); throw e }
    }
    fun signOut(delete: Boolean = false) = action {
        repository.signOut(delete); localJob?.cancel()
        mutable.value = FlashState(online = repository.online(),theme = state.value.theme)
    }
    fun refresh() = action { repository.refresh() }
    fun saveDeck(deck: Deck, done: () -> Unit) = action { repository.saveDeck(deck); done() }
    fun saveCard(card: FlashCard, done: () -> Unit = {}) = action { repository.saveCard(card); done() }
    fun duplicateDeck(deck: Deck) = action {
        val copy = deck.copy(id = newId(),title = "${deck.title} (copy)",version = 0,archived = false,deletedAt = null)
        repository.saveDeck(copy)
        repository.importCards(copy.copy(version = 1),state.value.cards.filter { it.deckId == deck.id && it.deletedAt == null }.map { it.copy(id = newId(),deckId = copy.id,version = 0) })
    }
    fun moveCard(card: FlashCard, delta: Int) = action {
        val cards = state.value.cards.filter { it.deckId == card.deckId && it.deletedAt == null }.sortedBy { it.order }
        val from = cards.indexOfFirst { it.id == card.id }; val to = from+delta
        if (from >= 0 && to in cards.indices) {
            // Space neighboring positions to support future insertions.
            val reordered = cards.toMutableList().apply { add(to,removeAt(from)) }
            for ((index,value) in reordered.withIndex()) if (value.order != index*1000L) repository.saveCard(value.copy(order = index*1000L))
        }
    }
    fun contentAction(entity: String, id: String, version: Int, actionName: String) = action { repository.contentAction(entity,id,version,actionName) }
    fun download(deck: Deck) = action { repository.download(deck.id) }
    fun removeDownload(deck: Deck) = action { repository.removeDownload(deck.id) }
    fun import(deck: Deck, cards: List<FlashCard>, done: () -> Unit) = action { repository.importCards(deck,cards); done() }
    fun startSession(deckIds: Set<String>, filter: String, direction: String, shuffle: Boolean, mode: String, count: Int, done: (String) -> Unit) = action {
        val s = state.value
        val decks = s.decks.filter { deckIds.isEmpty() || it.id in deckIds }
        check(decks.none { !it.downloaded && !it.archived && it.deletedAt == null }) { "Download the selected decks first" }
        val session = StudyEngine.create(decks,s.cards,s.progress,filter,direction,shuffle,mode,count)
        check(session.items.isNotEmpty()) { "No cards match these filters. Try All or another direction." }
        check(Codec.encodeToString(session).toByteArray().size <= 650000) { "Select fewer cards for this session" }
        repository.persistSession(session); done(session.id)
    }
    fun reveal(session: StudySession) = action { repository.persistSession(session.copy(revealed = !session.revealed,revision = session.revision+1)) }
    fun rate(session: StudySession, confidence: String) = action { repository.rate(session,confidence) }
    fun undo(session: StudySession) = action { repository.undo(session) }
    fun answer(session: StudySession, answer: String) = action {
        require(answer.length<=4000) { "Please keep this answer under 4,000 characters" }
        repository.answer(session,answer)
    }
    fun advance(session: StudySession, delta: Int = 1) = action {
        val next = session.cursor+delta
        repository.persistSession(session.copy(cursor = next.coerceIn(0,session.items.lastIndex),revealed = false,
            finished = if (session.mode == "browse") false else next >= session.items.size, submitted = if (session.mode == "test") next >= session.items.size else session.submitted,
            revision = session.revision+1,updatedAt = System.currentTimeMillis()))
    }
    fun overrideGrade(session: StudySession, key: String, verdict: String) = action {
        val old = session.grades[key] ?: return@action
        repository.persistSession(session.copy(grades = session.grades+(key to old.copy(verdict = verdict,explanation = "Grade corrected by you",manual = true)),revision = session.revision+1))
    }
    fun sendMessage(deckId: String, conversationId: String, message: String, done: () -> Unit) = action {
        repository.sendMessage(deckId,conversationId,message); done()
    }
    fun confirm(proposal: Proposal, approve: Boolean) = action { repository.confirm(proposal,approve) }
    fun saveReminder(reminder: StudyReminder, done: () -> Unit) = action { repository.saveReminder(reminder); done() }
    fun deleteReminder(reminder: StudyReminder) = action { repository.deleteReminder(reminder) }
    fun deleteConversation(id: String) = action { repository.deleteConversation(id) }
    fun openReminder(id: String) { mutable.update { it.copy(pendingReminder = id) } }
    fun clearReminder() { mutable.update { it.copy(pendingReminder = null) } }
    fun theme(value: String) { repository.preferences.edit().putString("theme",value).apply(); mutable.update { it.copy(theme = value) } }
    fun speech(rate: Float, voice: String?) {
        repository.preferences.edit().putFloat("rate",rate).putString("voice",voice).apply(); mutable.update { it.copy(rate = rate,voice = voice) }
    }
    fun imported(text: String) { mutable.update { it.copy(importText = text) } }
    fun prepareExport(text: String) { mutable.update { it.copy(exportText = text) } }
    fun exportBackup(done: (String) -> Unit) = action { val text = repository.exportBackup(); prepareExport(text); done(text) }
    fun restoreBackup(text: String, done: () -> Unit) = action { repository.restoreBackup(ImportExport.backup(text)); done() }
    override fun onCleared() { network.unregisterNetworkCallback(callback); repository.stopListeners(); super.onCleared() }
}
