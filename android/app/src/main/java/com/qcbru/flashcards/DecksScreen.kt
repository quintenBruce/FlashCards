@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.qcbru.flashcards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DecksScreen(state: FlashState, onDeck: (String) -> Unit) {
    var search by rememberSaveable(state.uid) { mutableStateOf("") }
    val query = search.trim()
    val decks = remember(state.decks, query) {
        state.decks.filter { deck ->
            deck.deletedAt == null && !deck.purgeRequested &&
                (deck.title.contains(query, ignoreCase = true) || deck.description.contains(query, ignoreCase = true))
        }.sortedByDescending { it.updatedAt }
    }
    val counts = remember(state.cards) {
        state.cards.asSequence().filter { it.deletedAt == null && !it.purgeRequested }
            .groupingBy { it.deckId }.eachCount()
    }
    val gridState = rememberLazyGridState()
    val focusManager = LocalFocusManager.current
    fun changeSearch(value: String) {
        search = value
        gridState.requestScrollToItem(0)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TextField(
            value = search,
            onValueChange = { changeSearch(it) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            singleLine = true,
            label = { Text("Search decks") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { changeSearch("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        if (decks.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                if (state.loading) {
                    LoadingIndicator(modifier = Modifier.semantics { contentDescription = "Loading decks" })
                } else {
                    Text(
                        if (query.isEmpty()) "No decks yet" else "No matching decks",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(288.dp),
                state = gridState,
                contentPadding = PaddingValues(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(decks, key = { it.id }) { deck ->
                    LibraryDeckCard(deck, if (deck.downloaded) counts[deck.id] ?: 0 else null) { onDeck(deck.id) }
                }
            }
        }
    }
}

@Composable
private fun LibraryDeckCard(deck: Deck, cardCount: Int?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(deck.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (deck.description.isNotBlank()) {
                Text(deck.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (cardCount != null) {
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(if (cardCount == 1) "1 card" else "$cardCount cards", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
