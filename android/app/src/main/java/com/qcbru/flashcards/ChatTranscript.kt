package com.qcbru.flashcards

data class ChatTranscriptEntry(
    val id:String,
    val createdAt:Long,
    val turn:Turn?=null,
    val request:ChatRequest?=null,
)

/** Newest first for a bottom-anchored conversation. Receipts replace pending messages. */
fun chatTranscript(turns:List<Turn>,requests:List<ChatRequest>):List<ChatTranscriptEntry> {
    val completedIds=turns.map{it.id}.toSet()
    val replies=turns.map{ChatTranscriptEntry("turn_${it.id}",it.createdAt,turn=it)}
    val pending=requests.filter{it.id !in completedIds}.map{
        ChatTranscriptEntry("request_${it.id}",it.createdAt,request=it)
    }
    return (replies+pending).sortedWith(compareByDescending<ChatTranscriptEntry>{it.createdAt}.thenByDescending{it.id})
}
