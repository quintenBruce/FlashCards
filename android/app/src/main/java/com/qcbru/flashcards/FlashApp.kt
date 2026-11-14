@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.qcbru.flashcards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import java.time.*
import java.time.format.DateTimeFormatter

@Serializable data object HomeRoute : NavKey
@Serializable data class DeckRoute(val id: String, val chat: Boolean = false) : NavKey
@Serializable data class SessionRoute(val id: String) : NavKey

@Composable fun FlashApp(state: FlashState, model: FlashViewModel, onSignIn: () -> Unit,
    onExport: (String,String) -> Unit, onOpenFile: () -> Unit, onNotifications: () -> Unit,
    onSpeak: (String,String) -> Unit, onVoices: () -> List<Pair<String,String>>) {
    val stack = rememberNavBackStack(HomeRoute)
    var tab by rememberSaveable { mutableStateOf("Decks") }
    var editDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var newDeck by rememberSaveable { mutableStateOf(false) }
    var setupDecks by rememberSaveable { mutableStateOf<String?>(null) }
    var setupFilter by rememberSaveable { mutableStateOf("all") }
    var importDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) { if(state.notice.isNotEmpty()) { snackbar.showSnackbar(state.notice); model.clearNotice() } }
    LaunchedEffect(state.uid) { if(state.uid == null) { stack.clear(); stack.add(HomeRoute) } }
    LaunchedEffect(state.pendingReminder,state.reminders) {
        val reminder = state.reminders.find { it.id == state.pendingReminder }
        if(reminder != null) { setupDecks=reminder.deckIds.joinToString(","); setupFilter=reminder.filter; model.clearReminder() }
    }
    val inSession=stack.lastOrNull() is SessionRoute
    val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide=maxWidth>=840.dp
        Scaffold(containerColor=MaterialTheme.colorScheme.background,
            snackbarHost={ SnackbarHost(snackbar) },
            topBar={ if(!inSession) TopAppBar(title={ Text("flashcards",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold) },
                navigationIcon={ if(stack.size>1) IconButton(onClick={stack.removeLastOrNull()}) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back") } },
                actions={ if(state.uid!=null) IconButton(onClick=model::refresh,enabled=state.online&&!state.busy) { Icon(Icons.Default.Refresh,"Refresh") } }) },
            bottomBar={ if(state.uid!=null&&!inSession&&!wide&&!keyboardVisible) NavigationBar {
                listOf("Decks","Review","Reminders","Settings").forEach { name -> NavigationBarItem(selected=tab==name,
                    onClick={tab=name;stack.clear();stack.add(HomeRoute)},icon={Icon(tabIcon(name),name)},label={Text(name)}) }
            } }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if(wide&&state.uid!=null&&!inSession) NavigationRail {
                    listOf("Decks","Review","Reminders","Settings").forEach { name -> NavigationRailItem(selected=tab==name,
                        onClick={tab=name;stack.clear();stack.add(HomeRoute)},icon={Icon(tabIcon(name),name)},label={Text(name)}) }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if(!state.online&&state.uid!=null) Surface(color=MaterialTheme.colorScheme.secondaryContainer) {
                        Text("Offline · Downloaded study and progress are available",Modifier.fillMaxWidth().padding(12.dp),style=MaterialTheme.typography.bodySmall)
                    }
                    if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(state.uid==null) Welcome(onSignIn,state.busy)
                    else NavDisplay(backStack=stack,onBack={ if(stack.size>1) stack.removeLastOrNull() },entryProvider={ key ->
                        when(key) {
                            is HomeRoute -> NavEntry(key) {
                                when(tab) {
                                    "Decks" -> DecksScreen(state,model,onDeck={stack.add(DeckRoute(it))},onCreate={newDeck=true},
                                        onAI={stack.add(DeckRoute(newId(),true))},onImport={importDeckId=it},onResume={stack.add(SessionRoute(it))},
                                        onEdit={editDeckId=it})
                                    "Review" -> ReviewScreen(state,onStudy={ids,filter->setupDecks=ids.joinToString(",");setupFilter=filter},onResume={stack.add(SessionRoute(it))})
                                    "Reminders" -> RemindersScreen(state,model,onNotifications)
                                    else -> SettingsScreen(state,model,onExport,onOpenFile,onVoices)
                                }
                            }
                            is DeckRoute -> NavEntry(key) { DeckScreen(key,state,model,onEdit={editDeckId=it},onImport={importDeckId=it},
                                onStudy={setupDecks=key.id;setupFilter="all"},onExport=onExport,onSpeak=onSpeak) }
                            is SessionRoute -> NavEntry(key) { SessionScreen(state.sessions.find {it.id==key.id},state,model,
                                onExit={stack.removeLastOrNull()},onSpeak=onSpeak) }
                            else -> error("Unknown navigation destination")
                        }
                    })
                }
            }
        }
    }
    if(newDeck||editDeckId!=null) DeckEditor(state.decks.find {it.id==editDeckId},state.busy,
        onDismiss={newDeck=false;editDeckId=null},onSave={deck->model.saveDeck(deck) {newDeck=false;editDeckId=null}})
    if(setupDecks!=null) StudySetup(setupFilter,state.busy,onDismiss={setupDecks=null},onStart={filter,direction,shuffle,mode,count->
        val ids=setupDecks!!.split(',').filter {it.isNotEmpty()}.toSet()
        model.startSession(ids,filter,direction,shuffle,mode,count) {id->setupDecks=null;stack.add(SessionRoute(id))}
    })
    importDeckId?.let { id -> state.decks.find {it.id==id}?.let { deck -> ImportDialog(deck,state,model,onOpenFile,onDismiss={importDeckId=null;model.imported("")}) } }
}
private fun tabIcon(name: String)=when(name) { "Decks"->Icons.Default.Style;"Review"->Icons.Default.School;"Reminders"->Icons.Default.Notifications;else->Icons.Default.Settings }
@Composable private fun Welcome(onSignIn:()->Unit,busy:Boolean) {
    Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center) {
        Icon(Icons.Default.Style,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp));Text("Make it stick.",style=MaterialTheme.typography.headlineLarge)
        Text("Your words, your pace. Build a deck, practice both ways, and find confidence one card at a time.",Modifier.padding(vertical=20.dp),style=MaterialTheme.typography.bodyLarge)
        Button(onClick=onSignIn,enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("Continue with Google")}
        Text("Private access for the app owner",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun Heading(title:String,subtitle:String="") {
    Text(title,style=MaterialTheme.typography.headlineMedium)
    if(subtitle.isNotEmpty())Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
}
@Composable private fun Empty(title:String,body:String) {
    Column(Modifier.fillMaxWidth().padding(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(title,style=MaterialTheme.typography.titleLarge);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
@Composable private fun DecksScreen(state:FlashState,model:FlashViewModel,onDeck:(String)->Unit,onCreate:()->Unit,onAI:()->Unit,
    onImport:(String)->Unit,onResume:(String)->Unit,onEdit:(String)->Unit) {
    var search by rememberSaveable{mutableStateOf("")};var sort by rememberSaveable{mutableStateOf("Recent")};var archived by rememberSaveable{mutableStateOf(false)}
    val decks=state.decks.filter{it.deletedAt==null&&!it.purgeRequested&&it.archived==archived&&it.title.contains(search,true)}
        .let {if(sort=="Name")it.sortedBy{d->d.title.lowercase()}else it.sortedByDescending{d->d.updatedAt}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item{Heading("Your decks","A little practice, a lasting difference.")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onCreate,enabled=state.online&&!state.busy){Icon(Icons.Default.Add,null);Text("New deck")};OutlinedButton(onAI,enabled=state.online&&!state.busy){Text("Create with AI")}}}
        item{OutlinedTextField(search,{search=it},label={Text("Search decks")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(sort=="Name",{sort=if(sort=="Name")"Recent"else"Name"},label={Text("Sort: $sort")});FilterChip(archived,{archived=!archived},label={Text("Archived")})}}
        val unfinished=state.sessions.firstOrNull{!it.finished}
        if(unfinished!=null)item{FilledTonalButton({onResume(unfinished.id)},modifier=Modifier.fillMaxWidth()){Text("Resume ${unfinished.title}")}}
        if(state.loading)item{CircularProgressIndicator()}
        if(decks.isEmpty())item{Empty("Room for something new","Create a deck manually or ask AI to build one from a topic or pasted notes.")}
        items(decks,key={it.id}){deck->
            Card(onClick={onDeck(deck.id)},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(deck.title,style=MaterialTheme.typography.titleLarge)
                    if(deck.description.isNotBlank())Text(deck.description,maxLines=3)
                    Text("${deck.termLanguage} ↔ ${deck.definitionLanguage}",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    Text(if(deck.downloaded)"${state.cards.count{it.deckId==deck.id&&it.deletedAt==null}} cards · Available offline" else "Download to study offline",style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton({model.download(deck)},enabled=state.online&&!state.busy){Text(if(deck.downloaded)"Update download"else"Download")}
                        TextButton({onEdit(deck.id)},enabled=state.online&&!state.busy){Text("Edit")}
                        TextButton({onImport(deck.id)},enabled=state.online&&!state.busy){Text("Import")}
                    }
                }
            }
        }
    }
}
@Composable private fun ReviewScreen(state:FlashState,onStudy:(Set<String>,String)->Unit,onResume:(String)->Unit) {
    var selected by rememberSaveable{mutableStateOf(listOf<String>())}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Heading("Keep it fresh","Review across decks, or revisit what you already know.")}
        items(state.decks.filter{it.deletedAt==null&&!it.archived},key={it.id}){d->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(d.id in selected,{selected=if(it)selected+d.id else selected-d.id});Text(d.title)}}
        item{Text("No selection includes all active decks.",style=MaterialTheme.typography.bodySmall)}
        item{Button({onStudy(selected.toSet(),"struggling")},modifier=Modifier.fillMaxWidth()){Text("Practice struggling cards")}}
        item{OutlinedButton({onStudy(selected.toSet(),"known")},modifier=Modifier.fillMaxWidth()){Text("Refresh known cards")}}
        item{OutlinedButton({onStudy(selected.toSet(),"all")},modifier=Modifier.fillMaxWidth()){Text("Study all selected decks")}}
        item{Text("Session history",style=MaterialTheme.typography.titleLarge)}
        if(state.sessions.isEmpty())item{Empty("Your first session awaits","Study history and unfinished sessions will appear here.")}
        items(state.sessions,key={it.id}){s->ListItem(headlineContent={Text(s.title)},supportingContent={Text("${s.mode} · ${if(s.finished)"Complete"else"Paused"}${if(s.syncConflict)" · saved on this device; cloud conflict"else""}")},trailingContent={TextButton({onResume(s.id)}){Text(if(s.finished)"View"else"Resume")}})}
    }
}
@Composable private fun DeckScreen(route:DeckRoute,state:FlashState,model:FlashViewModel,onEdit:(String)->Unit,onImport:(String)->Unit,
    onStudy:()->Unit,onExport:(String,String)->Unit,onSpeak:(String,String)->Unit) {
    val deck=state.decks.find{it.id==route.id}
    var tab by rememberSaveable(route.id){mutableStateOf(if(route.chat)"Chat"else"Cards")}
    var cardId by rememberSaveable(route.id){mutableStateOf<String?>(null)};var newCard by rememberSaveable(route.id){mutableStateOf(false)}
    var search by rememberSaveable(route.id){mutableStateOf("")};var stars by rememberSaveable(route.id){mutableStateOf(false)}
    var trash by rememberSaveable(route.id){mutableStateOf(false)};var deleteCardId by rememberSaveable{mutableStateOf<String?>(null)}
    val cards=state.cards.filter{it.deckId==route.id}.sortedBy{it.order}
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal=20.dp,vertical=12.dp)){Heading(deck?.title?:"Create a deck with AI",deck?.description?:"Describe what you want to learn. Review changes before saving.")}
        PrimaryTabRow(selectedTabIndex=listOf("Cards","Study","Chat").indexOf(tab)) {
            listOf("Cards","Study","Chat").forEach{name->Tab(tab==name,{tab=name},text={Text(name)})}
        }
        if(tab=="Chat")ChatScreen(route.id,state,model)
        else if(deck==null)Empty("No deck saved yet","Open Chat to create the deck, then approve the proposed changes.")
        else if(deck.deletedAt!=null)Empty("This deck is in trash","Restore it from Settings to study or edit it.")
        else if(tab=="Study")Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Practice your way",style=MaterialTheme.typography.titleLarge)
            Text("Choose cards, direction, and a study mode. English → Spanish and Spanish → English confidence are tracked separately.")
            if(!deck.downloaded)Button({model.download(deck)},enabled=state.online&&!state.busy){Text("Download complete deck")}
            Button(onStudy,enabled=deck.downloaded){Text("Set up a session")}
            for(direction in listOf("term","definition")) {
                Text(if(direction=="term")"Term first"else"Definition first",style=MaterialTheme.typography.titleMedium)
                Text(listOf("struggling","known","unrated").joinToString(" · "){filter->"${cards.count{eligible(it,direction,filter,state.progress)}} $filter"})
            }
        } else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({newCard=true},enabled=state.online&&!state.busy){Text("Add card")};TextButton({onEdit(deck.id)},enabled=state.online&&!state.busy){Text("Deck settings")};TextButton({onImport(deck.id)},enabled=state.online&&!state.busy){Text("Import")}}}
            item{OutlinedTextField(search,{search=it},label={Text("Find a card")},modifier=Modifier.fillMaxWidth())}
            item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(stars,{stars=!stars},label={Text("Starred")});FilterChip(trash,{trash=!trash},label={Text("Trash")});TextButton({onExport(ImportExport.delimited(cards,'\t'),"${deck.title}.tsv")},enabled=deck.downloaded){Text("Export TSV")};TextButton({onExport(ImportExport.delimited(cards,','),"${deck.title}.csv")},enabled=deck.downloaded){Text("CSV")}}}
            val shown=cards.filter{(it.deletedAt!=null)==trash&&(!stars||it.starred)&&(it.term.contains(search,true)||it.definition.contains(search,true))&&!it.purgeRequested}
            if(shown.isEmpty())item{Empty("No cards here yet","Add cards manually, import a list, or ask AI in Chat.")}
            items(shown,key={it.id}){card->Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically){Text(card.term,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));IconButton({model.saveCard(card.copy(starred=!card.starred))},enabled=state.online&&!state.busy&&!trash){Icon(if(card.starred)Icons.Default.Star else Icons.Default.StarBorder,if(card.starred)"Unstar card"else"Star card")}}
                    Text(card.definition);if(card.example.isNotBlank())Text(card.example,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        if(trash){TextButton({model.contentAction("cards",card.id,card.version,"restore")},enabled=state.online&&!state.busy){Text("Restore")};TextButton({deleteCardId=card.id}){Text("Delete permanently")}}
                        else {TextButton({cardId=card.id},enabled=state.online&&!state.busy){Text("Edit")};TextButton({model.saveCard(card.copy(id=newId(),version=0,order=(cards.maxOfOrNull{it.order}?:0)+1000))},enabled=state.online&&!state.busy){Text("Copy")};IconButton({model.moveCard(card,-1)},enabled=state.online&&!state.busy){Icon(Icons.Default.KeyboardArrowUp,"Move card up")};IconButton({model.moveCard(card,1)},enabled=state.online&&!state.busy){Icon(Icons.Default.KeyboardArrowDown,"Move card down")};IconButton({deleteCardId=card.id}){Icon(Icons.Default.DeleteOutline,"Move card to trash")}}
                    }
                }
            }}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton({model.duplicateDeck(deck)},enabled=state.online&&!state.busy&&deck.downloaded){Text("Duplicate deck")};TextButton({model.saveDeck(deck.copy(archived=!deck.archived))},enabled=state.online&&!state.busy){Text(if(deck.archived)"Unarchive"else"Archive")};TextButton({deleteCardId="deck"},enabled=state.online&&!state.busy){Text("Trash deck")}}}
        }
    }
    if(newCard||cardId!=null)CardEditor(cards.find{it.id==cardId}?:FlashCard(deckId=route.id,order=(cards.maxOfOrNull{it.order}?:0)+1000),state.busy,
        onDismiss={newCard=false;cardId=null},onSave={card->model.saveCard(card){newCard=false;cardId=null}})
    deleteCardId?.let{id->ConfirmDialog(if(trash)"Permanently delete this card?"else"Move to trash?","Deleted content can be restored for 30 days unless permanently deleted.",onDismiss={deleteCardId=null}){
        if(id=="deck"&&deck!=null)model.contentAction("decks",deck.id,deck.version,"trash")
        else cards.find{it.id==id}?.let{model.contentAction("cards",it.id,it.version,if(trash)"purge"else"trash")};deleteCardId=null
    }}
}
@Composable private fun ChatScreen(deckId:String,state:FlashState,model:FlashViewModel) {
    val conversationIds=state.conversations.filter{it.deckId==deckId}.sortedByDescending{it.updatedAt}.map{it.id}
    var conversation by rememberSaveable(deckId){mutableStateOf("${deckId}_${newId()}")}
    var draft by rememberSaveable(deckId,conversation){mutableStateOf("")};var details by rememberSaveable{mutableStateOf(false)}
    var deleting by rememberSaveable{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=12.dp)) {
            TextButton({conversation="${deckId}_${newId()}"}){Text("New conversation")}
            conversationIds.forEachIndexed{index,cid->FilterChip(conversation==cid,{conversation=cid},label={Text("Chat ${index+1}")})}
            TextButton({deleting=true}){Text("Delete chat")}
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            val turns=state.turns.filter{it.conversationId==conversation}.sortedBy{it.createdAt}
            if(turns.isEmpty())item{Empty("Build understanding","Try “Create 30 travel phrases with natural Spanish examples” or “Explain when these words use estar.” Changes wait for your approval.")}
            items(turns,key={it.id}){turn->
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium){Text(turn.user,Modifier.padding(14.dp))}
                    Text(turn.assistant)
                    val proposal=state.proposals.find{it.id==turn.proposalId}
                    if(proposal!=null)OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("${proposal.operations.size} proposed changes",style=MaterialTheme.typography.titleMedium)
                        Text(proposal.operations.groupingBy{it.kind.replace('_',' ')}.eachCount().entries.joinToString(" · "){"${it.value} ${it.key}"})
                        TextButton({details=!details}){Text(if(details)"Hide before/after"else"Review before/after")}
                        if(details)proposal.operations.forEach{o->Text(o.kind.replace('_',' '),fontWeight=FontWeight.SemiBold);o.before?.let{Text("Before: $it",style=MaterialTheme.typography.bodySmall)};Text("After: ${o.after?:"Move to trash"}",style=MaterialTheme.typography.bodySmall)}
                        if(proposal.state=="pending"&&proposal.expiresAt>System.currentTimeMillis())Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button({model.confirm(proposal,true)},enabled=state.online&&!state.busy){Text("Approve request")}
                            OutlinedButton({model.confirm(proposal,false)},enabled=state.online&&!state.busy){Text("Reject")}
                        }else Text(if(proposal.state=="pending")"Expired — ask for a fresh preview"else proposal.state.replaceFirstChar{it.uppercase()})
                    }}
                }
            }
        }
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft,{draft=it},label={Text("Ask about this deck")},modifier=Modifier.weight(1f),maxLines=5,enabled=!state.busy)
            Button({model.sendMessage(deckId,conversation,draft){draft=""}},enabled=state.online&&!state.busy&&draft.isNotBlank()){Text("Send")}
        }
    }
    if(deleting)ConfirmDialog("Delete conversation?","This removes its messages and pending proposals.",{deleting=false}){model.deleteConversation(conversation);conversation="${deckId}_${newId()}";deleting=false}
}
@Composable private fun SessionScreen(session:StudySession?,state:FlashState,model:FlashViewModel,onExit:()->Unit,onSpeak:(String,String)->Unit) {
    if(session==null){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};return}
    var answer by rememberSaveable(session.id,session.cursor){mutableStateOf("")}
    Column(Modifier.fillMaxSize().imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onExit){Icon(Icons.Default.Close,"Pause and exit session")};Text(session.title,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onExit){Text("Save & exit")}}
        if(session.syncConflict)Text("This session is saved on this device. Another device changed its cloud copy.",color=MaterialTheme.colorScheme.error)
        val item=session.current
        if(session.finished||session.submitted) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Heading("Session complete",if(session.mode=="practice")"Every selected card reached High. Come back whenever you’re ready."else"Review your answers below.")
                if(session.mode=="practice")Text("${session.completed.size} directional cards completed")
                else {
                    val graded=session.grades.values.mapNotNull{it.points};val pending=session.grades.values.count{it.points==null}
                    Text("${"%.1f".format(graded.sum())} / ${session.items.size} points${if(pending>0)" · $pending awaiting grading or review"else""}",style=MaterialTheme.typography.titleLarge)
                    session.items.forEach{question->
                        val grade=session.grades[question.key]
                        Text(question.prompt,fontWeight=FontWeight.SemiBold);Text("Your answer: ${grade?.answer.orEmpty()}");Text("Expected: ${question.answer}")
                        GradeFeedback(grade){verdict->model.overrideGrade(session,question.key,verdict)}
                        HorizontalDivider()
                    }
                }
            }
            if(session.undoJson!=null)OutlinedButton({model.undo(session)},enabled=!state.busy){Text("Undo last rating")}
            Button(onExit,modifier=Modifier.fillMaxWidth()){Text("Done")}
        }else if(item!=null) {
            val completed=if(session.mode=="practice")session.completed.size else session.cursor
            Text("${completed} / ${session.items.size} complete · ${session.mode}",style=MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress={completed.toFloat()/session.items.size.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth())
            if(session.mode in listOf("practice","browse")) {
                Text(if(session.mode=="practice")"Flip, then choose confidence. Swipe left: Low · right: High."else"Tap to flip. Swipe to browse.",style=MaterialTheme.typography.bodySmall)
                val context=LocalContext.current
                val animationMs=if(android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f)0 else 200
                AnimatedContent(targetState=item,modifier=Modifier.weight(1f),transitionSpec={
                    slideInHorizontally(tween(animationMs)){it} togetherWith slideOutHorizontally(tween(animationMs)){-it}
                },label="Next card"){shown->key(shown.key){FlashCardFace(shown,if(shown.key==item.key)session.revealed else false,onFlip={if(!state.busy)model.reveal(session)},onSpeak=onSpeak,onSwipe={right->
                    if(!state.busy){if(session.mode=="browse")model.advance(session,if(right)-1 else 1)else if(session.revealed)model.rate(session,if(right)"high"else"low")}
                })}}
                if(session.mode=="practice") {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("low" to "↻ Low","medium" to "≈ Medium","high" to "✓ High").forEach{(value,label)->
                            FilledTonalButton({model.rate(session,value)},enabled=session.revealed&&!state.busy,modifier=Modifier.weight(1f).heightIn(min=52.dp),contentPadding=PaddingValues(8.dp)){Text(label)}
                        }
                    }
                    TextButton({model.undo(session)},enabled=session.undoJson!=null&&!state.busy){Text("Undo last rating")}
                }else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton({model.advance(session,-1)},enabled=session.cursor>0&&!state.busy){Text("Previous")};OutlinedButton({model.advance(session,1)},enabled=session.cursor<session.items.lastIndex&&!state.busy){Text("Next")}}
            }else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(item.prompt,style=MaterialTheme.typography.headlineMedium)
                    TextButton({onSpeak(item.prompt,item.promptLanguage)}){Icon(Icons.AutoMirrored.Filled.VolumeUp,null);Text("Listen")}
                    val grade=session.grades[item.key]
                    if(item.choices.isEmpty())OutlinedTextField(answer,{answer=it},label={Text("Your answer")},modifier=Modifier.fillMaxWidth(),enabled=grade==null&&!state.busy)
                    else item.choices.forEach{choice->OutlinedButton({answer=choice},enabled=grade==null&&!state.busy,modifier=Modifier.fillMaxWidth()){if(answer==choice)Icon(Icons.Default.Check,null);Text(choice)}}
                    if(grade==null)Button({model.answer(session,answer)},enabled=!state.busy&&(answer.isNotBlank()||session.mode=="test")){Text("Save answer")}
                    else {
                        if(session.mode!="test"){Text("Expected: ${item.answer}");GradeFeedback(grade){model.overrideGrade(session,item.key,it)}}
                        else Text("Answer saved. Feedback appears after submission.")
                        Button({model.advance(session)},enabled=!state.busy){Text(if(session.cursor==session.items.lastIndex)"Submit ${if(session.mode=="test")"test"else"session"}"else"Next question")}
                    }
                }
            }
        }
    }
}
@Composable fun FlashCardFace(item:StudyItem,revealed:Boolean,onFlip:()->Unit,onSpeak:(String,String)->Unit,onSwipe:(Boolean)->Unit) {
    val context=LocalContext.current
    val reduced=android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val rotation by animateFloatAsState(if(revealed)180f else 0f,tween(if(reduced)0 else 220),label="Card flip")
    var drag by remember{mutableFloatStateOf(0f)}
    Card(onClick=onFlip,modifier=Modifier.fillMaxSize().heightIn(min=220.dp).graphicsLayer{rotationY=rotation;cameraDistance=16*density}
        .pointerInput(item.key,revealed){detectHorizontalDragGestures(onDragStart={drag=0f},onHorizontalDrag={change,amount->change.consume();drag+=amount},onDragEnd={if(kotlin.math.abs(drag)>100)onSwipe(drag>0);drag=0f})}
        .semantics{contentDescription=if(revealed)"Answer: ${item.answer}"else"Prompt: ${item.prompt}";onClick(label="Flip card"){onFlip();true}},
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
        val back=rotation>90
        Column(Modifier.fillMaxSize().graphicsLayer{rotationY=if(back)180f else 0f}.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            Text(if(back)"Answer"else"Prompt",style=MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(20.dp));Text(if(back)item.answer else item.prompt,style=MaterialTheme.typography.headlineMedium)
            if(back&&item.card.example.isNotBlank())Text(item.card.example,Modifier.padding(top=16.dp))
            if(back&&item.card.explanation.isNotBlank())Text(item.card.explanation,Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyMedium)
            TextButton({onSpeak(if(back)item.answer else item.prompt,if(back)item.answerLanguage else item.promptLanguage)}){Icon(Icons.AutoMirrored.Filled.VolumeUp,"Play visible side");Text("Listen")}
            Text("Tap to flip",style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun GradeFeedback(grade:Grade?,onOverride:(String)->Unit) {
    if(grade==null)return
    Text(when(grade.verdict){"pending"->"Awaiting AI grading — reconnect or check budget";"needs_review"->"Needs your review";else->grade.verdict.replace('_',' ').replaceFirstChar{it.uppercase()}},style=MaterialTheme.typography.titleSmall)
    if(grade.explanation.isNotBlank())Text(grade.explanation)
    Row(Modifier.horizontalScroll(rememberScrollState())){listOf("correct","partial","incorrect").forEach{verdict->TextButton({onOverride(verdict)}){Text("Mark $verdict")}}}
}

@Composable private fun StudySetup(initialFilter:String,busy:Boolean,onDismiss:()->Unit,onStart:(String,String,Boolean,String,Int)->Unit) {
    var filter by rememberSaveable{mutableStateOf(initialFilter)};var direction by rememberSaveable{mutableStateOf("term")}
    var shuffle by rememberSaveable{mutableStateOf(true)};var mode by rememberSaveable{mutableStateOf("practice")};var count by rememberSaveable{mutableStateOf("20")}
    FormDialog("Set up study",onDismiss) {
        Choice("Cards",listOf("all","starred","struggling","known","unrated"),filter,{filter=it})
        Choice("First side",listOf("term","definition","both"),direction,{direction=it})
        Choice("Mode",listOf("browse","practice","typed","multiple","test"),mode,{mode=it})
        Row(verticalAlignment=Alignment.CenterVertically){Switch(shuffle,{shuffle=it});Text("Shuffle cards")}
        if(mode in listOf("test","typed","multiple"))Field("Questions (1–100)",count,{count=it})
        Text("One High rating completes a card in this session. Low and Medium return later.")
        Button({onStart(filter,direction,shuffle,mode,count.toIntOrNull()?.coerceIn(1,100)?:20)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Start session")}
    }
}
@Composable private fun DeckEditor(original:Deck?,busy:Boolean,onDismiss:()->Unit,onSave:(Deck)->Unit) {
    val base=remember(original?.id){original?:Deck()}
    var title by rememberSaveable{mutableStateOf(base.title)};var description by rememberSaveable{mutableStateOf(base.description)}
    var term by rememberSaveable{mutableStateOf(base.termLanguage)};var definition by rememberSaveable{mutableStateOf(base.definitionLanguage)}
    var variety by rememberSaveable{mutableStateOf(base.spanishVariety)}
    FormDialog(if(original==null)"New deck"else"Edit deck",onDismiss) {
        Field("Title",title,{title=it});Field("Description",description,{description=it},false)
        Field("Term language",term,{term=it});Field("Definition language",definition,{definition=it})
        Choice("Spanish variety",listOf("es-419","es-MX","es-ES"),variety,{variety=it})
        Button({onSave(base.copy(title=title,description=description,termLanguage=term,definitionLanguage=definition,spanishVariety=variety))},enabled=title.isNotBlank()&&term.isNotBlank()&&definition.isNotBlank()&&!busy,modifier=Modifier.fillMaxWidth()){Text("Save deck")}
    }
}
@Composable private fun CardEditor(base:FlashCard,busy:Boolean,onDismiss:()->Unit,onSave:(FlashCard)->Unit) {
    var term by rememberSaveable{mutableStateOf(base.term)};var definition by rememberSaveable{mutableStateOf(base.definition)}
    var example by rememberSaveable{mutableStateOf(base.example)};var explanation by rememberSaveable{mutableStateOf(base.explanation)}
    var termAlt by rememberSaveable{mutableStateOf(base.termAlternatives.joinToString("\n"))};var definitionAlt by rememberSaveable{mutableStateOf(base.definitionAlternatives.joinToString("\n"))}
    FormDialog(if(base.version==0)"New card"else"Edit card",onDismiss) {
        Field("Term",term,{term=it},false);Field("Definition",definition,{definition=it},false)
        Field("Example sentence",example,{example=it},false);Field("Explanation",explanation,{explanation=it},false)
        Field("Alternative terms (one per line)",termAlt,{termAlt=it},false);Field("Alternative definitions (one per line)",definitionAlt,{definitionAlt=it},false)
        Button({onSave(base.copy(term=term,definition=definition,example=example,explanation=explanation,termAlternatives=termAlt.lines().filter{it.isNotBlank()},definitionAlternatives=definitionAlt.lines().filter{it.isNotBlank()}))},enabled=term.isNotBlank()&&definition.isNotBlank()&&!busy,modifier=Modifier.fillMaxWidth()){Text("Save card")}
    }
}
@Composable private fun ImportDialog(deck:Deck,state:FlashState,model:FlashViewModel,onOpenFile:()->Unit,onDismiss:()->Unit) {
    var source by rememberSaveable{mutableStateOf("")};var delimiter by rememberSaveable{mutableStateOf("Tab")};var preview by rememberSaveable{mutableStateOf(false)}
    LaunchedEffect(state.importText){if(state.importText.isNotEmpty()){source=state.importText;model.imported("");preview=false}}
    val parsed=remember(source,delimiter,preview,state.cards){if(preview)runCatching{ImportExport.parse(source,deck.id,state.cards.filter{it.deckId==deck.id},if(delimiter=="Tab")'\t'else',')}else null}
    FormDialog("Import into ${deck.title}",onDismiss) {
        Text("Paste term/definition rows or select a UTF-8 CSV/TSV file. Duplicate rows are skipped.")
        TextButton(onOpenFile){Text("Open file")};Choice("Separator",listOf("Tab","Comma"),delimiter,{delimiter=it;preview=false})
        Field("Content",source,{source=it;preview=false},false)
        OutlinedButton({preview=true},enabled=source.isNotBlank()){Text("Preview import")}
        parsed?.onFailure{Text(it.message?:"Invalid file",color=MaterialTheme.colorScheme.error)}?.getOrNull()?.let{rows->
            Text("${rows.count{it.card!=null&&!it.duplicate}} cards · ${rows.count{it.duplicate}} duplicates · ${rows.count{it.error!=null}} errors")
            rows.take(100).forEach{row->Text("${row.number}: ${row.error?:if(row.duplicate)"Duplicate — skipped"else"${row.card?.term} → ${row.card?.definition}"}",style=MaterialTheme.typography.bodySmall)}
            Button({model.import(deck,rows.filter{!it.duplicate}.mapNotNull{it.card},onDismiss)},enabled=!state.busy&&rows.none{it.error!=null}&&rows.any{it.card!=null&&!it.duplicate}){Text("Confirm import")}
        }
    }
}
@Composable private fun RemindersScreen(state:FlashState,model:FlashViewModel,onNotifications:()->Unit) {
    var editId by rememberSaveable{mutableStateOf<String?>(null)};var creating by rememberSaveable{mutableStateOf(false)}
    var deleting by rememberSaveable{mutableStateOf<String?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Heading("Make time to remember","A gentle nudge at the time you choose.")}
        item{Button({creating=true;onNotifications()},enabled=state.online&&!state.busy){Text("New reminder")}}
        if(state.reminders.isEmpty())item{Empty("Your schedule is open","Remind yourself to review a deck, struggling cards, or words you already know.")}
        items(state.reminders,key={it.id}){r->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(r.title,style=MaterialTheme.typography.titleMedium)
            Text("${r.filter} · ${if(r.deckIds.isEmpty())"All active decks"else"${r.deckIds.size} selected decks"}")
            Text(r.nextAt?.let{formatDate(it,r.schedule.timeZone)}?:"No future alert")
            Row{TextButton({editId=r.id},enabled=state.online&&!state.busy){Text("Edit")};TextButton({deleting=r.id},enabled=state.online&&!state.busy){Text("Delete")}}
        }}}
    }
    if(creating||editId!=null)ReminderEditor(state.reminders.find{it.id==editId},state,onDismiss={creating=false;editId=null},onSave={r->model.saveReminder(r){creating=false;editId=null}})
    deleting?.let{id->ConfirmDialog("Delete reminder?","Future alerts for this reminder will stop.",{deleting=null}){state.reminders.find{it.id==id}?.let(model::deleteReminder);deleting=null}}
}
@Composable private fun ReminderEditor(original:StudyReminder?,state:FlashState,onDismiss:()->Unit,onSave:(StudyReminder)->Unit) {
    val base=remember(original?.id){original?:StudyReminder()}
    var title by rememberSaveable{mutableStateOf(base.title)};var kind by rememberSaveable{mutableStateOf(base.schedule.kind)}
    var time by rememberSaveable{mutableStateOf("%02d:%02d".format(base.schedule.hour,base.schedule.minute))}
    var date by rememberSaveable{mutableStateOf(base.schedule.at?.let{Instant.ofEpochMilli(it).atZone(ZoneId.of(base.schedule.timeZone)).toLocalDate().toString()}?:LocalDate.now().toString())}
    var zone by rememberSaveable{mutableStateOf(base.schedule.timeZone)};var filter by rememberSaveable{mutableStateOf(base.filter)}
    var days by rememberSaveable{mutableStateOf(base.schedule.weekdays)};var decks by rememberSaveable{mutableStateOf(base.deckIds)}
    var enabled by rememberSaveable{mutableStateOf(base.enabled)};var error by rememberSaveable{mutableStateOf("")}
    FormDialog(if(original==null)"New reminder"else"Edit reminder",onDismiss) {
        Field("Title",title,{title=it});Choice("Repeat",listOf("once","daily","weekdays"),kind,{kind=it})
        if(kind=="once")Field("Date (YYYY-MM-DD)",date,{date=it})
        Field("Time (24-hour HH:mm)",time,{time=it});Field("Timezone",zone,{zone=it})
        if(kind=="weekdays")Row(Modifier.horizontalScroll(rememberScrollState())){(1..7).forEach{day->FilterChip(day in days,{days=if(day in days)days-day else days+day},label={Text(DayOfWeek.of(day).name.take(3).lowercase())})}}
        Choice("Review",listOf("all","struggling","known","starred","unrated"),filter,{filter=it})
        Text("No deck selection includes all active decks.")
        state.decks.filter{it.deletedAt==null&&!it.archived}.forEach{d->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(d.id in decks,{decks=if(it)decks+d.id else decks-d.id});Text(d.title)}}
        Row(verticalAlignment=Alignment.CenterVertically){Switch(enabled,{enabled=it});Text("Enabled")}
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        Button({runCatching {
            val local=LocalTime.parse(time);val timezone=ZoneId.of(zone)
            require(kind!="weekdays"||days.isNotEmpty()){ "Choose at least one weekday" }
            val at=if(kind=="once")LocalDate.parse(date).atTime(local).atZone(timezone).toInstant().toEpochMilli()else null
            require(at==null||at>System.currentTimeMillis()){ "Choose a future time" }
            onSave(base.copy(title=title,deckIds=decks,filter=filter,enabled=enabled,schedule=Schedule(kind,zone,local.hour,local.minute,days,at)))
        }.onFailure{error=it.message?:"Check the schedule"}},enabled=title.isNotBlank()&&!state.busy){Text("Save reminder")}
    }
}
@Composable private fun SettingsScreen(state:FlashState,model:FlashViewModel,onExport:(String,String)->Unit,onOpenFile:()->Unit,onVoices:()->List<Pair<String,String>>) {
    var delete by rememberSaveable{mutableStateOf(false)};var confirmation by rememberSaveable{mutableStateOf("")}
    var restore by rememberSaveable{mutableStateOf(false)};var trash by rememberSaveable{mutableStateOf(false)}
    var purgeId by rememberSaveable{mutableStateOf<String?>(null)};var voices by remember{mutableStateOf(emptyList<Pair<String,String>>())}
    var backupText by rememberSaveable{mutableStateOf("")}
    LaunchedEffect(state.importText){if(state.importText.isNotBlank()){backupText=state.importText;restore=true;model.imported("")}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item{Heading("Settings",state.email)}
        item{Choice("Appearance",listOf("system","light","dark"),state.theme,model::theme)}
        item{Text("Pronunciation",style=MaterialTheme.typography.titleLarge);Text("Playback speed: ${"%.2f".format(state.rate)}×");Slider(state.rate,{model.speech(it,state.voice)},valueRange=.5f..1.3f);TextButton({voices=onVoices()}){Text("Load available voices")}}
        if(voices.isNotEmpty())item{Column{TextButton({model.speech(state.rate,null)}){Text("Use language default")};voices.forEach{(name,label)->TextButton({model.speech(state.rate,name)}){Text("${if(state.voice==name)"✓ "else""}$label")}}}
        item{HorizontalDivider();Text("AI budget",style=MaterialTheme.typography.titleLarge);Text("${state.usage.month.ifBlank{"This month"}} · $${"%.2f".format((state.usage.spent+state.usage.reserved)/1_000_000.0)} / $${"%.2f".format(state.usage.cap/1_000_000.0)}");Text("Includes pending reservations. Resets monthly in America/Chicago. Firebase billing is separate.",style=MaterialTheme.typography.bodySmall);if(state.usage.warning)Text("80% of your AI budget is reserved or spent.",color=MaterialTheme.colorScheme.error);TextButton(model::refresh,enabled=state.online&&!state.busy){Text("Retry pending grades & sync")}}
        item{HorizontalDivider();Text("Backups",style=MaterialTheme.typography.titleLarge);Button({model.exportBackup{text->onExport(text,"flashcards-backup.json")}},enabled=state.online&&!state.busy){Text("Export complete JSON backup")};TextButton(onOpenFile,enabled=state.online&&!state.busy){Text("Open backup to restore")}}
        item{Text("Downloaded decks",style=MaterialTheme.typography.titleLarge)}
        items(state.decks.filter{it.downloaded},key={"download_${it.id}"}){d->Row(verticalAlignment=Alignment.CenterVertically){Text(d.title,Modifier.weight(1f));TextButton({model.removeDownload(d)}){Text("Remove download")}}}
        item{TextButton({trash=!trash}){Text(if(trash)"Hide trash"else"Show trash")}}
        if(trash) {
            items(state.decks.filter{it.deletedAt!=null&&!it.purgeRequested},key={"trash_${it.id}"}){d->Column{Text(d.title);Row{TextButton({model.contentAction("decks",d.id,d.version,"restore")},enabled=state.online&&!state.busy){Text("Restore")};TextButton({purgeId=d.id},enabled=state.online&&!state.busy){Text("Delete permanently")}}}}
        }
        item{HorizontalDivider();OutlinedButton({model.signOut()},enabled=!state.busy){Text("Sign out")};TextButton({delete=true},enabled=state.online&&!state.busy){Text("Delete account and all data")}}
    }
    if(restore)FormDialog("Restore backup",{restore=false}) {
        val parsed=remember(backupText){runCatching{ImportExport.backup(backupText)}}
        parsed.getOrNull()?.let{backup->Text(backup.collections.entries.joinToString("\n"){"${it.key}: ${it.value.size}"});Text("Restore creates new records and preserves your existing decks. Restored reminders are disabled until you review their schedules.");Button({model.restoreBackup(backupText){restore=false;backupText=""}},enabled=!state.busy){Text("Confirm restore")}}
        parsed.exceptionOrNull()?.let{Text(it.message?:"Invalid backup",color=MaterialTheme.colorScheme.error)}
    }
    if(delete)FormDialog("Delete your account",{delete=false}){Text("Access is revoked immediately. Your cloud data will be removed by the cleanup job. Export a backup first if needed.");Field("Type DELETE",confirmation,{confirmation=it});Button({model.signOut(true);delete=false},enabled=confirmation=="DELETE"&&!state.busy){Text("Permanently delete account")}}
    purgeId?.let{id->ConfirmDialog("Permanently delete deck?","The deck and its content will be removed by cleanup. This cannot be undone.",{purgeId=null}){state.decks.find{it.id==id}?.let{model.contentAction("decks",it.id,it.version,"purge")};purgeId=null}}
}
@Composable private fun Choice(label:String,options:List<String>,selected:String,onSelect:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text(label,style=MaterialTheme.typography.labelLarge);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){options.forEach{value->FilterChip(value==selected,{onSelect(value)},label={Text(value.replaceFirstChar{it.uppercase()})})}}}
}
@Composable private fun Field(label:String,value:String,onValue:(String)->Unit,single:Boolean=true) {
    OutlinedTextField(value,onValue,label={Text(label)},singleLine=single,minLines=if(single)1 else 2,maxLines=if(single)1 else 6,modifier=Modifier.fillMaxWidth())
}
@Composable private fun FormDialog(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onDismiss){Surface(shape=MaterialTheme.shapes.large){Column(Modifier.fillMaxWidth().heightIn(max=680.dp).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));IconButton(onDismiss){Icon(Icons.Default.Close,"Close dialog")}}
        content()
    }}}
}
@Composable private fun ConfirmDialog(title:String,body:String,onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton(onConfirm){Text("Confirm")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
private fun formatDate(at:Long,zone:String)=runCatching{Instant.ofEpochMilli(at).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a z"))}.getOrDefault("Invalid schedule")
