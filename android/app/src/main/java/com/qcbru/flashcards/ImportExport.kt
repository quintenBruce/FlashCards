package com.qcbru.flashcards

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

data class ImportRow(val number: Int, val card: FlashCard?, val error: String? = null, val duplicate: Boolean = false)
object ImportExport {
    fun parse(source: String, deckId: String, existing: List<FlashCard>, delimiter: Char): List<ImportRow> {
        require(source.toByteArray().size <= 2_000_000) { "Import is limited to 2 MB" }
        val rows = mutableListOf<List<String>>(); val row = mutableListOf<String>(); val field = StringBuilder()
        var quoted = false; var i = 0
        val input = source.removePrefix("\uFEFF")
        while (i < input.length) {
            val c = input[i]
            when {
                c == '"' && quoted && i + 1 < input.length && input[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == delimiter -> { row.add(field.toString()); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    row.add(field.toString()); field.clear(); rows.add(row.toList()); row.clear()
                    if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                }
                else -> field.append(c)
            }; i++
        }
        require(!quoted) { "Unclosed quoted field" }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row.toList()) }
        val seen = existing.map { normalized(it.term) to normalized(it.definition) }.toMutableSet()
        return rows.mapIndexedNotNull { index, values ->
            if (values.all { it.isBlank() }) return@mapIndexedNotNull null
            if (index == 0 && values.take(2).map(::normalized) == listOf("term", "definition")) return@mapIndexedNotNull null
            if (values.size < 2 || values[0].isBlank() || values[1].isBlank()) ImportRow(index + 1, null, "Term and definition are required")
            else if (values[0].length > 2000 || values[1].length > 4000) ImportRow(index + 1, null, "Card text exceeds supported length")
            else {
                val pair = normalized(values[0]) to normalized(values[1]); val duplicate = !seen.add(pair)
                ImportRow(index + 1, FlashCard(deckId = deckId, term = values[0].trim(), definition = values[1].trim(),
                    explanation = values.getOrElse(2) { "" }, example = values.getOrElse(3) { "" }, order = (existing.size + index).toLong()), duplicate = duplicate)
            }
        }
    }
    fun delimited(cards: List<FlashCard>, delimiter: Char): String {
        fun escaped(value: String) = "\"${value.replace("\"", "\"\"")}\""
        return (listOf(listOf("term","definition","explanation","example")) + cards.filter { it.deletedAt == null }.map {
            listOf(it.term, it.definition, it.explanation, it.example)
        }).joinToString("\r\n") { row -> row.joinToString(delimiter.toString()) { escaped(it) } }
    }
    fun backup(source: String): Backup {
        require(source.toByteArray().size <= 20_000_000) { "Backup exceeds 20 MB" }
        val backup = Codec.decodeFromString<Backup>(source.removePrefix("\uFEFF"))
        require(backup.format == "flashcards" && backup.schemaVersion == 1) { "Unsupported backup format" }
        require(backup.collections.keys.all { it in listOf("decks","cards","progress","reviews","sessions","reminders","conversations","turns") }) { "Unexpected backup collection" }
        for ((kind,rows) in backup.collections) for (row in rows) {
            val recordId = row["id"]?.jsonPrimitive?.content ?: error("A $kind record is missing its ID")
            require(recordId.matches(Regex("[a-zA-Z0-9_-]{1,100}"))) { "Invalid backup ID" }
            when (kind) {
                "decks" -> Codec.decodeFromJsonElement<Deck>(row).also { require(it.title.isNotBlank()) { "Deck title is required" } }
                "cards" -> Codec.decodeFromJsonElement<FlashCard>(row).also { require(it.deckId.isNotBlank()&&it.term.isNotBlank()&&it.definition.isNotBlank()) { "Invalid backup card" } }
                "progress" -> Codec.decodeFromJsonElement<Progress>(row)
                "reviews" -> Codec.decodeFromJsonElement<ReviewEvent>(row)
                "sessions" -> Codec.decodeFromJsonElement<StudySession>(row["value"] ?: error("Missing session snapshot")).also { require(it.id==recordId) { "Session ID mismatch" } }
                "reminders" -> Codec.decodeFromJsonElement<StudyReminder>(row).also {
                    java.time.ZoneId.of(it.schedule.timeZone)
                    require(it.schedule.kind in listOf("once","daily","weekdays")&&it.schedule.hour in 0..23&&it.schedule.minute in 0..59) { "Invalid backup schedule" }
                }
                "conversations" -> Codec.decodeFromJsonElement<Conversation>(row)
                "turns" -> Codec.decodeFromJsonElement<Turn>(row)
            }
        }
        return backup
    }
    fun remap(backup: Backup): Backup {
        val ids = backup.collections.values.flatten().mapNotNull { it["id"]?.jsonPrimitive?.content }.distinct().associateWith { newId() }.toMutableMap()
        backup.collections["cards"].orEmpty().forEach { card ->
            val old = card["id"]!!.jsonPrimitive.content
            for (direction in listOf("term","definition")) ids["${old}_$direction"] = "${ids[old]}_$direction"
        }
        // Progress IDs contain card IDs; keep server lookup conventions after remapping.
        backup.collections["progress"].orEmpty().forEach { p ->
            val old = p["id"]!!.jsonPrimitive.content; val card = p["cardId"]!!.jsonPrimitive.content
            ids[old] = "${ids[card] ?: card}_${p["direction"]!!.jsonPrimitive.content}"
        }
        fun replace(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.mapValues { replace(it.value) })
            is JsonArray -> JsonArray(element.map(::replace))
            is JsonPrimitive -> if (element.isString) JsonPrimitive(ids[element.content] ?: element.content) else element
            else -> element
        }
        return backup.copy(collections = backup.collections.mapValues { (_, rows) -> rows.map { replace(it).jsonObject } })
    }
}
