package com.qcbru.flashcards

import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.qcbru.flashcards.alerts.StudyAlerts
import com.qcbru.flashcards.storage.FlashDatabase
import com.qcbru.flashcards.storage.StoredDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit

class FlashRepository(private val context: Context, val database: FlashDatabase) {
    val auth = FirebaseAuth.getInstance()
    private val cloud = FirebaseFirestore.getInstance("default")
    private val functions = FirebaseFunctions.getInstance("us-south1")
    val preferences = context.getSharedPreferences("flashcards", Context.MODE_PRIVATE)
    private val listeners = mutableListOf<ListenerRegistration>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reviewMutex = Mutex()
    private val flushMutex = Mutex()
    private val snapshotMutex = Mutex()
    private var listenerGeneration = 0
    private val syncFailures = MutableStateFlow<Map<String,String>>(emptyMap())
    val syncErrors = syncFailures.asStateFlow()
    val dao get() = database.documents()
    val uid get() = auth.currentUser?.uid
    fun online(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }
    suspend fun call(name: String, value: Map<String, Any?> = emptyMap(), timeoutSeconds: Long = 60): JsonObject {
        check(online()) { "Offline. Downloaded study and saved progress remain available." }
        val reference = value["requestId"] as? String ?: "none"
        val started = System.currentTimeMillis()
        Log.i("FlashCardsRequest", "started operation=$name requestId=$reference")
        try {
            val result = withTimeout((timeoutSeconds+5)*1000) {
                functions.getHttpsCallable(name).withTimeout(timeoutSeconds, TimeUnit.SECONDS).call(value).await().data.toJson().jsonObject
            }
            Log.i("FlashCardsRequest", "succeeded operation=$name requestId=$reference elapsedMs=${System.currentTimeMillis()-started}")
            return result
        } catch (error: Exception) {
            Log.e("FlashCardsRequest", "failed operation=$name requestId=$reference code=${failureCode(error)} elapsedMs=${System.currentTimeMillis()-started}")
            throw error
        }
    }
    suspend fun initialize() {
        call("bootstrap")
        val user = uid ?: return

        // Room rows are scoped by UID, including pending offline work.
        preferences.edit().putString("active_uid", user).apply()
        observeCloud(user)
        runCatching { registerDevice() }
        restoreRemoteSessions(user)
        SyncWorker.enqueue(context)
    }
    fun observeLocal(user: String): Flow<List<StoredDocument>> = dao.observe(user)
    private fun observeCloud(user: String) {
        stopListeners()
        val generation=listenerGeneration
        for (kind in listOf("decks","progress","reminders","turns","proposals","usage","conversations")) {
            listeners += cloud.collection("users").document(user).collection(kind).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FlashCardsSync", "listener.failed collection=$kind code=${error.code}")
                    syncFailures.update { it + (kind to "Cloud sync failed for $kind (${error.code}). Refresh or check Firebase permissions and App Check.") }
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.metadata.isFromCache) return@addSnapshotListener
                scope.launch {
                    try {
                    snapshotMutex.withLock {
                    if (uid != user || generation!=listenerGeneration) return@launch

                    database.runInTransaction {
                        if (uid == user) {
                            val pendingIds=if(kind=="progress")dao.list(user,"review_pending").map{Codec.decodeFromString<ReviewEvent>(it.body)}.map{"${it.cardId}_${it.direction}"}.toSet() else emptySet()
                            if(pendingIds.isEmpty())dao.clearKind(user,kind)
                            else dao.list(user,kind).filter{it.id !in pendingIds}.forEach{dao.remove(user,kind,it.id)}
                            dao.putAll(snapshot.documents.filter{it.id !in pendingIds}.map { StoredDocument(user, kind, it.id, (it.data.orEmpty() + ("id" to it.id)).toJson().toString()) })
                        }
                    }
                    if (kind == "reminders") StudyAlerts.reschedule(context, user)
                    if (kind == "decks") {
                        snapshot.documents.filter{it.get("deletedAt")!=null||it.get("purgeRequested")==true}.forEach{dao.remove(user,"downloads",it.id)}
                        SyncWorker.enqueue(context)
                    }
                    syncFailures.update { it - kind }
                    }
                    } catch (error: CancellationException) { throw error } catch (error: Exception) {
                        Log.e("FlashCardsSync", "cache.failed collection=$kind code=${failureCode(error)}")
                        syncFailures.update { it + (kind to "Downloaded $kind could not be saved. Refresh to retry.") }
                    }
                }
            }
        }
    }
    fun stopListeners() { listenerGeneration++; listeners.forEach { it.remove() }; listeners.clear(); syncFailures.value=emptyMap() }
    suspend fun refresh() {
        val user = uid ?: return
        check(online()) { "You are offline" }
        observeCloud(user)
        restoreRemoteSessions(user)
        for (row in withContext(Dispatchers.IO) { dao.list(user,"downloads") }) { try { download(row.id) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Other decks and pending progress still sync. */ } }
        flush()
    }
    private suspend fun restoreRemoteSessions(user: String) = withContext(Dispatchers.IO) {
        var cursor: DocumentSnapshot? = null
        do {
            var query = cloud.collection("users").document(user).collection("sessions").orderBy(FieldPath.documentId()).limit(100)
            if (cursor != null) query = query.startAfter(cursor!!)
            val page = query.get(Source.SERVER).await()
            if (uid != user) return@withContext
            database.runInTransaction {
                for (doc in page.documents) {
                    val local=dao.get(user,"sessions",doc.id)?.let{Codec.decodeFromString<StudySession>(it.body)}
                    if (local==null || (dao.get(user,"session_pending",doc.id)==null && (doc.getLong("version")?:0)>local.serverVersion)) {
                        val session = Codec.decodeFromJsonElement<StudySession>(doc.get("value").toJson()).copy(serverVersion = (doc.getLong("version") ?: 1).toInt())
                        dao.put(StoredDocument(user,"sessions",doc.id,Codec.encodeToString(session)))
                        for ((key,grade) in session.grades) if (grade.verdict == "pending") {
                            val item = session.items.find { it.key == key } ?: continue
                            val pending = GradeRequest(sessionId = session.id,itemKey = key,item = item,answer = grade.answer)
                            dao.put(StoredDocument(user,"grade_pending",pending.id,Codec.encodeToString(pending)))
                        }
                    }
                }
            }
            cursor = if (page.size() == 100) page.documents.last() else null
        } while (cursor != null)
    }
    suspend fun prepareDeck(deckId: String) {
        val user=uid ?: error("Sign in to open this deck")
        val cached=withContext(Dispatchers.IO){dao.get(user,"downloads",deckId)!=null}
        if(!cached) download(deckId) else if(online())refreshSavedDeck(deckId)
    }
    private suspend fun refreshSavedDeck(deckId: String) {
        try { download(deckId) }
        catch(e: CancellationException){throw e}
        catch(_: Exception){syncFailures.update{it + (deckId to "Saved. Connect to load this deck again.")}}
    }
    suspend fun download(deckId: String) = withContext(Dispatchers.IO) {
        val user = uid ?: return@withContext
        val deck = cloud.collection("users").document(user).collection("decks").document(deckId).get(Source.SERVER).await()
        check(deck.exists() && deck.get("deletedAt") == null) { "Deck is unavailable" }
        val rows = mutableListOf<StoredDocument>(); var cursor: DocumentSnapshot? = null
        do {
            var query = cloud.collection("users").document(user).collection("cards").whereEqualTo("deckId",deckId).orderBy("order").limit(200)
            if (cursor != null) query = query.startAfter(cursor!!)
            val page = query.get(Source.SERVER).await()
            rows += page.documents.map { StoredDocument(user,"cards",it.id,(it.data.orEmpty() + ("id" to it.id)).toJson().toString()) }
            cursor = if (page.size() == 200) page.documents.last() else null
        } while (cursor != null)
        if (uid != user) return@withContext
        database.runInTransaction {
            dao.list(user,"cards").filter { Codec.decodeFromString<FlashCard>(it.body).deckId == deckId }.forEach { dao.remove(user,"cards",it.id) }
            dao.putAll(rows)
            dao.put(StoredDocument(user,"decks",deckId,(deck.data.orEmpty() + ("id" to deckId)).toJson().toString()))
            dao.put(StoredDocument(user,"downloads",deckId,"{}"))
        }
    }
    suspend fun removeDownload(deckId: String) = withContext(Dispatchers.IO) {
        val user = uid ?: return@withContext
        database.runInTransaction {
            dao.list(user,"cards").filter { Codec.decodeFromString<FlashCard>(it.body).deckId == deckId }.forEach { dao.remove(user,"cards",it.id) }
            dao.remove(user,"downloads",deckId)
        }
    }
    private fun mutationId(payload:String):String {
        val digest=java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString(""){"%02x".format(it)}
        val key="mutation_${uid}_$digest"
        return preferences.getString(key,null) ?: newId().also{preferences.edit().putString(key,it).commit()}
    }
    suspend fun saveDeck(deck: Deck) {
        call("mutateContent", mapOf("requestId" to mutationId(Codec.encodeToString(deck)), "entity" to "decks", "action" to "save", "id" to deck.id,
            "expectedVersion" to deck.version, "value" to Codec.encodeToJsonElement(deck).toCloud()))
        refreshSavedDeck(deck.id)
    }
    suspend fun saveCard(card: FlashCard) {
        val user=uid ?: error("Sign in to save a card")
        call("mutateContent", mapOf("requestId" to mutationId(Codec.encodeToString(card)),
            "entity" to "cards", "action" to "save", "id" to card.id,
            "expectedVersion" to card.version, "value" to Codec.encodeToJsonElement(card).toCloud()))
        withContext(Dispatchers.IO) {
            try {
            val saved=cloud.collection("users").document(user).collection("cards").document(card.id).get(Source.SERVER).await()
            if(uid==user && saved.exists())dao.put(StoredDocument(user,"cards",card.id,(saved.data.orEmpty()+("id" to card.id)).toJson().toString()))
            } catch(e:CancellationException){throw e} catch(_:Exception){syncFailures.update{it+(card.deckId to "Card saved. Reopen this deck to refresh it.")}}
        }
    }
    suspend fun contentAction(entity: String, recordId: String, version: Int, action: String) {
        call("mutateContent", mapOf("requestId" to newId(), "entity" to entity, "id" to recordId, "expectedVersion" to version, "action" to action))
        if (entity == "cards") {
            val user = uid ?: return
            val card = withContext(Dispatchers.IO) { dao.get(user,"cards",recordId)?.let { Codec.decodeFromString<FlashCard>(it.body) } }
            if (card != null) download(card.deckId)
        }
    }
    suspend fun importCards(deck: Deck, cards: List<FlashCard>) {
        var currentVersion = deck.version
        try { for (batch in cards.chunked(100)) {
            val result = call("importContent", mapOf("requestId" to newId(),"deckId" to deck.id,"expectedVersion" to currentVersion,
                "cards" to batch.map { Codec.encodeToJsonElement(it).toCloud() }))
            currentVersion = result["version"]!!.jsonPrimitive.int
        } } catch (e: Exception) { runCatching { download(deck.id) }; throw e }
        refreshSavedDeck(deck.id)
    }
    suspend fun persistSession(session: StudySession): StudySession = withContext(Dispatchers.IO) {
        val user = uid ?: error("Sign in before starting a session")
        database.runInTransaction { writeSession(user, session) }
        val saved=dao.get(user,"sessions",session.id) ?: error("The session could not be saved on this device")
        check(uid==user) { "Account changed while saving the session" }
        Log.i("FlashCardsSession","saved sessionId=${session.id} revision=${session.revision}")
        SyncWorker.enqueue(context)
        Codec.decodeFromString<StudySession>(saved.body)
    }
    private fun writeSession(user: String, session: StudySession) {
        val existing = dao.get(user,"sessions",session.id)?.let { Codec.decodeFromString<StudySession>(it.body) }
        val grades = session.grades.mapValues { (key,grade) ->
            val saved = existing?.grades?.get(key)
            if (grade.verdict == "pending" && saved != null && saved.answer == grade.answer && saved.verdict != "pending") saved else grade
        }
        val merged = session.copy(serverVersion = maxOf(session.serverVersion,existing?.serverVersion ?: 0), grades = grades, updatedAt = System.currentTimeMillis())
        dao.put(StoredDocument(user,"sessions",session.id,Codec.encodeToString(merged)))
        dao.put(StoredDocument(user,"session_pending",session.id,"{}"))
    }
    suspend fun rate(session: StudySession, confidence: String): StudySession = withContext(Dispatchers.IO) {
        reviewMutex.withLock {
        val user = uid ?: error("Sign in first"); val item = session.current ?: error("Session complete")
        val progressId = "${item.card.id}_${item.direction}"
        val old = dao.get(user,"progress",progressId)?.let { Codec.decodeFromString<Progress>(it.body) }
        val event = ReviewEvent(sessionId = session.id, cardId = item.card.id, cardVersion = item.card.learningVersion,
            direction = item.direction, confidence = confidence, expectedVersion = old?.version ?: 0)
        val next = StudyEngine.rated(session,confidence).copy(undoJson = Codec.encodeToString(session.copy(undoJson = null, undoEventId = null, undoProgress = null)), undoEventId = event.id, undoProgress = old)
        database.runInTransaction {
            writeSession(user,next)
            dao.put(StoredDocument(user,"review_pending",event.id,Codec.encodeToString(event)))
            dao.put(StoredDocument(user,"progress",progressId,Codec.encodeToString(Progress(progressId,item.card.id,item.direction,confidence,item.card.learningVersion,(old?.version ?: 0)+1,event.id,System.currentTimeMillis()))))
        }
        SyncWorker.enqueue(context); next
        }
    }
    suspend fun undo(session: StudySession): StudySession = withContext(Dispatchers.IO) {
        reviewMutex.withLock {
        val user = uid ?: error("Sign in first"); val restored = Codec.decodeFromString<StudySession>(session.undoJson ?: error("Nothing to undo"))
        val item = restored.current!!; val progressId = "${item.card.id}_${item.direction}"
        val eventId = session.undoEventId!!

        val current = dao.get(user,"progress",progressId)?.let { Codec.decodeFromString<Progress>(it.body) }
        val next = restored.copy(revision = session.revision+1, serverVersion = session.serverVersion, updatedAt = System.currentTimeMillis())
        var restoredProgress = session.undoProgress
        database.runInTransaction {
            val event = ReviewEvent(sessionId = session.id, cardId = item.card.id, cardVersion = item.card.learningVersion, direction = item.direction,
                confidence = session.undoProgress?.confidence ?: "low", expectedVersion = current?.version ?: 0, undoEventId = eventId)
            dao.put(StoredDocument(user,"review_pending",event.id,Codec.encodeToString(event)))
            restoredProgress = restoredProgress?.copy(version = (current?.version ?: 0)+1,eventId = event.id,updatedAt = System.currentTimeMillis())
            val progress = restoredProgress
            if (progress == null) dao.remove(user,"progress",progressId)
            else dao.put(StoredDocument(user,"progress",progressId,Codec.encodeToString(progress)))
            writeSession(user,next)
        }
        SyncWorker.enqueue(context); next
        }
    }
    suspend fun answer(session: StudySession, answer: String): StudySession = withContext(Dispatchers.IO) {
        val user = uid ?: error("Sign in first"); val item = session.current ?: error("No question")
        val grade = when {
            answer.isBlank() -> Grade(answer,"incorrect","No answer was provided.")
            item.choices.isNotEmpty() -> Grade(answer, if (exactAnswer(item,answer)) "correct" else "incorrect", "Expected: ${item.answer}")
            exactAnswer(item,answer) -> Grade(answer,"correct","Matches an accepted answer.")
            else -> Grade(answer)
        }
        val next = session.copy(grades = session.grades + (item.key to grade), revealed = session.mode != "test", revision = session.revision+1)
        database.runInTransaction {
            writeSession(user,next)
            if (grade.verdict == "pending") {
                val request = GradeRequest(sessionId = session.id, itemKey = item.key, item = item, answer = answer)
                dao.put(StoredDocument(user,"grade_pending",request.id,Codec.encodeToString(request)))
            }
        }
        SyncWorker.enqueue(context); next
    }
    suspend fun saveChatRequest(user: String, request: ChatRequest) = withContext(Dispatchers.IO) {
        if (uid == user) dao.put(StoredDocument(user,"chat_requests",request.id,Codec.encodeToString(request)))
    }
    suspend fun sendMessage(user: String, request: ChatRequest, recover: Boolean): ChatRequest {
        require(request.message.isNotBlank() && request.message.length <= 8000) { "Enter a message of at most 8,000 characters." }
        check(uid == user) { "Sign in again before sending." }
        val payload = mapOf("requestId" to request.id,"deckId" to request.deckId,"conversationId" to request.conversationId,"message" to request.message)
        val receipt = withTimeout(225_000) {
            var result = if (recover) call("getChatRequest",mapOf("requestId" to request.id),30) else call("sendDeckMessage",payload,210)
            if (result["state"]?.jsonPrimitive?.content == "not_found") result = call("sendDeckMessage",payload,210)
            while (result["state"]?.jsonPrimitive?.content == "pending") {
                delay(2500)
                check(uid == user) { "Account changed." }
                result = call("getChatRequest",mapOf("requestId" to request.id),30)
            }
            result
        }
        check(uid == user) { "Account changed." }
        val status = receipt["state"]?.jsonPrimitive?.content
        if (status == "succeeded") {
            val turn = receipt["turn"]?.jsonObject ?: error("The server returned no saved response. Check request status.")
            val proposal = receipt["proposal"] as? JsonObject
            withContext(Dispatchers.IO) {
                database.runInTransaction {
                    if (uid == user) {
                        // Keep receipts separately so a delayed cloud snapshot cannot erase them.
                        dao.put(StoredDocument(user,"chat_turns",request.id,turn.toString()))
                        dao.put(StoredDocument(user,"chat_requests",request.id,Codec.encodeToString(request.copy(state="succeeded",error=""))))
                        if (proposal != null) dao.put(StoredDocument(user,"chat_proposals",proposal.getValue("id").jsonPrimitive.content,proposal.toString()))
                        dao.put(StoredDocument(user,"conversations",request.conversationId,Codec.encodeToString(Conversation(request.conversationId,request.deckId,System.currentTimeMillis()))))
                    }
                }
            }
            return request.copy(state="succeeded",error="")
        }
        val failure = (receipt["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.content
        val code = (receipt["error"] as? JsonObject)?.get("code")?.jsonPrimitive?.content ?: status
        Log.e("FlashCardsChat", "request.failed requestId=${request.id} code=$code")
        return request.copy(state=if(status=="failed")"failed"else"uncertain",error=failure ?: "The request outcome is unknown. Check status to recover its response without sending a second AI request.")
    }
    suspend fun confirm(proposal: Proposal, approve: Boolean) {
        val user=uid ?: return
        val result=call("confirmProposal",mapOf("proposalId" to proposal.id,"approve" to approve))
        val applied=result["rejected"]?.jsonPrimitive?.booleanOrNull != true
        withContext(Dispatchers.IO) {
            if(uid==user)dao.put(StoredDocument(user,"chat_proposals",proposal.id,Codec.encodeToString(proposal.copy(state=if(applied)"applied"else"rejected"))))
        }
        if(applied && proposal.operations.none{it.kind=="delete_deck"}) refreshSavedDeck(proposal.deckId)
    }
    suspend fun saveReminder(reminder: StudyReminder) {
        call("upsertReminder",mapOf("requestId" to newId(),"id" to reminder.id,"expectedVersion" to reminder.version,"value" to Codec.encodeToJsonElement(reminder).toCloud()))
    }
    suspend fun deleteReminder(reminder: StudyReminder) {
        call("applyReminderAction", mapOf("requestId" to newId(),"id" to reminder.id,"expectedVersion" to reminder.version,"action" to "delete"))
    }
    suspend fun remoteCreationDraft():String? {
        val user=uid ?: return null
        if(!online())return null
        val slot=cloud.collection("users").document(user).collection("creation").document("active").get(Source.SERVER).await()
        val deckId=slot.getString("deckId") ?: return null
        val conversationId=slot.getString("conversationId") ?: return null
        val deck=cloud.collection("users").document(user).collection("decks").document(deckId).get(Source.SERVER).await()
        val conversation=cloud.collection("users").document(user).collection("conversations").document(conversationId).get(Source.SERVER).await()
        if(!deck.exists()&&conversation.get("deletedAt")==null) {
            preferences.edit().putString("creation_conversation_${user}_$deckId",conversationId).commit()
            return deckId
        }
        return null
    }
    suspend fun deleteConversation(conversationId: String) {
        val user=uid ?: return
        call("deleteConversation",mapOf("id" to conversationId))
        withContext(Dispatchers.IO){dao.remove(user,"conversations",conversationId)}
        withContext(Dispatchers.IO) {
            if(uid==user)for(kind in listOf("chat_requests","chat_turns","chat_proposals","turns","proposals"))for(row in dao.list(user,kind)) {
                if(Codec.parseToJsonElement(row.body).jsonObject["conversationId"]?.jsonPrimitive?.content==conversationId)dao.remove(user,kind,row.id)
            }
        }
    }
    suspend fun registerDevice() {
        if (uid == null || !online()) return
        val device = preferences.getString("device_id",null) ?: newId().also { preferences.edit().putString("device_id",it).apply() }
        call("updateDevice",mapOf("deviceId" to device,"token" to FirebaseMessaging.getInstance().token.await()))
    }
    suspend fun exportBackup(): String {
        val collections = linkedMapOf<String,List<JsonObject>>()
        for (kind in listOf("decks","cards","progress","reviews","sessions","reminders","conversations","turns")) {
            val rows = mutableListOf<JsonObject>(); var cursor: String? = null
            do {
                val page = call("exportBackupPage",mapOf("collection" to kind,"cursor" to cursor))
                rows += page["rows"]!!.jsonArray.map { it.jsonObject }
                cursor = page["cursor"]?.jsonPrimitive?.contentOrNull
            } while (cursor != null)
            collections[kind] = rows
        }
        return Codec.encodeToString(Backup(collections = collections))
    }
    suspend fun restoreBackup(backup: Backup) {
        val user = uid ?: return
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(Codec.encodeToString(backup).toByteArray()).joinToString(""){"%02x".format(it)}
        val jobId = "restore_$digest"
        val remapped = withContext(Dispatchers.IO) {
            if (dao.get(user,"restore_jobs",jobId) == null) {
                val mapped = ImportExport.remap(backup)
                database.runInTransaction {
                    for ((kind,rows) in mapped.collections) for ((index,row) in rows.withIndex()) {
                        dao.put(StoredDocument(user,"restore_rows","${jobId}_${kind}_$index",mapOf("jobId" to jobId,"kind" to kind,"index" to index,"row" to row).toJson().toString()))
                    }
                    dao.put(StoredDocument(user,"restore_jobs",jobId,"{}"))
                }
            }
            val stored = dao.list(user,"restore_rows").map { Codec.parseToJsonElement(it.body).jsonObject }.filter { it["jobId"]?.jsonPrimitive?.content == jobId }
            backup.copy(collections = stored.groupBy { it["kind"]!!.jsonPrimitive.content }.mapValues { (_,rows) -> rows.sortedBy {it["index"]!!.jsonPrimitive.int}.map {it["row"]!!.jsonObject} })
        }
        for (kind in listOf("decks","cards","progress","reviews","sessions","reminders","conversations","turns")) {
            for ((index,batch) in remapped.collections[kind].orEmpty().chunked(100).withIndex()) call("restoreBackupPage",mapOf("requestId" to "${digest.take(40)}_${kind}_$index","collection" to kind,"rows" to JsonArray(batch).toCloud()))
        }
        // Restore saved session snapshots locally as well as in cloud.
        withContext(Dispatchers.IO) {
            remapped.collections["sessions"].orEmpty().forEach { row ->
                val value = row["value"] ?: return@forEach
                val session = Codec.decodeFromJsonElement<StudySession>(value).copy(serverVersion = row["version"]?.jsonPrimitive?.int ?: 1, undoJson = null, undoEventId = null, undoProgress = null)
                dao.put(StoredDocument(user,"sessions",session.id,Codec.encodeToString(session)))
                for ((key,grade) in session.grades) if (grade.verdict == "pending") {
                    val item = session.items.find { it.key == key } ?: continue
                    val pending = GradeRequest(sessionId = session.id,itemKey = key,item = item,answer = grade.answer)
                    dao.put(StoredDocument(user,"grade_pending",pending.id,Codec.encodeToString(pending)))
                }
            }
        }
        refresh()
    }
    suspend fun clearLocal(user: String) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelAllWorkByTag("sync-$user")
        StudyAlerts.cancelAll(context,user)
        context.getSystemService(NotificationManager::class.java).cancelAll()
        dao.clear(user)
    }
    suspend fun signOut(delete: Boolean = false) {
        val user = uid ?: return
        if (delete) call("deleteAccount",mapOf("confirm" to "DELETE"))
        else runCatching { call("updateDevice",mapOf("deviceId" to preferences.getString("device_id","") ,"remove" to true)) }
        stopListeners(); auth.signOut(); if(delete)clearLocal(user) else withContext(Dispatchers.IO){StudyAlerts.cancelAll(context,user);context.getSystemService(NotificationManager::class.java).cancelAll()}
        preferences.edit().remove("active_uid").apply()
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
    }
    suspend fun flush() = withContext(Dispatchers.IO) {
        val user = uid ?: return@withContext
        flushMutex.withLock {
        var gradesNeedRetry=false
        for (row in dao.list(user,"review_pending").sortedBy { Codec.decodeFromString<ReviewEvent>(it.body).occurredAt }) {
            if (uid != user) return@withContext
            val event = Codec.decodeFromString<ReviewEvent>(row.body)
            val result = call("syncReviewEvent",Codec.encodeToJsonElement(event).toCloud() as Map<String,Any?>)
            if (uid != user) return@withContext
            database.runInTransaction {
                dao.remove(user,"review_pending",row.id)
                val p = result["progress"]
                val progressId = "${event.cardId}_${event.direction}"
                // Keep optimistic state while later events for the same card remain queued.
                if (dao.list(user,"review_pending").none { val r = Codec.decodeFromString<ReviewEvent>(it.body); r.cardId == event.cardId && r.direction == event.direction }) {
                    if (p == null || p is JsonNull) dao.remove(user,"progress",progressId)
                    else dao.put(StoredDocument(user,"progress",progressId,p.toString()))
                }
            }
        }
        for (row in dao.list(user,"grade_pending")) {
            if (uid != user) return@withContext
            val request = Codec.decodeFromString<GradeRequest>(row.body)
            val currentSession = dao.get(user,"sessions",request.sessionId)?.let { Codec.decodeFromString<StudySession>(it.body) }
            if (currentSession == null || currentSession.grades[request.itemKey]?.manual == true) {
                dao.remove(user,"grade_pending",row.id); continue
            }
            try {
                val result = call("gradeAnswer",mapOf("requestId" to request.id,"card" to Codec.encodeToJsonElement(request.item.card).toCloud(),
                    "direction" to request.item.direction,"answer" to request.answer,"variety" to request.item.variety))
                if (uid != user) return@withContext
                database.runInTransaction {
                    val saved = dao.get(user,"sessions",request.sessionId)
                    if (saved != null) {
                        val s = Codec.decodeFromString<StudySession>(saved.body)
                        if (s.grades[request.itemKey]?.manual != true) writeSession(user,s.copy(grades = s.grades + (request.itemKey to
                            Grade(request.answer,result["verdict"]!!.jsonPrimitive.content,result["explanation"]!!.jsonPrimitive.content)),revision = s.revision+1))
                    }
                    dao.remove(user,"grade_pending",row.id)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                gradesNeedRetry=true
                // Keep pending work; a provider failure is never an incorrect answer.
            }
        }
        for (row in dao.list(user,"session_pending")) {
            if (uid != user) return@withContext
            val saved = dao.get(user,"sessions",row.id) ?: continue
            val session = Codec.decodeFromString<StudySession>(saved.body)
            if (session.syncConflict) continue
            try {
                val result = call("saveStudySession",mapOf("requestId" to "${session.id}_${session.revision}","id" to session.id,
                    "expectedVersion" to session.serverVersion,"session" to Codec.encodeToJsonElement(session.copy(undoJson = null,undoEventId = null,undoProgress = null)).toCloud()))
                if (uid != user) return@withContext
                database.runInTransaction {
                    val latest = dao.get(user,"sessions",row.id)?.let { Codec.decodeFromString<StudySession>(it.body) } ?: return@runInTransaction
                    dao.put(StoredDocument(user,"sessions",row.id,Codec.encodeToString(latest.copy(serverVersion = result["version"]!!.jsonPrimitive.int))))
                    if (latest.revision == session.revision) dao.remove(user,"session_pending",row.id)
                }
            } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
                if (e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
                    database.runInTransaction {
                        val latest=dao.get(user,"sessions",row.id)?.let{Codec.decodeFromString<StudySession>(it.body)}
                        if(latest!=null)dao.put(StoredDocument(user,"sessions",row.id,Codec.encodeToString(latest.copy(syncConflict=true))))
                    }
                } else throw e
            }
        }
        for (row in dao.list(user,"reminder_pending")) {
            if (uid != user) return@withContext
            call("applyReminderAction", Codec.parseToJsonElement(row.body).toCloud() as Map<String,Any?>)
            dao.remove(user,"reminder_pending",row.id)
        }
        check(!gradesNeedRetry) { "Some answers are awaiting grading" }
        }
    }
}

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as FlashApplication).repository
        if (repository.uid == null || inputData.getString("uid") != repository.uid) return Result.success()
        return try {
            repository.flush()
            runCatching { repository.registerDevice() }
            StudyAlerts.reschedule(applicationContext,repository.uid!!)
            Result.success()
        }
        catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun enqueue(context: Context) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val work = OneTimeWorkRequestBuilder<SyncWorker>().setInputData(workDataOf("uid" to uid)).addTag("sync-$uid")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-$uid",ExistingWorkPolicy.APPEND_OR_REPLACE,work)
        }
    }
}
