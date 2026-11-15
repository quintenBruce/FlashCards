package com.qcbru.flashcards

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DecksScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchByDescriptionOpenDeckAndClearSearch() {
        val library = FlashState(decks = listOf(
            Deck(id = "travel", title = "Travel Spanish", description = "Ordering coffee"),
            Deck(id = "botany", title = "Wildflowers"),
            Deck(id = "trash", title = "Old coffee notes", deletedAt = 1)
        ))
        var openedDeck = ""
        compose.setContent { FlashTheme("light") { DecksScreen(library) { openedDeck = it } } }
        compose.onNode(hasSetTextAction()).performTextInput("COFFEE")
        compose.onNodeWithText("Wildflowers").assertDoesNotExist()
        compose.onNodeWithText("Old coffee notes").assertDoesNotExist()
        compose.onNodeWithText("Ordering coffee").performClick()
        compose.runOnIdle { assertEquals("travel", openedDeck) }
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("Wildflowers").assertExists()
    }
}
