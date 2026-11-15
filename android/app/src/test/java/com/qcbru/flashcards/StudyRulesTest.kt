package com.qcbru.flashcards

import org.junit.Assert.*
import org.junit.Test

class StudyRulesTest {
    private val deck = Deck(id = "one", title = "Spanish", downloaded = true)
    private val second = deck.copy(id = "two", title = "French")
    private val card = FlashCard(id = "card", deckId = deck.id, term = "year", definition = "año")
    private val item = StudyItem("card_term", card, "term", "en", "es", "es-419")
    private fun session(id: String = "session", scope: List<String> = listOf(deck.id), at: Long = 1) =
        StudySession(id = id, deckIds = scope, items = listOf(item), queue = listOf(item.key), updatedAt = at)

    @Test fun onlyCanonicalUnfinishedSessionsAppearInContinue() {
        val old = session(id = "old")
        val latest = session(id = "latest", at = 2)
        val results = activeStudies(listOf(
            old, latest, session("done").copy(finished = true),
            session("submitted").copy(submitted = true),
            session("replaced").copy(supersededBy = "latest"),
            session("empty").copy(items = emptyList())
        ))
        assertEquals(listOf("latest"), results.map { it.id })
    }

    @Test fun combinedLegacyOverlapDoesNotResumeTwoSessionsForOneDeck() {
        val combined = session("combined", listOf("one", "two"))
        val newer = session("newer", listOf("one"), at = 2)
        assertEquals(listOf("newer"), activeStudies(listOf(combined, newer)).map { it.id })
        assertNull(resumableStudy(listOf(combined, newer), setOf("two")))
    }

    @Test fun independentSessionsCanBothContinueAndTiesAreStable() {
        val a = session("a")
        val b = session("b")
        val other = session("other", listOf("two"))
        assertEquals(listOf("other", "b"), activeStudies(listOf(a, b, other)).map { it.id })
    }

    @Test fun startingNewStudyExcludesOccupiedAndUnavailableDecks() {
        val hidden = second.copy(id = "hidden", deletedAt = 1)
        val purged = second.copy(id = "purged", purgeRequested = true)
        assertEquals(listOf("two"), newStudyScope(listOf(deck, second, hidden, purged), listOf(session()), null, true).map { it.id })
        assertThrows(IllegalArgumentException::class.java) {
            newStudyScope(listOf(deck, second), listOf(session()), setOf("one"), true)
        }
    }

    @Test fun deselectingAllDoesNotStartAcrossEveryDeck() {
        assertThrows(IllegalArgumentException::class.java) {
            newStudyScope(listOf(deck, second), emptyList(), emptySet(), true)
        }
        assertEquals(2, newStudyScope(listOf(deck, second), emptyList(), null, true).size)
    }

    @Test fun missingExplicitDeckDoesNotSilentlyExpandScope() {
        assertThrows(IllegalArgumentException::class.java) {
            selectedStudyDecks(listOf(deck), setOf("removed"), true)
        }
    }

    @Test fun offlineAllDecksUsesSavedDecksAndExplicitUncachedSelectionFails() {
        val remote = second.copy(downloaded = false)
        assertEquals(listOf(deck), selectedStudyDecks(listOf(deck, remote), emptySet(), false))
        assertThrows(IllegalArgumentException::class.java) {
            selectedStudyDecks(listOf(deck, remote), setOf("two"), false)
        }
        assertEquals(listOf(remote), selectedStudyDecks(listOf(remote), emptySet(), true))
    }

    @Test fun unavailableOfflineLibraryCannotStartAnEmptySession() {
        assertThrows(IllegalArgumentException::class.java) {
            selectedStudyDecks(listOf(deck.copy(downloaded = false)), emptySet(), false)
        }
    }

    @Test fun countAndDirectionMatchEngineForVersionedProgress() {
        val progress = Progress("card_term", card.id, "term", "high", 1, 1, "event", 0)
        val saved = card.copy(version = 2, contentVersion = 1)
        val options = StudyOptions(filter = "known", direction = "both", shuffle = false)
        val count = matchingStudyCount(listOf(deck), listOf(saved), listOf(progress), options)
        assertEquals(1, count)
        assertEquals(StudyEngine.create(listOf(deck), listOf(saved), listOf(progress), options.filter, options.direction, false, options.mode, 100).items.size, count)
        assertEquals(0, matchingStudyCount(listOf(deck), listOf(saved.copy(contentVersion = 2)), listOf(progress), options))
    }

    @Test fun countsExcludeDeletedPurgedAndUnselectedCards() {
        assertEquals(1, matchingStudyCount(listOf(deck), listOf(
            card, card.copy(id = "trash", deletedAt = 1), card.copy(id = "purged", purgeRequested = true), card.copy(deckId = "two")
        ), emptyList(), StudyOptions()))
    }

    @Test fun unknownDeckContentsNeverClaimZeroAvailableCards() {
        assertNull(matchingStudyCount(listOf(deck.copy(downloaded = false)), emptyList(), emptyList(), StudyOptions()))
    }

    @Test fun knownEmptyFilterReportsZeroWithoutCreatingSession() {
        assertEquals(0, matchingStudyCount(listOf(deck), listOf(card), emptyList(), StudyOptions(filter = "struggling")))
    }

    @Test fun optionsRejectInvalidCountsAndUnknownValues() {
        listOf(0, -1, 101).forEach { count ->
            assertThrows(IllegalArgumentException::class.java) { StudyOptions(count = count).validate() }
        }
        assertThrows(IllegalArgumentException::class.java) { StudyOptions(mode = "other").validate() }
        assertThrows(IllegalArgumentException::class.java) { StudyOptions(direction = "other").validate() }
        assertThrows(IllegalArgumentException::class.java) { StudyOptions(filter = "other").validate() }
        StudyOptions(count = 1).validate()
        StudyOptions(count = 100).validate()
    }

    @Test fun practiceProgressCountsOnlyUniqueCurrentItems() {
        val progress = studySessionProgress(session().copy(completed = listOf(item.key, item.key, "old")))
        assertEquals(SessionProgress(1, 1), progress)
        assertEquals(1f, progress.fraction)
    }

    @Test fun browseAndWrittenProgressUseTheirActualSavedState() {
        assertEquals(0, studySessionProgress(session().copy(mode = "browse")).completed)
        assertEquals(1, studySessionProgress(session().copy(mode = "browse", finished = true)).completed)
        val written = session().copy(mode = "test", grades = mapOf(item.key to Grade(answer = ""), "old" to Grade()))
        assertEquals(SessionProgress(1, 1), studySessionProgress(written))
        assertEquals(0f, studySessionProgress(written.copy(items = emptyList())).fraction)
    }

    @Test fun previousCannotReopenFinishedHistoryWhileDeckIsOccupied() {
        val finished = session("done").copy(finished = true, undoJson = "{}")
        assertFalse(canReturnToPrevious(finished, listOf(finished, session("new"))))
        assertTrue(canReturnToPrevious(finished, listOf(finished)))
        assertFalse(canReturnToPrevious(finished.copy(supersededBy = "new"), listOf(finished)))
        assertFalse(canReturnToPrevious(finished.copy(undoJson = null), listOf(finished)))
    }
}
