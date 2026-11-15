package com.qcbru.flashcards

import kotlinx.serialization.Serializable

/** Prefer the newest saved session when older snapshots claim the same deck. */
fun activeStudies(sessions: List<StudySession>): List<StudySession> {
    val occupied = mutableSetOf<String>()
    return sessions.filter { !it.finished && !it.submitted && it.supersededBy == null && it.items.isNotEmpty() }
        .sortedWith(compareByDescending<StudySession> { it.updatedAt }.thenByDescending { it.id })
        .filter { session ->
            val scope = session.studyDeckIds()
            if (scope.any { it in occupied }) false else {
                occupied.addAll(scope)
                scope.isNotEmpty()
            }
        }
}

internal fun selectedStudyDecks(decks: List<Deck>, selected: Set<String>, online: Boolean): List<Deck> {
    val active = decks.filter { it.deletedAt == null && !it.purgeRequested }
    require(selected.all { id -> active.any { it.id == id } }) { "A selected deck is unavailable. Choose your decks again." }
    val scope = if (selected.isEmpty()) active.filter { online || it.downloaded } else active.filter { it.id in selected }
    require(scope.isNotEmpty()) { if (online) "Add a deck with cards to start studying." else "Connect to load a deck before studying offline." }
    require(online || scope.all { it.downloaded }) { "Connect to load the selected decks before studying offline." }
    return scope
}

internal fun availableStudyDecks(decks: List<Deck>, sessions: List<StudySession>, online: Boolean): List<Deck> {
    val occupied = activeStudies(sessions).flatMap { it.studyDeckIds() }.toSet()
    return decks.filter { it.deletedAt == null && !it.purgeRequested && it.id !in occupied && (online || it.downloaded) }
}

/** Null selects all available decks. An empty explicit selection selects none. */
internal fun newStudyScope(decks: List<Deck>, sessions: List<StudySession>, selected: Set<String>?, online: Boolean): List<Deck> {
    require(selected == null || selected.isNotEmpty()) { "Choose at least one deck." }
    return selectedStudyDecks(availableStudyDecks(decks, sessions, online), selected.orEmpty(), online)
}

internal fun canReturnToPrevious(session: StudySession, sessions: List<StudySession>): Boolean =
    session.undoJson != null && session.supersededBy == null &&
        activeStudies(sessions).none { it.id != session.id && it.studyDeckIds().intersect(session.studyDeckIds()).isNotEmpty() }

@Serializable
internal data class StudyOptions(
    val filter: String = "all",
    val direction: String = "term",
    val shuffle: Boolean = true,
    val mode: String = "practice",
    val count: Int = 20
) {
    fun validate() {
        require(filter in listOf("all", "struggling", "known", "starred", "unrated")) { "Choose which cards to study." }
        require(direction in listOf("term", "definition", "both")) { "Choose a card direction." }
        require(mode in listOf("practice", "browse", "typed", "multiple", "test")) { "Choose a study mode." }
        require(count in 1..100) { "Choose between 1 and 100 cards." }
    }
}

internal fun matchingStudyCount(decks: List<Deck>, cards: List<FlashCard>, progress: List<Progress>, options: StudyOptions): Int? {
    if (decks.any { !it.downloaded }) return null
    val scope = decks.map { it.id }.toSet()
    val directions = if (options.direction == "both") listOf("term", "definition") else listOf(options.direction)
    return cards.filter { it.deckId in scope }.sumOf { card -> directions.count { eligible(card, it, options.filter, progress) } }
}

internal data class SessionProgress(val completed: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

internal fun studySessionProgress(session: StudySession): SessionProgress {
    val keys = session.items.map { it.key }.toSet()
    val completed = when (session.mode) {
        "practice" -> session.completed.toSet().count { it in keys }
        "browse" -> if (session.finished) keys.size else session.cursor.coerceIn(0, keys.size)
        else -> session.grades.keys.count { it in keys }
    }
    return SessionProgress(completed, keys.size)
}
