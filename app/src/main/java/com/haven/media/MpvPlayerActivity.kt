package com.haven.media

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.delay
import java.util.Locale

private val PlayerBg = Color(0xFF090C09)
private val PlayerLeaf = Color(0xFFB5E6A5)
private val PlayerMuted = Color(0xFFA0A69E)

class MpvPlayerActivity : ComponentActivity() {
    private var mpv: MPVLib? = null
    private var playerError by mutableStateOf<String?>(null)
    private var loaded = false
    private var surfaceCallback: SurfaceHolder.Callback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        if (uri == null) {
            playerError = "This media item could not be opened."
        } else {
            initializeMpv()
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = PlayerLeaf, background = PlayerBg, surface = Color(0xFF171B17))) {
                PlayerScreen(
                    uri = uri,
                    mpv = mpv,
                    error = playerError,
                    onClose = ::finish,
                    onError = { playerError = it },
                    onSurface = ::attachSurface
                )
            }
        }
    }

    private fun initializeMpv() {
        try {
            val engine = MPVLib.create(applicationContext)
            if (engine == null) {
                playerError = "The mpv engine could not start."
                return
            }
            if (intent.getBooleanExtra(EXTRA_WEBDAV, false)) {
                val account = WebDavVault.load(applicationContext)
                val remoteUri = intent.getStringExtra(EXTRA_URI)
                if (account == null || remoteUri == null || !WebDavClient.isAllowedMediaUrl(account, remoteUri)) {
                    playerError = "The secure pCloud stream is no longer available. Reconnect your account and try again."
                    return
                }
                engine.setOptionString("http-header-fields", "Authorization: ${account.authorization}")
                val caBundle = applicationContext.filesDir.resolve("cacert.pem")
                if (!caBundle.isFile || caBundle.length() == 0L) {
                    applicationContext.assets.open("cacert.pem").use { source ->
                        caBundle.outputStream().use { target -> source.copyTo(target) }
                    }
                }
                engine.setOptionString("tls-verify", "yes")
                engine.setOptionString("tls-ca-file", caBundle.absolutePath)
            }
            engine.setOptionString("config", "no")
            engine.setOptionString("vo", "gpu")
            engine.setOptionString("gpu-context", "android")
            engine.setOptionString("opengl-es", "yes")
            engine.setOptionString("hwdec", "mediacodec")
            engine.setOptionString("ao", "audiotrack,opensles")
            engine.setOptionString("input-default-bindings", "no")
            engine.setOptionString("osc", "no")
            engine.setOptionString("osd-level", "0")
            engine.setOptionString("keep-open", "yes")
            engine.setOptionString("save-position-on-quit", "no")
            engine.setOptionString("demuxer-max-bytes", "67108864")
            engine.setOptionString("demuxer-max-back-bytes", "67108864")
            engine.init()
            mpv = engine
        } catch (error: Throwable) {
            playerError = error.message?.let { "Could not start mpv: $it" } ?: "Could not start the mpv engine."
        }
    }

    private fun attachSurface(surface: android.view.Surface) {
        val engine = mpv ?: return
        try {
            engine.attachSurface(surface)
            if (!loaded) {
                val uri = intent.getStringExtra(EXTRA_URI) ?: return
                engine.command(arrayOf("loadfile", uri))
                loaded = true
            }
        } catch (error: Throwable) {
            playerError = "mpv could not open this media file."
        }
    }

    override fun onDestroy() {
        surfaceCallback?.let { callback ->
            (window.decorView as? android.view.ViewGroup)?.findViewWithTag<SurfaceView>("haven-mpv-surface")
                ?.holder?.removeCallback(callback)
        }
        runCatching { mpv?.command(arrayOf("stop")) }
        runCatching { mpv?.destroy() }
        mpv = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "com.haven.media.extra.MEDIA_URI"
        const val EXTRA_NAME = "com.haven.media.extra.MEDIA_NAME"
        const val EXTRA_WEBDAV = "com.haven.media.extra.WEBDAV_STREAM"
    }

    @Composable
    private fun PlayerScreen(
        uri: Uri?,
        mpv: MPVLib?,
        error: String?,
        onClose: () -> Unit,
        onError: (String) -> Unit,
        onSurface: (android.view.Surface) -> Unit
    ) {
        val context = LocalContext.current
        val name = remember(uri) {
            intent.getStringExtra(EXTRA_NAME)?.takeIf(String::isNotBlank)
                ?: uri?.lastPathSegment?.substringAfterLast('/')?.substringBefore('?')
                ?: "Now playing"
        }
        var playing by remember { mutableStateOf(true) }
        var controlsVisible by remember { mutableStateOf(true) }
        var position by remember { mutableFloatStateOf(0f) }
        var duration by remember { mutableFloatStateOf(0f) }
        var dragging by remember { mutableStateOf(false) }
        var currentPositionText by remember { mutableStateOf("0:00") }
        var durationText by remember { mutableStateOf("0:00") }
        var speed by remember { mutableFloatStateOf(1f) }
        var showSpeedMenu by remember { mutableStateOf(false) }
        var isAudio by remember { mutableStateOf(uri?.toString()?.substringBefore('?')?.substringAfterLast('.')?.lowercase() in setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus")) }

        val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { subtitle ->
            if (subtitle != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(subtitle, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    mpv?.command(arrayOf("sub-add", subtitle.toString(), "select"))
                }.onFailure { onError("Could not load that subtitle file.") }
            }
        }

        BackHandler(onBack = onClose)

        LaunchedEffect(mpv) {
            while (true) {
                mpv?.let { player ->
                    val pos = (player.getPropertyDouble("time-pos") ?: 0.0).toFloat().coerceAtLeast(0f)
                    val len = (player.getPropertyDouble("duration") ?: 0.0).toFloat().coerceAtLeast(0f)
                    if (!dragging) position = pos
                    duration = len
                    currentPositionText = formatPlayerTime(pos)
                    durationText = formatPlayerTime(len)
                    playing = player.getPropertyBoolean("pause") != true
                    isAudio = player.getPropertyBoolean("current-tracks/video/selected") != true
                }
                delay(350)
            }
        }

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF111711), PlayerBg, Color(0xFF050605)))
            ).clickable { controlsVisible = !controlsVisible }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    SurfaceView(viewContext).apply {
                        tag = "haven-mpv-surface"
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        val callback = object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                onSurface(holder.surface)
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                runCatching { mpv?.detachSurface() }
                            }
                        }
                        surfaceCallback = callback
                        holder.addCallback(callback)
                    }
                }
            )

            if (isAudio && error == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(142.dp).clip(RoundedCornerShape(36.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF314532), Color(0xFF172219)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = PlayerLeaf, modifier = Modifier.size(70.dp))
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text("HAVEN  ·  MPV AUDIO", color = PlayerLeaf, fontSize = 9.sp, letterSpacing = 2.sp)
                }
            }

            AnimatedVisibility(
                visible = controlsVisible || error != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(150.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(.72f), Color.Transparent))))
                    Box(Modifier.fillMaxWidth().height(190.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.82f)))))

                    Row(
                        Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(horizontal = 15.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(.09f))) {
                            Icon(Icons.Rounded.Close, "Close player", tint = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("MPV PLAYER", color = PlayerLeaf, fontSize = 9.sp, letterSpacing = 1.6.sp)
                        }
                        Box {
                            TextButton(onClick = { showSpeedMenu = true }) { Text(String.format(Locale.US, "%.2gx", speed), color = Color.White, fontWeight = FontWeight.SemiBold) }
                            DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                                listOf(.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                                    DropdownMenuItem(
                                        text = { Text(if (rate == 1f) "Normal" else "${rate}×") },
                                        onClick = {
                                            speed = rate
                                            mpv?.setPropertyDouble("speed", rate.toDouble())
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (error != null) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            color = Color(0xEE191E19),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.ErrorOutline, null, tint = PlayerLeaf, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(10.dp))
                                Text("Unable to play this file", color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(error, color = PlayerMuted, fontSize = 12.sp)
                                Spacer(Modifier.height(14.dp))
                                TextButton(onClick = onClose) { Text("Close", color = PlayerLeaf) }
                            }
                        }
                    }

                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 17.dp)) {
                        Slider(
                            value = if (duration > 0f) position.coerceIn(0f, duration) else 0f,
                            onValueChange = { position = it; dragging = true },
                            onValueChangeFinished = {
                                mpv?.command(arrayOf("seek", position.toString(), "absolute"))
                                dragging = false
                            },
                            valueRange = 0f..duration.coerceAtLeast(1f),
                            enabled = error == null,
                            colors = SliderDefaults.colors(thumbColor = PlayerLeaf, activeTrackColor = PlayerLeaf, inactiveTrackColor = Color.White.copy(.24f))
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(currentPositionText, color = PlayerMuted, fontSize = 10.sp)
                            Text(durationText, color = PlayerMuted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream")) }) {
                                Icon(Icons.Rounded.Subtitles, "Load subtitles", tint = Color.White)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { mpv?.command(arrayOf("seek", "-10", "relative")) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(29.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            IconButton(
                                onClick = {
                                    playing = !playing
                                    mpv?.setPropertyBoolean("pause", !playing)
                                },
                                modifier = Modifier.size(62.dp).clip(CircleShape).background(PlayerLeaf)
                            ) {
                                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = PlayerBg, modifier = Modifier.size(34.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            IconButton(onClick = { mpv?.command(arrayOf("seek", "10", "relative")) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(29.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {
                                val next = if ((mpv?.getPropertyString("video-aspect-override") ?: "no") == "no") "16:9" else "no"
                                mpv?.setPropertyString("video-aspect-override", next)
                            }) {
                                Icon(Icons.Rounded.AspectRatio, "Toggle aspect ratio", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlayerTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
