package com.qcbru.flashcards

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class FlashCardFaceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun visibleSideHasAccessibleLabelAndFlips() {
        var flipped=false
        val item=StudyItem("card_term",FlashCard(term="year",definition="año"),"term","en","es-419","es-419")
        compose.setContent { FlashTheme("light") { FlashCardFace(item,false,{flipped=true},{_,_->},{}) } }
        compose.onNodeWithContentDescription("Prompt: year").assertExists().performClick()
        org.junit.Assert.assertTrue(flipped)
    }
}
