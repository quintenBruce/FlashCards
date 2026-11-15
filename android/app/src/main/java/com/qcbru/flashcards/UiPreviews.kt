package com.qcbru.flashcards

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name="Phone light",widthDp=360,heightDp=640,showBackground=true)
@Preview(name="Phone large text",widthDp=360,heightDp=640,fontScale=2f,showBackground=true)
@Composable private fun PromptPreview() {
    FlashTheme("light") { Box(Modifier.fillMaxSize().padding(20.dp)) {
        FlashCardFace(sample(),false,{},{_,_->},{})
    } }
}
@Preview(name="Dark answer",widthDp=360,heightDp=640,showBackground=true)
@Preview(name="Landscape answer",widthDp=740,heightDp=360,showBackground=true)
@Composable private fun AnswerPreview() {
    FlashTheme("dark") { Box(Modifier.fillMaxSize().padding(20.dp)) {
        FlashCardFace(sample(),true,{},{_,_->},{})
    } }
}
private fun sample()=StudyItem("example_term",FlashCard(term="I miss you",definition="Te extraño",
    example="Te extraño mucho. ¿Cuándo vuelves?",explanation="Common in Latin America. “Te echo de menos” is also correct."),"term","en-US","es-419","es-419")

@Preview(name="Library",widthDp=360,heightDp=640,showBackground=true)
@Preview(name="Library large text",widthDp=360,heightDp=640,fontScale=2f,showBackground=true)
@Preview(name="Wide library",widthDp=760,heightDp=640,showBackground=true)
@Composable private fun LibraryPreview() {
    FlashTheme("light") {
        DecksScreen(FlashState(decks=listOf(
            Deck(id="travel",title="Travel Spanish",description="Order a meal, ask for directions and buy a train ticket.",downloaded=true),
            Deck(id="class",title="Words from this week's class on the past tense",
                description="Practice the verbs from Tuesday's lesson by telling a story about a place you visited and the people you met along the way."),
            Deck(id="plants",title="Local wildflowers")
        ),cards=listOf(FlashCard(deckId="travel"))),onDeck={})
    }
}

@Preview(name="Chat bubbles",widthDp=360,heightDp=640,showBackground=true)
@Preview(name="Chat bubbles large text",widthDp=360,heightDp=640,fontScale=2f,showBackground=true)
@Composable private fun ChatBubblesPreview() {
    FlashTheme("light") {
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            ChatBubble(fromUser=true) { androidx.compose.material3.Text("Create a deck of travel phrases.") }
            ChatBubble { androidx.compose.material3.Text("Here are useful phrases for your trip. Review the cards before saving.") }
            ChatBubble(fromUser=true) { androidx.compose.material3.Text("Add phrases for ordering food, too.") }
            LoadingMessageBubble()
        }
    }
}

@Preview(name="Study phone",widthDp=360,heightDp=740,showBackground=true)
@Preview(name="Study large text",widthDp=360,heightDp=740,fontScale=2f,showBackground=true)
@Preview(name="Study wide",widthDp=760,heightDp=740,showBackground=true)
@Composable private fun StudyPreview() {
    val item = sample()
    FlashTheme("light") {
        StudyScreen(FlashState(
            decks=listOf(Deck(id=item.card.deckId,title="Travel Spanish",downloaded=true),Deck(id="class",title="Words from class",downloaded=true)),
            cards=listOf(item.card, item.card.copy(id="class_card",deckId="class")),
            sessions=listOf(StudySession(id="resume",title="Travel Spanish",deckIds=listOf(item.card.deckId),items=listOf(item),queue=listOf(item.key)))
        ),onStart={_,_->},onOpen={},onDecks={})
    }
}
