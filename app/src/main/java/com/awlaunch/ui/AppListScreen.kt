package com.awlaunch.ui

import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.awlaunch.LauncherViewModel
import com.awlaunch.data.AppInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@Composable
fun AppListScreen(vm: LauncherViewModel, homeEvents: SharedFlow<Unit>) {
    val apps by vm.apps.collectAsState()
    val pinned by vm.pinned.collectAsState()
    val hidden by vm.hidden.collectAsState()
    val names by vm.names.collectAsState()
    val groups by vm.groups.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val searchFocusRequester = remember { FocusRequester() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                focusManager.clearFocus(force = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showHidden by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AppInfo?>(null) }
    var groupTarget by remember { mutableStateOf<AppInfo?>(null) }
    var renameGroupTarget by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }

    val groupOptions: List<String> by remember(groups, pinned) {
        derivedStateOf {
            buildList {
                add(SEARCH_GROUP)
                if (pinned.isNotEmpty()) add(PINNED_GROUP)
                add(ALL_GROUP)
                addAll(groups.keys.sorted())
            }
        }
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { groupOptions.size })

    // Land on Favorites if available, else All. Skips the Search page (index 0).
    var initialJumpDone by remember { mutableStateOf(false) }
    LaunchedEffect(groupOptions) {
        if (!initialJumpDone && groupOptions.isNotEmpty()) {
            val target = groupOptions.indexOf(PINNED_GROUP)
                .takeIf { it >= 0 }
                ?: groupOptions.indexOf(ALL_GROUP).takeIf { it >= 0 }
                ?: 0
            if (target > 0) pagerState.scrollToPage(target)
            initialJumpDone = true
        }
    }

    val currentGroup: String = groupOptions.getOrElse(pagerState.currentPage) { ALL_GROUP }

    // One LazyListState per group, kept across page swipes.
    val listStates = remember { mutableStateMapOf<String, LazyListState>() }
    fun listStateFor(group: String): LazyListState =
        listStates.getOrPut(group) { LazyListState() }

    // Filtered/sorted apps for the *current* page — drives the fast-scroll bar.
    val currentPageApps: List<AppInfo> by remember(
        currentGroup, apps, names, hidden, showHidden, groups, pinned, searchQuery
    ) {
        derivedStateOf {
            appsForGroup(currentGroup, apps, names, hidden, showHidden, groups, pinned, searchQuery)
        }
    }
    val currentGrouped: List<Pair<Char, List<AppInfo>>> by remember(currentGroup, currentPageApps) {
        derivedStateOf {
            if (currentGroup == ALL_GROUP)
                currentPageApps.groupBy { bucketFor(it.label) }.toList().sortedBy { it.first }
            else emptyList()
        }
    }
    val currentLetterIndex: Map<Char, Int> by remember(currentGroup, currentPageApps) {
        derivedStateOf { computeLetterIndex(currentGroup, currentPageApps, currentGrouped) }
    }
    val presentLetters = remember(currentLetterIndex) { currentLetterIndex.keys.toSet() }

    // Scroll the newly-active page to top whenever the page changes.
    LaunchedEffect(pagerState.currentPage) {
        listStateFor(currentGroup).scrollToItem(0)
        if (currentGroup == SEARCH_GROUP) searchFocusRequester.requestFocus()
        else focusManager.clearFocus()
    }
    LaunchedEffect(Unit) {
        homeEvents.collect {
            focusManager.clearFocus(force = true)
            listStateFor(currentGroup).scrollToItem(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GreetingHeader(
                showHidden = showHidden,
                onToggleShowHidden = { showHidden = !showHidden }
            )

            GroupTabsHeader(
                options = groupOptions,
                selectedIndex = pagerState.currentPage,
                onSelectIndex = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } },
                onRenameGroupRequest = { renameGroupTarget = it }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val groupName = groupOptions.getOrNull(page) ?: return@HorizontalPager
                    val isCurrent = page == pagerState.currentPage
                    val pageApps = if (isCurrent) currentPageApps else remember(
                        groupName, apps, names, hidden, showHidden, groups, pinned, searchQuery
                    ) {
                        appsForGroup(groupName, apps, names, hidden, showHidden, groups, pinned, searchQuery)
                    }
                    GroupPage(
                        groupName = groupName,
                        pageApps = pageApps,
                        preGrouped = if (isCurrent) currentGrouped else emptyList(),
                        pinned = pinned,
                        hidden = hidden,
                        listState = listStateFor(groupName),
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onLaunch = { app, bounds -> vm.launch(app, bounds) },
                        onInfo = { app, bounds -> vm.openInfo(app, bounds) },
                        onTogglePin = { vm.togglePin(it) },
                        onToggleHide = { vm.toggleHide(it) },
                        onRequestRename = { renameTarget = it },
                        onRequestGroup = { groupTarget = it },
                        searchFocusRequester = if (isCurrent) searchFocusRequester else null
                    )
                }

                FastScrollBar(
                    presentLetters = presentLetters,
                    onLetterSelected = { letter ->
                        currentLetterIndex[letter]?.let { index ->
                            scope.launch { listStateFor(currentGroup).scrollToItem(index) }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }

    renameTarget?.let { target ->
        val original = apps.firstOrNull { it.key == target.key }?.label ?: target.label
        RenameDialog(
            originalLabel = original,
            currentLabel = names[target.key] ?: original,
            onDismiss = { renameTarget = null },
            onSubmit = { newName ->
                val trimmed = newName.trim()
                vm.setName(target.key, if (trimmed.isBlank() || trimmed == original) null else trimmed)
                renameTarget = null
            }
        )
    }

    groupTarget?.let { target ->
        val original = apps.firstOrNull { it.key == target.key }?.label ?: target.label
        val membership = remember(groups, target.key) {
            groups.filterValues { target.key in it }.keys
        }
        GroupsDialog(
            appLabel = names[target.key] ?: original,
            allGroups = groups.keys.sorted(),
            memberships = membership,
            onToggleMembership = { group, member ->
                if (member) vm.addAppToGroup(target.key, group)
                else vm.removeAppFromGroup(target.key, group)
            },
            onCreateGroup = { name ->
                if (name.isNotBlank()) vm.addAppToGroup(target.key, name)
            },
            onDismiss = { groupTarget = null }
        )
    }

    renameGroupTarget?.let { oldName ->
        RenameDialog(
            originalLabel = oldName,
            currentLabel = oldName,
            onDismiss = { renameGroupTarget = null },
            onSubmit = { newName ->
                val trimmed = newName.trim()
                if (trimmed.isNotBlank() && trimmed != oldName) vm.renameGroup(oldName, trimmed)
                renameGroupTarget = null
            },
            onDelete = {
                vm.deleteGroup(oldName)
                renameGroupTarget = null
            }
        )
    }
}

private const val ALL_GROUP = "All"
private const val PINNED_GROUP = "Favorites"
private const val SEARCH_GROUP = "Search"

@Composable
private fun GreetingHeader(
    showHidden: Boolean,
    onToggleShowHidden: () -> Unit
) {
    var now by remember { mutableStateOf(Date()) }
    val greetingLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(greetingLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) now = Date()
        }
        greetingLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { greetingLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val hour = remember(now) { Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onToggleShowHidden() })
            }
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = greeting,
            color = Cream,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            fontFamily = CormorantGaramond
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dateFmt.format(now),
                color = Cream,
                fontSize = 22.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontFamily = CormorantGaramond,
                letterSpacing = 0.04.em,
                style = TextStyle(fontFeatureSettings = "onum tnum")
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = timeFmt.format(now).lowercase(Locale.getDefault()),
                color = Cream,
                fontSize = 22.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontFamily = CormorantGaramond,
                letterSpacing = 0.04.em,
                style = TextStyle(fontFeatureSettings = "onum tnum")
            )
            if (showHidden) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "· hidden",
                    color = CreamDim,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    fontFamily = CormorantGaramond,
                    letterSpacing = 0.04.em
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    isPinned: Boolean,
    isHidden: Boolean,
    onLaunch: (Rect?) -> Unit,
    onInfo: (Rect?) -> Unit,
    onTogglePin: () -> Unit,
    onToggleHide: () -> Unit,
    onRequestRename: () -> Unit,
    onRequestGroup: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var isPressed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press-scale"
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.6f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "press-alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                val w = coords.size.width
                val h = coords.size.height
                bounds = Rect(pos.x.toInt(), pos.y.toInt(), (pos.x + w).toInt(), (pos.y + h).toInt())
            }
            .pointerInput(app.key) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val released = tryAwaitRelease()
                        isPressed = false
                        if (!released) {
                            // canceled (e.g., long-press completed elsewhere) — nothing else to do.
                        }
                    },
                    onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLaunch(bounds)
                    },
                    onLongPress = { menuOpen = true }
                )
            }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = pressAlpha
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(
            text = app.label,
            color = if (isHidden) CreamDim else Cream,
            fontSize = 22.sp,
            fontStyle = if (isHidden) FontStyle.Italic else FontStyle.Normal,
            fontWeight = FontWeight.Normal,
            fontFamily = CormorantGaramond
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin" else "Pin") },
                onClick = {
                    menuOpen = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text(if (isHidden) "Unhide" else "Hide") },
                onClick = {
                    menuOpen = false
                    onToggleHide()
                }
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menuOpen = false
                    onRequestRename()
                }
            )
            DropdownMenuItem(
                text = { Text("Groups…") },
                onClick = {
                    menuOpen = false
                    onRequestGroup()
                }
            )
            DropdownMenuItem(
                text = { Text("App info") },
                onClick = {
                    menuOpen = false
                    onInfo(bounds)
                }
            )
        }
    }
}

@Composable
private fun RenameDialog(
    originalLabel: String,
    currentLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var text by remember(currentLabel) { mutableStateOf(currentLabel) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(originalLabel, fontFamily = CormorantGaramond) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(originalLabel) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF111111),
                    unfocusedTextColor = Color(0xFF111111)
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                        Text(if (confirmDelete) "Really delete?" else "Delete")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun SectionHeader(letter: Char) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = letter.toString(),
            color = CreamDim,
            fontSize = 16.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            fontFamily = CormorantGaramond,
            letterSpacing = 0.15.em
        )
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(CreamFaint)
        )
    }
}

@Composable
private fun GroupTabsHeader(
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onRenameGroupRequest: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(CreamFaint)
        )
        Spacer(Modifier.width(12.dp))
        GroupSelector(
            options = options,
            selectedIndex = selectedIndex,
            onSelectIndex = onSelectIndex,
            onRenameGroupRequest = onRenameGroupRequest
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(CreamFaint)
        )
    }
}

@Composable
private fun GroupSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onRenameGroupRequest: (String) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    if (options.isEmpty()) return
    val idx = selectedIndex.coerceIn(0, options.size - 1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "‹",
            color = CreamDim,
            fontSize = 22.sp,
            fontFamily = CormorantGaramond,
            modifier = Modifier
                .clickable(enabled = options.size > 1) {
                    onSelectIndex((idx - 1 + options.size) % options.size)
                }
                .padding(horizontal = 6.dp)
        )
        Box {
            val currentGroupName = options[idx]
            Text(
                text = currentGroupName,
                color = Cream,
                fontSize = 18.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontFamily = CormorantGaramond,
                letterSpacing = 0.04.em,
                modifier = Modifier
                    .pointerInput(currentGroupName) {
                        detectTapGestures(
                            onTap = { menuOpen = true },
                            onLongPress = {
                                if (currentGroupName !in listOf(SEARCH_GROUP, ALL_GROUP, PINNED_GROUP))
                                    onRenameGroupRequest(currentGroupName)
                            }
                        )
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                options.forEachIndexed { i, g ->
                    DropdownMenuItem(
                        text = { Text(g) },
                        onClick = {
                            onSelectIndex(i)
                            menuOpen = false
                        }
                    )
                }
            }
        }
        Text(
            text = "›",
            color = CreamDim,
            fontSize = 22.sp,
            fontFamily = CormorantGaramond,
            modifier = Modifier
                .clickable(enabled = options.size > 1) {
                    onSelectIndex((idx + 1) % options.size)
                }
                .padding(horizontal = 6.dp)
        )
    }
}

@Composable
private fun GroupsDialog(
    appLabel: String,
    allGroups: List<String>,
    memberships: Set<String>,
    onToggleMembership: (group: String, member: Boolean) -> Unit,
    onCreateGroup: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appLabel, fontFamily = CormorantGaramond) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                allGroups.forEach { g ->
                    val inGroup = g in memberships
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleMembership(g, !inGroup) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = inGroup,
                            onCheckedChange = { onToggleMembership(g, it) },
                            colors = CheckboxDefaults.colors()
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(g)
                    }
                }
                if (creating) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        placeholder = { Text("Group name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row {
                        TextButton(onClick = {
                            if (newName.isNotBlank()) onCreateGroup(newName)
                            newName = ""
                            creating = false
                        }) { Text("Create") }
                        TextButton(onClick = {
                            newName = ""
                            creating = false
                        }) { Text("Cancel") }
                    }
                } else {
                    TextButton(onClick = { creating = true }) {
                        Text("＋ New group")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun GroupPage(
    groupName: String,
    pageApps: List<AppInfo>,
    preGrouped: List<Pair<Char, List<AppInfo>>>,
    pinned: Set<String>,
    hidden: Set<String>,
    listState: LazyListState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onInfo: (AppInfo, Rect?) -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleHide: (String) -> Unit,
    onRequestRename: (AppInfo) -> Unit,
    onRequestGroup: (AppInfo) -> Unit,
    searchFocusRequester: FocusRequester? = null
) {
    val grouped = if (preGrouped.isNotEmpty()) preGrouped else remember(pageApps) {
        if (groupName == ALL_GROUP)
            pageApps.groupBy { bucketFor(it.label) }.toList().sortedBy { it.first }
        else emptyList()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (groupName == SEARCH_GROUP) {
            SearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                focusRequester = searchFocusRequester
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 0.dp, end = 36.dp, top = 4.dp, bottom = 32.dp)
        ) {
            if (groupName == ALL_GROUP) {
                grouped.forEach { (letter, appsInGroup) ->
                    item(key = "h_$letter", contentType = "header") {
                        SectionHeader(letter)
                    }
                    items(appsInGroup, key = { it.key }, contentType = { "app" }) { app ->
                        AppRow(
                            app = app,
                            isPinned = app.key in pinned,
                            isHidden = app.key in hidden,
                            onLaunch = { bounds -> onLaunch(app, bounds) },
                            onInfo = { bounds -> onInfo(app, bounds) },
                            onTogglePin = { onTogglePin(app.key) },
                            onToggleHide = { onToggleHide(app.key) },
                            onRequestRename = { onRequestRename(app) },
                            onRequestGroup = { onRequestGroup(app) }
                        )
                    }
                }
            } else {
                items(pageApps, key = { it.key }, contentType = { "app" }) { app ->
                    AppRow(
                        app = app,
                        isPinned = app.key in pinned,
                        isHidden = app.key in hidden,
                        onLaunch = { bounds -> onLaunch(app, bounds) },
                        onInfo = { bounds -> onInfo(app, bounds) },
                        onTogglePin = { onTogglePin(app.key) },
                        onToggleHide = { onToggleHide(app.key) },
                        onRequestRename = { onRequestRename(app) },
                        onRequestGroup = { onRequestGroup(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester? = null
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        placeholder = {
            Text(
                "search",
                color = CreamDim,
                fontStyle = FontStyle.Italic,
                fontFamily = CormorantGaramond,
                fontSize = 20.sp
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = Cream,
            unfocusedTextColor = Cream,
            cursorColor = Cream,
            focusedIndicatorColor = CreamFaint,
            unfocusedIndicatorColor = CreamFaint
        ),
        textStyle = TextStyle(
            fontFamily = CormorantGaramond,
            fontSize = 20.sp,
            color = Cream
        )
    )
}

private fun appsForGroup(
    groupName: String,
    apps: List<AppInfo>,
    names: Map<String, String>,
    hidden: Set<String>,
    showHidden: Boolean,
    groups: Map<String, Set<String>>,
    pinned: Set<String>,
    searchQuery: String
): List<AppInfo> {
    val includeHidden = showHidden || groupName == SEARCH_GROUP
    val visible = apps
        .filter { includeHidden || it.key !in hidden }
        .map { app -> names[app.key]?.let { app.copy(label = it) } ?: app }
        .sortedBy { it.label.lowercase() }

    return when (groupName) {
        ALL_GROUP -> visible
        SEARCH_GROUP -> {
            if (searchQuery.isBlank()) emptyList()
            else visible.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }
        PINNED_GROUP -> visible.filter { it.key in pinned }
        else -> {
            val keys = groups[groupName] ?: emptySet()
            visible.filter { it.key in keys }
        }
    }
}

private fun computeLetterIndex(
    groupName: String,
    pageApps: List<AppInfo>,
    grouped: List<Pair<Char, List<AppInfo>>> = emptyList()
): Map<Char, Int> {
    val map = HashMap<Char, Int>()
    if (groupName == ALL_GROUP) {
        var i = 0
        val g = if (grouped.isNotEmpty()) grouped
                else pageApps.groupBy { bucketFor(it.label) }.toList().sortedBy { it.first }
        for ((letter, list) in g) {
            map[letter] = i
            i += 1 + list.size
        }
    } else {
        pageApps.forEachIndexed { i, app ->
            map.putIfAbsent(bucketFor(app.label), i)
        }
    }
    return map
}

private fun bucketFor(label: String): Char {
    val c = label.firstOrNull()?.uppercaseChar() ?: '#'
    return if (c.isLetter()) c else '#'
}
