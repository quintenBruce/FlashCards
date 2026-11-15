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
}
