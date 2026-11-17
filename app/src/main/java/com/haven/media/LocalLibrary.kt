package com.haven.media

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.Locale

private data class LocalMedia(
    val uri: Uri,
    val name: String,
    val mime: String?,
    val size: Long,
    val path: String,
    val folder: Boolean = false
)

@Composable
internal fun LocalLibraryScreen(destination: Destination) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("haven_local_library", 0) }
    var media by remember { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var currentPath by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()\n    HavenAppContextHolder.context = context

    fun persistUris() {
        prefs.edit().putStringSet("uris", media.filterNot { it.folder }.map { it.uri.toString() }.toSet()).apply()
    }

    val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val item = readDocument(context, uri)
                if (media.none { it.uri == uri }) media = media + item
            }
            persistUris()
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            busy = true
            val savedTrees = prefs.getStringSet("trees", emptySet()).orEmpty().toMutableSet()\n            savedTrees.add(uri.toString())\n            prefs.edit().putStringSet("trees", savedTrees).apply()\n            scope.launch {
                val scanned = withContext(Dispatchers.IO) { scanTree(context, uri) }
                media = (media.filterNot { it.path.startsWith(uri.toString()) } + scanned)
                    .distinctBy { it.uri }
                persistUris()
                busy = false
                Toast.makeText(context, "Added ${scanned.count { !it.folder }} items", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        val saved = prefs.getStringSet("uris", emptySet()).orEmpty()
        if (saved.isNotEmpty()) {
            media = withContext(Dispatchers.IO) {
                saved.mapNotNull { raw -> runCatching { readDocument(context, Uri.parse(raw)) }.getOrNull() }
            }
        }
        val trees = prefs.getStringSet("trees", emptySet()).orEmpty()
        if (trees.isNotEmpty()) {
            busy = true
            media = media + withContext(Dispatchers.IO) { trees.flatMap { scanTree(context, Uri.parse(it)) } }
            media = media.distinctBy { it.uri }
            busy = false
        }
    }

    val filtered = remember(media, destination, currentPath, query) {
        media.filter { item ->
            val inPath = if (currentPath.isEmpty()) !item.path.contains("/") else item.path.startsWith("$currentPath/")
            val matchesType = item.folder || when (destination) {
                Destination.Movies -> item.mime?.startsWith("video/") == true || item.name.isVideo()
                Destination.Music -> item.mime?.startsWith("audio/") == true || item.name.isAudio()
                Destination.Photos -> item.mime?.startsWith("image/") == true || item.name.isImage()
                else -> true
            }
            inPath && matchesType && item.name.contains(query, ignoreCase = true)
        }.sortedWith(compareByDescending<LocalMedia> { it.folder }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(10.dp))
        Surface(color = Color(0xFF243528), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(destination.icon, null, tint = Color(0xFFB5E6A5), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (currentPath.isBlank()) "Your local ${destination.label.lowercase()}" else currentPath.substringAfterLast('/'), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${filtered.count { !it.folder }} items · stored on this device", fontSize = 10.sp, color = Color(0xFF929B92))
                }
                if (currentPath.isNotBlank()) IconButton(onClick = { currentPath = currentPath.substringBeforeLast('/', "") }) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = Color(0xFFB5E6A5))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Search this library") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { filesPicker.launch(arrayOf("*/*")) }, enabled = !busy) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("Add files")
            }
            OutlinedButton(onClick = { folderPicker.launch(null) }, enabled = !busy) {
                Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(5.dp)); Text("Add folder")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFB5E6A5))
        if (filtered.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.LibraryAdd, null, tint = Color(0xFF929B92), modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(12.dp))
                Text(if (query.isNotBlank()) "No matches found" else "Your collection starts here", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("Add media or a folder from your device.", color = Color(0xFF929B92), fontSize = 12.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.uri.toString() }) { item ->
                    LocalMediaRow(item, onClick = {
                        if (item.folder) currentPath = item.path
                        else runCatching {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(item.uri, item.mime ?: "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }.onFailure {
                            Toast.makeText(context, "No compatible app found for this file", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun LocalMediaRow(item: LocalMedia, onClick: () -> Unit) {
    val icon = when {
        item.folder -> Icons.Rounded.Folder
        item.mime?.startsWith("video/") == true || item.name.isVideo() -> Icons.Rounded.Movie
        item.mime?.startsWith("audio/") == true || item.name.isAudio() -> Icons.Rounded.MusicNote
        item.mime?.startsWith("image/") == true || item.name.isImage() -> Icons.Rounded.Photo
        else -> Icons.Rounded.InsertDriveFile
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF191E19))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF243528)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color(0xFFB5E6A5))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(if (item.folder) "Folder · open" else "${item.mime ?: "File"} · ${formatSize(item.size)}", color = Color(0xFF929B92), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(if (item.folder) Icons.Rounded.ChevronRight else Icons.Rounded.OpenInNew, null, tint = Color(0xFF929B92))
    }
}

private fun readDocument(uri: Uri): LocalMedia {
    val nameAndSize = runCatching {
        val cursor = HavenAppContextHolder.context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val name = it.getString(0) ?: uri.lastPathSegment ?: "Media"
                val size = if (it.isNull(1)) 0L else it.getLong(1)
                name to size
            } else null
        }
    }.getOrNull() ?: ((uri.lastPathSegment ?: "Media") to 0L)
    val mime = context.contentResolver.getType(uri)
    return LocalMedia(uri, nameAndSize.first, mime, nameAndSize.second, nameAndSize.first)
}

private object HavenAppContextHolder {
    lateinit var context: android.content.Context
}

private fun scanTree(treeUri: Uri): List<LocalMedia> {
    HavenAppContextHolder.context.let { }
    val root = DocumentFile.fromTreeUri(HavenAppContextHolder.context, treeUri) ?: return emptyList()
    val result = mutableListOf<LocalMedia>()
    fun visit(folder: DocumentFile, prefix: String, depth: Int) {
        if (depth > 8 || result.size > 500) return
        folder.listFiles().forEach { child ->
            val path = if (prefix.isEmpty()) child.name.orEmpty() else "$prefix/${child.name.orEmpty()}"
            if (child.isDirectory) {
                result += LocalMedia(child.uri, child.name ?: "Folder", null, 0, path, true)
                visit(child, path, depth + 1)
            } else {
                val name = child.name ?: "Media"
                result += LocalMedia(child.uri, name, child.type, child.length(), path)
            }
        }
    }
    visit(root, "", 0)
    return result
}

private fun String.isVideo() = substringAfterLast('.', "").lowercase() in setOf("mp4", "mkv", "mov", "webm", "avi", "m4v")
private fun String.isAudio() = substringAfterLast('.', "").lowercase() in setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus")
private fun String.isImage() = substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "heic", "gif", "bmp")
private fun formatSize(size: Long): String = when {
    size >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", size / 1_000_000_000.0)
    size >= 1_000_000 -> String.format(Locale.US, "%.1f MB", size / 1_000_000.0)
    size >= 1_000 -> String.format(Locale.US, "%.0f KB", size / 1_000.0)
    else -> "$size B"
}
