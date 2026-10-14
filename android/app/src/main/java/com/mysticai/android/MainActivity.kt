package com.mysticai.android

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 10, 15)
        window.navigationBarColor = android.graphics.Color.rgb(9, 10, 15)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent { MysticTheme { MysticApp() } }
    }
}

@Composable
private fun MysticApp(model: MysticViewModel = viewModel()) {
    val context = LocalContext.current
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var documentToExport by remember { mutableStateOf<Project?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri ->
        val project = documentToExport
        documentToExport = null
        if (uri != null && project != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(project.html.toByteArray(Charsets.UTF_8))
                        } ?: error("The selected location could not be opened.")
                    }
                } catch (exception: Exception) {
                    model.reportError("Could not save your HTML file: ${exception.message}")
                }
            }
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                try {
                    val files = withContext(Dispatchers.IO) {
                        uris.map { uri -> context.readReference(uri) }
                    }
                    model.addReferences(files)
                } catch (exception: Exception) {
                    model.reportError(exception.message ?: "Could not read the selected reference file.")
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        model.initialize(context.filesDir)
    }

    val selectedProject = state.projects.firstOrNull { it.id == state.selectedProjectId }
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF111019), Ink, Ink))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader()
            Box(modifier = Modifier.weight(1f)) {
                when (state.activeTab) {
                    AppTab.BUILD -> BuildScreen(
                        state = state,
                        selectedProject = selectedProject,
                        onPromptChanged = model::setPrompt,
                        onPickFiles = { fileLauncher.launch(arrayOf("*/*")) },
                        onRemoveFile = model::removeReference,
                        onBuild = model::build,
                        onDismissError = model::clearError,
                        onExport = { project ->
                            documentToExport = project
                            exportLauncher.launch("${projectFileName(project.prompt)}.html")
                        },
                    )
                    AppTab.PROJECTS -> ProjectsScreen(
                        state = state,
                        onOpen = model::selectProject,
                        onCreate = { model.setTab(AppTab.BUILD) },
                    )
                    AppTab.PROFILE -> ProfileScreen(state = state)
                }
            }
            BottomNavigation(
                selected = state.activeTab,
                onSelect = model::setTab,
                modifier = Modifier.padding(bottom = systemBottom),
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 15.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(37.dp)
                    .shadow(9.dp, RoundedCornerShape(13.dp))
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(Champagne, Color(0xFFB7925A)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Ink, modifier = Modifier.size(21.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy((-2).dp)) {
                Text("MYSTIC", color = MainText, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.4.sp)
                Text("AI STUDIO", color = Champagne, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.1.sp)
            }
        }
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x1AFFD88D))
                .border(1.dp, Color(0x35E6C98D), CircleShape)
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Mint))
            Text("GEMINI LIVE", color = Champagne, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun BuildScreen(
    state: MysticUiState,
    selectedProject: Project?,
    onPromptChanged: (String) -> Unit,
    onPickFiles: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    onBuild: () -> Unit,
    onDismissError: () -> Unit,
    onExport: (Project) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 17.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("YOUR PRIVATE CREATIVE STUDIO", color = SoftText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                    Box(Modifier.width(25.dp).height(1.dp).background(Champagne.copy(alpha = .7f)))
                }
                Text("Dream it.\nBuild it beautifully.", color = MainText, fontSize = 35.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp)
                Text("One idea. Five specialist agents. A real app you can use.", color = SoftText, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        item {
            PromptComposer(
                state = state,
                onPromptChanged = onPromptChanged,
                onPickFiles = onPickFiles,
                onRemoveFile = onRemoveFile,
                onBuild = onBuild,
            )
        }
        if (state.error != null) {
            item { ErrorCard(message = state.error, onDismiss = onDismissError) }
        }
        item { AgentShowcase(isBuilding = state.isBuilding, project = selectedProject) }
        if (selectedProject != null) {
            item {
                ProjectPreview(
                    project = selectedProject,
                    onExport = { onExport(selectedProject) },
                )
            }
        } else if (!state.isBuilding) {
            item { EmptyPreviewHint() }
        }
    }
}

@Composable
private fun PromptComposer(
    state: MysticUiState,
    onPromptChanged: (String) -> Unit,
    onPickFiles: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    onBuild: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A1922), Surface, Color(0xFF12131A))))
            .border(1.dp, Color(0xFF34303A), RoundedCornerShape(24.dp))
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Champagne, modifier = Modifier.size(17.dp))
            Text("A little idea goes a long way", color = MainText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF0E0F15))
                .border(1.dp, Hairline, RoundedCornerShape(15.dp))
                .padding(13.dp),
        ) {
            if (state.prompt.isBlank()) {
                Text(
                    "Describe the app you want to bring to life…",
                    color = SoftText.copy(alpha = .78f),
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                )
            }
            BasicTextField(
                value = state.prompt,
                onValueChange = { if (it.length <= 6000) onPromptChanged(it) },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(color = MainText, fontSize = 12.sp, lineHeight = 19.sp),
                cursorBrush = SolidColor(Champagne),
                maxLines = 6,
            )
            Text(
                "${state.prompt.length}/6000",
                color = SoftText.copy(alpha = .75f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = onPickFiles,
                enabled = !state.isBuilding,
                colors = ButtonDefaults.textButtonColors(contentColor = SoftText),
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Add references", fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Text("IMAGES · PDF · TEXT", color = SoftText.copy(alpha = .8f), fontSize = 8.sp, letterSpacing = .8.sp)
        }
        if (state.references.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.references.forEachIndexed { index, file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(Raised)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${file.name} · ${formatBytes(file.bytes.size.toLong())}", color = MainText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemoveFile(index) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp)) {
                            Icon(Icons.Filled.Close, "Remove ${file.name}", tint = SoftText, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        Button(
            onClick = onBuild,
            enabled = !state.isBuilding,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Champagne, contentColor = Ink, disabledContainerColor = Champagne.copy(alpha = .55f)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 17.dp, vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().height(51.dp),
        ) {
            if (state.isBuilding) {
                CircularProgressIndicator(color = Ink, strokeWidth = 2.dp, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
                Text("Your team is creating…", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("Build my app", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = .2.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(15.dp))
            }
        }
        Text(
            "YOUR FILES ARE SENT SECURELY TO MYSTIC AI FOR THIS BUILD",
            color = SoftText.copy(alpha = .75f),
            fontSize = 8.sp,
            letterSpacing = .65.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun AgentShowcase(isBuilding: Boolean, project: Project?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("THE CREATIVE COLLECTIVE", color = SoftText, fontSize = 8.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                Text("Five minds. One creation.", color = MainText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isBuilding) Color(0x2D79D9B5) else Color(0x22E6C98D))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(if (isBuilding) "IN SESSION" else if (project != null) "COMPLETE" else "READY", color = if (isBuilding) Mint else Champagne, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            agentNames.forEachIndexed { index, (key, name) ->
                val result = project?.agents?.get(key)
                AgentCard(
                    index = index + 1,
                    name = name,
                    status = when {
                        isBuilding -> "WORKING"
                        result?.status == "Complete" -> "COMPLETE"
                        result?.status == "Failed" -> "NEEDS REVIEW"
                        else -> "READY"
                    },
                    active = isBuilding,
                    done = result?.status == "Complete" && !isBuilding,
                )
            }
        }
    }
}

@Composable
private fun AgentCard(index: Int, name: String, status: String, active: Boolean, done: Boolean) {
    val accent = if (done) Mint else if (active) Champagne else Lavender
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A1A25), Surface)))
            .border(1.dp, if (active || done) accent.copy(alpha = .43f) else Hairline, RoundedCornerShape(15.dp))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = .13f)),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Filled.Check, null, tint = accent, modifier = Modifier.size(15.dp))
                else Text("%02d".format(index), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.size(5.dp).clip(CircleShape).background(if (active) Champagne else if (done) Mint else SoftText.copy(alpha = .45f)))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, color = MainText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(status, color = accent, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .55.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ProjectPreview(project: Project, onExport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 15.dp, end = 9.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("YOUR LIVE PREVIEW", color = Champagne, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text(project.prompt, color = MainText, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onExport) {
                Icon(Icons.Filled.Share, null, tint = Champagne, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Export", color = Champagne, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Divider(color = Hairline)
        Box(modifier = Modifier.fillMaxWidth().height(330.dp).background(Color.White)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                return request.url.scheme == "http" || request.url.scheme == "https"
                            }

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                if (request.url.scheme == "http" || request.url.scheme == "https") {
                                    return WebResourceResponse("text/plain", "UTF-8", null)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        loadDataWithBaseURL("https://preview.invalid/", project.html, "text/html", "UTF-8", null)
                    }
                },
                update = { view ->
                    if (view.tag != project.id) {
                        view.tag = project.id
                        view.loadDataWithBaseURL("https://preview.invalid/", project.html, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${project.model}  ·  ${formatDate(project.createdAt)}", color = SoftText, fontSize = 9.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(Mint))
                Text("SANDBOXED", color = Mint, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
            }
        }
    }
}

@Composable
private fun EmptyPreviewHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Color(0xFF11131B))
            .border(1.dp, Hairline, RoundedCornerShape(17.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier.size(37.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x20AA9BFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Code, null, tint = Lavender, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Your canvas is ready", color = MainText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("Your finished app will appear here, ready to preview and export.", color = SoftText, fontSize = 9.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF27151B))
            .border(1.dp, Rose.copy(alpha = .35f), RoundedCornerShape(13.dp))
            .padding(start = 12.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(message, color = Color(0xFFFFB6BE), fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Dismiss error", tint = Rose, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun ProjectsScreen(state: MysticUiState, onOpen: (Project) -> Unit, onCreate: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(19.dp))
        Text("YOUR LIBRARY", color = SoftText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Spacer(Modifier.height(6.dp))
        Text("Made by you.", color = MainText, fontSize = 31.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.7).sp)
        Spacer(Modifier.height(4.dp))
        Text("Every idea, saved and ready to return to.", color = SoftText, fontSize = 11.sp)
        Spacer(Modifier.height(18.dp))
        if (state.projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0x19E6C98D)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.FolderOpen, null, tint = Champagne, modifier = Modifier.size(26.dp))
                    }
                    Text("A blank canvas.", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Your first creation is only a prompt away.", color = SoftText, fontSize = 11.sp)
                    Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = Champagne, contentColor = Ink)) {
                        Text("Create your first app", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
            ) {
                items(state.projects, key = Project::id) { project ->
                    ProjectRow(project = project, selected = project.id == state.selectedProjectId, onClick = { onOpen(project) })
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(project: Project, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) Color(0xFF1A1922) else Surface)
            .border(1.dp, if (selected) Champagne.copy(alpha = .4f) else Hairline, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(41.dp).clip(RoundedCornerShape(13.dp)).background(Color(0x1FE6C98D)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Code, null, tint = Champagne, modifier = Modifier.size(19.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(project.prompt, color = MainText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${formatDate(project.createdAt)}  ·  ${project.model}", color = SoftText, fontSize = 9.sp)
        }
        Icon(Icons.Filled.FolderOpen, null, tint = if (selected) Champagne else SoftText, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun ProfileScreen(state: MysticUiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("YOUR STUDIO", color = SoftText, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text("Thoughtfully built.", color = MainText, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.6).sp)
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF211D27), Surface)))
                .border(1.dp, Color(0xFF433A3B), RoundedCornerShape(21.dp)).padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x24E6C98D)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Champagne, modifier = Modifier.size(21.dp))
            }
            Text("Mystic AI", color = MainText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("A focused creative studio where five specialist agents turn your ideas into polished, working web apps.", color = SoftText, fontSize = 11.sp, lineHeight = 17.sp)
            Text("POWERED BY  ${state.projects.firstOrNull()?.model ?: "GEMINI 2.5 FLASH"}", color = Champagne, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        SettingRow(icon = Icons.Filled.Tune, title = "Five focused agents", detail = "Plan · Design · Architect · Build · Review")
        SettingRow(icon = Icons.Filled.FolderOpen, title = "Private project library", detail = "${state.projects.size} saved ${if (state.projects.size == 1) "creation" else "creations"} on this device")
        SettingRow(icon = Icons.Filled.Share, title = "Export your work", detail = "Save a standalone HTML file anytime")
        Spacer(Modifier.weight(1f))
        Text("Your projects stay on this device. Files you attach are sent to Mystic AI for your build.", color = SoftText, fontSize = 9.sp, lineHeight = 14.sp)
        Text("MYSTIC AI  ·  ANDROID 1.0", color = SoftText.copy(alpha = .7f), fontSize = 8.sp, letterSpacing = 1.2.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Surface).border(1.dp, Hairline, RoundedCornerShape(15.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(icon, null, tint = Champagne, modifier = Modifier.size(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = MainText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = SoftText, fontSize = 9.sp)
        }
    }
}

@Composable
private fun BottomNavigation(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0E14))
            .border(1.dp, Color(0xFF20212A))
            .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Triple(AppTab.BUILD, Icons.Filled.Home, "Create"),
            Triple(AppTab.PROJECTS, Icons.Filled.FolderOpen, "Projects"),
            Triple(AppTab.PROFILE, Icons.Filled.Person, "Studio"),
        ).forEach { (tab, icon, label) ->
            val active = selected == tab
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(icon, null, tint = if (active) Champagne else SoftText, modifier = Modifier.size(18.dp))
                Text(label, color = if (active) Champagne else SoftText, fontSize = 8.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                Box(Modifier.width(4.dp).height(2.dp).clip(CircleShape).background(if (active) Champagne else Color.Transparent))
            }
        }
    }
}

private fun Context.readReference(uri: Uri): ReferenceFile {
    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "Reference file"
    val mimeType = contentResolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
    val allowed = mimeType in setOf(
        "image/png", "image/jpeg", "image/webp", "image/gif", "application/pdf",
        "text/plain", "text/markdown", "text/csv", "text/html", "text/css",
        "text/javascript", "application/json",
    )
    if (!allowed) throw IllegalArgumentException("$name is not a supported image, PDF, or text file.")
    val output = ByteArrayOutputStream()
    contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > 3 * 1024 * 1024) {
                throw IllegalArgumentException("$name is larger than the 3 MB reference limit.")
            }
            output.write(buffer, 0, count)
        }
    } ?: throw IllegalArgumentException("Could not open $name.")
    if (output.size() == 0) throw IllegalArgumentException("$name is empty.")
    return ReferenceFile(name, mimeType, output.toByteArray())
}

private fun projectFileName(prompt: String): String =
    prompt.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "mystic-app" }

private fun formatBytes(bytes: Long): String =
    if (bytes < 1_048_576) "${maxOf(1, bytes / 1024)} KB" else "%.1f MB".format(Locale.ROOT, bytes / 1_048_576.0)

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
