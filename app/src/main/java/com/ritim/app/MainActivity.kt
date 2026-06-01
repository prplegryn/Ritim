package com.ritim.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RitimApp()
        }
    }
}

@Composable
fun RitimApp() {
    val pageBackground = Color(0xFFF4F7F8)
    val backdrop = rememberLayerBackdrop {
        drawRect(pageBackground)
        drawContent()
    }
    val musicSongs = rememberMusicFolderSongs()
    val displaySongs = remember(musicSongs) {
        musicSongs.ifEmpty { fallbackSongs() }
    }
    var selectedSongId by rememberSaveable { mutableStateOf<Long?>(null) }
    var playing by rememberSaveable { mutableStateOf(false) }
    val currentSong = remember(displaySongs, selectedSongId) {
        displaySongs.firstOrNull { it.id == selectedSongId } ?: displaySongs.first()
    }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var activePageIndex by rememberSaveable { mutableStateOf(0) }
    var playerCardVisible by rememberSaveable { mutableStateOf(false) }
    var playerBackdropActive by rememberSaveable { mutableStateOf(false) }
    val pageScale by animateFloatAsState(
        targetValue = if (playerBackdropActive) 0.965f else 1f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
    )
    val blurRadius by animateFloatAsState(
        targetValue = if (playerBackdropActive) 14f else 0f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        RitimPageBackground(
            pageIndex = activePageIndex,
            songs = displaySongs,
            onSongSelected = { selectedSongId = it.id },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pageScale
                    scaleY = pageScale
                }
                .blur(blurRadius.dp)
                .layerBackdrop(backdrop)
        )

        RitimBottomControls(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = {
                selectedTabIndex = it
                activePageIndex = it
            },
            onSearchSelected = { activePageIndex = 3 },
            onMiniPlayerSelected = {
                playerBackdropActive = true
                playerCardVisible = true
            },
            song = currentSong,
            playing = playing,
            onPlayingChange = { playing = it },
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .blur(blurRadius.dp)
        )

        if (playerCardVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = if (playerBackdropActive) 0.30f else 0.08f))
            )
            PlayerCardPage(
                song = currentSong,
                playing = playing,
                onPlayingChange = { playing = it },
                onDismissStart = { playerBackdropActive = false },
                onDismiss = { playerCardVisible = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun RitimPageBackground(
    pageIndex: Int,
    songs: List<SongSample>,
    onSongSelected: (SongSample) -> Unit,
    modifier: Modifier = Modifier
) {
    val targetIndex = pageIndex.coerceIn(0, 3)
    var currentIndex by remember { mutableIntStateOf(targetIndex) }
    var previousIndex by remember { mutableIntStateOf(targetIndex) }
    var direction by remember { mutableIntStateOf(0) }
    var searchTransition by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(targetIndex) {
        if (targetIndex != currentIndex) {
            val isSearchTransition = targetIndex == 3 || currentIndex == 3
            previousIndex = currentIndex
            direction = if (targetIndex > currentIndex) 1 else -1
            searchTransition = isSearchTransition
            currentIndex = targetIndex
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (isSearchTransition) 360 else 380,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    Box(modifier) {
        if (progress.value < 1f) {
            PageLayer(
                pageIndex = previousIndex,
                songs = songs,
                onSongSelected = onSongSelected,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (searchTransition) {
                            alpha = 1f - progress.value
                            translationY = if (previousIndex == 3) {
                                size.height * 0.05f * progress.value
                            } else {
                                -size.height * 0.018f * progress.value
                            }
                            scaleX = 1f - 0.01f * progress.value
                            scaleY = 1f - 0.01f * progress.value
                        } else {
                            alpha = 1f - progress.value
                            translationX = -direction * size.width * 0.11f * progress.value
                            scaleX = 1f + 0.006f * progress.value
                            scaleY = 1f + 0.006f * progress.value
                        }
                    }
            )
        }
        PageLayer(
            pageIndex = currentIndex,
            songs = songs,
            onSongSelected = onSongSelected,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val remaining = 1f - progress.value
                    if (searchTransition) {
                        alpha = 0.15f + 0.85f * progress.value
                        translationY = if (currentIndex == 3) {
                            size.height * 0.08f * remaining
                        } else {
                            -size.height * 0.035f * remaining
                        }
                        scaleX = 0.985f + 0.015f * progress.value
                        scaleY = 0.985f + 0.015f * progress.value
                    } else {
                        alpha = 0.18f + 0.82f * progress.value
                        translationX = direction * size.width * 0.13f * remaining
                        scaleX = 0.992f + 0.008f * progress.value
                        scaleY = 0.992f + 0.008f * progress.value
                    }
                }
        )
    }
}

@Composable
private fun PageLayer(
    pageIndex: Int,
    songs: List<SongSample>,
    onSongSelected: (SongSample) -> Unit,
    modifier: Modifier = Modifier
) {
    when (pageIndex) {
        0 -> HomePage(
            songs = songs,
            onSongSelected = onSongSelected,
            modifier = modifier
        )
        3 -> Box(modifier.background(Color.White))
        else -> {
            PageBackgroundLayer(
                visual = pageVisual(pageIndex),
                modifier = modifier
            )
        }
    }
}

@Composable
private fun HomePage(
    songs: List<SongSample>,
    onSongSelected: (SongSample) -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = remember(songs) { homeSectionsFromSongs(songs) }
    val scrollState = rememberScrollState()

    Column(
        modifier
            .background(Color.White)
            .fillMaxSize()
            .verticalScroll(scrollState)
            .statusBarsPadding()
            .padding(top = 22.dp, bottom = 166.dp)
    ) {
        BasicText(
            "主页",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            style = TextStyle(
                color = Color(0xFF111315),
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold
            )
        )

        Column(
            Modifier.padding(top = 26.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp)
        ) {
            sections.forEach { section ->
                HomeSection(
                    section = section,
                    onSongSelected = onSongSelected
                )
            }
        }
    }
}

@Composable
private fun HomeSection(
    section: HomeSectionData,
    onSongSelected: (SongSample) -> Unit
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                section.title,
                style = TextStyle(
                    color = Color(0xFF151719),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            ThickChevron(
                modifier = Modifier
                    .padding(start = 9.dp)
                    .size(15.dp),
                color = Color(0xFF151719)
            )
        }

        Row(
            Modifier
                .padding(top = 14.dp)
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            section.items.forEach { item ->
                SongTile(
                    item = item,
                    tall = section.tallTiles,
                    onClick = { onSongSelected(item) }
                )
            }
        }
    }
}

@Composable
private fun SongTile(
    item: SongSample,
    tall: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 110)
    )
    val tileWidth = if (tall) 124.dp else 112.dp
    Column(
        Modifier
            .width(tileWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        CoverArt(
            colors = item.colors,
            seed = item.seed,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (tall) 2f / 3f else 1f)
        )
        BasicText(
            item.title,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = Color(0xFF171A1D),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
        BasicText(
            item.artist,
            modifier = Modifier.padding(top = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = Color(0xFF737B82),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )
        )
    }
}

@Composable
private fun CoverArt(
    colors: List<Color>,
    seed: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(colors))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = size.minDimension * (0.22f + (seed % 3) * 0.04f),
                center = Offset(size.width * 0.28f, size.height * 0.28f)
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.08f),
                radius = size.minDimension * 0.36f,
                center = Offset(size.width * 0.78f, size.height * 0.76f)
            )
            repeat(4) { index ->
                val y = size.height * (0.32f + index * 0.12f)
                drawLine(
                    color = Color.White.copy(alpha = 0.22f),
                    start = Offset(size.width * 0.18f, y),
                    end = Offset(size.width * 0.84f, y + ((seed + index) % 3 - 1) * 8.dp.toPx()),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun ThickChevron(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF111315)
) {
    Canvas(modifier) {
        val strokeWidth = 3.1.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.34f, size.height * 0.22f),
            end = Offset(size.width * 0.68f, size.height * 0.50f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.68f, size.height * 0.50f),
            end = Offset(size.width * 0.34f, size.height * 0.78f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private data class HomeSectionData(
    val title: String,
    val tallTiles: Boolean,
    val items: List<SongSample>
)

private data class SongSample(
    val id: Long,
    val title: String,
    val artist: String,
    val colors: List<Color>,
    val seed: Int,
    val durationMs: Long = 0L,
    val dateAddedSeconds: Long = 0L,
    val contentUri: Uri? = null
)

@Composable
private fun rememberMusicFolderSongs(): List<SongSample> {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<SongSample>>(emptyList()) }
    var permissionGranted by remember { mutableStateOf(hasAudioReadPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(context) {
        val granted = hasAudioReadPermission(context)
        permissionGranted = granted
        if (!granted) {
            permissionLauncher.launch(audioReadPermission())
        }
    }

    LaunchedEffect(context, permissionGranted) {
        if (permissionGranted) {
            songs = loadMusicFolderSongs(context)
        }
    }

    return songs
}

private fun homeSectionsFromSongs(songs: List<SongSample>): List<HomeSectionData> {
    val source = songs.ifEmpty { fallbackSongs() }
    val recentAdded = source
        .sortedWith(
            compareByDescending<SongSample> { it.dateAddedSeconds }
                .thenBy { it.title.lowercase() }
        )
        .take(8)
    val recommended = source
        .sortedBy { (it.seed * 31) xor it.title.length }
        .take(8)
        .ifEmpty { recentAdded }

    return listOf(
        HomeSectionData(
            title = "最近播放",
            tallTiles = true,
            items = recentAdded
        ),
        HomeSectionData(
            title = "最近热门",
            tallTiles = false,
            items = recentAdded
        ),
        HomeSectionData(
            title = "最近添加",
            tallTiles = false,
            items = recentAdded
        ),
        HomeSectionData(
            title = "为你推荐",
            tallTiles = false,
            items = recommended
        )
    )
}

private suspend fun loadMusicFolderSongs(context: Context): List<SongSample> =
    withContext(Dispatchers.IO) {
        if (!hasAudioReadPermission(context)) return@withContext emptyList()

        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            pathColumn
        )
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND $pathColumn LIKE ?"
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND $pathColumn LIKE ?"
        }
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("Music/%")
        } else {
            arrayOf("%/Music/%")
        }
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC, ${MediaStore.Audio.Media.TITLE} ASC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val displayName = cursorString(cursor, displayNameIndex)
                        val title = cleanSongTitle(
                            cursorString(cursor, titleIndex),
                            displayName
                        )
                        val artist = cleanArtistName(cursorString(cursor, artistIndex))
                        val seed = stableSongSeed("$id|$title|$artist")
                        add(
                            SongSample(
                                id = id,
                                title = title,
                                artist = artist,
                                colors = paletteForSeed(seed),
                                seed = seed,
                                durationMs = cursorLong(cursor, durationIndex),
                                dateAddedSeconds = cursorLong(cursor, dateAddedIndex),
                                contentUri = Uri.withAppendedPath(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    id.toString()
                                )
                            )
                        )
                    }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

private fun hasAudioReadPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        ContextCompat.checkSelfPermission(context, audioReadPermission()) == PackageManager.PERMISSION_GRANTED

private fun audioReadPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun cursorString(cursor: android.database.Cursor, index: Int): String? =
    if (cursor.isNull(index)) null else cursor.getString(index)

private fun cursorLong(cursor: android.database.Cursor, index: Int): Long =
    if (cursor.isNull(index)) 0L else cursor.getLong(index)

private fun cleanSongTitle(title: String?, displayName: String?): String {
    val normalized = title?.takeIf { it.isMeaningfulMediaText() }
    return normalized ?: displayName
        ?.substringBeforeLast('.', displayName)
        ?.takeIf { it.isNotBlank() }
        ?: "未知歌曲"
}

private fun cleanArtistName(artist: String?): String =
    artist?.takeIf { it.isMeaningfulMediaText() } ?: "未知艺术家"

private fun String.isMeaningfulMediaText(): Boolean {
    val trimmed = trim()
    return trimmed.isNotEmpty() && trimmed != "<unknown>"
}

private fun fallbackSongs(): List<SongSample> {
    val base = listOf(
        "Soft Loop" to "Mira Vale",
        "Blue Static" to "Nolan East",
        "Paper Moon" to "Yun Seo",
        "Small Hours" to "Lena Park",
        "Glass Road" to "Theo Lane",
        "Warm Signal" to "Aria Sun",
        "Low Tide" to "Miles Wren",
        "Neon Rain" to "Echo Field"
    )
    return base.mapIndexed { index, pair ->
        val seed = stableSongSeed("${pair.first}|${pair.second}|$index")
        SongSample(
            id = -(index + 1L),
            title = pair.first,
            artist = pair.second,
            colors = paletteForSeed(seed),
            seed = seed,
            dateAddedSeconds = 1_700_000_000L - index
        )
    }
}

private fun paletteForSeed(seed: Int): List<Color> =
    songPalettes[(seed and Int.MAX_VALUE) % songPalettes.size]

private fun stableSongSeed(value: String): Int {
    var hash = 17
    value.forEach { char ->
        hash = hash * 31 + char.code
    }
    return hash
}

private val songPalettes = listOf(
    listOf(Color(0xFF7EC8E3), Color(0xFFE8F5E9)),
    listOf(Color(0xFFFFB4A2), Color(0xFFFFD6A5)),
    listOf(Color(0xFFB8C0FF), Color(0xFFEFD3D7)),
    listOf(Color(0xFF95D5B2), Color(0xFFD8F3DC)),
    listOf(Color(0xFFFFCAD4), Color(0xFFF4ACB7)),
    listOf(Color(0xFFA9DEF9), Color(0xFFE4C1F9)),
    listOf(Color(0xFFE9EDC9), Color(0xFFD4A373)),
    listOf(Color(0xFFCDB4DB), Color(0xFFFFC8DD))
)

@Composable
private fun PageBackgroundLayer(
    visual: PageVisual,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.background(Brush.verticalGradient(visual.gradient))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val short = size.minDimension
            val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(
                color = visual.accent.copy(alpha = 0.22f),
                radius = short * 0.42f,
                center = Offset(size.width * 0.18f, size.height * 0.18f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.30f),
                radius = short * 0.34f,
                center = Offset(size.width * 0.88f, size.height * 0.34f)
            )
            drawCircle(
                color = visual.accent.copy(alpha = 0.16f),
                radius = short * 0.45f,
                center = Offset(size.width * 0.78f, size.height * 0.82f)
            )
            repeat(8) { index ->
                val y = size.height * (0.16f + index * 0.095f)
                val drift = if (index % 2 == 0) short * 0.05f else -short * 0.04f
                drawLine(
                    color = visual.line.copy(alpha = 0.08f),
                    start = Offset(size.width * 0.08f, y),
                    end = Offset(size.width * 0.92f, y + drift),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }
            repeat(7) { index ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.42f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(
                        x = size.width * (0.16f + index * 0.11f),
                        y = size.height * (0.58f + (index % 3) * 0.055f)
                    )
                )
            }
        }
    }
}

private fun pageVisual(index: Int): PageVisual =
    when (index) {
        1 -> PageVisual(
            gradient = listOf(Color(0xFFF0F6F2), Color(0xFFDDECE5), Color(0xFFEAF0F6)),
            line = Color(0xFF2E7D5B),
            accent = Color(0xFF88C7A2)
        )
        2 -> PageVisual(
            gradient = listOf(Color(0xFFF8F2EF), Color(0xFFEADFD8), Color(0xFFF2EAF0)),
            line = Color(0xFF936550),
            accent = Color(0xFFD4A28C)
        )
        else -> PageVisual(
            gradient = listOf(Color(0xFFF4F7F8), Color(0xFFE6EEF4), Color(0xFFF5EFF3)),
            line = Color(0xFF0088FF),
            accent = Color(0xFF8DCBFF)
        )
    }

private data class PageVisual(
    val gradient: List<Color>,
    val line: Color,
    val accent: Color
)

@Composable
private fun RitimBottomControls(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onSearchSelected: () -> Unit,
    onMiniPlayerSelected: () -> Unit,
    song: SongSample,
    playing: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFF0088FF)
    val containerColor = Color(0xFFFAFAFA).copy(0.4f)
    val navHeight = 62.dp
    val playerHeight = 56.dp

    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        MiniPlayerBar(
            backdrop = backdrop,
            containerColor = containerColor,
            height = playerHeight,
            song = song,
            playing = playing,
            onPlayingChange = onPlayingChange,
            onClick = onMiniPlayerSelected,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier
                .fillMaxWidth()
                .height(navHeight),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidBottomTabs(
                selectedTabIndex = { selectedTabIndex },
                onTabSelected = onTabSelected,
                backdrop = backdrop,
                tabsCount = 3,
                accentColor = accentColor,
                blurRadius = 8.dp,
                refractionHeight = 24.dp,
                refractionAmount = 24.dp,
                containerColor = containerColor,
                containerHeight = navHeight,
                focusHeight = 54.dp,
                modifier = Modifier.weight(1f)
            ) {
                listOf("主页", "库", "我的").forEachIndexed { index, label ->
                    LiquidBottomTab(
                        onClick = { onTabSelected(index) },
                        selected = selectedTabIndex == index,
                        label = label
                    )
                }
            }

            LiquidButton(
                onClick = onSearchSelected,
                backdrop = backdrop,
                modifier = Modifier
                    .size(navHeight)
                    .semantics { contentDescription = "搜索" },
                surfaceColor = containerColor,
                blurRadius = 8.dp,
                refractionHeight = 18.dp,
                refractionAmount = 22.dp,
                height = navHeight,
                horizontalPadding = 0.dp,
                pressScale = 0.96f
            ) {
                SearchGlyph(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF1E1E1E)
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    backdrop: Backdrop,
    containerColor: Color,
    height: Dp,
    song: SongSample,
    playing: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 120)
    )

    LiquidStaticBar(
        backdrop = backdrop,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = "迷你播放器" }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        surfaceColor = containerColor,
        height = height,
        startPadding = 32.dp,
        endPadding = 12.dp,
        blurRadius = 8.dp,
        refractionHeight = 18.dp,
        refractionAmount = 22.dp
    ) {
        MiniCoverArt(
            song = song,
            modifier = Modifier
                .size(42.dp)
                .shadow(
                    elevation = 1.dp,
                    shape = RoundedCornerShape(9.dp),
                    clip = false
                )
        )

        Column(
            Modifier
                .height(36.dp)
                .weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            BasicText(
                song.title,
                modifier = Modifier.offset(y = 1.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color(0xFF171A1D),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            BasicText(
                song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color(0xFF687076),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        }

        Row(
            Modifier.offset(x = (-5).dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniIconButton(
                contentDescription = if (playing) "暂停" else "播放",
                onClick = { onPlayingChange(!playing) }
            ) {
                PlayPauseGlyph(
                    playing = playing,
                    modifier = Modifier.size(26.dp),
                    color = Color(0xFF1E1E1E)
                )
            }

            MiniIconButton(
                contentDescription = "下一曲",
                onClick = {}
            ) {
                NextGlyph(
                    modifier = Modifier.size(27.dp),
                    color = Color(0xFF1E1E1E)
                )
            }
        }
    }
}

@Composable
private fun MiniIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.08f else 1f,
        animationSpec = tween(durationMillis = 120)
    )

    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun PlayerCardPage(
    song: SongSample,
    playing: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onDismissStart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val dragOffset = remember { Animatable(0f) }
    var lastDownwardDrag by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
        )
    }

    BoxWithConstraints(modifier) {
        val screenHeight = constraints.maxHeight.toFloat()
        val offsetY = (1f - progress.value) * screenHeight + dragOffset.value
        val compactPlayerLayout = maxHeight < 700.dp
        val coverSize = minOf(
            maxWidth - 56.dp,
            if (compactPlayerLayout) 254.dp else 304.dp
        )

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offsetY
                    alpha = 0.90f + 0.10f * progress.value
                }
                .background(Color.Black)
        ) {
            CoverColorField(
                song = song,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.34f
                        scaleY = 1.34f
                    }
                    .blur(82.dp)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp)
                    .padding(top = 14.dp, bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.42f))
                )

                Spacer(Modifier.height(if (compactPlayerLayout) 28.dp else 42.dp))

                SolidCoverArt(
                    song = song,
                    modifier = Modifier
                        .size(coverSize)
                        .clip(RoundedCornerShape(16.dp))
                )

                Spacer(Modifier.height(if (compactPlayerLayout) 28.dp else 38.dp))

                PlayerLine(
                    progress = 0.28f,
                    modifier = Modifier.fillMaxWidth(),
                    height = 4.dp
                )

                Spacer(Modifier.height(if (compactPlayerLayout) 24.dp else 32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerIconButton(
                        contentDescription = "上一曲",
                        size = 52.dp,
                        onClick = {}
                    ) {
                        PreviousGlyph(
                            modifier = Modifier.size(30.dp),
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.width(28.dp))
                    PlayerIconButton(
                        contentDescription = if (playing) "暂停" else "播放",
                        size = 70.dp,
                        onClick = { onPlayingChange(!playing) }
                    ) {
                        PlayPauseGlyph(
                            playing = playing,
                            modifier = Modifier.size(40.dp),
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.width(28.dp))
                    PlayerIconButton(
                        contentDescription = "下一曲",
                        size = 52.dp,
                        onClick = {}
                    ) {
                        NextGlyph(
                            modifier = Modifier.size(30.dp),
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(if (compactPlayerLayout) 24.dp else 30.dp))

                PlayerLine(
                    progress = 0.68f,
                    modifier = Modifier.fillMaxWidth(),
                    height = 3.dp
                )

                Spacer(Modifier.height(if (compactPlayerLayout) 22.dp else 28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerIconButton(
                        contentDescription = "歌词",
                        size = 48.dp,
                        onClick = {},
                        pressScale = 0.97f
                    ) {
                        LyricsGlyph(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    }
                    PlayerIconButton(
                        contentDescription = "播放列表",
                        size = 48.dp,
                        onClick = {},
                        pressScale = 0.97f
                    ) {
                        PlaylistGlyph(
                            modifier = Modifier.size(25.dp),
                            color = Color.White
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.52f)
                    .align(Alignment.TopCenter)
                    .pointerInput(screenHeight) {
                        detectDragGestures(
                            onDragStart = {
                                lastDownwardDrag = 0f
                            },
                            onDragEnd = {
                                val shouldDismiss =
                                    dragOffset.value > screenHeight * 0.12f || lastDownwardDrag > 18f
                                if (shouldDismiss) {
                                    scope.launch {
                                        onDismissStart()
                                        progress.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = 170,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                        dragOffset.snapTo(0f)
                                        onDismiss()
                                    }
                                } else {
                                    scope.launch {
                                        dragOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = 140,
                                                easing = FastOutSlowInEasing
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    dragOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            durationMillis = 140,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                lastDownwardDrag = dragAmount.y
                                scope.launch {
                                    dragOffset.snapTo(max(0f, dragOffset.value + dragAmount.y))
                                }
                            }
                        )
                    }
                )
            }
        }
    }

@Composable
private fun MiniCoverArt(
    song: SongSample,
    modifier: Modifier = Modifier
) {
    SolidCoverArt(
        song = song,
        modifier = modifier.clip(RoundedCornerShape(9.dp))
    )
}

@Composable
private fun SolidCoverArt(
    song: SongSample,
    modifier: Modifier = Modifier
) {
    Box(modifier.background(song.colors.first()))
}

@Composable
private fun CoverColorField(
    song: SongSample,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.background(
            Brush.linearGradient(
                colors = song.colors,
                start = Offset.Zero,
                end = Offset(1200f, 1200f)
            )
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val short = size.minDimension
            drawCircle(
                color = Color.White.copy(alpha = 0.26f),
                radius = short * (0.28f + (song.seed and 3) * 0.03f),
                center = Offset(size.width * 0.24f, size.height * 0.28f)
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.13f),
                radius = short * 0.48f,
                center = Offset(size.width * 0.82f, size.height * 0.76f)
            )
            drawCircle(
                color = song.colors.last().copy(alpha = 0.46f),
                radius = short * 0.34f,
                center = Offset(size.width * 0.70f, size.height * 0.20f)
            )
        }
    }
}

@Composable
private fun PlayerLine(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp
) {
    Canvas(
        modifier
            .height(height)
            .fillMaxWidth()
    ) {
        val y = size.height / 2f
        val strokeWidth = size.height
        drawLine(
            color = Color.White.copy(alpha = 0.24f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(0f, y),
            end = Offset(size.width * progress.coerceIn(0f, 1f), y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PlayerIconButton(
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
    pressScale: Float = 0.94f,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = tween(durationMillis = 110)
    )

    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun PlayPauseGlyph(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF111315)
) {
    Canvas(modifier) {
        if (playing) {
            val strokeWidth = size.minDimension * 0.16f
            drawLine(
                color = color,
                start = Offset(size.width * 0.36f, size.height * 0.24f),
                end = Offset(size.width * 0.36f, size.height * 0.76f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.64f, size.height * 0.24f),
                end = Offset(size.width * 0.64f, size.height * 0.76f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.34f, size.height * 0.22f)
                lineTo(size.width * 0.34f, size.height * 0.78f)
                lineTo(size.width * 0.78f, size.height * 0.50f)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
private fun PreviousGlyph(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF111315)
) {
    Canvas(modifier) {
        val first = Path().apply {
            moveTo(size.width * 0.84f, size.height * 0.24f)
            lineTo(size.width * 0.84f, size.height * 0.76f)
            lineTo(size.width * 0.52f, size.height * 0.50f)
            close()
        }
        val second = Path().apply {
            moveTo(size.width * 0.52f, size.height * 0.24f)
            lineTo(size.width * 0.52f, size.height * 0.76f)
            lineTo(size.width * 0.20f, size.height * 0.50f)
            close()
        }
        drawPath(first, color)
        drawPath(second, color)
    }
}

@Composable
private fun NextGlyph(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF111315)
) {
    Canvas(modifier) {
        val first = Path().apply {
            moveTo(size.width * 0.16f, size.height * 0.24f)
            lineTo(size.width * 0.16f, size.height * 0.76f)
            lineTo(size.width * 0.48f, size.height * 0.50f)
            close()
        }
        val second = Path().apply {
            moveTo(size.width * 0.48f, size.height * 0.24f)
            lineTo(size.width * 0.48f, size.height * 0.76f)
            lineTo(size.width * 0.80f, size.height * 0.50f)
            close()
        }
        drawPath(first, color)
        drawPath(second, color)
    }
}

@Composable
private fun LyricsGlyph(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier) {
        val strokeWidth = size.minDimension * 0.09f
        repeat(4) { index ->
            val y = size.height * (0.22f + index * 0.18f)
            val end = if (index == 3) size.width * 0.62f else size.width * 0.84f
            drawLine(
                color = color,
                start = Offset(size.width * 0.16f, y),
                end = Offset(end, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PlaylistGlyph(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier) {
        val strokeWidth = size.minDimension * 0.085f
        repeat(3) { index ->
            val y = size.height * (0.24f + index * 0.22f)
            drawCircle(
                color = color,
                radius = strokeWidth * 0.62f,
                center = Offset(size.width * 0.17f, y)
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.32f, y),
                end = Offset(size.width * 0.84f, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun SearchGlyph(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF111315)
) {
    Canvas(modifier) {
        val strokeWidth = 2.25.dp.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension * 0.31f,
            center = Offset(size.width * 0.43f, size.height * 0.42f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.64f, size.height * 0.64f),
            end = Offset(size.width * 0.84f, size.height * 0.84f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
