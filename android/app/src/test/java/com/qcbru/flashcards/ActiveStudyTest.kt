package com.qcbru.flashcards

import org.junit.Assert.*
import org.junit.Test

class ActiveStudyTest {
    private val card=FlashCard(id="card",deckId="deck")
    private fun session(id:String="one",finished:Boolean=false,at:Long=1)=StudySession(
        id=id,deckIds=listOf("deck"),finished=finished,updatedAt=at,
        items=listOf(StudyItem("key",card,"term","en","es","es-419")))
    @Test fun anotherModeStillResumesTheSameDeckSession() {
        val existing=session().copy(mode="test")
        assertEquals(existing,resumableStudy(listOf(existing),setOf("deck")))
    }
    @Test fun completedAndSubmittedSessionsFreeTheDeck() {
        assertNull(resumableStudy(listOf(session(finished=true),session("two").copy(submitted=true)),setOf("deck")))
    }
    @Test fun combinedStudyOccupiesEachDeck() {
        val combined=session().copy(deckIds=listOf("deck","other"))
        assertEquals(combined,resumableStudy(listOf(combined),setOf("other")))
    }
    @Test fun legacySessionInfersScopeFromCardSnapshots() {
        val legacy=session().copy(deckIds=emptyList())
        assertEquals(legacy,resumableStudy(listOf(legacy),setOf("deck")))
        assertNull(resumableStudy(listOf(legacy),setOf("different")))
    }
    @Test fun legacyDuplicatesChooseNewestDeterministically() {
        assertEquals("two",resumableStudy(listOf(session(),session("two",at=2)),setOf("deck"))?.id)
    }
}
