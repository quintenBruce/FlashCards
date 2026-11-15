package com.qcbru.flashcards

import org.junit.Assert.*
import org.junit.Test
import com.qcbru.flashcards.alerts.ReminderClock
import java.time.Instant

class StudyEngineTest {
    private val deck=Deck(id="deck",title="Spanish",version=1,downloaded=true)
    private val card=FlashCard(id="card",deckId="deck",term="year",definition="año",version=1)
    @Test fun confidenceIsDirectionalAndOneHighFinishes() {
        val s=StudyEngine.create(listOf(deck),listOf(card),emptyList(),"all","both",false,"practice",20)
        assertEquals(2,s.items.size)
        val rated=StudyEngine.rated(s.copy(revealed=true),"high")
        assertFalse(rated.finished);assertEquals(1,rated.completed.size)
        assertTrue(StudyEngine.rated(rated.copy(revealed=true),"high").finished)
    }
    @Test fun ratingsRequireRevealAndSmallQueuesNeverWait() {
        val s=StudyEngine.create(listOf(deck),listOf(card),emptyList(),"all","term",false,"practice",20)
        try { StudyEngine.rated(s,"high");fail("Must reveal") } catch (_:IllegalArgumentException) {}
        assertEquals(s.queue,StudyEngine.rated(s.copy(revealed=true),"low").queue)
    }
    @Test fun accentsAreNotStripped() {
        val item=StudyItem("key",card,"term","en","es-419","es-419")
        assertTrue(exactAnswer(item," AÑO "));assertFalse(exactAnswer(item,"ano"))
        assertNotEquals(normalized("si"),normalized("sí"))
    }
    @Test fun insufficientChoicesFallsBackToTyped() {
        val s=StudyEngine.create(listOf(deck),listOf(card),emptyList(),"all","term",false,"multiple",20)
        assertTrue(s.items.single().choices.isEmpty())
    }
    @Test fun metadataVersionDoesNotInvalidateLearning() {
        val p=Progress("card_term","card","term","high",1,1,"event",0)
        assertTrue(eligible(card.copy(version=2,contentVersion=1),"term","known",listOf(p)))
        assertFalse(eligible(card.copy(version=2,contentVersion=2),"term","known",listOf(p)))
    }
    @Test fun csvRoundTripsQuotesNewlinesAndUnicode() {
        val c=card.copy(term="say \"hello\"",definition="hola, amigo\n¿qué tal?")
        val rows=ImportExport.parse(ImportExport.delimited(listOf(c),','),"deck",emptyList(),',')
        assertEquals(c.term,rows.single().card!!.term);assertEquals(c.definition,rows.single().card!!.definition)
        assertTrue(ImportExport.parse("year\taño\nyear\taño","deck",emptyList(),'\t').last().duplicate)
    }
    @Test fun dstGapAndOverlapMatchServerPolicy() {
        val gap=Schedule(timeZone="America/Chicago",hour=2,minute=30)
        assertEquals(Instant.parse("2027-03-14T08:00:00Z").toEpochMilli(),ReminderClock.next(gap,Instant.parse("2027-03-14T06:00:00Z").toEpochMilli()))
        val overlap=Schedule(timeZone="America/Chicago",hour=1,minute=30)
        assertEquals(Instant.parse("2026-11-02T07:30:00Z").toEpochMilli(),ReminderClock.next(overlap,Instant.parse("2026-11-01T06:31:00Z").toEpochMilli()))
    }
    @Test fun practiceSubsetKeepsDeckScopeForResume() {
        val cards=(1..120).map{card.copy(id="card_$it",order=it.toLong())}
        val session=StudyEngine.create(listOf(deck),cards,emptyList(),"all","term",false,"practice",20)
        assertEquals(20,session.items.size)
        assertEquals(listOf("deck"),session.deckIds)
    }
    @Test fun legacyArchivedDecksRemainAccessibleInTrashOnlyWorkflow() {
        val session=StudyEngine.create(listOf(deck.copy(archived=true)),listOf(card),emptyList(),"all","term",false,"practice",20)
        assertEquals(1,session.items.size)
    }    @Test fun trashedDecksAndPurgedDecksCannotBeStudied() {
        for(unavailable in listOf(deck.copy(deletedAt=1),deck.copy(purgeRequested=true))) {
            val session=StudyEngine.create(listOf(unavailable),listOf(card),emptyList(),"all","term",false,"practice",20)
            assertTrue(session.items.isEmpty())
        }
    }}
