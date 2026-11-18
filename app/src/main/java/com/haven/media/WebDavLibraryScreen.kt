package com.haven.media

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.net.URI
import java.util.Locale

private val CloudLeaf = Color(0xFFB5E6A5)
private val CloudMuted = Color(0xFF929B92)
private val CloudSurface = Color(0xFF191E19)

@Composable
internal fun WebDavLibraryScreen(destination: Destination, onShowLocal: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(WebDavVault.load(context)) }
    var email by remember { mutableStateOf(account?.email.orEmpty()) }
    var password by remember { mutableStateOf(account?.password.orEmpty()) }
    var region by remember { mutableStateOf(account?.region ?: WebDavRegion.US) }
    var entries by remember { mutableStateOf<List<WebDavEntry>>(emptyList()) }
    val folderStack = remember { mutableStateListOf("/") }
    var search by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadFolder(active: WebDavAccount, href: String) {
        scope.launch {
            busy = true
            error = null
            try {
                entries = WebDavClient.listFolder(active, href)
                if (folderStack.lastOrNull() != href) {
                    if (href == "/") {
                        folderStack.clear()
                        folderStack.add("/")
                    } else {
                        folderStack.add(href)
                    }
                }
            } catch (failure: Exception) {
                error = failure.message ?: "Could not load this pCloud folder."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(account) {
        account?.let { loadFolder(it, "/") }
    }

    BackHandler(enabled = account != null && folderStack.size > 1) {
        val active = account ?: return@BackHandler
        folderStack.removeAt(folderStack.lastIndex)
        loadFolder(active, folderStack.last())
    }

    val filtered = remember(entries, search, destination) {
        entries.filter { entry ->
            entry.name.contains(search, ignoreCase = true) &&
                (entry.isFolder || when (destination) {
                    Destination.Movies -> entry.name.isVideo()
                    Destination.Music -> entry.name.isAudio()
                    Destination.Photos -> entry.name.isImage()
                    else -> true
                })
        }
    }

    if (account == null) {
        var showPassword by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(18.dp))
            Surface(color = Color(0xFF18251A), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF243528)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CloudQueue, null, tint = CloudLeaf, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(15.dp))
                    Text("Connect your pCloud", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Browse your collection and play media directly from your lifetime storage.", color = CloudMuted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShowLocal, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PhoneAndroid, null); Spacer(Modifier.width(6.dp)); Text("On device")
                }
                FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF243528), contentColor = CloudLeaf)) {
                    Icon(Icons.Rounded.Cloud, null); Spacer(Modifier.width(6.dp)); Text("pCloud")
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text("DATA REGION", color = CloudMuted, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    listOf(WebDavRegion.US, WebDavRegion.EU).forEach { option ->
                        FilterChip(
                            selected = region == option,
                            onClick = { region = option },
                            label = { Text(option.label) },
                            leadingIcon = { Icon(if (option == WebDavRegion.US) Icons.Rounded.Public else Icons.Rounded.Language, null) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("pCloud email") },
                    leadingIcon = { Icon(Icons.Rounded.Email, null) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("pCloud password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Toggle password visibility")
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(14.dp))
                if (error != null) CloudError(error!!)
                Button(
                    onClick = {
                        val candidate = WebDavAccount(region, email.trim(), password)
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                val root = WebDavClient.listFolder(candidate, "/")
                                WebDavVault.save(context, candidate)
                                account = candidate
                                entries = root
                                folderStack.clear()
                                folderStack.add("/")
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not connect to pCloud."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CloudLeaf, contentColor = Color(0xFF101310))
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = Color(0xFF101310))
                    else {
                        Icon(Icons.Rounded.LockOpen, null); Spacer(Modifier.width(8.dp)); Text("Connect securely", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Your password is encrypted on this device. pCloud Crypto folders are not available over WebDAV.", color = CloudMuted, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
        return
    }

    val active = account!!
    val currentHref = folderStack.lastOrNull() ?: "/"
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onShowLocal, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PhoneAndroid, null); Spacer(Modifier.width(6.dp)); Text("On device")
            }
            FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF243528), contentColor = CloudLeaf)) {
                Icon(Icons.Rounded.Cloud, null); Spacer(Modifier.width(6.dp)); Text("pCloud")
            }
            IconButton(onClick = {
                WebDavVault.clear(context)
                account = null
                entries = emptyList()
                password = ""
                error = null
            }) { Icon(Icons.Rounded.Logout, "Disconnect pCloud", tint = CloudMuted) }
        }
        Spacer(Modifier.height(10.dp))
        Surface(color = Color(0xFF243528), shape = RoundedCornerShape(19.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(destination.icon, null, tint = CloudLeaf, modifier = Modifier.size(27.dp))
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(folderTitle(currentHref), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${filtered.count { !it.isFolder }} items · pCloud ${active.region.label}", fontSize = 10.sp, color = CloudMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (folderStack.size > 1) IconButton(onClick = {
                    folderStack.removeAt(folderStack.lastIndex)
                    loadFolder(active, folderStack.last())
                }) { Icon(Icons.Rounded.ArrowBack, "Parent folder", tint = CloudLeaf) }
                IconButton(onClick = { loadFolder(active, currentHref) }, enabled = !busy) { Icon(Icons.Rounded.Refresh, "Refresh folder", tint = CloudLeaf) }
            }
        }
        Spacer(Modifier.height(11.dp))
        OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, placeholder = { Text("Search this pCloud folder") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(8.dp))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CloudLeaf)
        if (error != null) { Spacer(Modifier.height(6.dp)); CloudError(error!!) }
        if (filtered.isEmpty() && !busy) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.CloudOff, null, tint = CloudMuted, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(12.dp))
                Text(if (search.isNotBlank()) "No matches in this folder" else "This folder is empty", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("Folders and media from your pCloud account will appear here.", color = CloudMuted, fontSize = 11.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 6.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(filtered, key = { it.href }) { entry ->
                    WebDavRow(entry) {
                        if (entry.isFolder) loadFolder(active, entry.href)
                        else if (entry.name.isVideo() || entry.name.isAudio() || entry.name.isImage()) {
                            val mediaUrl = WebDavClient.fileUrl(active, entry.href)
                            if (WebDavClient.isAllowedMediaUrl(active, mediaUrl)) {
                                context.startActivity(Intent(context, MpvPlayerActivity::class.java).apply {
                                    putExtra(MpvPlayerActivity.EXTRA_URI, mediaUrl)
                                    putExtra(MpvPlayerActivity.EXTRA_NAME, entry.name)
                                    putExtra(MpvPlayerActivity.EXTRA_WEBDAV, true)
                                })
                            }
                        } else Toast.makeText(context, "Built-in player supports video, music, and photos.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun WebDavRow(entry: WebDavEntry, onClick: () -> Unit) {
    val icon = when {
        entry.isFolder -> Icons.Rounded.Folder
        entry.name.isVideo() -> Icons.Rounded.Movie
        entry.name.isAudio() -> Icons.Rounded.MusicNote
        entry.name.isImage() -> Icons.Rounded.Photo
        else -> Icons.Rounded.InsertDriveFile
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(CloudSurface)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF243528)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = CloudLeaf)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(if (entry.isFolder) "Folder · browse" else "${entry.mimeType ?: "Media"} · ${formatCloudSize(entry.size)}", color = CloudMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(if (entry.isFolder) Icons.Rounded.ChevronRight else Icons.Rounded.PlayCircle, null, tint = CloudMuted)
    }
}

@Composable
private fun CloudError(message: String) {
    Surface(color = Color(0xFF382522), shape = RoundedCornerShape(13.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = Color(0xFFFFB4A9))
            Spacer(Modifier.width(9.dp))
            Text(message, color = Color(0xFFFFDAD3), fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

private fun folderTitle(href: String): String = runCatching {
    URI(href).path.trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() } ?: "pCloud library"
}.getOrElse {
    Uri.parse(href).lastPathSegment?.takeIf { it.isNotBlank() } ?: "pCloud library"
}

private fun String.isVideo() = substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("mp4", "mkv", "mov", "webm", "avi", "m4v", "mpeg", "mpg", "ts")
private fun String.isAudio() = substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "aiff", "wma")
private fun String.isImage() = substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp", "heic", "gif", "bmp", "avif")
private fun formatCloudSize(size: Long): String = when {
    size >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", size / 1_000_000_000.0)
    size >= 1_000_000 -> String.format(Locale.US, "%.1f MB", size / 1_000_000.0)
    size >= 1_000 -> String.format(Locale.US, "%.0f KB", size / 1_000.0)
    else -> "$size B"
}
