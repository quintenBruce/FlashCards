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
