package com.haven.media

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val Ink = Color(0xFF101310)
private val Surface = Color(0xFF191E19)
private val SurfaceRaised = Color(0xFF222922)
private val Muted = Color(0xFF929B92)
private val Leaf = Color(0xFFB5E6A5)
private val LeafDark = Color(0xFF243528)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 19, 16)
        window.navigationBarColor = android.graphics.Color.rgb(16, 19, 16)
        setContent { HavenTheme { HavenApp() } }
    }
}

@Composable
private fun HavenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink, surface = Surface, surfaceVariant = SurfaceRaised,
            primary = Leaf, onPrimary = Ink, onBackground = Color(0xFFF1F3EE),
            onSurface = Color(0xFFF1F3EE), secondary = Color(0xFF9ACB91)
        ),
        content = content
    )
}

internal enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home), Movies("Movies", Icons.Rounded.Movie),
    Music("Music", Icons.Rounded.MusicNote), Photos("Photos", Icons.Rounded.Photo),
    Files("Files", Icons.Rounded.Folder)
}

private data class MediaItem(val title: String, val subtitle: String, val image: String, val progress: Float)
private val watchItems = listOf(
    MediaItem("The Grand Budapest Hotel", "Wes Anderson · 58 min left", "photo-1478720568477-152d9b164e26", .72f),
    MediaItem("Perfect Days", "Wim Wenders · 34 min left", "photo-1485846234645-a62644f84728", .43f),
    MediaItem("Little Forest", "Yim Soon-rye · 1 hr 12 min left", "photo-1440404653325-ab127d49adece", .26f)
)
private data class CollectionItem(val title: String, val subtitle: String, val image: String, val destination: Destination)
private val collections = listOf(
    CollectionItem("Movies", "24 films", "photo-1489599849927-2ee91c4cc6e6", Destination.Movies),
    CollectionItem("Soundtrack", "186 tracks", "photo-1511379938547-c1f69419868d", Destination.Music),
    CollectionItem("Photo journal", "1,284 moments", "photo-1470770841072-f978cf4d019e", Destination.Photos),
    CollectionItem("Weekend escapes", "12 favorites", "photo-1500530855697-b586d89ba3ee", Destination.Files)
)

@Composable
private fun HavenApp() {
    var destination by remember { mutableStateOf(Destination.Home) }
    var cloudLibrary by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    fun navigateTo(next: Destination) {
        if (next != destination) destination = next
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            Column {
                MiniPlayer(playing, onPlay = { playing = !playing })
                NavigationBar(containerColor = Color(0xFF151915), tonalElevation = 0.dp, windowInsets = NavigationBarDefaults.windowInsets) {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { cloudLibrary = false; navigateTo(item) },
                            icon = {
                                val iconScale by animateFloatAsState(
                                    targetValue = if (destination == item) 1.06f else 1f,
                                    animationSpec = spring(dampingRatio = .78f, stiffness = 480f),
                                    label = "navigation-icon-${item.name}"
                                )
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                                )
                            },
                            label = { Text(item.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Ink, indicatorColor = Leaf,
                                selectedTextColor = Leaf, unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            TopBar(destination, cloudLibrary, showSearch, search, { showSearch = !showSearch }, { search = it }, {
                cloudLibrary = true
                destination = Destination.Files
            })
            AnimatedContent(
                targetState = destination to cloudLibrary,
                transitionSpec = {
                    val direction = if (targetState.first.ordinal > initialState.first.ordinal) 1 else -1
                    (fadeIn(animationSpec = tween(210, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(
                            animationSpec = tween(260, easing = FastOutSlowInEasing),
                            initialOffsetX = { direction * 22 }
                        ) + scaleIn(
                            initialScale = .992f,
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        )) togetherWith
                        (fadeOut(animationSpec = tween(130)) +
                            slideOutHorizontally(
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                targetOffsetX = { -direction * 12 }
                            ) + scaleOut(
                                targetScale = .996f,
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            ))
                },
                label = "library-destination",
                modifier = Modifier.weight(1f)
            ) { target ->
                when {
                    target.first == Destination.Home -> HomeScreen(onNavigate = ::navigateTo)
                    target.second -> WebDavLibraryScreen(target.first) { cloudLibrary = false }
                    else -> LocalLibraryScreen(target.first)
                }
            }
        }
    }
}

@Composable
private fun TopBar(destination: Destination, cloudMode: Boolean, searchOpen: Boolean, search: String, onSearch: () -> Unit, onValue: (String) -> Unit, onCloud: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("HAVEN  /  PERSONAL LIBRARY", color = Muted, fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            AnimatedContent(
                targetState = if (cloudMode) "pCloud" else destination.label,
                transitionSpec = {
                    (fadeIn(tween(150)) + slideInVertically(tween(190)) { it / 4 }) togetherWith
                        (fadeOut(tween(100)) + slideOutVertically(tween(150)) { -it / 4 })
                },
                label = "destination-title"
            ) { label ->
                Text(label, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
        AnimatedVisibility(
            visible = searchOpen,
            modifier = Modifier.weight(1.2f),
            enter = fadeIn(tween(160)) + expandHorizontally(
                expandFrom = Alignment.End,
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ),
            exit = fadeOut(tween(100)) + shrinkHorizontally(
                shrinkTowards = Alignment.End,
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(search, onValue, Modifier.weight(1f), placeholder = { Text("Search your library") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(8.dp))
            }
        }
        IconButton(onClick = onSearch) { Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = "Search", tint = Leaf) }
        IconButton(onClick = onCloud) { Icon(Icons.Rounded.CloudDone, contentDescription = "Open pCloud library", tint = Leaf) }
    }
}

@Composable
private fun HomeScreen(onNavigate: (Destination) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("A LITTLE TIME FOR YOURSELF", color = Muted, fontSize = 10.sp, letterSpacing = 1.3.sp)
        Spacer(Modifier.height(6.dp))
        Text("Good evening, Dave.", fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
        Spacer(Modifier.height(18.dp))
        FeaturedFilm()
        Spacer(Modifier.height(25.dp))
        SectionHeading("Continue watching", "Your next quiet evening starts here", "View all", { onNavigate(Destination.Movies) })
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 2.dp)) {
            items(watchItems) { item -> WatchCard(item) }
        }
        Spacer(Modifier.height(24.dp))
        SectionHeading("Your collections", "Little worlds, all your own", "Browse", { onNavigate(Destination.Files) })
        Spacer(Modifier.height(12.dp))
        collections.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { CollectionCard(it, Modifier.weight(1f), { onNavigate(it.destination) }) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(22.dp))
        SectionHeading("In your rotation", "A few favorites to set the mood", "Open music", { onNavigate(Destination.Music) })
        Spacer(Modifier.height(12.dp))
        AlbumRow()
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun FeaturedFilm() {
    Box(Modifier.fillMaxWidth().height(285.dp).clip(RoundedCornerShape(24.dp))) {
        RemoteArt("photo-1478720568477-152d9b164e26", Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(.08f), Ink.copy(.96f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Surface(color = Ink.copy(.64f), shape = CircleShape) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Leaf)); Spacer(Modifier.width(7.dp))
                    Text("PICK UP WHERE YOU LEFT OFF", fontSize = 9.sp, color = Leaf, letterSpacing = .7.sp)
                }
            }
            Spacer(Modifier.height(11.dp))
            Text("The Grand Budapest Hotel", fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp)
            Spacer(Modifier.height(4.dp))
            Text("A charming little escape is waiting for you.", fontSize = 11.sp, color = Color(0xFFD0D5CC))
            Spacer(Modifier.height(13.dp))
            FilledTonalButton(onClick = {}, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Leaf, contentColor = Ink), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Continue watching", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(3.dp)); Text(subtitle, fontSize = 10.sp, color = Muted) }
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(action, fontSize = 10.sp, color = Leaf) }
    }
}

@Composable
private fun WatchCard(item: MediaItem) {
    Row(Modifier.width(282.dp).clip(RoundedCornerShape(16.dp)).background(Surface).clickable {}.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(83.dp).height(61.dp).clip(RoundedCornerShape(10.dp))) {
            RemoteArt(item.image, Modifier.fillMaxSize())
            Icon(Icons.Rounded.PlayCircle, null, Modifier.align(Alignment.Center).size(25.dp), tint = Color.White)
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.White.copy(.35f)))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(item.progress).height(3.dp).background(Leaf))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp)); Text(item.subtitle, fontSize = 9.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.MoreVert, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CollectionCard(item: CollectionItem, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(119.dp).clip(RoundedCornerShape(17.dp)).clickable(onClick = onClick)) {
        RemoteArt(item.image, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(.88f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp)); Text(item.subtitle, fontSize = 9.sp, color = Color(0xFFD0D5CC))
        }
    }
}

private data class Album(val name: String, val artist: String, val image: String)
@Composable
private fun AlbumRow() {
    val albums = listOf(
        Album("Bon Iver", "Bon Iver", "photo-1519682337058-a94d519337bc"),
        Album("For Emma, Forever Ago", "Bon Iver", "photo-1470225620780-dba8ba36b745"),
        Album("Carrie & Lowell", "Sufjan Stevens", "photo-1511379938547-c1f69419868d"),
        Album("Blue", "Joni Mitchell", "photo-1493225457124-a3eb161ffa5f")
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        items(albums) { album ->
            Column(Modifier.width(112.dp).clickable {}) {
                RemoteArt(album.image, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(15.dp)))
                Spacer(Modifier.height(8.dp)); Text(album.name, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp)); Text(album.artist, fontSize = 9.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun LibraryScreen(destination: Destination) {
    val icon = destination.icon
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Surface(color = LeafDark, shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Leaf, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(14.dp))
                Column { Text("Your ${destination.label.lowercase()} collection", fontSize = 16.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Connect pCloud to browse your own library", fontSize = 10.sp, color = Muted) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("MADE FOR YOUR COLLECTION", color = Muted, fontSize = 9.sp, letterSpacing = 1.3.sp)
        Spacer(Modifier.height(9.dp)); Text("A space that grows with you.", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp)); Text("Your files, familiar folders, and future favorites will find a home here.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
        Spacer(Modifier.height(19.dp))
        OutlinedButton(onClick = {}, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Leaf)) {
            Icon(Icons.Rounded.Cloud, null); Spacer(Modifier.width(8.dp)); Text("Connect pCloud")
        }
        Spacer(Modifier.height(24.dp))
        collections.filter { it.destination == destination || destination == Destination.Files }.forEach { CollectionCard(it, Modifier.fillMaxWidth(), {}) ; Spacer(Modifier.height(11.dp)) }
    }
}

@Composable
private fun MiniPlayer(playing: Boolean, onPlay: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp).clip(RoundedCornerShape(16.dp)).background(SurfaceRaised).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RemoteArt("photo-1519682337058-a94d519337bc", Modifier.size(39.dp).clip(RoundedCornerShape(9.dp)))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) { Text("Holocene", fontSize = 10.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(3.dp)); Text("Bon Iver · Bon Iver", fontSize = 9.sp, color = Muted) }
        IconButton(onClick = {}, modifier = Modifier.size(35.dp)) { Icon(Icons.Rounded.SkipPrevious, null, tint = Color.LightGray) }
        IconButton(onClick = onPlay, modifier = Modifier.size(35.dp).clip(CircleShape).background(Leaf)) {
            AnimatedContent(
                targetState = playing,
                transitionSpec = {
                    (fadeIn(tween(110)) + scaleIn(initialScale = .78f, animationSpec = tween(180, easing = FastOutSlowInEasing))) togetherWith
                        (fadeOut(tween(90)) + scaleOut(targetScale = .78f, animationSpec = tween(150, easing = FastOutSlowInEasing)))
                },
                label = "play-pause-icon"
            ) { isPlaying ->
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Ink)
            }
        }
        IconButton(onClick = {}, modifier = Modifier.size(35.dp)) { Icon(Icons.Rounded.SkipNext, null, tint = Color.LightGray) }
    }
}

@Composable
private fun RemoteArt(id: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = "https://images.unsplash.com/$id?auto=format&fit=crop&w=900&q=80",
        contentDescription = null,
        modifier = modifier.background(Brush.linearGradient(listOf(Color(0xFF304033), Color(0xFF202520))))
    )
}
