package com.ahmad.kplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaEntry(val uri: Uri, val title: String, val durationMs: Long, val isVideo: Boolean)

class MainActivity : ComponentActivity() {
    private var externalUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalUri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App(externalUri) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) externalUri = intent.data
    }
}

@Composable
fun App(externalUri: Uri?) {
    var playlist by remember { mutableStateOf<List<MediaEntry>?>(null) }
    var startIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(externalUri) {
        externalUri?.let {
            playlist = listOf(MediaEntry(it, it.lastPathSegment ?: "Media", 0, true))
            startIndex = 0
        }
    }

    val list = playlist
    if (list == null) {
        LibraryScreen(onPlay = { items, index -> playlist = items; startIndex = index })
    } else {
        BackHandler { playlist = null }
        PlayerScreen(list, startIndex, onBack = { playlist = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onPlay: (List<MediaEntry>, Int) -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var granted by remember { mutableStateOf(false) }

    val perms = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = it.values.any { ok -> ok }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onPlay(listOf(MediaEntry(it, it.lastPathSegment ?: "Media", 0, true)), 0)
        }
    }

    LaunchedEffect(Unit) { permLauncher.launch(perms) }
    LaunchedEffect(granted) {
        if (granted) items = withContext(Dispatchers.IO) { queryMedia(context) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("KPlayer") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { picker.launch(arrayOf("video/*", "audio/*")) }) {
                Text("Open file")
            }
        }
    ) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text(if (granted) "No media found" else "Allow media access or open a file")
            }
        } else {
            LazyColumn(Modifier.padding(pad)) {
                itemsIndexed(items) { index, item ->
                    ListItem(
                        headlineContent = { Text(item.title, maxLines = 1) },
                        supportingContent = { Text((if (item.isVideo) "Video" else "Audio") + " • " + formatTime(item.durationMs)) },
                        modifier = Modifier.clickable { onPlay(items, index) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun queryMedia(context: Context): List<MediaEntry> {
    val out = mutableListOf<MediaEntry>()
    fun scan(base: Uri, isVideo: Boolean) {
        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, "duration")
        context.contentResolver.query(base, proj, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nC = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dC = c.getColumnIndexOrThrow("duration")
            while (c.moveToNext()) {
                out += MediaEntry(ContentUris.withAppendedId(base, c.getLong(idC)), c.getString(nC) ?: "?", c.getLong(dC), isVideo)
            }
        }
    }
    runCatching { scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true) }
    runCatching { scan(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, false) }
    return out
}
