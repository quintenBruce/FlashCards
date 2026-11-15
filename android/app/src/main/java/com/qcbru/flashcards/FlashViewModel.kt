package com.qcbru.flashcards

import android.app.Application
import android.net.*
import android.util.Log
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
    val chatRequests: List<ChatRequest> = emptyList(), val activeChats: Set<String> = emptySet(), val syncError: String = "",
    val savingSessions: Set<String> = emptySet(), val studyErrors: Map<String,String> = emptyMap(),
)
class FlashViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as FlashApplication).repository
    private val mutable = MutableStateFlow(FlashState(theme = repository.preferences.getString("theme","system") ?: "system",
        rate = repository.preferences.getFloat("rate",.85f),voice = repository.preferences.getString("voice",null)))
    val state = mutable.asStateFlow()
    private var localJob: Job? = null
    private val chatJobs = mutableMapOf<String,Job>()
    private var activeActions = 0
    private val network = application.getSystemService(ConnectivityManager::class.java)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { updateNetwork() }
        override fun onLost(network: Network) { updateNetwork() }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { updateNetwork() }
    }
    init {
        network.registerDefaultNetworkCallback(callback)
        viewModelScope.launch { repository.syncErrors.collect { errors -> mutable.update { it.copy(syncError=errors.values.joinToString("\n")) } } }
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
            repository.observeLocal(uid).catch { error ->
                Log.e("FlashCardsStorage","observe.failed code=${failureCode(error)}")
                mutable.update { it.copy(loading=false,notice="Saved data could not be loaded. Restart the app and preserve a backup before clearing storage.") }
            }.collect { rows ->
                var decodeFailure: String? = null
                fun <T> read(kind: String, decode: (String) -> T): List<T> = rows.filter { it.kind == kind }.mapNotNull { row ->
                    runCatching { decode(row.body) }.onFailure {
                        Log.e("FlashCardsStorage","decode.failed kind=$kind id=${row.id} code=${failureCode(it)}")
                        decodeFailure="A saved $kind record could not be read. Export a backup before clearing storage."
                    }.getOrNull()
                }
                val downloads = rows.filter { it.kind == "downloads" }.map { it.id }.toSet()
                mutable.update { it.copy(loading = false,
                    decks = read("decks") { Codec.decodeFromString<Deck>(it) }.map { it.copy(downloaded = it.id in downloads) },
                    cards = read("cards") { Codec.decodeFromString<FlashCard>(it) }, progress = read("progress") { Codec.decodeFromString<Progress>(it) },
                    sessions = read("sessions") { Codec.decodeFromString<StudySession>(it) }.map { saved ->
                        val current=it.sessions.find{session->session.id==saved.id}
                        if(current!=null&&current.revision>saved.revision)current else saved
                    }.sortedByDescending { it.updatedAt },
                    reminders = read("reminders") { Codec.decodeFromString<StudyReminder>(it) },
                    turns = (read("turns") { Codec.decodeFromString<Turn>(it) }+read("chat_turns") { Codec.decodeFromString<Turn>(it) }).distinctBy { it.id },
                    proposals = (read("proposals") { Codec.decodeFromString<Proposal>(it) }+read("chat_proposals") { Codec.decodeFromString<Proposal>(it) })
                        .sortedBy { if(it.state=="pending")1 else 0 }.distinctBy { it.id },
                    conversations = read("conversations") { Codec.decodeFromString<Conversation>(it) }.filter { it.deletedAt == null },
                    chatRequests = read("chat_requests") { Codec.decodeFromString<ChatRequest>(it) }.map { request ->
                        if((read("turns"){Codec.decodeFromString<Turn>(it)}+read("chat_turns"){Codec.decodeFromString<Turn>(it)}).any{it.id==request.id})request.copy(state="succeeded",error="") else request
                    },
                    usage = read("usage") { Codec.decodeFromString<Usage>(it) }.maxByOrNull { it.month } ?: Usage(),
                    notice=decodeFailure?:it.notice) }
            }
        }
    }
    fun notice(message: String) { mutable.update { it.copy(notice = message) } }
    fun clearNotice() = notice("")
    fun action(block: suspend () -> Unit) {
        activeActions++
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { withTimeout(120_000) { block() } } catch (e: TimeoutCancellationException) { notice(operationError(e)) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Log.e("FlashCardsOperation", "failed code=${failureCode(e)}")
                notice(operationError(e))
            } finally { activeActions--; mutable.update { it.copy(busy = activeActions > 0) } }
        }
    }
    fun signedIn(token: String) = action {
        val user = repository.auth.signInWithCredential(GoogleAuthProvider.getCredential(token,null)).await().user ?: error("Sign-in failed")
        try { repository.initialize(); observe(user.uid) }
        catch (e: Exception) { repository.signOut(); throw e }
    }
    fun signOut(delete: Boolean = false) = action {
        chatJobs.values.forEach { it.cancel() }; chatJobs.clear()
        repository.signOut(delete); localJob?.cancel()
        mutable.value = FlashState(online = repository.online(),theme = state.value.theme)
    }
    fun refresh() = action { repository.refresh() }
    fun saveDeck(deck: Deck, done: () -> Unit) = action { val user=repository.uid; repository.saveDeck(deck); if(user==repository.uid)done() }
    fun saveCard(card: FlashCard, done: () -> Unit = {}) = action { val user=repository.uid; repository.saveCard(card); if(user==repository.uid)done() }
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
    fun contentAction(entity: String, id: String, version: Int, actionName: String, done: () -> Unit = {}) = action { repository.contentAction(entity,id,version,actionName); done() }
    fun download(deck: Deck) = action { repository.download(deck.id) }
    fun removeDownload(deck: Deck) = action { repository.removeDownload(deck.id) }
    fun import(deck: Deck, cards: List<FlashCard>, done: () -> Unit) = action { repository.importCards(deck,cards); done() }
    fun startSession(deckIds: Set<String>, filter: String, direction: String, shuffle: Boolean, mode: String, count: Int, done: (String) -> Unit) = action {
        val selected=state.value.decks.filter{(deckIds.isEmpty()||it.id in deckIds)&&it.deletedAt==null&&!it.purgeRequested}
        resumableStudy(state.value.sessions,selected.map{it.id}.toSet())?.let{existing->
            val resumed=repository.startOrResumeSession(existing)
            done(resumed.id);return@action
        }
        selected.forEach{repository.prepareDeck(it.id)}
        val user=repository.uid ?: error("Sign in to study")
        val rows=withContext(Dispatchers.IO){repository.dao.list(user,"cards")}
        val freshCards=rows.map{Codec.decodeFromString<FlashCard>(it.body)}
        val s=state.value.copy(cards=freshCards)
        val decks=selected.map{it.copy(downloaded=true)}
        val session = withContext(Dispatchers.Default) { StudyEngine.create(decks,s.cards,s.progress,filter,direction,shuffle,mode,count) }
        check(session.items.isNotEmpty()) { "No cards match these filters. Try All or another direction." }
        check(Codec.encodeToString(session).toByteArray().size <= 650000) { "Select fewer cards for this session" }
        val saved=repository.startOrResumeSession(session)
        mutable.update { it.copy(sessions=it.sessions.filterNot{existing->existing.id==saved.id}+saved) }
        Log.i("FlashCardsSession","opening sessionId=${saved.id}")
        done(saved.id)
    }
    private fun studyAction(session: StudySession, block: suspend (StudySession) -> StudySession) {
        val user=state.value.uid ?: return
        if(session.id in state.value.savingSessions)return
        val current=state.value.sessions.find{it.id==session.id} ?: return
        if(current.revision!=session.revision)return // Ignore a gesture delivered by an outgoing card.
        mutable.update { it.copy(savingSessions=it.savingSessions+session.id,studyErrors=it.studyErrors-session.id) }
        viewModelScope.launch {
            try {
                val saved=withTimeout(15_000){block(current)}
                if(repository.uid==user)mutable.update { it.copy(sessions=it.sessions.filterNot{other->other.id==saved.id}+saved) }
                Log.i("FlashCardsSession","action.saved sessionId=${session.id} revision=${saved.revision}")
            } catch(error: TimeoutCancellationException) {
                mutable.update { it.copy(studyErrors=it.studyErrors+(session.id to "Saving took too long. Leave this session and reopen it to load its latest saved state.")) }
            } catch(error: CancellationException){throw error} catch(error: Exception) {
                Log.e("FlashCardsSession","action.failed sessionId=${session.id} code=${failureCode(error)}")
                mutable.update { it.copy(studyErrors=it.studyErrors+(session.id to operationError(error))) }
            } finally { mutable.update { it.copy(savingSessions=it.savingSessions-session.id) } }
        }
    }
    fun reveal(session: StudySession) = studyAction(session) { repository.persistSession(it.copy(revealed = !it.revealed,revision = it.revision+1)) }
    fun rate(session: StudySession, confidence: String) = studyAction(session) { repository.rate(it,confidence) }
    fun undo(session: StudySession) = studyAction(session) { repository.undo(it) }
    fun answer(session: StudySession, answer: String) = studyAction(session) {
        require(answer.length<=4000) { "Please keep this answer under 4,000 characters" }
        repository.answer(it,answer)
    }
    fun advance(session: StudySession, delta: Int = 1) = studyAction(session) { current ->
        val next = current.cursor+delta
        repository.persistSession(current.copy(cursor = next.coerceIn(0,current.items.lastIndex),revealed = false,
            finished = next >= current.items.size, submitted = if (current.mode == "test") next >= current.items.size else current.submitted,
            revision = current.revision+1,updatedAt = System.currentTimeMillis()))
    }
    fun overrideGrade(session: StudySession, key: String, verdict: String) = studyAction(session) { current ->
        val old = current.grades[key] ?: return@studyAction current
        repository.persistSession(current.copy(grades = current.grades+(key to old.copy(verdict = verdict,explanation = "Grade corrected by you",manual = true)),revision = current.revision+1))
    }
    fun creationDraft():String? {
        val s=state.value
        return creationDraftId(s.decks,s.conversations,s.chatRequests,
            repository.preferences.getString("creation_draft_${s.uid}",null))
    }
    fun inspectCreation(done:(Boolean)->Unit) = action {
        repository.remoteCreationDraft()?.let{repository.preferences.edit().putString("creation_draft_${state.value.uid}",it).commit()}
        done(creationDraft()!=null)
    }
    fun openCreation(fresh:Boolean,done:(String)->Unit) = action {
        val user=state.value.uid ?: return@action
        val current=repository.remoteCreationDraft() ?: creationDraft()
        if(fresh) {
            val deckIds=state.value.decks.map{it.id}.toSet()
            val old=state.value.conversations.filter{it.deckId !in deckIds}.map{it.id}+
                state.value.chatRequests.filter{it.deckId !in deckIds}.map{it.conversationId}+
                listOfNotNull(current?.let{repository.preferences.getString("creation_conversation_${user}_$it","draft_$it")})
            old.distinct().forEach{conversationId->
                chatJobs.remove(conversationId)?.cancelAndJoin()
                repository.deleteConversation(conversationId)
            }
        }
        val id=if(fresh) newId() else current ?: newId()
        if(repository.uid==user) {
            repository.preferences.edit().putString("creation_draft_$user",id).commit()
            done(id)
        }
    }
    fun clearDeckChat(deckId:String,done:(String)->Unit) = action {
        val user=state.value.uid ?: return@action
        val snapshot=state.value
        val ids=deckConversationIds(deckId,snapshot.conversations,snapshot.chatRequests).toMutableSet()
        ids.add("draft_$deckId")
        repository.preferences.getString("creation_conversation_${user}_$deckId",null)?.let{ids.add(it)}
        ids.forEach{chatJobs.remove(it)?.cancelAndJoin()}
        repository.clearDeckChat(deckId,ids)
        if(repository.uid==user) {
            val freshId=newId()
            repository.preferences.edit().putString("creation_conversation_${user}_$deckId",freshId).commit()
            mutable.update{current->current.copy(
                conversations=current.conversations.filterNot{it.deckId==deckId},
                chatRequests=current.chatRequests.filterNot{it.deckId==deckId},
                turns=current.turns.filterNot{it.conversationId in ids},
                proposals=current.proposals.filterNot{it.deckId==deckId},
                activeChats=current.activeChats-ids,
            )}
            done(freshId)
        }
    }
    fun sendMessage(deckId: String, conversationId: String, message: String) {
        val unresolved = state.value.chatRequests.firstOrNull { it.conversationId==conversationId && requestNeedsRecovery(it,state.value.turns) }
        if (unresolved != null) { notice("Check the previous request's status before sending another message."); return }
        if (message.isBlank() || message.length>8000) { notice("Enter a message of at most 8,000 characters."); return }
        runChat(ChatRequest(conversationId=conversationId,deckId=deckId,message=message.trim()),false)
    }
    fun recoverChat(request: ChatRequest) = runChat(request,true)
    private fun runChat(request: ChatRequest, recover: Boolean) {
        val user = state.value.uid ?: return
        if (request.conversationId in state.value.activeChats) return
        mutable.update { it.copy(activeChats=it.activeChats+request.conversationId,
            chatRequests=it.chatRequests.filterNot { previous -> previous.id==request.id } + request.copy(state="pending",error="")) }
        chatJobs[request.conversationId] = viewModelScope.launch {
            try {
                repository.saveChatRequest(user,request.copy(state="pending",error=""))
                val result = try { repository.sendMessage(user,request,recover) } catch (error: TimeoutCancellationException) { request.copy(state="uncertain",error=operationError(error)) } catch (error: CancellationException) { throw error } catch (error: Exception) {
                    Log.e("FlashCardsChat", "failed requestId=${request.id} code=${failureCode(error)}")
                    // A lost response must be recovered with the same ID, even for an HTTP error.
                    request.copy(state="uncertain",error=operationError(error))
                }
                repository.saveChatRequest(user,result)
            } catch (error: CancellationException) { throw error } catch (error: Exception) {
                Log.e("FlashCardsChat", "local.failed requestId=${request.id} code=${failureCode(error)}")
                mutable.update { state -> state.copy(chatRequests=state.chatRequests.map { if(it.id==request.id)it.copy(state="uncertain",error="The request could not be saved on this device. Check status before retrying.")else it }) }
            } finally {
                mutable.update { it.copy(activeChats=it.activeChats-request.conversationId) }
                chatJobs.remove(request.conversationId)
            }
        }
    }
    fun confirm(proposal: Proposal, approve: Boolean, done: () -> Unit = {}) = action { repository.confirm(proposal,approve); done() }
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
