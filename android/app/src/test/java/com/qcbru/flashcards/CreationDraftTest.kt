package com.qcbru.flashcards

import org.junit.Assert.*
import org.junit.Test

class CreationDraftTest {
    @Test fun canonicalDraftWinsOverLegacyConversations() {
        assertEquals("current",creationDraftId(emptyList(),listOf(Conversation("old","legacy",500)),emptyList(),"current"))
    }
    @Test fun savedAndTrashedDecksAreNeverCreationDrafts() {
        val decks=listOf(Deck(id="saved"),Deck(id="trashed",deletedAt=1))
        assertNull(creationDraftId(decks,listOf(Conversation("a","saved"),Conversation("b","trashed")),emptyList(),"saved"))
    }
    @Test fun legacyDraftMigrationChoosesNewestAndIgnoresDeletedConversations() {
        assertEquals("new",creationDraftId(emptyList(),listOf(Conversation("a","old",1),Conversation("b","new",2),Conversation("c","deleted",3,4)),emptyList(),null))
    }
    @Test fun requestSurvivesMissingConversationMetadata() {
        assertEquals("draft",creationDraftId(emptyList(),emptyList(),listOf(ChatRequest(conversationId="c",deckId="draft",message="Topic")),null))
    }
    @Test fun matchingTurnUnblocksUncertainRequest() {
        val request=ChatRequest(id="r",conversationId="c",deckId="d",message="Topic",state="uncertain")
        assertTrue(requestNeedsRecovery(request,emptyList()))
        assertFalse(requestNeedsRecovery(request,listOf(Turn(id="r"))))
        assertFalse(requestNeedsRecovery(request.copy(state="failed"),emptyList()))
    }
}
