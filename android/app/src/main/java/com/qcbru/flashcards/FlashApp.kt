@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.qcbru.flashcards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import java.time.*
import java.time.format.DateTimeFormatter

@Serializable data object HomeRoute : NavKey
@Serializable data class DeckRoute(val id: String) : NavKey
@Serializable data class ChatRoute(val id: String, val creating: Boolean = false, val conversationId: String? = null) : NavKey
@Serializable data class SecondaryRoute(val name: String) : NavKey
@Serializable data class SessionRoute(val id: String) : NavKey

@Composable fun FlashApp(state: FlashState, model: FlashViewModel, onSignIn: () -> Unit,
    onNotifications: () -> Unit,
    onSpeak: (String,String) -> Unit, onVoices: () -> List<Pair<String,String>>) {
    val stack = rememberNavBackStack(HomeRoute)
    var addSheet by rememberSaveable { mutableStateOf(false) }
    var draftPrompt by rememberSaveable { mutableStateOf(false) }
    var clearChatDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var newDeck by rememberSaveable { mutableStateOf(false) }
    var editDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var setupDecks by rememberSaveable { mutableStateOf<String?>(null) }
    var setupFilter by rememberSaveable { mutableStateOf("all") }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        if(state.notice.isNotEmpty()) { snackbar.showSnackbar(state.notice); model.clearNotice() }
    }
    LaunchedEffect(state.uid) {
        if(state.uid == null) { stack.clear(); stack.add(HomeRoute); addSheet=false; draftPrompt=false; clearChatDeckId=null; newDeck=false; editDeckId=null; setupDecks=null }
    }
    LaunchedEffect(state.pendingReminder,state.reminders) {
        state.reminders.find { it.id==state.pendingReminder }?.let {
            setupDecks=it.deckIds.joinToString(","); setupFilter=it.filter; model.clearReminder()
        }
    }
    val current=stack.lastOrNull()
    var menu by remember(current) { mutableStateOf(false) }
    var deckTrash by rememberSaveable((current as? DeckRoute)?.id) { mutableStateOf(false) }
    val currentDeck=(current as? DeckRoute)?.let{route->state.decks.find{it.id==route.id}}
    val title=when(current) {
        is DeckRoute -> state.decks.find { it.id==current.id }?.title ?: "Deck"
        is ChatRoute -> if(current.creating) "Create with AI" else "Ask AI"
        is SecondaryRoute -> if(current.name=="Study history") "Study" else current.name
        else -> stringResource(R.string.app_name)
    }
    Scaffold(snackbarHost={SnackbarHost(snackbar)},
        topBar={if(current !is SessionRoute) TopAppBar(title={Text(title)},
            navigationIcon={if(current is DeckRoute || current is ChatRoute) IconButton({stack.removeLastOrNull()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},
            actions={
                if(state.uid!=null && current is ChatRoute && !current.creating) {
                    IconButton({clearChatDeckId=current.id},enabled=state.online&&!state.busy) {
                        Icon(Icons.Default.DeleteSweep,"Clear conversation history")
                    }
                }
                if(state.uid!=null && currentDeck!=null) Box {
                IconButton({menu=true}){Icon(Icons.Default.MoreVert,"Deck options")}
                DropdownMenu(menu,{menu=false}) {
                    currentDeck?.let { deck ->
                        DropdownMenuItem(text={Text(if(deckTrash)"Show active cards"else"Card trash")},onClick={menu=false;deckTrash=!deckTrash})
                        DropdownMenuItem(text={Text("Study options")},onClick={menu=false;setupDecks=deck.id;setupFilter="all"},enabled=true)
                        DropdownMenuItem(text={Text("Edit deck")},onClick={menu=false;editDeckId=deck.id},enabled=state.online&&!state.busy)
                        DropdownMenuItem(text={Text("Move to trash")},onClick={menu=false;deleteDeckId=deck.id},enabled=state.online&&!state.busy)
                    }
                }
            }})},
        bottomBar={
            if(state.uid!=null && current !is ChatRoute && current !is SessionRoute) {
                NavigationBar {
                    val destinations = listOf(
                        Triple(HomeRoute, "Decks", Icons.Default.Style),
                        Triple(SecondaryRoute("Study history"), "Study", Icons.Default.School),
                        Triple(SecondaryRoute("Reminders"), "Reminders", Icons.Default.Notifications),
                        Triple(SecondaryRoute("Settings"), "Settings", Icons.Default.Settings)
                    )
                    destinations.forEach { (destination, label, icon) ->
                        NavigationBarItem(
                            selected=current==destination || (destination is HomeRoute && current is DeckRoute),
                            onClick={
                                if(current!=destination) {
                                    while(stack.size>1) stack.removeLastOrNull()
                                    if(destination !is HomeRoute) stack.add(destination)
                                }
                            },
                            icon={Icon(icon,null)},
                            label={Text(label)}
                        )
                    }
                }
            }
        },
        floatingActionButtonPosition=FabPosition.Center,
        floatingActionButton={
            if(state.uid!=null && current is HomeRoute) {
                Box(Modifier.widthIn(max=760.dp).fillMaxWidth().padding(horizontal=16.dp), contentAlignment=Alignment.CenterEnd) {
                    ExtendedFloatingActionButton(onClick={addSheet=true},icon={Icon(Icons.Default.Add,null)},text={Text("Add deck")})
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=Alignment.TopCenter) {
            Box(Modifier.widthIn(max=760.dp).fillMaxSize()) {
                if(state.uid==null) Welcome(onSignIn,state.busy)
                else NavDisplay(backStack=stack,onBack={if(stack.size>1)stack.removeLastOrNull()},entryProvider={ key ->
                    val tabTransitions = if(key is HomeRoute || key is SecondaryRoute) {
                        NavDisplay.transitionSpec {
                            if(isFooterDestination(initialState.key) && isFooterDestination(targetState.key)) {
                                fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                            } else null
                        } + NavDisplay.popTransitionSpec {
                            if(isFooterDestination(initialState.key) && isFooterDestination(targetState.key)) {
                                fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                            } else null
                        }
                    } else emptyMap()
                    NavEntry(key,metadata=tabTransitions) {
                        val latest by model.state.collectAsStateWithLifecycle()
                        when(key) {
                            is HomeRoute -> DecksScreen(latest,onDeck={stack.add(DeckRoute(it))})
                            is SecondaryRoute -> when(key.name) {
                                "Study history" -> StudyScreen(
                                    latest,
                                    onStart={ids,options->
                                        model.startSession(ids,options.filter,options.direction,options.shuffle,options.mode,options.count) {
                                            if(stack.lastOrNull()==key)stack.add(SessionRoute(it))
                                        }
                                    },
                                    onOpen={id->model.openStudySession(id){if(stack.lastOrNull()==key)stack.add(SessionRoute(it))}},
                                    onDecks={while(stack.size>1)stack.removeLastOrNull()}
                                )
                                "Reminders" -> RemindersScreen(latest,model,onNotifications)
                                else -> SettingsScreen(latest,model,onVoices)
                            }
                            is DeckRoute -> DeckScreen(key,latest,model,trash=deckTrash,onChat={stack.add(ChatRoute(key.id))},
                                onStudy={
                                    model.startSession(setOf(key.id),"all","term",true,"practice",20){if(stack.lastOrNull()==key)stack.add(SessionRoute(it))}
                                },
                                )
                            is ChatRoute -> ChatScreen(key.id,latest,model,initialConversation=key.conversationId,onDeleted={if(stack.lastOrNull()==key){stack.removeLastOrNull();if(stack.lastOrNull() is DeckRoute)stack.removeLastOrNull()}},onCreated={
                                if(key.creating && stack.lastOrNull()==key){stack.removeLastOrNull();stack.add(DeckRoute(key.id))}
                            })
                            is SessionRoute -> SessionScreen(latest.sessions.find{it.id==key.id},latest,model,onExit={stack.removeLastOrNull()},onSpeak=onSpeak)
                        }
                    }
                })
            }
        }
    }
    clearChatDeckId?.let { deckId ->
        ConfirmDialog("Clear conversation history?","This clears AI messages and pending suggestions for this deck. Your cards stay saved.",{clearChatDeckId=null}) {
            clearChatDeckId=null
            val origin=stack.lastOrNull()
            model.clearDeckChat(deckId) { conversationId ->
                if(stack.lastOrNull()==origin && origin is ChatRoute && origin.id==deckId) {
                    stack.removeLastOrNull()
                    stack.add(ChatRoute(deckId,conversationId=conversationId))
                }
            }
        }
    }
    deleteDeckId?.let { id ->
        val deck=state.decks.find{it.id==id}
        if(deck!=null) ConfirmDialog("Move deck to trash?","You can restore this deck from Settings.",{deleteDeckId=null}) {
            deleteDeckId=null
            model.contentAction("decks",deck.id,deck.version,"trash") {
                if((stack.lastOrNull() as? DeckRoute)?.id==id)stack.removeLastOrNull()
            }
        }
    }
    if(addSheet) ModalBottomSheet(onDismissRequest={addSheet=false}) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {

            val height = 64.dp
            val buttonModifier = Modifier.fillMaxWidth().heightIn(min=height)
            val shapes = ButtonDefaults.shapesFor(height)
            val labelStyle = ButtonDefaults.textStyleFor(height)
            Button({addSheet=false;newDeck=true}, modifier=buttonModifier, shapes=shapes) {
                Text("Create manually", style=labelStyle)
            }
            Button(
                onClick={
                    addSheet=false
                    model.inspectCreation { exists ->
                        if(exists) draftPrompt=true else model.openCreation(false){stack.add(ChatRoute(it,true))}
                    }
                },
                modifier=buttonModifier, shapes=shapes, enabled=!state.busy
            ) { Text("Create with AI", style=labelStyle) }
        }
    }
    if(draftPrompt) AlertDialog(onDismissRequest={draftPrompt=false},title={Text("Continue your draft?")},text={Text("Resume your AI deck or discard it and start fresh.")},confirmButton={TextButton({draftPrompt=false;model.openCreation(false){stack.add(ChatRoute(it,true))}}){Text("Resume draft")}},dismissButton={TextButton({draftPrompt=false;model.openCreation(true){stack.add(ChatRoute(it,true))}}){Text("Start fresh")}})
    if(newDeck||editDeckId!=null) DeckEditor(state.decks.find{it.id==editDeckId},state.busy,
        onDismiss={newDeck=false;editDeckId=null},onSave={deck->
            val creating=newDeck
            model.saveDeck(deck){newDeck=false;editDeckId=null;if(creating)stack.add(DeckRoute(deck.id))}
        })
    if(setupDecks!=null) StudySetup(setupFilter,state.busy,onDismiss={setupDecks=null},onStart={filter,direction,shuffle,mode,count->
        model.startSession(setupDecks!!.split(',').filter{it.isNotBlank()}.toSet(),filter,direction,shuffle,mode,count){
            setupDecks=null;stack.add(SessionRoute(it))
        }
    })
}
private fun isFooterDestination(key:Any) = key is HomeRoute || key is SecondaryRoute
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
@Composable private fun DeckScreen(route:DeckRoute,state:FlashState,model:FlashViewModel,trash:Boolean,onChat:()->Unit,onStudy:()->Unit) {

    var search by rememberSaveable(route.id){mutableStateOf("")}
    val deck=state.decks.find{it.id==route.id}

    var deleteTarget by remember{mutableStateOf<FlashCard?>(null)}

    var editorJson by rememberSaveable(route.id){mutableStateOf<String?>(null)}
    val editor=editorJson?.let{Codec.decodeFromString<FlashCard>(it)}
    var error by remember(route.id){mutableStateOf("")}
    var loading by remember(route.id){mutableStateOf(false)}
    var retry by remember{mutableStateOf(0)}
    LaunchedEffect(route.id,retry) {
        loading=true
        try {model.repository.prepareDeck(route.id)} catch(e:Exception){error=operationError(e)} finally{loading=false}
    }
    val query=search.trim()
    val cards=state.cards.filter{it.deckId==route.id&&(it.deletedAt!=null)==trash&&!it.purgeRequested&&(it.term.contains(query,true)||it.definition.contains(query,true))}.sortedBy{it.order}
    val listState=rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        // Chips reserve 8 dp above and below their visible surface for their touch targets.
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp).horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(deck!=null) AssistChip(
                onClick=onStudy,
                enabled=resumableStudy(state.sessions,setOf(route.id))!=null||cards.isNotEmpty(),
                label={Text(if(resumableStudy(state.sessions,setOf(route.id))!=null)"Resume study"else"Study")},
                leadingIcon={Icon(Icons.Default.School,null,Modifier.size(18.dp))})
            AssistChip(onClick={editorJson=Codec.encodeToString(FlashCard.serializer(),FlashCard(deckId=route.id,order=(cards.maxOfOrNull{it.order}?:0)+1000))},
                enabled=deck!=null,label={Text("Add card")},leadingIcon={Icon(Icons.Default.Add,null,Modifier.size(18.dp))})
            AssistChip(onClick=onChat,enabled=deck!=null,label={Text("Ask AI")},leadingIcon={Icon(Icons.Default.ChatBubbleOutline,null,Modifier.size(18.dp))})
        }
        SearchInput(search,{
            search=it
            listState.requestScrollToItem(0)
        },label="Find a card",modifier=Modifier.padding(horizontal=16.dp))
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f),state=listState,contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(loading&&cards.isEmpty())item{CircularProgressIndicator()}
        if(error.isNotBlank())item{Text(error);TextButton({error="";retry++}){Text("Retry")}}
        if(!loading&&cards.isEmpty()&&error.isBlank())item{Empty("Add your first card","Build this deck one card at a time.")}
        items(cards,key={it.id}){card->
            var cardMenu by remember{mutableStateOf(false)}
            ListItem(headlineContent={Text(card.term)},supportingContent={Text(card.definition)},trailingContent={Box {
                IconButton({cardMenu=true}){Icon(Icons.Default.MoreVert,"Card options")}
                DropdownMenu(cardMenu,{cardMenu=false}) {
                    if(trash)DropdownMenuItem(text={Text("Restore")},onClick={cardMenu=false;model.contentAction("cards",card.id,card.version,"restore")})
                    else DropdownMenuItem(text={Text("Edit")},onClick={cardMenu=false;editorJson=Codec.encodeToString(FlashCard.serializer(),card)})
                    DropdownMenuItem(text={Text(if(card.starred)"Unstar"else"Star")},onClick={cardMenu=false;model.saveCard(card.copy(starred=!card.starred))})
                    DropdownMenuItem(text={Text("Move to trash")},onClick={cardMenu=false;deleteTarget=card})
                }
            }})
            HorizontalDivider()
        }
    }
    }
    deleteTarget?.let{card->ConfirmDialog("Move card to trash?","You can restore this card from Card trash.",{deleteTarget=null}){deleteTarget=null;model.contentAction("cards",card.id,card.version,"trash")}}
    editor?.let{draft->CardEditor(draft,state.busy,onDismiss={editorJson=null},onSave={model.saveCard(it){editorJson=null}})}
}
@Composable internal fun ChatBubble(
    fromUser: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Surface(
            modifier=Modifier.widthIn(max=maxWidth * .88f)
                .align(if(fromUser) Alignment.CenterEnd else Alignment.CenterStart),
            shape=MaterialTheme.shapes.medium,
            color=if(fromUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp),
                content=content,
            )
        }
    }
}

@Composable internal fun LoadingMessageBubble() {
    val context=LocalContext.current
    val reducedMotion=android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )==0f
    ChatBubble {
        Row(
            Modifier.height(20.dp).semantics(mergeDescendants=true) {
                contentDescription="AI is replying"
            },
            horizontalArrangement=Arrangement.spacedBy(6.dp),
            verticalAlignment=Alignment.CenterVertically,
        ) {
            if(reducedMotion) {
                repeat(3) { ReplyDot() }
            } else {
                val transition=rememberInfiniteTransition(label="Reply dots")
                repeat(3) { index ->
                    val hop by transition.animateFloat(
                        initialValue=0f,
                        targetValue=0f,
                        animationSpec=infiniteRepeatable(keyframes {
                            durationMillis=1200
                            0f at index * 150
                            -6f at index * 150 + 150
                            0f at index * 150 + 300
                        }),
                        label="Reply dot $index",
                    )
                    ReplyDot(hop)
                }
            }
        }
    }
}

@Composable private fun ReplyDot(hop: Float = 0f) {
    Box(Modifier.size(8.dp)
        .graphicsLayer { translationY=hop.dp.toPx() }
        .background(MaterialTheme.colorScheme.onSecondaryContainer,
            androidx.compose.foundation.shape.CircleShape))
}
@Composable private fun ChatScreen(deckId:String,state:FlashState,model:FlashViewModel,initialConversation:String?,onDeleted:()->Unit,onCreated:()->Unit) {
    val conversationIds=(state.conversations.filter{it.deckId==deckId}.sortedByDescending{it.updatedAt}.map{it.id}+
        state.chatRequests.filter{it.deckId==deckId}.sortedByDescending{it.createdAt}.map{it.conversationId}).distinct()
    var selectedConversation by rememberSaveable(deckId){mutableStateOf<String?>(null)}
    val conversation=initialConversation?:selectedConversation?:model.repository.preferences.getString("creation_conversation_${state.uid}_$deckId",null)?:conversationIds.firstOrNull()?:"draft_$deckId"
    val requests=state.chatRequests.filter{it.conversationId==conversation}.sortedBy{it.createdAt}
    val active=conversation in state.activeChats
    val unresolved=requests.lastOrNull{it.state in listOf("pending","uncertain")}
    val turns=state.turns.filter{it.conversationId==conversation}.sortedBy{it.createdAt}
    val outstanding=requests.filter{request->turns.none{it.id==request.id}}
    val transcript=chatTranscript(turns,requests)
    val latestProposalId=turns.lastOrNull{it.proposalId!=null}?.proposalId
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    var followLatest by remember(conversation){mutableStateOf(true)}
    LaunchedEffect(conversation) {
        listState.scrollToItem(0)
        snapshotFlow {
            listState.isScrollInProgress to
                (listState.firstVisibleItemIndex==0&&listState.firstVisibleItemScrollOffset==0)
        }.collect { (scrolling,atLatest) ->
            if(scrolling)followLatest=atLatest
        }
    }
    val viewportHeight=listState.layoutInfo.viewportEndOffset-listState.layoutInfo.viewportStartOffset
    LaunchedEffect(conversation,turns.lastOrNull()?.id,outstanding.lastOrNull()?.id,active,viewportHeight) {
        if(followLatest&&(turns.isNotEmpty()||outstanding.isNotEmpty()))listState.scrollToItem(0)
    }
    var draft by rememberSaveable(deckId,conversation){mutableStateOf("")};var details by rememberSaveable{mutableStateOf<String?>(null)}

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f),state=listState,reverseLayout=true,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(turns.isEmpty()&&requests.isEmpty())item{Empty("What would you like to learn?","Describe a topic or paste your notes.")}
            items(transcript,key={it.id}) { entry ->
                val request=entry.request
                if(request!=null) {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    ChatBubble(fromUser=true) { Text(request.message) }
                    if(active&&request.id==unresolved?.id) LoadingMessageBubble()
                    else ChatBubble {
                        if(request.state=="failed")Text("Request failed. Your deck was not changed.",color=MaterialTheme.colorScheme.error)
                        else Text("The response has not been confirmed. Check status to recover it.")
                        if(request.error.isNotBlank())Text(request.error,color=MaterialTheme.colorScheme.error)

                        if(!active&&request.state!="failed")OutlinedButton({model.recoverChat(request)},enabled=state.online){Text("Check status / retry safely")}
                        if(!active&&request.state=="failed")TextButton({draft=request.message}){Text("Use message again")}
                    }
                }
                }
                val turn=entry.turn
                if(turn!=null) {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    ChatBubble(fromUser=true) { Text(turn.user) }
                    ChatBubble {
                        Text(turn.assistant)
                        val proposal=state.proposals.find{it.id==turn.proposalId}
                        if(turn.proposalId!=null&&proposal==null) {
                            Text("The approval preview has not loaded yet.",color=MaterialTheme.colorScheme.error)
                            TextButton({state.chatRequests.find{it.id==turn.id}?.let(model::recoverChat)?:model.refresh()},enabled=!active){Text("Recover approval preview")}
                    }
                    if(proposal!=null)OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("${proposal.operations.size} proposed changes",style=MaterialTheme.typography.titleMedium)
                        Text(proposal.operations.groupingBy{it.kind.replace('_',' ')}.eachCount().entries.joinToString(" · "){"${it.value} ${it.key}"})
                        TextButton({details=if(details==proposal.id)null else proposal.id}){Text(if(details==proposal.id)"Hide changes"else"Review before/after")}
                        if(details==proposal.id)proposal.operations.forEach{o->Text(o.kind.replace('_',' '),fontWeight=FontWeight.SemiBold);o.before?.let{before->Text(before.entries.filter{it.key in listOf("title","term","definition")}.joinToString("\n"){it.value.toString().trim('"')},style=MaterialTheme.typography.bodySmall)};Text(o.after?.entries?.filter{it.key in listOf("title","term","definition","example","explanation")}?.joinToString("\n"){it.value.toString().trim('"')} ?: "Move to trash",style=MaterialTheme.typography.bodySmall)}
                        if(proposal.state=="pending"&&proposal.id==latestProposalId&&proposal.expiresAt>System.currentTimeMillis())Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button({model.confirm(proposal,true) { if(proposal.operations.any{it.kind=="create_deck"})onCreated();if(proposal.operations.any{it.kind=="delete_deck"})onDeleted() }},enabled=state.online&&!state.busy&&!active){Text(if(proposal.operations.any{it.kind=="create_deck"})"Create deck"else"Apply changes")}
                        }else Text(if(proposal.state=="pending"&&proposal.id!=latestProposalId)"Revised below"else if(proposal.state=="pending")"Expired — ask for a fresh preview"else proposal.state.replaceFirstChar{it.uppercase()})
                    }}
                    }
                }
                }
            }
        }
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft,{draft=it},label={Text(if(state.decks.any{it.id==deckId})"Ask about this deck…"else"Describe your deck…")},modifier=Modifier.weight(1f),maxLines=5,enabled=!active,isError=draft.length>8000)
            Button({followLatest=true;selectedConversation=conversation;model.sendMessage(deckId,conversation,draft);draft=""},enabled=state.online&&!active&&unresolved==null&&draft.isNotBlank()&&draft.length<=8000){Text("Send")}
        }
    }
}
@Composable private fun SessionScreen(session:StudySession?,state:FlashState,model:FlashViewModel,onExit:()->Unit,onSpeak:(String,String)->Unit) {
    if(session==null){Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
        Text(if(state.loading)"Loading saved sessions…"else"This session could not be loaded.")
        if(state.loading)CircularProgressIndicator()
        else Text("Return to Study to open a saved session.")
        TextButton(onExit){Text("Go back")}
    };return}
    val sessionSaving=session.id in state.savingSessions
    var answer by rememberSaveable(session.id,session.cursor){mutableStateOf("")}
    Column(Modifier.fillMaxSize().imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onExit){Icon(Icons.Default.Close,"Pause and exit session")};Text(session.title,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);}
        if(session.syncConflict)Text("This session is saved on this device. Another device changed its cloud copy.",color=MaterialTheme.colorScheme.error)
        state.studyErrors[session.id]?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Box(Modifier.fillMaxWidth().height(4.dp)){if(sessionSaving)LinearProgressIndicator(Modifier.fillMaxWidth())}
        val item=session.current
        if(session.finished||session.submitted) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Heading(if(session.supersededBy!=null)"Session replaced"else"Session complete",if(session.supersededBy!=null)"Your newer session is available from the deck."else if(session.mode=="practice")"Every selected card reached High. Come back whenever you’re ready."else"Review your answers below.")
                if(session.mode=="browse")Text("${session.items.size} cards reviewed")
                else if(session.mode=="practice")Text("${session.completed.size} directional cards completed")
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
            if(session.undoJson!=null&&session.supersededBy==null)OutlinedButton({model.undo(session)},enabled=!sessionSaving&&canReturnToPrevious(session,state.sessions)){Text("Previous")}
            Button(onExit,modifier=Modifier.fillMaxWidth()){Text("Done")}
        }else if(item!=null) {
            val completed=if(session.mode=="practice")session.completed.size else session.cursor
            Text("${completed} / ${session.items.size} complete · ${session.mode}",style=MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress={completed.toFloat()/session.items.size.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth())
            if(session.mode in listOf("practice","browse")) {
                val context=LocalContext.current
                val animationMs=if(android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f)0 else 200
                AnimatedContent(targetState=item,contentKey={it.key},modifier=Modifier.weight(1f).fillMaxWidth().clipToBounds(),transitionSpec={
                    (slideInHorizontally(tween(animationMs)){it} togetherWith slideOutHorizontally(tween(animationMs)){-it}).using(null)
                },label="Next card"){shown->key(shown.key){FlashCardFace(shown,if(shown.key==item.key)session.revealed else false,onFlip={if(!sessionSaving)model.reveal(session)},onSpeak=onSpeak,onSwipe={right->
                    if(!sessionSaving){if(session.mode=="browse")model.advance(session,if(right)-1 else 1)else if(session.revealed)model.rate(session,if(right)"high"else"low")}
                },enabled=shown.key==item.key&&!sessionSaving)}}
                if(session.mode=="practice") {
                    if(session.revealed) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("low" to "↻ Low","medium" to "≈ Medium","high" to "✓ High").forEach{(value,label)->
                            FilledTonalButton({model.rate(session,value)},enabled=session.revealed&&!sessionSaving,modifier=Modifier.weight(1f).heightIn(min=52.dp),contentPadding=PaddingValues(8.dp)){Text(label)}
                        }
                    }
                    }
                    TextButton({model.undo(session)},enabled=canReturnToPrevious(session,state.sessions)&&!sessionSaving){Text("Previous")}
                }else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton({model.advance(session,-1)},enabled=session.cursor>0&&!sessionSaving){Text("Previous")};OutlinedButton({model.advance(session,1)},enabled=!sessionSaving){Text(if(session.cursor==session.items.lastIndex)"Finish"else"Next")}}
            }else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(item.prompt,style=MaterialTheme.typography.headlineMedium)
                    TextButton({onSpeak(item.prompt,item.promptLanguage)}){Icon(Icons.AutoMirrored.Filled.VolumeUp,null);Text("Listen")}
                    val grade=session.grades[item.key]
                    if(item.choices.isEmpty())OutlinedTextField(answer,{answer=it},label={Text("Your answer")},modifier=Modifier.fillMaxWidth(),enabled=grade==null&&!sessionSaving)
                    else item.choices.forEach{choice->OutlinedButton({answer=choice},enabled=grade==null&&!sessionSaving,modifier=Modifier.fillMaxWidth()){if(answer==choice)Icon(Icons.Default.Check,null);Text(choice)}}
                    if(grade==null)Button({model.answer(session,answer)},enabled=!sessionSaving&&(answer.isNotBlank()||session.mode=="test")){Text("Save answer")}
                    else {
                        if(session.mode!="test"){Text("Expected: ${item.answer}");GradeFeedback(grade){model.overrideGrade(session,item.key,it)}}
                        else Text("Answer saved. Feedback appears after submission.")
                        Button({model.advance(session)},enabled=!sessionSaving){Text(if(session.cursor==session.items.lastIndex)"Submit ${if(session.mode=="test")"test"else"session"}"else"Next question")}
                    }
                }
            }
        }else {
            Text("This session has no current card. Exit and start a new session.",color=MaterialTheme.colorScheme.error)
            Button(onExit){Text("Back to decks")}
        }
    }
}
@Composable fun FlashCardFace(item:StudyItem,revealed:Boolean,onFlip:()->Unit,onSpeak:(String,String)->Unit,onSwipe:(Boolean)->Unit,enabled:Boolean=true) {
    val context=LocalContext.current
    val reduced=android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val rotation by animateFloatAsState(if(revealed)180f else 0f,tween(if(reduced)0 else 220),label="Card flip")
    val swipe by rememberUpdatedState(onSwipe)
    Card(onClick=onFlip,enabled=enabled,modifier=Modifier.fillMaxSize().graphicsLayer{rotationY=rotation;cameraDistance=16*density}
        .pointerInput(item.key,enabled){
            if(enabled){var drag=0f;val threshold=72.dp.toPx()
                detectHorizontalDragGestures(onDragStart={drag=0f},onHorizontalDrag={change,amount->change.consume();drag+=amount},onDragCancel={drag=0f},onDragEnd={if(kotlin.math.abs(drag)>threshold)swipe(drag>0);drag=0f})}
        }
        .semantics{contentDescription=if(revealed)"Answer: ${item.answer}"else"Prompt: ${item.prompt}";onClick(label="Flip card"){if(enabled){onFlip();true}else false}},
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
        val back by remember{derivedStateOf{rotation>90f}}
        Column(Modifier.fillMaxSize().graphicsLayer{rotationY=if(back)180f else 0f}.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp));Text(if(back)item.answer else item.prompt,style=MaterialTheme.typography.headlineMedium)
            if(back&&item.card.example.isNotBlank())Text(item.card.example,Modifier.padding(top=16.dp))
            if(back&&item.card.explanation.isNotBlank())Text(item.card.explanation,Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyMedium)
            TextButton({onSpeak(if(back)item.answer else item.prompt,if(back)item.answerLanguage else item.promptLanguage)}){Icon(Icons.AutoMirrored.Filled.VolumeUp,"Play visible side");Text("Listen")}
        }
    }
}
@Composable private fun GradeFeedback(grade:Grade?,onOverride:(String)->Unit) {
    if(grade==null)return
    Text(when(grade.verdict){"pending"->"Awaiting grade";"needs_review"->"Needs your review";else->grade.verdict.replace('_',' ').replaceFirstChar{it.uppercase()}},style=MaterialTheme.typography.titleSmall)
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
        Field("Cards (1–100)",count,{count=it})
        Text("One High rating completes a card in this session. Low and Medium return later.")
        if(count.toIntOrNull() !in 1..100)Text("Choose between 1 and 100 cards.",color=MaterialTheme.colorScheme.error)
        Button({onStart(filter,direction,shuffle,mode,count.toInt())},enabled=!busy&&count.toIntOrNull() in 1..100,modifier=Modifier.fillMaxWidth()){Text("Start session")}
    }
}
@Composable private fun DeckEditor(original:Deck?,busy:Boolean,onDismiss:()->Unit,onSave:(Deck)->Unit) {
    val draftId=rememberSaveable{newId()}
    val base=remember(original?.id){original?:Deck(id=draftId)}
    var title by rememberSaveable{mutableStateOf(base.title)};var description by rememberSaveable{mutableStateOf(base.description)}
    var term by rememberSaveable{mutableStateOf(base.termLanguage)};var definition by rememberSaveable{mutableStateOf(base.definitionLanguage)}
    var variety by rememberSaveable{mutableStateOf(base.spanishVariety)}
    FormDialog(if(original==null)"New deck"else"Edit deck",onDismiss,dirty=title!=base.title||description!=base.description||term!=base.termLanguage||definition!=base.definitionLanguage) {
        Field("Title",title,{title=it});Field("Description",description,{description=it},false)
        var more by rememberSaveable{mutableStateOf(false)}
        TextButton({more=!more}){Text("Language options")}
        if(more){Field("Term language",term,{term=it});Field("Definition language",definition,{definition=it})
        Choice("Spanish variety",listOf("es-419","es-MX","es-ES"),variety,{variety=it})}
        Button({onSave(base.copy(title=title,description=description,termLanguage=term,definitionLanguage=definition,spanishVariety=variety))},enabled=title.isNotBlank()&&title.length<=200&&description.length<=2000&&term.isNotBlank()&&definition.isNotBlank()&&!busy,modifier=Modifier.fillMaxWidth()){Text("Save deck")}
    }
}
@Composable private fun CardEditor(base:FlashCard,busy:Boolean,onDismiss:()->Unit,onSave:(FlashCard)->Unit) {
    var term by rememberSaveable{mutableStateOf(base.term)};var definition by rememberSaveable{mutableStateOf(base.definition)}
    var example by rememberSaveable{mutableStateOf(base.example)};var explanation by rememberSaveable{mutableStateOf(base.explanation)}
    var termAlt by rememberSaveable{mutableStateOf(base.termAlternatives.joinToString("\n"))};var definitionAlt by rememberSaveable{mutableStateOf(base.definitionAlternatives.joinToString("\n"))}
    FormDialog(if(base.version==0)"New card"else"Edit card",onDismiss,dirty=term!=base.term||definition!=base.definition||example!=base.example||explanation!=base.explanation||termAlt!=base.termAlternatives.joinToString("\n")||definitionAlt!=base.definitionAlternatives.joinToString("\n")) {
        Field("Front",term,{term=it},false);Field("Back",definition,{definition=it},false)
        var more by rememberSaveable{mutableStateOf(false)}
        TextButton({more=!more}){Text("More options")}
        if(more){Field("Example sentence",example,{example=it},false);Field("Explanation",explanation,{explanation=it},false)
        Field("Alternative terms (one per line)",termAlt,{termAlt=it},false);Field("Alternative definitions (one per line)",definitionAlt,{definitionAlt=it},false)}
        Button({onSave(base.copy(term=term,definition=definition,example=example,explanation=explanation,termAlternatives=termAlt.lines().filter{it.isNotBlank()},definitionAlternatives=definitionAlt.lines().filter{it.isNotBlank()}))},enabled=term.isNotBlank()&&term.length<=2000&&definition.isNotBlank()&&definition.length<=4000&&example.length<=2000&&explanation.length<=4000&&listOf(termAlt,definitionAlt).all{value->value.lines().filter{it.isNotBlank()}.let{it.size<=20&&it.all{line->line.length<=1000}}}&&!busy,modifier=Modifier.fillMaxWidth()){Text("Save card")}
    }
}
@Composable private fun SettingsScreen(state:FlashState,model:FlashViewModel,onVoices:()->List<Pair<String,String>>) {
    var delete by rememberSaveable{mutableStateOf(false)};var confirmation by rememberSaveable{mutableStateOf("")}
    var trash by rememberSaveable{mutableStateOf(false)}
    var purgeId by rememberSaveable{mutableStateOf<String?>(null)};var voices by remember{mutableStateOf(emptyList<Pair<String,String>>())}

    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item{Heading("Settings",state.email)}
        item{Choice("Appearance",listOf("system","light","dark"),state.theme,model::theme)}
        item{Text("Pronunciation",style=MaterialTheme.typography.titleLarge);Text("Playback speed: ${"%.2f".format(state.rate)}×");Slider(state.rate,{model.speech(it,state.voice)},valueRange=.5f..1.3f);TextButton({voices=onVoices()}){Text("Load available voices")}}
        if(voices.isNotEmpty())item{Column{TextButton({model.speech(state.rate,null)}){Text("Use language default")};voices.forEach{(name,label)->TextButton({model.speech(state.rate,name)}){Text("${if(state.voice==name)"✓ "else""}$label")}}}}
        item{TextButton({trash=!trash}){Text(if(trash)"Hide trash"else"Show trash")}}
        if(trash) {
            items(state.decks.filter{it.deletedAt!=null&&!it.purgeRequested},key={"trash_${it.id}"}){d->Column{Text(d.title);Row{TextButton({model.contentAction("decks",d.id,d.version,"restore")},enabled=state.online&&!state.busy){Text("Restore")};TextButton({purgeId=d.id},enabled=state.online&&!state.busy){Text("Delete permanently")}}}}
        }
        item{HorizontalDivider();OutlinedButton({model.signOut()},enabled=!state.busy){Text("Sign out")};TextButton({delete=true},enabled=state.online&&!state.busy){Text("Delete account and all data")}}
    }
    if(delete)FormDialog("Delete your account",{delete=false}){Text("This permanently removes your account and saved learning data.");Field("Type DELETE",confirmation,{confirmation=it});Button({model.signOut(true);delete=false},enabled=confirmation=="DELETE"&&!state.busy){Text("Permanently delete account")}}
    purgeId?.let{id->ConfirmDialog("Permanently delete deck?","The deck and its content will be removed by cleanup. This cannot be undone.",{purgeId=null}){state.decks.find{it.id==id}?.let{model.contentAction("decks",it.id,it.version,"purge")};purgeId=null}}
}
@Composable private fun Choice(label:String,options:List<String>,selected:String,onSelect:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text(label,style=MaterialTheme.typography.labelLarge);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){options.forEach{value->FilterChip(value==selected,{onSelect(value)},label={Text(value.replaceFirstChar{it.uppercase()})})}}}
}
@Composable private fun Field(label:String,value:String,onValue:(String)->Unit,single:Boolean=true) {
    val limit=when(label){"Title"->200;"Description"->2000;"Front"->2000;"Back","Explanation"->4000;"Example sentence"->2000;else->Int.MAX_VALUE}
    OutlinedTextField(value,onValue,label={Text(label)},isError=value.length>limit,supportingText={if(value.length>limit)Text("Use $limit characters or fewer")},singleLine=single,minLines=if(single)1 else 2,maxLines=if(single)1 else 6,modifier=Modifier.fillMaxWidth())
}
@Composable internal fun FormDialog(title:String,onDismiss:()->Unit,dirty:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    var discard by remember{mutableStateOf(false)}
    val close={if(dirty)discard=true else onDismiss()}
    if(discard)ConfirmDialog("Discard changes?","Your unsaved changes will be lost.",{discard=false}){discard=false;onDismiss()}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(shape=MaterialTheme.shapes.large){Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));IconButton(close){Icon(Icons.Default.Close,"Close dialog")}}
        content()
    }}}
}
@Composable internal fun ConfirmDialog(title:String,body:String,onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton(onConfirm){Text("Confirm")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
private fun formatDate(at:Long,zone:String)=runCatching{Instant.ofEpochMilli(at).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a z"))}.getOrDefault("Invalid schedule")
