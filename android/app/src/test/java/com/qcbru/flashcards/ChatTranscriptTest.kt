package com.qcbru.flashcards

import org.junit.Assert.*
import org.junit.Test

class ChatTranscriptTest {
    private fun request(id:String,at:Long)=ChatRequest(id=id,conversationId="c",deckId="d",message=id,createdAt=at)
    @Test fun oldFailedRequestCannotDisplaceLatestReplyAtBottom() {
        val entries=chatTranscript(listOf(Turn(id="new",createdAt=20)),listOf(request("old",10).copy(state="failed")))
        assertEquals(listOf("turn_new","request_old"),entries.map{it.id})
    }
    @Test fun latestOutgoingRequestAppearsAtBottom() {
        val entries=chatTranscript(listOf(Turn(id="old",createdAt=10)),listOf(request("new",20)))
        assertEquals("request_new",entries.first().id)
    }
    @Test fun savedReplyReplacesItsPendingRequest() {
        val entries=chatTranscript(listOf(Turn(id="same",createdAt=20)),listOf(request("same",10)))
        assertEquals(1,entries.size)
        assertNotNull(entries.single().turn)
        assertNull(entries.single().request)
    }
    @Test fun clearingDeckHistoryTargetsOnlyThatDeck() {
        val conversations=listOf(Conversation("mine","d"),Conversation("other","other"))
        val requests=listOf(request("pending",1),request("unrelated",2).copy(deckId="other",conversationId="other"))
        assertEquals(setOf("mine","c"),deckConversationIds("d",conversations,requests))
        assertEquals(setOf("other"),deckConversationIds("other",conversations,requests))
    }
    @Test fun pendingChatIsIncludedBeforeConversationMetadataExists() {
        assertEquals(setOf("c"),deckConversationIds("d",emptyList(),listOf(request("pending",1))))
        assertTrue(deckConversationIds("unknown",emptyList(),listOf(request("pending",1))).isEmpty())
    }}
