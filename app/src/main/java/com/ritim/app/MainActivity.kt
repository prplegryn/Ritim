package com.ritim.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RitimApp()
        }
    }
}

private data class SeekRequest(
    val id: Int,
    val progress: Float
)

private class PlaybackRuntimeTracker {
    var progress: Float = 0f
    var positionMs: Long = 0L
}

private const val previousRestartThresholdMs = 3_000L
private const val metadataKeySampleRate = 38
private const val metadataKeyBitsPerSample = 39
private val rajdhaniSemiBoldFontFamily = FontFamily(Font(R.font.rajdhani_semibold))

@Composable
fun RitimApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    val playbackRuntimeTracker = remember { PlaybackRuntimeTracker() }
    var seekRequestId by remember { mutableIntStateOf(0) }
    var seekRequest by remember { mutableStateOf<SeekRequest?>(null) }
    var playerVolume by remember(context) {
        mutableFloatStateOf(systemMusicVolumeProgress(context))
    }
    var favoriteSongIds by remember(context) {
        mutableStateOf(loadMusicLibraryIndex(context).favoriteSongIds)
    }
    val currentSong = remember(displaySongs, selectedSongId) {
        displaySongs.firstOrNull { it.id == selectedSongId } ?: displaySongs.first()
    }
    val toggleFavorite: (SongSample) -> Unit = { song ->
        val nextFavorites = favoriteSongIds.toMutableSet().apply {
            if (!add(song.id)) {
                remove(song.id)
            }
        }.toSet()
        favoriteSongIds = nextFavorites
        scope.launch {
            saveMusicLibraryIndex(
                context = context,
                index = MusicLibraryIndex(favoriteSongIds = nextFavorites)
            )
        }
    }
    val requestSeek: (Float) -> Unit = { progress ->
        val coercedProgress = progress.coerceIn(0f, 1f)
        seekRequestId += 1
        seekRequest = SeekRequest(seekRequestId, coercedProgress)
        playbackProgress = coercedProgress
        playbackRuntimeTracker.progress = coercedProgress
        playbackRuntimeTracker.positionMs = (currentSong.durationMs * coercedProgress).toLong()
    }
    val selectRelativeSong: (Int) -> Unit = { offset ->
        val currentIndex = displaySongs.indexOfFirst { it.id == currentSong.id }.let { index ->
            if (index >= 0) index else 0
        }
        val nextIndex = (currentIndex + offset + displaySongs.size) % displaySongs.size
        selectedSongId = displaySongs[nextIndex].id
        seekRequest = null
        playbackProgress = 0f
        playbackRuntimeTracker.progress = 0f
        playbackRuntimeTracker.positionMs = 0L
    }
    val selectSongForPlayback: (SongSample) -> Unit = { song ->
        if (song.id == currentSong.id) {
            requestSeek(0f)
        } else {
            selectedSongId = song.id
            seekRequest = null
            playbackProgress = 0f
            playbackRuntimeTracker.progress = 0f
            playbackRuntimeTracker.positionMs = 0L
        }
        playing = true
    }
    val onPreviousSelected: () -> Unit = {
        if (playbackRuntimeTracker.positionMs >= previousRestartThresholdMs) {
            requestSeek(0f)
        } else {
            selectRelativeSong(-1)
        }
    }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var activePageIndex by rememberSaveable { mutableStateOf(0) }
    var playerCardVisible by rememberSaveable { mutableStateOf(false) }
    var playerLyricsVisible by rememberSaveable { mutableStateOf(false) }
    var playerDismissRequestId by remember { mutableIntStateOf(0) }
    var playerBackdropProgress by remember { mutableFloatStateOf(0f) }
    var rememberedTranslationSongIds by remember {
        mutableStateOf(emptySet<Long>())
    }
    var aiLyricsSettings by remember(context) {
        mutableStateOf(loadAiLyricsTranslationSettings(context))
    }
    val pageScale = 1f - 0.035f * playerBackdropProgress
    val blurRadius = 14f * playerBackdropProgress
    val overlayAlpha = 0.30f * playerBackdropProgress
    AudioPlaybackEffect(
        song = currentSong,
        playing = playing,
        seekRequest = seekRequest,
        onPlayingChange = { playing = it },
        onProgressChange = {
            playbackRuntimeTracker.progress = it
            if (playerCardVisible) {
                playbackProgress = it
            }
        },
        onPositionChange = { playbackRuntimeTracker.positionMs = it }
    )

    BackHandler(enabled = true) {
        when {
            playerCardVisible -> {
                playerDismissRequestId += 1
            }
            activePageIndex == 5 -> {
                activePageIndex = 4
            }
            activePageIndex == 4 -> {
                activePageIndex = 2
            }
            activePageIndex == 3 -> {
                activePageIndex = selectedTabIndex
            }
            activePageIndex != 0 || selectedTabIndex != 0 -> {
                selectedTabIndex = 0
                activePageIndex = 0
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        RitimPageBackground(
            pageIndex = activePageIndex,
            songs = displaySongs,
            onSongSelected = selectSongForPlayback,
            aiLyricsSettings = aiLyricsSettings,
            onSettingsSelected = { activePageIndex = 4 },
            onAiLyricsSettingsSelected = { activePageIndex = 5 },
            onAiLyricsSettingsSave = { settings ->
                aiLyricsSettings = settings
                scope.launch {
                    saveAiLyricsTranslationSettings(context, settings)
                }
            },
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
                playbackProgress = playbackRuntimeTracker.progress
                playerVolume = systemMusicVolumeProgress(context)
                playerBackdropProgress = 0f
                playerCardVisible = true
            },
            song = currentSong,
            playing = playing,
            onPlayingChange = { playing = it },
            onNext = { selectRelativeSong(1) },
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .blur(blurRadius.dp)
        )

        if (playerCardVisible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = overlayAlpha))
            )
            PlayerCardPage(
                song = currentSong,
                playing = playing,
                playbackProgress = playbackProgress,
                volume = playerVolume,
                favorite = favoriteSongIds.contains(currentSong.id),
                lyricsVisible = playerLyricsVisible,
                aiLyricsSettings = aiLyricsSettings,
                rememberedTranslationSongIds = rememberedTranslationSongIds,
                onRememberedTranslationSongIdsChange = { rememberedTranslationSongIds = it },
                dismissRequestId = playerDismissRequestId,
                onPlayingChange = { playing = it },
                onSeek = requestSeek,
                onVolumeChange = { playerVolume = setSystemMusicVolumeProgress(context, it) },
                onFavoriteToggle = { toggleFavorite(currentSong) },
                onLyricsVisibleChange = { playerLyricsVisible = it },
                onPrevious = onPreviousSelected,
                onNext = { selectRelativeSong(1) },
                onBackdropProgressChange = { playerBackdropProgress = it },
                onDismiss = {
                    playerBackdropProgress = 0f
                    playerCardVisible = false
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AudioPlaybackEffect(
    song: SongSample,
    playing: Boolean,
    seekRequest: SeekRequest?,
    onPlayingChange: (Boolean) -> Unit,
    onProgressChange: (Float) -> Unit,
    onPositionChange: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerHolder = remember { mutableStateOf<MediaPlayer?>(null) }
    var preparedSongId by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            playerHolder.value?.release()
            playerHolder.value = null
        }
    }

    LaunchedEffect(context, song.id, playing) {
        if (preparedSongId != song.id) {
            playerHolder.value?.release()
            playerHolder.value = null
            preparedSongId = song.id
            onProgressChange(0f)
            onPositionChange(0L)
        }

        if (!playing) {
            playerHolder.value?.let { player ->
                runCatching {
                    if (player.isPlaying) player.pause()
                }
            }
            return@LaunchedEffect
        }

        val uri = song.contentUri
        if (uri == null) {
            onPlayingChange(false)
            onProgressChange(0f)
            onPositionChange(0L)
            return@LaunchedEffect
        }

        val existingPlayer = playerHolder.value
        if (existingPlayer != null) {
            runCatching {
                existingPlayer.start()
            }.onFailure {
                existingPlayer.release()
                playerHolder.value = null
                preparedSongId = null
                onPlayingChange(false)
                onProgressChange(0f)
                onPositionChange(0L)
            }
            return@LaunchedEffect
        }

        val player = createMediaPlayer(context, uri)
        if (player == null) {
            playerHolder.value = null
            preparedSongId = null
            onPlayingChange(false)
            onProgressChange(0f)
            onPositionChange(0L)
            return@LaunchedEffect
        }

        playerHolder.value = player
        preparedSongId = song.id
        seekRequest?.let { request ->
            player.seekToProgress(request.progress)?.let { targetPosition ->
                onProgressChange(request.progress.coerceIn(0f, 1f))
                onPositionChange(targetPosition.toLong())
            }
        }
        player.setOnCompletionListener { completedPlayer ->
            scope.launch {
                runCatching { completedPlayer.seekTo(0) }
                onProgressChange(0f)
                onPositionChange(0L)
                onPlayingChange(false)
            }
        }
        player.setOnErrorListener { failedPlayer, _, _ ->
            scope.launch {
                failedPlayer.release()
                if (playerHolder.value === failedPlayer) {
                    playerHolder.value = null
                }
                preparedSongId = null
                onProgressChange(0f)
                onPositionChange(0L)
                onPlayingChange(false)
            }
            true
        }
        runCatching {
            player.start()
        }.onFailure {
            player.release()
            playerHolder.value = null
            preparedSongId = null
            onPlayingChange(false)
            onProgressChange(0f)
            onPositionChange(0L)
        }
    }

    LaunchedEffect(song.id, seekRequest?.id) {
        val request = seekRequest ?: return@LaunchedEffect
        val player = playerHolder.value ?: return@LaunchedEffect
        player.seekToProgress(request.progress)?.let { targetPosition ->
            onProgressChange(request.progress.coerceIn(0f, 1f))
            onPositionChange(targetPosition.toLong())
        }
    }

    LaunchedEffect(song.id, playing) {
        while (playing) {
            val player = playerHolder.value
            val position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            val progress = runCatching {
                if (player != null && player.duration > 0) {
                    position.toFloat() / player.duration.toFloat()
                } else {
                    0f
                }
            }.getOrDefault(0f)
            onProgressChange(progress.coerceIn(0f, 1f))
            onPositionChange(position.toLong())
            delay(300)
        }
    }
}

private suspend fun createMediaPlayer(context: Context, uri: Uri): MediaPlayer? =
    withContext(Dispatchers.IO) {
        createPreparedMediaPlayer {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                val length = descriptor.length
                if (length >= 0L) {
                    setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        length
                    )
                } else {
                    setDataSource(descriptor.fileDescriptor)
                }
                prepare()
            }
                ?: error("Unable to open audio descriptor")
        } ?: createPreparedMediaPlayer {
            setDataSource(context, uri)
            prepare()
        }
    }

private fun MediaPlayer.seekToProgress(progress: Float): Int? =
    runCatching {
        val mediaDuration = duration
        if (mediaDuration <= 0) {
            null
        } else {
            val targetPosition = (mediaDuration * progress.coerceIn(0f, 1f))
                .toInt()
                .coerceIn(0, mediaDuration)
            seekTo(targetPosition)
            targetPosition
        }
    }.getOrNull()

private fun systemMusicVolumeProgress(context: Context): Float {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return 0f
    val maxVolume = max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        .toFloat()
        .div(maxVolume.toFloat())
        .coerceIn(0f, 1f)
}

private fun setSystemMusicVolumeProgress(context: Context, progress: Float): Float {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return progress.coerceIn(0f, 1f)
    val maxVolume = max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    val targetVolume = (progress.coerceIn(0f, 1f) * maxVolume)
        .roundToInt()
        .coerceIn(0, maxVolume)
    runCatching {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
    }
    return systemMusicVolumeProgress(context)
}

private fun createPreparedMediaPlayer(prepareBlock: MediaPlayer.() -> Unit): MediaPlayer? {
    val player = MediaPlayer()
    return runCatching {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        player.prepareBlock()
        player
    }.getOrElse {
        player.release()
        null
    }
}

@Composable
private fun RitimPageBackground(
    pageIndex: Int,
    songs: List<SongSample>,
    onSongSelected: (SongSample) -> Unit,
    aiLyricsSettings: AiLyricsTranslationSettings,
    onSettingsSelected: () -> Unit,
    onAiLyricsSettingsSelected: () -> Unit,
    onAiLyricsSettingsSave: (AiLyricsTranslationSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val targetIndex = pageIndex.coerceIn(0, 5)
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
                aiLyricsSettings = aiLyricsSettings,
                onSettingsSelected = onSettingsSelected,
                onAiLyricsSettingsSelected = onAiLyricsSettingsSelected,
                onAiLyricsSettingsSave = onAiLyricsSettingsSave,
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
            aiLyricsSettings = aiLyricsSettings,
            onSettingsSelected = onSettingsSelected,
            onAiLyricsSettingsSelected = onAiLyricsSettingsSelected,
            onAiLyricsSettingsSave = onAiLyricsSettingsSave,
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
    aiLyricsSettings: AiLyricsTranslationSettings,
    onSettingsSelected: () -> Unit,
    onAiLyricsSettingsSelected: () -> Unit,
    onAiLyricsSettingsSave: (AiLyricsTranslationSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    when (pageIndex) {
        0 -> HomePage(
            songs = songs,
            onSongSelected = onSongSelected,
            modifier = modifier
        )
        2 -> MinePage(
            onSettingsSelected = onSettingsSelected,
            modifier = modifier
        )
        3 -> Box(modifier.background(Color.White))
        4 -> SettingsPage(
            onAiLyricsSettingsSelected = onAiLyricsSettingsSelected,
            modifier = modifier
        )
        5 -> AiLyricsSettingsPage(
            settings = aiLyricsSettings,
            onSave = onAiLyricsSettingsSave,
            modifier = modifier
        )
        else -> {
            PageBackgroundLayer(
                visual = pageVisual(pageIndex),
                modifier = modifier
            )
        }
    }
}

@Composable
private fun MinePage(
    onSettingsSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(Color.White)
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 20.dp, bottom = 166.dp)
    ) {
        SettingsMenuRow(
            title = "设置",
            onClick = onSettingsSelected,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun SettingsPage(
    onAiLyricsSettingsSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(Color.White)
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 20.dp, bottom = 166.dp)
    ) {
        SettingsMenuRow(
            title = "AI歌词翻译",
            onClick = onAiLyricsSettingsSelected,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun AiLyricsSettingsPage(
    settings: AiLyricsTranslationSettings,
    onSave: (AiLyricsTranslationSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var targetLanguage by remember(settings) { mutableStateOf(settings.targetLanguage) }
    var apiBaseUrl by remember(settings) { mutableStateOf(settings.apiBaseUrl) }
    var apiKey by remember(settings) { mutableStateOf(settings.apiKey) }
    var modelName by remember(settings) { mutableStateOf(settings.modelName) }
    val scrollState = rememberScrollState()

    Column(
        modifier
            .background(Color.White)
            .fillMaxSize()
            .verticalScroll(scrollState)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 186.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsLanguageDropdown(
            label = "目标翻译语言",
            value = targetLanguage,
            onValueChange = { targetLanguage = it }
        )
        SettingsInputField(
            label = "API baseurl",
            value = apiBaseUrl,
            onValueChange = { apiBaseUrl = it },
            placeholder = "https://api.openai.com/v1"
        )
        SettingsInputField(
            label = "API Key",
            value = apiKey,
            onValueChange = { apiKey = it },
            placeholder = "sk-..."
        )
        SettingsInputField(
            label = "AI模型名",
            value = modelName,
            onValueChange = { modelName = it },
            placeholder = "gpt-4o-mini"
        )
        SettingsSaveButton(
            onClick = {
                onSave(
                    AiLyricsTranslationSettings(
                        targetLanguage = targetLanguage.trim().ifBlank { "中文" },
                        apiBaseUrl = apiBaseUrl.trim(),
                        apiKey = apiKey.trim(),
                        modelName = modelName.trim()
                    )
                )
            },
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SettingsMenuRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.99f else 1f,
        animationSpec = tween(durationMillis = 120)
    )

    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F7F8))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            title,
            style = TextStyle(
                color = Color(0xFF111315),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        )
        ThickChevron(
            modifier = Modifier.size(14.dp),
            color = Color(0xFF3C4145)
        )
    }
}

@Composable
private fun SettingsLanguageDropdown(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val languages = listOf("中文", "英语", "日语", "韩语", "法语", "德语", "西班牙语")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsFieldLabel(label)
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF5F7F8))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = { expanded = !expanded }
                    )
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    value.ifBlank { "中文" },
                    style = TextStyle(
                        color = Color(0xFF151719),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                ThickChevron(
                    modifier = Modifier
                        .size(13.dp)
                        .graphicsLayer { rotationZ = if (expanded) 90f else 0f },
                    color = Color(0xFF596168)
                )
            }

            if (expanded) {
                Column(
                    Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF5F7F8))
                ) {
                    languages.forEach { language ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = {
                                        onValueChange(language)
                                        expanded = false
                                    }
                                )
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicText(
                                language,
                                style = TextStyle(
                                    color = if (language == value) {
                                        Color(0xFF0088FF)
                                    } else {
                                        Color(0xFF151719)
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsFieldLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color(0xFF151719),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF5F7F8))
                .padding(horizontal = 14.dp),
            decorationBox = { innerTextField ->
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isBlank()) {
                        BasicText(
                            placeholder,
                            style = TextStyle(
                                color = Color(0xFF9AA2A9),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun SettingsFieldLabel(label: String) {
    BasicText(
        label,
        style = TextStyle(
            color = Color(0xFF626A71),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    )
}

@Composable
private fun SettingsSaveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 120)
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111315))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            "保存",
            style = TextStyle(
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
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
        SongCoverArt(
            song = item,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (tall) 2f / 3f else 1f),
            cornerRadius = 10.dp,
            generatedFallback = true
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
private fun SongCoverArt(
    song: SongSample,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    generatedFallback: Boolean = true
) {
    val cover = rememberSongCover(song)
    val image = cover.image
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(song.colors.first())
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (generatedFallback && (cover.loaded || song.contentUri == null)) {
            CoverArt(
                colors = song.colors,
                seed = song.seed,
                modifier = Modifier.fillMaxSize()
            )
        }
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
    val albumId: Long = 0L,
    val durationMs: Long = 0L,
    val dateAddedSeconds: Long = 0L,
    val contentUri: Uri? = null,
    val audioQualityLabel: String = "",
    val displayName: String = "",
    val relativePath: String = "",
    val filePath: String = ""
)

private data class LrcLine(
    val timeMs: Long,
    val text: String
)

private data class LyricsLoadState(
    val lines: List<LrcLine> = emptyList(),
    val loaded: Boolean = false
)

private data class MusicLibraryIndex(
    val favoriteSongIds: Set<Long> = emptySet()
)

private data class AiLyricsTranslationSettings(
    val targetLanguage: String = "中文",
    val apiBaseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = ""
)

private enum class LyricsTranslationStatus {
    Idle,
    Loading,
    Ready,
    NotNeeded,
    Failed
}

private data class LyricsTranslationResult(
    val status: LyricsTranslationStatus = LyricsTranslationStatus.Idle,
    val lines: List<LrcLine> = emptyList()
)

private data class LyricsTranslationUiState(
    val status: LyricsTranslationStatus = LyricsTranslationStatus.Idle,
    val lines: Map<Long, String> = emptyMap()
) {
    val loading: Boolean
        get() = status == LyricsTranslationStatus.Loading
}

private val coverImageCache = mutableMapOf<Long, ImageBitmap?>()
private val albumArtBaseUri: Uri = Uri.parse("content://media/external/audio/albumart")

private data class CoverImageState(
    val image: ImageBitmap?,
    val loaded: Boolean
)

private fun initialCoverState(context: Context, song: SongSample): CoverImageState {
    val memoryCover = cachedCoverImage(song.id)
    if (memoryCover != null || hasCachedCoverImage(song.id)) {
        return CoverImageState(image = memoryCover, loaded = true)
    }
    val diskCover = loadCachedCoverBitmap(context, song.albumId, song.contentUri)?.asImageBitmap()
    if (diskCover != null) {
        synchronized(coverImageCache) {
            coverImageCache[song.id] = diskCover
        }
        return CoverImageState(image = diskCover, loaded = true)
    }
    return CoverImageState(image = null, loaded = false)
}

@Composable
private fun rememberSongCover(song: SongSample): CoverImageState {
    val context = LocalContext.current
    var coverState by remember(song.id) {
        mutableStateOf(initialCoverState(context, song))
    }

    LaunchedEffect(context, song.id, song.contentUri) {
        if (!hasCachedCoverImage(song.id)) {
            val loadedImage = loadSongCoverImage(
                context = context,
                uri = song.contentUri,
                albumId = song.albumId
            )
            synchronized(coverImageCache) {
                coverImageCache[song.id] = loadedImage
            }
            coverState = CoverImageState(image = loadedImage, loaded = true)
        } else {
            coverState = CoverImageState(
                image = cachedCoverImage(song.id),
                loaded = true
            )
        }
    }

    return coverState
}

private fun hasCachedCoverImage(songId: Long): Boolean =
    synchronized(coverImageCache) {
        coverImageCache.containsKey(songId)
    }

private fun cachedCoverImage(songId: Long): ImageBitmap? =
    synchronized(coverImageCache) {
        coverImageCache[songId]
    }

private suspend fun loadSongCoverImage(
    context: Context,
    uri: Uri?,
    albumId: Long
): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val cachedCover = loadCachedCoverBitmap(context, albumId, uri)
        if (cachedCover != null) return@withContext cachedCover.asImageBitmap()

        val embeddedBitmap = if (uri == null) null else runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val data = retriever.embeddedPicture ?: return@runCatching null
                BitmapFactory.decodeByteArray(data, 0, data.size)
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()

        val bitmap = embeddedBitmap ?: loadAlbumArtBitmap(context, albumId)
        if (bitmap != null) {
            saveCoverBitmap(context, albumId, uri, bitmap)
        }
        bitmap?.asImageBitmap()
    }

private fun loadAlbumArtImage(context: Context, albumId: Long): ImageBitmap? {
    if (albumId <= 0L) return null
    return loadAlbumArtBitmap(context, albumId)?.asImageBitmap()
}

private fun loadAlbumArtBitmap(context: Context, albumId: Long): Bitmap? {
    if (albumId <= 0L) return null
    val albumArtUri = ContentUris.withAppendedId(albumArtBaseUri, albumId)
    return runCatching {
        context.contentResolver.openInputStream(albumArtUri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
}

private fun loadCachedCoverBitmap(context: Context, albumId: Long, uri: Uri?): Bitmap? {
    val file = coverCacheFile(context, albumId, uri)
    return if (file.exists()) {
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    } else {
        null
    }
}

private fun saveCoverBitmap(context: Context, albumId: Long, uri: Uri?, bitmap: Bitmap) {
    runCatching {
        val file = coverCacheFile(context, albumId, uri)
        file.parentFile?.mkdirs()
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
        }
    }
}

private fun coverCacheFile(context: Context, albumId: Long, uri: Uri?): File {
    val rawKey = if (albumId > 0L) {
        "album_$albumId"
    } else {
        "song_${stableSongSeed(uri?.toString().orEmpty())}"
    }
    return File(File(context.cacheDir, "covers"), "$rawKey.jpg")
}

private suspend fun saveCachedMusicIndex(context: Context, songs: List<SongSample>) =
    withContext(Dispatchers.IO) {
        runCatching {
            val array = JSONArray()
            songs.forEach { song ->
                array.put(
                    JSONObject()
                        .put("id", song.id)
                        .put("title", song.title)
                        .put("artist", song.artist)
                        .put("seed", song.seed)
                        .put("albumId", song.albumId)
                        .put("durationMs", song.durationMs)
                        .put("dateAddedSeconds", song.dateAddedSeconds)
                        .put("contentUri", song.contentUri?.toString())
                        .put("audioQualityLabel", song.audioQualityLabel)
                        .put("displayName", song.displayName)
                        .put("relativePath", song.relativePath)
                        .put("filePath", song.filePath)
                )
            }
            musicIndexFile(context).writeText(array.toString())
        }
    }

private fun loadCachedMusicIndex(context: Context): List<SongSample> =
    runCatching {
        val file = musicIndexFile(context)
        if (!file.exists()) return@runCatching emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val seed = item.optInt("seed", 0)
                val uriText = item.optString("contentUri").takeIf { it.isNotBlank() && it != "null" }
                add(
                    SongSample(
                        id = item.optLong("id"),
                        title = item.optString("title", "未知歌曲"),
                        artist = item.optString("artist", "未知艺术家"),
                        colors = paletteForSeed(seed),
                        seed = seed,
                        albumId = item.optLong("albumId"),
                        durationMs = item.optLong("durationMs"),
                        dateAddedSeconds = item.optLong("dateAddedSeconds"),
                        contentUri = uriText?.let(Uri::parse),
                        audioQualityLabel = item.optString("audioQualityLabel"),
                        displayName = item.optString("displayName"),
                        relativePath = item.optString("relativePath"),
                        filePath = item.optString("filePath")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

private fun musicIndexFile(context: Context): File =
    File(context.filesDir, "music_index.json")

private suspend fun saveMusicLibraryIndex(context: Context, index: MusicLibraryIndex) =
    withContext(Dispatchers.IO) {
        runCatching {
            val favorites = JSONArray()
            index.favoriteSongIds.sorted().forEach { songId ->
                favorites.put(songId)
            }
            val json = JSONObject().put("favorites", favorites)
            musicLibraryIndexFile(context).writeText(json.toString())
        }
    }

private fun loadMusicLibraryIndex(context: Context): MusicLibraryIndex =
    runCatching {
        val file = musicLibraryIndexFile(context)
        if (!file.exists()) return@runCatching MusicLibraryIndex()
        val json = JSONObject(file.readText())
        val favorites = mutableSetOf<Long>()
        val favoriteArray = json.optJSONArray("favorites") ?: JSONArray()
        for (index in 0 until favoriteArray.length()) {
            favorites.add(favoriteArray.optLong(index))
        }
        MusicLibraryIndex(favoriteSongIds = favorites)
    }.getOrDefault(MusicLibraryIndex())

private fun musicLibraryIndexFile(context: Context): File =
    File(context.filesDir, "music_library_index.json")

private suspend fun saveAiLyricsTranslationSettings(
    context: Context,
    settings: AiLyricsTranslationSettings
) =
    withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject()
                .put("targetLanguage", settings.targetLanguage)
                .put("apiBaseUrl", settings.apiBaseUrl)
                .put("apiKey", settings.apiKey)
                .put("modelName", settings.modelName)
            aiLyricsTranslationSettingsFile(context).writeText(json.toString())
        }
    }

private fun loadAiLyricsTranslationSettings(context: Context): AiLyricsTranslationSettings =
    runCatching {
        val file = aiLyricsTranslationSettingsFile(context)
        if (!file.exists()) return@runCatching AiLyricsTranslationSettings()
        val json = JSONObject(file.readText())
        AiLyricsTranslationSettings(
            targetLanguage = json.optString("targetLanguage", "中文").ifBlank { "中文" },
            apiBaseUrl = json.optString("apiBaseUrl"),
            apiKey = json.optString("apiKey"),
            modelName = json.optString("modelName")
        )
    }.getOrDefault(AiLyricsTranslationSettings())

private fun aiLyricsTranslationSettingsFile(context: Context): File =
    File(context.filesDir, "ai_lyrics_translation_settings.json")

@Composable
private fun rememberSongLyricsState(song: SongSample): LyricsLoadState {
    val context = LocalContext.current
    var lyricsState by remember(song.id, song.displayName, song.relativePath, song.filePath) {
        mutableStateOf(LyricsLoadState())
    }

    LaunchedEffect(context, song.id, song.displayName, song.relativePath, song.filePath) {
        lyricsState = LyricsLoadState()
        lyricsState = LyricsLoadState(
            lines = loadSongLyrics(context, song),
            loaded = true
        )
    }

    return lyricsState
}

private suspend fun loadSongLyrics(context: Context, song: SongSample): List<LrcLine> =
    withContext(Dispatchers.IO) {
        val lyricsFile = findLyricsFile(context, song) ?: return@withContext emptyList()
        parseLrcText(readLyricsText(lyricsFile))
    }

@Composable
private fun rememberTranslatedSongLyrics(
    song: SongSample,
    enabled: Boolean,
    settings: AiLyricsTranslationSettings
): LyricsTranslationUiState {
    val context = LocalContext.current
    var translationState by remember(song.id, settings.targetLanguage, settings.apiBaseUrl, settings.modelName) {
        mutableStateOf(LyricsTranslationUiState())
    }

    LaunchedEffect(
        context,
        song.id,
        song.displayName,
        song.relativePath,
        song.filePath,
        enabled,
        settings
    ) {
        translationState = if (enabled) {
            LyricsTranslationUiState(status = LyricsTranslationStatus.Loading)
            val result = LyricsTranslationJobs
                .request(context.applicationContext, song, settings)
                .await()
            LyricsTranslationUiState(
                status = result.status,
                lines = result.lines.associate { it.timeMs to it.text }
            )
        } else {
            LyricsTranslationUiState()
        }
    }

    return translationState
}

private object LyricsTranslationJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val jobs = mutableMapOf<String, Deferred<LyricsTranslationResult>>()

    fun request(
        context: Context,
        song: SongSample,
        settings: AiLyricsTranslationSettings
    ): Deferred<LyricsTranslationResult> {
        val key = translationJobKey(context, song, settings)
        return synchronized(lock) {
            jobs[key]?.let { return@synchronized it }
            val job = scope.async {
                loadOrCreateTranslatedLyrics(context, song, settings).also {
                    synchronized(lock) {
                        jobs.remove(key)
                    }
                }
            }
            jobs[key] = job
            job
        }
    }
}

private fun translationJobKey(
    context: Context,
    song: SongSample,
    settings: AiLyricsTranslationSettings
): String {
    val sourceFile = findLyricsFile(context, song)
    return listOf(
        sourceFile?.absolutePath ?: song.id.toString(),
        settings.targetLanguage,
        settings.apiBaseUrl,
        settings.modelName
    ).joinToString("|")
}

private suspend fun loadOrCreateTranslatedLyrics(
    context: Context,
    song: SongSample,
    settings: AiLyricsTranslationSettings
): LyricsTranslationResult =
    withContext(Dispatchers.IO) {
        val sourceFile = findLyricsFile(context, song)
            ?: return@withContext LyricsTranslationResult(LyricsTranslationStatus.Failed)
        val cachedFile = translatedLyricsFile(sourceFile, settings.targetLanguage)
        if (cachedFile.exists()) {
            return@withContext LyricsTranslationResult(
                status = LyricsTranslationStatus.Ready,
                lines = parseLrcText(readLyricsText(cachedFile))
            )
        }
        val sourceLyrics = parseLrcText(readLyricsText(sourceFile))
        if (sourceLyrics.isEmpty()) {
            return@withContext LyricsTranslationResult(LyricsTranslationStatus.Failed)
        }
        val apiReady = settings.apiBaseUrl.isNotBlank() &&
            settings.apiKey.isNotBlank() &&
            settings.modelName.isNotBlank()
        if (!apiReady) {
            return@withContext LyricsTranslationResult(LyricsTranslationStatus.Failed)
        }

        val sourceLanguage = runCatching {
            val folder = File(sourceFile.parentFile, "translatedLyrics")
            folder.mkdirs()
            sourceFile.copyTo(File(folder, sourceFile.name), overwrite = true)
            detectLyricsLanguage(song, sourceLyrics, settings)
        }.getOrNull().orEmpty()
        if (sameLanguage(sourceLanguage, settings.targetLanguage)) {
            return@withContext LyricsTranslationResult(LyricsTranslationStatus.NotNeeded)
        }

        val translatedLines = runCatching {
            val songContext = evaluateSongContext(song, sourceLyrics, sourceLanguage, settings)
            translateLyricLines(
                song = song,
                lyrics = sourceLyrics,
                sourceLanguage = sourceLanguage,
                songContext = songContext,
                settings = settings
            )
        }.getOrNull() ?: return@withContext LyricsTranslationResult(LyricsTranslationStatus.Failed)

        if (translatedLines.isNotEmpty()) {
            runCatching {
                cachedFile.parentFile?.mkdirs()
                cachedFile.writeText(buildLrcText(translatedLines))
            }
        }
        LyricsTranslationResult(
            status = if (translatedLines.isEmpty()) {
                LyricsTranslationStatus.Failed
            } else {
                LyricsTranslationStatus.Ready
            },
            lines = translatedLines
        )
    }

private suspend fun detectLyricsLanguage(
    song: SongSample,
    lyrics: List<LrcLine>,
    settings: AiLyricsTranslationSettings
): String {
    val sampleText = lyrics.joinToString("\n") { it.text }.take(4_000)
    return requestAiText(
        settings = settings,
        systemPrompt = "你只返回歌曲歌词的主要原生语言名称，不要标点，不要解释。韩语罗马音也返回韩语，日语罗马音也返回日语。",
        userPrompt = "歌曲：${song.title}\n艺术家：${song.artist}\n歌词：\n$sampleText"
    ).lineSequence().firstOrNull().orEmpty().trim()
}

private suspend fun evaluateSongContext(
    song: SongSample,
    lyrics: List<LrcLine>,
    sourceLanguage: String,
    settings: AiLyricsTranslationSettings
): String {
    val fullText = lyrics.joinToString("\n") { it.text }.take(8_000)
    return requestAiText(
        settings = settings,
        systemPrompt = "你是歌词翻译前的语境分析助手。请概括歌曲叙事视角、情绪、意象、关键词、人称和翻译时应保持的语气。不要翻译整首歌。",
        userPrompt = "歌曲：${song.title}\n艺术家：${song.artist}\n原语言：$sourceLanguage\n目标语言：${settings.targetLanguage}\n歌词：\n$fullText"
    )
}

private suspend fun translateLyricLines(
    song: SongSample,
    lyrics: List<LrcLine>,
    sourceLanguage: String,
    songContext: String,
    settings: AiLyricsTranslationSettings
): List<LrcLine> =
    coroutineScope {
        val semaphore = Semaphore(6)
        lyrics.map { line ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val translatedText = requestAiText(
                        settings = settings,
                        systemPrompt = "你是歌词单句翻译器。只返回这一句的${settings.targetLanguage}译文，不要解释，不要引号，不要编号，不要<think>内容。保持歌曲语境、口吻和押韵感，不要生硬直译。",
                        userPrompt = "歌曲：${song.title}\n艺术家：${song.artist}\n原语言：$sourceLanguage\n整歌语境：$songContext\n原句：${line.text}"
                    ).lineSequence().firstOrNull().orEmpty().trim()
                    line.copy(text = translatedText.ifBlank { line.text })
                }
            }
        }.awaitAll()
    }

private fun translatedLyricsFile(sourceFile: File, targetLanguage: String): File {
    val folder = File(sourceFile.parentFile, "translatedLyrics")
    val baseName = sourceFile.name.substringBeforeLast('.', sourceFile.name)
    val languageName = targetLanguage.ifBlank { "中文" }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
    return File(folder, "$baseName.$languageName.lrc")
}

private suspend fun hasCachedTranslatedLyrics(
    context: Context,
    song: SongSample,
    targetLanguage: String
): Boolean =
    withContext(Dispatchers.IO) {
        val sourceFile = findLyricsFile(context, song) ?: return@withContext false
        translatedLyricsFile(sourceFile, targetLanguage).exists()
    }

private fun buildLrcText(lines: List<LrcLine>): String =
    lines.joinToString(separator = "\n") { line ->
        "[${formatLrcTimestamp(line.timeMs)}]${line.text}"
    }

private fun formatLrcTimestamp(timeMs: Long): String {
    val safeMs = timeMs.coerceAtLeast(0L)
    val minutes = safeMs / 60_000L
    val seconds = (safeMs % 60_000L) / 1_000L
    val hundredths = (safeMs % 1_000L) / 10L
    return minutes.toString().padStart(2, '0') +
        ":" +
        seconds.toString().padStart(2, '0') +
        "." +
        hundredths.toString().padStart(2, '0')
}

private suspend fun requestAiText(
    settings: AiLyricsTranslationSettings,
    systemPrompt: String,
    userPrompt: String
): String =
    withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(chatCompletionsUrl(settings.apiBaseUrl)).openConnection() as HttpURLConnection)
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")

            val body = JSONObject()
                .put("model", settings.modelName)
                .put("temperature", 0.2)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userPrompt))
                )
            connection.outputStream.use { stream ->
                stream.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                return@runCatching ""
            }
            val json = JSONObject(responseText)
            val choices = json.optJSONArray("choices") ?: JSONArray()
            val firstChoice = choices.optJSONObject(0) ?: JSONObject()
            val message = firstChoice.optJSONObject("message")
            stripAiThinking(
                message?.optString("content")
                    ?: firstChoice.optString("text")
            )
        }.getOrDefault("")
    }

private fun chatCompletionsUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    return if (trimmed.endsWith("/chat/completions")) {
        trimmed
    } else {
        "$trimmed/chat/completions"
    }
}

private fun stripAiThinking(text: String): String =
    text
        .replace(Regex("""<think>.*?</think>""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""<think>.*""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""</think>"""), "")
        .trim()
        .trim('"', '\'', '“', '”')

private fun sameLanguage(sourceLanguage: String, targetLanguage: String): Boolean {
    val source = normalizeLanguageName(sourceLanguage)
    val target = normalizeLanguageName(targetLanguage)
    return source.isNotBlank() && target.isNotBlank() && source == target
}

private fun normalizeLanguageName(language: String): String =
    language
        .lowercase()
        .replace(Regex("""[\s\p{Punct}，。！？；：、（）【】《》“”‘’]+"""), "")
        .replace("普通话", "中文")
        .replace("汉语", "中文")
        .replace("国语", "中文")
        .replace("mandarin", "中文")
        .replace("chinese", "中文")
        .replace("korean", "韩语")
        .replace("japanese", "日语")
        .replace("english", "英语")

private fun findLyricsFile(context: Context, song: SongSample): File? {
    val audioName = song.displayName.ifBlank {
        song.filePath.substringAfterLast('/').ifBlank { "${song.title}.mp3" }
    }
    val audioNameLower = audioName.lowercase()
    val audioBaseLower = audioNameLower.substringBeforeLast('.', audioNameLower)
    val acceptedNames = setOf(
        "$audioBaseLower.lrc",
        "$audioNameLower.lrc"
    )
    return lyricsDirectory(context, song)
        ?.listFiles()
        ?.firstOrNull { file ->
            file.isFile && file.name.lowercase() in acceptedNames
        }
}

private fun lyricsDirectory(context: Context, song: SongSample): File? {
    if (song.filePath.isNotBlank()) {
        return File(song.filePath).parentFile
    }
    val publicMusicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    val relativePath = song.relativePath
        .removePrefix("Music/")
        .trim('/')
    return if (relativePath.isBlank()) {
        publicMusicDir
    } else {
        File(publicMusicDir, relativePath)
    }.takeIf { it.exists() && it.isDirectory }
}

private fun readLyricsText(file: File): String {
    val bytes = runCatching { file.readBytes() }.getOrDefault(ByteArray(0))
    if (bytes.isEmpty()) return ""
    return runCatching { bytes.toString(Charsets.UTF_8) }
        .getOrElse {
            runCatching { bytes.toString(Charset.forName("GBK")) }.getOrDefault("")
        }
}

private fun parseLrcText(text: String): List<LrcLine> {
    val timestampPattern = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    return text.lineSequence()
        .flatMap { rawLine ->
            val matches = timestampPattern.findAll(rawLine).toList()
            if (matches.isEmpty()) {
                emptySequence<LrcLine>()
            } else {
                val lyricText = timestampPattern.replace(rawLine, "").trim()
                if (lyricText.isBlank()) {
                    emptySequence<LrcLine>()
                } else {
                    matches.asSequence().mapNotNull { match ->
                        val minute = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                        val second = match.groupValues.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
                        val fractionText = match.groupValues.getOrNull(3).orEmpty()
                        val fractionMs = when (fractionText.length) {
                            1 -> fractionText.toLongOrNull()?.times(100L)
                            2 -> fractionText.toLongOrNull()?.times(10L)
                            else -> fractionText.take(3).padEnd(3, '0').toLongOrNull()
                        } ?: 0L
                        LrcLine(
                            timeMs = minute * 60_000L + second * 1_000L + fractionMs,
                            text = lyricText
                        )
                    }
                }
            }
        }
        .sortedBy { it.timeMs }
        .toList()
}

@Composable
private fun rememberMusicFolderSongs(): List<SongSample> {
    val context = LocalContext.current
    var songs by remember {
        mutableStateOf(loadCachedMusicIndex(context))
    }
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
            val loadedSongs = loadMusicFolderSongs(context)
            if (loadedSongs.isNotEmpty()) {
                songs = loadedSongs
                saveCachedMusicIndex(context, loadedSongs)
            }
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
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(pathColumn)
        }.toTypedArray()
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
                val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val pathIndex = cursor.getColumnIndexOrThrow(pathColumn)

                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIndex)
                        val displayName = cursorString(cursor, displayNameIndex)
                        val pathText = cursorString(cursor, pathIndex).orEmpty()
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
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
                                albumId = cursorLong(cursor, albumIdIndex),
                                durationMs = cursorLong(cursor, durationIndex),
                                dateAddedSeconds = cursorLong(cursor, dateAddedIndex),
                                contentUri = contentUri,
                                audioQualityLabel = detectAudioQualityLabel(
                                    context = context,
                                    uri = contentUri,
                                    displayName = displayName,
                                    mediaStoreMimeType = cursorString(cursor, mimeTypeIndex),
                                    mediaStoreBitrate = 0L
                                ),
                                displayName = displayName.orEmpty(),
                                relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    pathText
                                } else {
                                    ""
                                },
                                filePath = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    pathText
                                } else {
                                    ""
                                }
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

private data class AudioQualityMetadata(
    val mimeType: String?,
    val bitrate: Long,
    val sampleRate: Long,
    val bitsPerSample: Int
)

private fun detectAudioQualityLabel(
    context: Context,
    uri: Uri,
    displayName: String?,
    mediaStoreMimeType: String?,
    mediaStoreBitrate: Long
): String {
    val metadata = readAudioQualityMetadata(
        context = context,
        uri = uri,
        fallbackMimeType = mediaStoreMimeType,
        fallbackBitrate = mediaStoreBitrate
    )
    val extension = displayName
        ?.substringAfterLast('.', "")
        ?.lowercase()
        .orEmpty()
    val mime = metadata.mimeType.orEmpty().lowercase()

    return when {
        isDsdAudio(extension, mime) -> dsdQualityLabel(metadata.sampleRate)
        isLosslessAudio(extension, mime) -> {
            if (metadata.bitsPerSample >= 24 && metadata.sampleRate >= 88_200L) {
                "HI-RES"
            } else {
                "LOSSLESS"
            }
        }
        isLossyAudio(extension, mime) && metadata.bitrate >= 192_000L -> "HQ"
        else -> ""
    }
}

private fun readAudioQualityMetadata(
    context: Context,
    uri: Uri,
    fallbackMimeType: String?,
    fallbackBitrate: Long
): AudioQualityMetadata {
    val retriever = MediaMetadataRetriever()
    return runCatching {
        retriever.setDataSource(context, uri)
        AudioQualityMetadata(
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?: fallbackMimeType,
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()
                ?: fallbackBitrate,
            sampleRate = retriever.extractMetadata(metadataKeySampleRate)
                ?.toLongOrNull()
                ?: 0L,
            bitsPerSample = retriever.extractMetadata(metadataKeyBitsPerSample)
                ?.toIntOrNull()
                ?: 0
        )
    }.getOrElse {
        AudioQualityMetadata(
            mimeType = fallbackMimeType,
            bitrate = fallbackBitrate,
            sampleRate = 0L,
            bitsPerSample = 0
        )
    }.also {
        runCatching { retriever.release() }
    }
}

private fun isDsdAudio(extension: String, mime: String): Boolean =
    extension in setOf("dsf", "dff", "dsd") ||
        mime.contains("dsd") ||
        mime.contains("dsf") ||
        mime.contains("dff")

private fun dsdQualityLabel(sampleRate: Long): String =
    when {
        sampleRate >= 45_158_400L -> "DSD1024"
        sampleRate >= 22_579_200L -> "DSD512"
        sampleRate >= 11_289_600L -> "DSD256"
        sampleRate >= 5_644_800L -> "DSD128"
        sampleRate >= 2_822_400L -> "DSD64"
        else -> ""
    }

private fun isLosslessAudio(extension: String, mime: String): Boolean =
    extension in setOf("flac", "wav", "wave", "aiff", "aif", "aifc", "alac", "ape", "wv") ||
        mime.contains("flac") ||
        mime.contains("wav") ||
        mime.contains("wave") ||
        mime.contains("aiff") ||
        mime.contains("alac") ||
        mime.contains("ape") ||
        mime.contains("wavpack")

private fun isLossyAudio(extension: String, mime: String): Boolean =
    extension in setOf("mp3", "m4a", "aac", "ogg", "opus", "wma", "mp4", "3gp") ||
        mime.contains("mpeg") ||
        mime.contains("mp4") ||
        mime.contains("aac") ||
        mime.contains("ogg") ||
        mime.contains("opus") ||
        mime.contains("vorbis") ||
        mime.contains("wma") ||
        mime.contains("3gpp")

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
    onNext: () -> Unit,
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
            onNext = onNext,
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
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 120)
    )

    Box(
        modifier
            .height(height)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = "迷你播放器" }
    ) {
        LiquidStaticBar(
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
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
                    onClick = onNext
                ) {
                    NextGlyph(
                        modifier = Modifier.size(27.dp),
                        color = Color(0xFF1E1E1E)
                    )
                }
            }
        }

        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick
                    )
            )
            Spacer(Modifier.width(104.dp))
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
    playbackProgress: Float,
    volume: Float,
    favorite: Boolean,
    lyricsVisible: Boolean,
    aiLyricsSettings: AiLyricsTranslationSettings,
    rememberedTranslationSongIds: Set<Long>,
    onRememberedTranslationSongIdsChange: (Set<Long>) -> Unit,
    dismissRequestId: Int,
    onPlayingChange: (Boolean) -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onFavoriteToggle: () -> Unit,
    onLyricsVisibleChange: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBackdropProgressChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val dragOffset = remember { Animatable(0f) }
    var lastDownwardDrag by remember { mutableFloatStateOf(0f) }
    var translationActiveSongId by rememberSaveable { mutableStateOf<Long?>(null) }
    val translationActive = translationActiveSongId == song.id
    val lyricsState = rememberSongLyricsState(song)
    val lyrics = lyricsState.lines
    val translationState = rememberTranslatedSongLyrics(
        song = song,
        enabled = translationActive,
        settings = aiLyricsSettings
    )
    val translatedLyrics = translationState.lines
    LaunchedEffect(song.id, aiLyricsSettings.targetLanguage) {
        val shouldRestore = rememberedTranslationSongIds.contains(song.id) &&
            hasCachedTranslatedLyrics(context, song, aiLyricsSettings.targetLanguage)
        translationActiveSongId = if (shouldRestore) song.id else null
    }
    LaunchedEffect(song.id, translationState.status) {
        if (
            translationState.status == LyricsTranslationStatus.NotNeeded ||
            translationState.status == LyricsTranslationStatus.Failed
        ) {
            if (translationActiveSongId == song.id) {
                translationActiveSongId = null
            }
            onRememberedTranslationSongIdsChange(rememberedTranslationSongIds - song.id)
        }
    }
    val lyricsProgress by animateFloatAsState(
        targetValue = if (lyricsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
    )
    val currentPositionMs = (song.durationMs * playbackProgress.coerceIn(0f, 1f)).toLong()
    val activeLyricIndex = lyrics.indexOfLast { it.timeMs <= currentPositionMs }
        .coerceAtLeast(0)

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(dismissRequestId) {
        if (dismissRequestId > 0) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
            )
            dragOffset.snapTo(0f)
            onDismiss()
        }
    }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val screenHeight = constraints.maxHeight.toFloat()
        val offsetY = (1f - progress.value) * screenHeight + dragOffset.value
        val backdropProgress = if (screenHeight > 0f) {
            (1f - offsetY / screenHeight).coerceIn(0f, 1f)
        } else {
            progress.value.coerceIn(0f, 1f)
        }
        val compactPlayerLayout = maxHeight < 700.dp
        val coverSize = minOf(
            maxWidth - 56.dp,
            if (compactPlayerLayout) 254.dp else 304.dp
        )
        val contentWidth = maxWidth - 56.dp
        val progressSideInset = (contentWidth - coverSize) / 2f
        val lyricsEdgeInset = progressSideInset + 12.dp
        val sideControlSize = if (compactPlayerLayout) 66.dp else 76.dp
        val mainControlSize = if (compactPlayerLayout) 92.dp else 104.dp
        val sideControlIconSize = if (compactPlayerLayout) 42.dp else 48.dp
        val mainControlIconSize = if (compactPlayerLayout) 56.dp else 64.dp
        val controlSpacer = if (compactPlayerLayout) 14.dp else 18.dp
        val coverLift = if (compactPlayerLayout) 6.dp else 10.dp
        val controlsTopPadding = if (compactPlayerLayout) 10.dp else 14.dp
        val lyricsSpacing = 20.dp
        val lyricsCoverScale = if (compactPlayerLayout) 0.20f else 0.18f
        val lyricsCoverSize = coverSize * lyricsCoverScale
        val naturalLyricsBottomGap = coverLift + controlsTopPadding
        val lyricsPanelBottomExtension = if (naturalLyricsBottomGap > lyricsSpacing) {
            naturalLyricsBottomGap - lyricsSpacing
        } else {
            0.dp
        }
        val lyricsPanelEdgeInset = maxOf(0.dp, lyricsEdgeInset - 12.dp)
        val headerSpacer = if (compactPlayerLayout) 28.dp - coverLift else 42.dp - coverLift
        val translationButtonTopOffset = 14.dp + 4.dp + headerSpacer + coverSize + 26.dp
        val coverTranslationXPx = with(density) {
            12.dp.toPx() * lyricsProgress
        }
        fun startDismissDrag() {
            lastDownwardDrag = 0f
        }
        fun applyDismissDrag(dragDeltaY: Float) {
            lastDownwardDrag = dragDeltaY
            scope.launch {
                dragOffset.snapTo(max(0f, dragOffset.value + dragDeltaY))
            }
        }
        fun settleDismissDrag() {
            val shouldDismiss =
                dragOffset.value > screenHeight * 0.12f || lastDownwardDrag > 18f
            if (shouldDismiss) {
                scope.launch {
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
        }
        fun cancelDismissDrag() {
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
        fun Modifier.playerDismissDrag(enabled: Boolean = true): Modifier =
            if (!enabled) {
                this
            } else {
                pointerInput(screenHeight) {
                    detectDragGestures(
                        onDragStart = {
                            startDismissDrag()
                        },
                        onDragEnd = {
                            settleDismissDrag()
                        },
                        onDragCancel = {
                            cancelDismissDrag()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            applyDismissDrag(dragAmount.y)
                        }
                    )
                }
            }
        SideEffect {
            onBackdropProgressChange(backdropProgress)
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offsetY
                    alpha = 0.90f + 0.10f * progress.value
                    clip = true
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                }
                .clipToBounds()
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

            Box(
                Modifier
                    .fillMaxSize()
                    .playerDismissDrag()
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp)
                    .padding(top = 14.dp, bottom = if (compactPlayerLayout) 22.dp else 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.42f))
                )

                Spacer(Modifier.height(if (compactPlayerLayout) 28.dp - coverLift else 42.dp - coverLift))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(coverSize)
                ) {
                    val smallCoverInteractionSource = remember { MutableInteractionSource() }
                    SolidCoverArt(
                        song = song,
                        modifier = Modifier
                            .size(coverSize)
                            .align(Alignment.TopCenter)
                            .playerDismissDrag(enabled = !lyricsVisible)
                            .graphicsLayer {
                                val scale = 1f - (1f - lyricsCoverScale) * lyricsProgress
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                                translationX = coverTranslationXPx
                            }
                            .clip(RoundedCornerShape(16.dp))
                    )

                    if (lyricsProgress > 0.01f) {
                        LyricsHeaderRow(
                            song = song,
                            favorite = favorite,
                            onFavoriteToggle = onFavoriteToggle,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(
                                    start = lyricsEdgeInset + lyricsCoverSize + 12.dp,
                                    end = lyricsEdgeInset
                                )
                                .fillMaxWidth()
                                .height(lyricsCoverSize)
                                .graphicsLayer { alpha = lyricsProgress }
                        )

                        LyricsPanel(
                            lyrics = lyrics,
                            activeIndex = activeLyricIndex,
                            currentPositionMs = currentPositionMs,
                            songDurationMs = song.durationMs,
                            playing = playing,
                            translationsVisible = translationActive,
                            translatedLyrics = translatedLyrics,
                            lyricsLoaded = lyricsState.loaded,
                            edgePadding = lyricsPanelEdgeInset,
                            onLyricSeek = onSeek,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(coverSize - lyricsCoverSize - lyricsSpacing + lyricsPanelBottomExtension)
                                .offset(y = 10.dp)
                                .graphicsLayer { alpha = lyricsProgress }
                        )
                    }

                    if (lyricsVisible) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(start = lyricsEdgeInset)
                                .size(lyricsCoverSize)
                                .clickable(
                                    interactionSource = smallCoverInteractionSource,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { onLyricsVisibleChange(false) }
                                )
                        )
                    }
                }

                Spacer(Modifier.height(coverLift))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = controlsTopPadding),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .width(coverSize)
                            .offset(y = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PlayerSongInfoRow(
                            song = song,
                            favorite = favorite,
                            onFavoriteToggle = onFavoriteToggle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = 1f - lyricsProgress }
                        )

                        PlayerTimeline(
                            progress = playbackProgress,
                            durationMs = song.durationMs,
                            qualityLabel = song.audioQualityLabel,
                            onSeek = onSeek,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerIconButton(
                            contentDescription = "上一曲",
                            size = sideControlSize,
                            onClick = onPrevious,
                            pressScale = 0.95f
                        ) {
                            PreviousGlyph(
                                modifier = Modifier.size(sideControlIconSize),
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.width(controlSpacer))
                        PlayerIconButton(
                            contentDescription = if (playing) "暂停" else "播放",
                            size = mainControlSize,
                            onClick = { onPlayingChange(!playing) },
                            pressScale = 0.96f
                        ) {
                            PlayPauseGlyph(
                                playing = playing,
                                modifier = Modifier.size(mainControlIconSize),
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.width(controlSpacer))
                        PlayerIconButton(
                            contentDescription = "下一曲",
                            size = sideControlSize,
                            onClick = onNext,
                            pressScale = 0.95f
                        ) {
                            NextGlyph(
                                modifier = Modifier.size(sideControlIconSize),
                                color = Color.White
                            )
                        }
                    }

                    PlayerVolumeControl(
                        volume = volume,
                        onVolumeChange = onVolumeChange,
                        modifier = Modifier.width(coverSize)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerToggleIconButton(
                            contentDescription = "歌词",
                            selected = lyricsVisible,
                            size = 48.dp,
                            onClick = { onLyricsVisibleChange(!lyricsVisible) },
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
                }
            }

            if (lyricsProgress > 0.01f) {
                TranslationPillButton(
                    selected = translationActive,
                    loading = translationState.loading,
                    onClick = {
                        if (translationActive) {
                            translationActiveSongId = null
                            onRememberedTranslationSongIdsChange(rememberedTranslationSongIds - song.id)
                        } else {
                            translationActiveSongId = song.id
                            onRememberedTranslationSongIdsChange(rememberedTranslationSongIds + song.id)
                        }
                    },
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 28.dp + lyricsEdgeInset)
                        .align(Alignment.TopStart)
                        .offset(y = translationButtonTopOffset)
                        .graphicsLayer { alpha = lyricsProgress }
                )
            }

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
    val cover = rememberSongCover(song)
    val image = cover.image
    Box(modifier.background(song.colors.first())) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun CoverColorField(
    song: SongSample,
    modifier: Modifier = Modifier
) {
    val cover = rememberSongCover(song)
    val image = cover.image
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.background(song.colors.first()),
            contentScale = ContentScale.Crop
        )
    } else {
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
}

@Composable
private fun PlayerSongInfoRow(
    song: SongSample,
    favorite: Boolean,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.height(42.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            Modifier
                .weight(1f)
                .height(38.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            BasicText(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerIconButton(
                contentDescription = if (favorite) "取消收藏" else "收藏",
                size = 42.dp,
                onClick = onFavoriteToggle,
                pressScale = 0.96f
            ) {
                StarGlyph(
                    filled = favorite,
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            }
            PlayerIconButton(
                contentDescription = "更多",
                size = 38.dp,
                onClick = {},
                pressScale = 0.96f
            ) {
                MoreGlyph(
                    modifier = Modifier.size(22.dp),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TranslationPillButton(
    selected: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(loading) {
        if (loading) {
            while (true) {
                pulse.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 760, easing = FastOutSlowInEasing)
                )
                pulse.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 860, easing = FastOutSlowInEasing)
                )
            }
        } else {
            pulse.snapTo(0f)
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed && !loading) 0.96f else 1f,
        animationSpec = tween(durationMillis = 120)
    )
    val backgroundColor = when {
        loading -> Color.White.copy(alpha = 0.16f + 0.20f * pulse.value)
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.13f)
    }
    Box(
        modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .semantics { contentDescription = "翻译" }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !loading,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        TranslationGlyph(
            modifier = Modifier.size(20.dp),
            color = if (selected) Color(0xFF111315) else Color.White.copy(alpha = 0.86f)
        )
    }
}

@Composable
private fun TranslationGlyph(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 1.45.dp.toPx()
            val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            drawRoundRect(
                color = color.copy(alpha = 0.92f),
                topLeft = Offset(size.width * 0.08f, size.height * 0.16f),
                size = Size(size.width * 0.58f, size.height * 0.48f),
                cornerRadius = corner,
                style = Stroke(width = strokeWidth)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.34f, size.height * 0.36f),
                size = Size(size.width * 0.58f, size.height * 0.48f),
                cornerRadius = corner,
                style = Stroke(width = strokeWidth)
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.23f, size.height * 0.67f),
                end = Offset(size.width * 0.33f, size.height * 0.58f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.73f, size.height * 0.84f),
                end = Offset(size.width * 0.62f, size.height * 0.74f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        BasicText(
            "A",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 5.dp, y = 2.dp),
            style = TextStyle(
                color = color,
                fontSize = 8.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold
            )
        )
        BasicText(
            "文",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-3).dp, y = (-1).dp),
            style = TextStyle(
                color = color,
                fontSize = 8.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun LyricsHeaderRow(
    song: SongSample,
    favorite: Boolean,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            BasicText(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerIconButton(
                contentDescription = if (favorite) "取消收藏" else "收藏",
                size = 36.dp,
                onClick = onFavoriteToggle,
                pressScale = 0.96f
            ) {
                StarGlyph(
                    filled = favorite,
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
            }
            PlayerIconButton(
                contentDescription = "更多",
                size = 34.dp,
                onClick = {},
                pressScale = 0.96f
            ) {
                MoreGlyph(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun LyricsPanel(
    lyrics: List<LrcLine>,
    activeIndex: Int,
    currentPositionMs: Long,
    songDurationMs: Long,
    playing: Boolean,
    translationsVisible: Boolean,
    translatedLyrics: Map<Long, String>,
    lyricsLoaded: Boolean,
    edgePadding: Dp,
    onLyricSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val scrollState = rememberScrollState()
        val density = LocalDensity.current
        val viewportHeightPx = constraints.maxHeight
        val activeAnchorFromTop = maxHeight * 0.35f
        val activeAnchorBottomPadding = maxHeight - activeAnchorFromTop
        val lineSpacing = 12.dp
        val activeLineShiftPx = with(density) { -3.dp.toPx() }
        var userScrollSuspended by remember { mutableStateOf(false) }
        var userScrollRequest by remember { mutableIntStateOf(0) }
        val lineHeights = remember(lyrics) {
            mutableStateListOf<Int>().apply {
                repeat(lyrics.size) { add(0) }
            }
        }
        val manualScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && available.y != 0f) {
                        userScrollSuspended = true
                        userScrollRequest += 1
                    }
                    return Offset.Zero
                }
            }
        }
        fun scrollTargetForIndex(index: Int): Int {
            if (lyrics.isEmpty()) {
                return 0
            }
            val targetIndex = index.coerceIn(0, lyrics.lastIndex)
            val spacingPx = with(density) { lineSpacing.toPx().roundToInt() }
            val activeTop = lineHeights
                .take(targetIndex)
                .sum() + spacingPx * targetIndex
            val activeHeight = lineHeights
                .getOrNull(targetIndex)
                ?.takeIf { it > 0 }
                ?: with(density) { 52.dp.toPx().roundToInt() }
            return (activeTop + activeHeight / 2).coerceIn(0, scrollState.maxValue)
        }

        LaunchedEffect(
            lyrics.size,
            activeIndex,
            scrollState.maxValue,
            viewportHeightPx,
            userScrollSuspended
        ) {
            if (lyrics.isNotEmpty() && !userScrollSuspended) {
                scrollState.animateScrollTo(scrollTargetForIndex(activeIndex))
            }
        }

        LaunchedEffect(userScrollRequest, playing) {
            if (userScrollRequest > 0 && playing) {
                delay(3_000)
                userScrollSuspended = false
                scrollState.animateScrollTo(scrollTargetForIndex(activeIndex))
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .lyricsVerticalFade(
                    topEdgeHeight = 30.dp,
                    bottomEdgeHeight = 38.dp
                )
        ) {
            if (lyrics.isEmpty()) {
                if (lyricsLoaded) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            "暂无歌词",
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.32f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(manualScrollConnection)
                        .verticalScroll(scrollState)
                        .padding(horizontal = edgePadding)
                        .padding(top = activeAnchorFromTop, bottom = activeAnchorBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(lineSpacing)
                ) {
                    lyrics.forEachIndexed { index, line ->
                        val active = index == activeIndex
                        val nextTimeMs = lyrics.getOrNull(index + 1)?.timeMs
                        val lineEndMs = nextTimeMs ?: songDurationMs.takeIf { it > line.timeMs }
                        val lineDurationMs = ((lineEndMs ?: (line.timeMs + 4_200L)) - line.timeMs)
                            .coerceAtLeast(1_200L)
                        LyricsMarqueeLine(
                            text = line.text,
                            active = active,
                            scrolling = active && currentPositionMs >= line.timeMs,
                            durationMs = lineDurationMs,
                            elapsedMs = (currentPositionMs - line.timeMs).coerceAtLeast(0L),
                            translation = translatedLyrics[line.timeMs].orEmpty(),
                            translationVisible = translationsVisible,
                            activeLineShiftPx = activeLineShiftPx,
                            onClick = {
                                val seekDurationMs = max(
                                    1L,
                                    songDurationMs.takeIf { it > 0L }
                                        ?: ((lyrics.lastOrNull()?.timeMs ?: line.timeMs) + 1_000L)
                                )
                                userScrollSuspended = true
                                userScrollRequest += 1
                                onLyricSeek((line.timeMs.toFloat() / seekDurationMs.toFloat()).coerceIn(0f, 1f))
                            },
                            onHeightMeasured = { height ->
                                if (lineHeights.getOrNull(index) != height) {
                                    lineHeights[index] = height
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsMarqueeLine(
    text: String,
    active: Boolean,
    scrolling: Boolean,
    durationMs: Long,
    elapsedMs: Long,
    translation: String,
    translationVisible: Boolean,
    activeLineShiftPx: Float,
    onClick: () -> Unit,
    onHeightMeasured: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .onSizeChanged { size -> onHeightMeasured(size.height) }
    ) {
        val viewportWidthPx = constraints.maxWidth.toFloat()
        val leftInsetPx = with(density) { 12.dp.toPx() }
        var lineWidthPx by remember(text) { mutableFloatStateOf(0f) }
        val activeScale = 1.34f
        val effectiveLineWidthPx = lineWidthPx * if (active) activeScale else 1f
        val hiddenDistancePx = (effectiveLineWidthPx + leftInsetPx - viewportWidthPx)
            .coerceAtLeast(0f)
        val marqueeOffset = remember(text) { Animatable(0f) }
        val leftFadeProgress = with(density) {
            ((-marqueeOffset.value - 8.dp.toPx()) / 18.dp.toPx()).coerceIn(0f, 1f)
        }
        val lineScale by animateFloatAsState(
            targetValue = if (active) activeScale else 1f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
        val lineShift by animateFloatAsState(
            targetValue = if (active) activeLineShiftPx else 0f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
        val translationProgress by animateFloatAsState(
            targetValue = if (translationVisible && translation.isNotBlank()) 1f else 0f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
        val verticalPadding = if (active && hiddenDistancePx > 1f) {
            30.dp
        } else if (active) {
            18.dp
        } else {
            6.dp
        }

        LaunchedEffect(scrolling, text, hiddenDistancePx, durationMs) {
            if (scrolling && hiddenDistancePx > 1f) {
                val edgeReserve = with(density) { 10.dp.toPx() }
                val targetOffset = -(hiddenDistancePx + edgeReserve)
                val elapsedProgress = elapsedMs.toFloat() / durationMs.toFloat()
                val startingOffset = targetOffset * elapsedProgress.coerceIn(0f, 0.82f)
                marqueeOffset.snapTo(startingOffset)
                val remainingDuration = (durationMs - elapsedMs)
                    .coerceAtLeast(900L)
                    .coerceAtMost(12_000L)
                delay(180)
                marqueeOffset.animateTo(
                    targetValue = targetOffset,
                    animationSpec = tween(
                        durationMillis = (remainingDuration - 180L).coerceAtLeast(720L).toInt(),
                        easing = FastOutSlowInEasing
                    )
                )
            } else {
                marqueeOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .then(
                    if (hiddenDistancePx > 1f) {
                        Modifier.lyricsHorizontalFade(
                            edgeWidth = 28.dp,
                            leftFadeProgress = leftFadeProgress
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = verticalPadding)
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = leftInsetPx + marqueeOffset.value + lineShift
                        scaleX = lineScale
                        scaleY = lineScale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
            ) {
                BasicText(
                    text,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    onTextLayout = { result ->
                        lineWidthPx = if (result.lineCount > 0) result.getLineRight(0) else 0f
                    },
                    style = TextStyle(
                        color = Color.White.copy(alpha = if (active) 0.92f else 0.44f),
                        fontSize = 16.sp,
                        lineHeight = 34.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (translation.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .height(17.dp * translationProgress)
                            .clipToBounds()
                            .lyricsTranslationRevealFade()
                            .graphicsLayer {
                                alpha = translationProgress * if (active) 0.72f else 0.46f
                                translationY = (1f - translationProgress) * -4.dp.toPx()
                            }
                            .padding(top = 2.dp * translationProgress)
                    ) {
                        BasicText(
                            translation,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.lyricsVerticalFade(
    topEdgeHeight: Dp,
    bottomEdgeHeight: Dp
): Modifier =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        val topEdgePx = topEdgeHeight.toPx().coerceAtMost(size.height / 2f)
        val bottomEdgePx = bottomEdgeHeight.toPx().coerceAtMost(size.height / 2f)
        if ((topEdgePx > 0f || bottomEdgePx > 0f) && size.height > 0f) {
            val topStop = topEdgePx / size.height
            val bottomStop = bottomEdgePx / size.height
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        topStop to Color.Black,
                        (1f - bottomStop) to Color.Black,
                        1f to Color.Transparent
                    )
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

private fun Modifier.lyricsHorizontalFade(
    edgeWidth: Dp,
    leftFadeProgress: Float
): Modifier =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        val edgePx = edgeWidth.toPx().coerceAtMost(size.width / 2f)
        if (edgePx > 0f && size.width > 0f) {
            val edgeStop = edgePx / size.width
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 1f - leftFadeProgress.coerceIn(0f, 1f)),
                        edgeStop to Color.Black,
                        (1f - edgeStop) to Color.Black,
                        1f to Color.Transparent
                    )
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

private fun Modifier.lyricsTranslationRevealFade(): Modifier =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        val edgePx = 8.dp.toPx().coerceAtMost(size.height)
        if (edgePx > 0f && size.height > 0f) {
            val edgeStop = edgePx / size.height
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        edgeStop to Color.Black,
                        1f to Color.Black
                    )
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

@Composable
private fun PlayerTimeline(
    progress: Float,
    durationMs: Long,
    qualityLabel: String,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMs = durationMs.coerceAtLeast(0L)
    val remainingMs = (totalMs - (totalMs * progress.coerceIn(0f, 1f)).toLong())
        .coerceAtLeast(0L)
    Column(modifier) {
        ScrubbablePlayerLine(
            progress = progress,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth(),
            height = 4.dp
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(15.dp)
        ) {
            PlayerTimeText(
                text = "-${formatPlaybackDuration(remainingMs)}",
                modifier = Modifier.align(Alignment.CenterStart)
            )
            if (qualityLabel.isNotBlank()) {
                PlayerQualityText(
                    text = qualityLabel,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            PlayerTimeText(
                text = formatPlaybackDuration(totalMs),
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun PlayerTimeText(
    text: String,
    modifier: Modifier = Modifier
) {
    BasicText(
        text,
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(
            color = Color.White.copy(alpha = 0.52f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    )
}

@Composable
private fun PlayerQualityText(
    text: String,
    modifier: Modifier = Modifier
) {
    BasicText(
        text,
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 10.sp,
            fontFamily = rajdhaniSemiBoldFontFamily,
            fontWeight = FontWeight.SemiBold
        )
    )
}

@Composable
private fun PlayerVolumeControl(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.height(26.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VolumeGlyph(
            loud = false,
            modifier = Modifier.size(13.dp),
            color = Color.White.copy(alpha = 0.56f)
        )
        Spacer(Modifier.width(10.dp))
        ScrubbablePlayerLine(
            progress = volume,
            onSeek = onVolumeChange,
            modifier = Modifier.weight(1f),
            height = 3.dp
        )
        Spacer(Modifier.width(10.dp))
        VolumeGlyph(
            loud = true,
            modifier = Modifier.size(15.dp),
            color = Color.White.copy(alpha = 0.56f)
        )
    }
}

private fun formatPlaybackDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

@Composable
private fun ScrubbablePlayerLine(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp
) {
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    val animatedHeight by animateFloatAsState(
        targetValue = if (dragging) height.value + 1.1f else height.value,
        animationSpec = tween(durationMillis = 80)
    )

    BoxWithConstraints(
        modifier
            .height(20.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        PlayerLine(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
            height = animatedHeight.dp
        )
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(widthPx) {
                    fun seekTo(offsetX: Float) {
                        onSeek((offsetX / widthPx).coerceIn(0f, 1f))
                    }

                    detectDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            seekTo(offset.x)
                        },
                        onDragEnd = {
                            dragging = false
                        },
                        onDragCancel = {
                            dragging = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            seekTo(change.position.x)
                        }
                    )
                }
        )
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
private fun PlayerToggleIconButton(
    contentDescription: String,
    selected: Boolean,
    size: Dp,
    onClick: () -> Unit,
    pressScale: Float = 0.97f,
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
        Box(
            Modifier
                .size(width = 38.dp, height = 32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) {
                        Color.White.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    }
                )
        )
        content()
    }
}

@Composable
private fun StarGlyph(
    filled: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier) {
        val strokeWidth = 1.2.dp.toPx()
        val inset = if (filled) 0f else strokeWidth * 0.5f
        fun x(fraction: Float) = inset + (size.width - inset * 2f) * fraction
        fun y(fraction: Float) = inset + (size.height - inset * 2f) * fraction
        val path = Path().apply {
            moveTo(x(0.50f), y(0.12f))
            lineTo(x(0.61f), y(0.38f))
            lineTo(x(0.89f), y(0.40f))
            lineTo(x(0.68f), y(0.58f))
            lineTo(x(0.75f), y(0.86f))
            lineTo(x(0.50f), y(0.71f))
            lineTo(x(0.25f), y(0.86f))
            lineTo(x(0.32f), y(0.58f))
            lineTo(x(0.11f), y(0.40f))
            lineTo(x(0.39f), y(0.38f))
            close()
        }
        if (filled) {
            drawPath(path, color)
        } else {
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun MoreGlyph(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier) {
        val radius = size.minDimension * 0.085f
        listOf(0.22f, 0.50f, 0.78f).forEach { x ->
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(size.width * x, size.height * 0.50f)
            )
        }
    }
}

@Composable
private fun VolumeGlyph(
    loud: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier) {
        val speaker = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.40f)
            lineTo(size.width * 0.30f, size.height * 0.40f)
            lineTo(size.width * 0.52f, size.height * 0.22f)
            lineTo(size.width * 0.52f, size.height * 0.78f)
            lineTo(size.width * 0.30f, size.height * 0.60f)
            lineTo(size.width * 0.12f, size.height * 0.60f)
            close()
        }
        drawPath(speaker, color)
        val strokeWidth = size.minDimension * 0.09f
        drawArc(
            color = color,
            startAngle = -38f,
            sweepAngle = 76f,
            useCenter = false,
            topLeft = Offset(size.width * 0.38f, size.height * 0.33f),
            size = Size(size.width * 0.28f, size.height * 0.34f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        if (loud) {
            drawArc(
                color = color,
                startAngle = -42f,
                sweepAngle = 84f,
                useCenter = false,
                topLeft = Offset(size.width * 0.38f, size.height * 0.20f),
                size = Size(size.width * 0.48f, size.height * 0.60f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
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
            drawRect(
                color = color,
                topLeft = Offset(size.width * 0.33f, size.height * 0.24f),
                size = Size(size.width * 0.12f, size.height * 0.52f)
            )
            drawRect(
                color = color,
                topLeft = Offset(size.width * 0.55f, size.height * 0.24f),
                size = Size(size.width * 0.12f, size.height * 0.52f)
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
