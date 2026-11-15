package com.qcbru.flashcards

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

val Codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun newId(): String = UUID.randomUUID().toString()

@Serializable data class Deck(
    val id: String = newId(), val title: String = "", val description: String = "",
    val termLanguage: String = "en-US", val definitionLanguage: String = "es-419", val spanishVariety: String = "es-419",
    val version: Int = 0, val archived: Boolean = false, val deletedAt: Long? = null, val updatedAt: Long = 0,
    val downloaded: Boolean = false, val purgeRequested: Boolean = false,
)
@Serializable data class FlashCard(
    val id: String = newId(), val deckId: String = "", val term: String = "", val definition: String = "",
    val explanation: String = "", val example: String = "", val termAlternatives: List<String> = emptyList(),
    val definitionAlternatives: List<String> = emptyList(), val starred: Boolean = false, val order: Long = 0,
    val version: Int = 0, val deletedAt: Long? = null, val updatedAt: Long = 0, val purgeRequested: Boolean = false,
    val contentVersion: Int = 0,
) { val learningVersion get() = if (contentVersion > 0) contentVersion else version }
@Serializable data class Progress(
    val id: String, val cardId: String, val direction: String, val confidence: String,
    val cardVersion: Int, val version: Int, val eventId: String, val updatedAt: Long,
)
@Serializable data class StudyItem(
    val key: String, val card: FlashCard, val direction: String, val termLanguage: String,
    val definitionLanguage: String, val variety: String, val choices: List<String> = emptyList(),
) {
    val prompt get() = if (direction == "term") card.term else card.definition
    val answer get() = if (direction == "term") card.definition else card.term
    val alternatives get() = if (direction == "term") card.definitionAlternatives else card.termAlternatives
    val promptLanguage get() = if (direction == "term") termLanguage else definitionLanguage
    val answerLanguage get() = if (direction == "term") definitionLanguage else termLanguage
}
@Serializable data class Grade(val answer: String = "", val verdict: String = "pending", val explanation: String = "", val manual: Boolean = false) {
    val points get(): Double? = when (verdict) { "correct" -> 1.0; "partial" -> .5; "incorrect" -> 0.0; else -> null }
}
@Serializable data class StudySession(
    val deckIds: List<String> = emptyList(), val id: String = newId(), val title: String = "Practice", val mode: String = "practice", val items: List<StudyItem> = emptyList(),
    val queue: List<String> = emptyList(), val completed: List<String> = emptyList(), val grades: Map<String, Grade> = emptyMap(),
    val cursor: Int = 0, val revealed: Boolean = false, val submitted: Boolean = false, val finished: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis(),
    val revision: Int = 0, val serverVersion: Int = 0, val undoJson: String? = null,
    val undoEventId: String? = null, val undoProgress: Progress? = null, val syncConflict: Boolean = false,
) {
    val current get() = if (mode == "practice") items.find { it.key == queue.firstOrNull() } else items.getOrNull(cursor)
}
@Serializable data class ReviewEvent(
    val id: String = newId(), val sessionId: String, val cardId: String, val cardVersion: Int,
    val direction: String, val confidence: String, val expectedVersion: Int, val occurredAt: Long = System.currentTimeMillis(),
    val undoEventId: String? = null,
)
@Serializable data class GradeRequest(val id: String = newId(), val sessionId: String, val itemKey: String, val item: StudyItem, val answer: String)
@Serializable data class Schedule(
    val kind: String = "daily", val timeZone: String = java.time.ZoneId.systemDefault().id,
    val hour: Int = 19, val minute: Int = 0, val weekdays: List<Int> = emptyList(), val at: Long? = null,
)
@Serializable data class StudyReminder(
    val id: String = newId(), val title: String = "Study flashcards", val deckIds: List<String> = emptyList(), val filter: String = "all",
    val schedule: Schedule = Schedule(), val enabled: Boolean = true, val nextAt: Long? = null,
    val version: Int = 0, val updatedAt: Long = 0,
)
@Serializable data class Turn(val id: String = "", val conversationId: String = "", val user: String = "", val assistant: String = "", val proposalId: String? = null, val createdAt: Long = 0)
@Serializable data class Conversation(val id: String, val deckId: String, val updatedAt: Long = 0, val deletedAt: Long? = null)
@Serializable data class ChatRequest(
    val id: String = newId(), val conversationId: String, val deckId: String, val message: String,
    val state: String = "pending", val error: String = "", val createdAt: Long = System.currentTimeMillis(),
)
@Serializable data class ProposalOperation(val kind: String, val targetId: String, val expectedVersion: Int,
    val before: JsonObject? = null, val after: JsonObject? = null)
@Serializable data class Proposal(val id: String, val deckId: String, val conversationId: String,
    val operations: List<ProposalOperation>, val state: String = "pending", val expiresAt: Long = 0)
@Serializable data class Usage(val month: String = "", val spent: Long = 0, val reserved: Long = 0, val cap: Long = 10_000_000, val warning: Boolean = false)
@Serializable data class Backup(val format: String = "flashcards", val schemaVersion: Int = 1, val exportedAt: Long = System.currentTimeMillis(), val collections: Map<String, List<JsonObject>>)

fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC).trim().replace(Regex("\\s+"), " ").lowercase(Locale.forLanguageTag("es"))
fun exactAnswer(item: StudyItem, answer: String): Boolean = (listOf(item.answer) + item.alternatives).any { normalized(it) == normalized(answer) }
fun eligible(card: FlashCard, direction: String, filter: String, progress: List<Progress>): Boolean {
    val p = progress.find { it.cardId == card.id && it.direction == direction && it.cardVersion == card.learningVersion }
    return card.deletedAt == null && !card.purgeRequested && when (filter) {
        "starred" -> card.starred
        "struggling" -> p?.confidence in listOf("low", "medium")
        "known" -> p?.confidence == "high"
        "unrated" -> p == null
        else -> true
    }
}
object StudyEngine {
    fun create(decks: List<Deck>, cards: List<FlashCard>, progress: List<Progress>, filter: String, direction: String,
        shuffle: Boolean, mode: String, count: Int): StudySession {
        val active = decks.filter { !it.archived && it.deletedAt == null && it.downloaded }.associateBy { it.id }
        val pool = cards.sortedWith(compareBy({ it.deckId }, { it.order })).flatMap { card ->
            val deck = active[card.deckId] ?: return@flatMap emptyList()
            (if (direction == "both") listOf("term", "definition") else listOf(direction)).filter { eligible(card, it, filter, progress) }.map { dir ->
                StudyItem("${card.id}_$dir", card, dir, deck.termLanguage, deck.definitionLanguage, deck.spanishVariety)
            }
        }.let { if (shuffle) it.shuffled() else it }
        val selected = pool.take(count.coerceIn(1,100))
        val items = selected.mapIndexed { index, item ->
            if (mode != "multiple" && !(mode == "test" && index % 2 == 0)) item
            else {
                val known = (listOf(item.answer) + item.alternatives).map(::normalized).toSet()
                val distractors = pool.filter { it.direction == item.direction && it.answerLanguage == item.answerLanguage }
                    .filter { other -> (listOf(other.answer) + other.alternatives).none { normalized(it) in known } }
                    .map { it.answer }.distinctBy(::normalized).shuffled().take(3)
                item.copy(choices = if (distractors.size == 3) (distractors + item.answer).shuffled() else emptyList())
            }
        }
        return StudySession(deckIds=active.keys.toList(), title = if (active.size == 1) active.values.first().title else "Combined review", mode = mode,
            items = items, queue = items.map { it.key }, finished = items.isEmpty())
    }
    fun rated(session: StudySession, confidence: String): StudySession {
        require(session.mode == "practice" && session.revealed && confidence in listOf("low","medium","high"))
        val current = session.queue.first(); val queue = session.queue.drop(1).toMutableList()
        if (confidence != "high") queue.add(minOf(queue.size, if (confidence == "low") 2 else 5), current)
        return session.copy(queue = queue, completed = if (confidence == "high") session.completed + current else session.completed,
            revealed = false, finished = queue.isEmpty(), revision = session.revision + 1, updatedAt = System.currentTimeMillis())
    }
}

fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { it.key.toString() to it.value.toJson() })
    is Iterable<*> -> JsonArray(map { it.toJson() })
    else -> JsonPrimitive(toString())
}
fun JsonElement.toCloud(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> entries.associate { it.key to it.value.toCloud() }
    is JsonArray -> map { it.toCloud() }
    is JsonPrimitive -> if (isString) content else booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
}
