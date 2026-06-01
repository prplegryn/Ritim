package com.ritim.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
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
    modifier: Modifier = Modifier
) {
    when (pageIndex) {
        0 -> HomePage(modifier)
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
    modifier: Modifier = Modifier
) {
    val sections = remember { sampleHomeSections() }
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
                HomeSection(section)
            }
        }
    }
}

@Composable
private fun HomeSection(section: HomeSectionData) {
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
                SongTile(item, tall = section.tallTiles)
            }
        }
    }
}

@Composable
private fun SongTile(
    item: SongSample,
    tall: Boolean
) {
    val tileWidth = if (tall) 124.dp else 112.dp
    Column(Modifier.width(tileWidth)) {
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
    val title: String,
    val artist: String,
    val colors: List<Color>,
    val seed: Int
)

private fun sampleHomeSections(): List<HomeSectionData> {
    val palettes = listOf(
        listOf(Color(0xFF7EC8E3), Color(0xFFE8F5E9)),
        listOf(Color(0xFFFFB4A2), Color(0xFFFFD6A5)),
        listOf(Color(0xFFB8C0FF), Color(0xFFEFD3D7)),
        listOf(Color(0xFF95D5B2), Color(0xFFD8F3DC)),
        listOf(Color(0xFFFFCAD4), Color(0xFFF4ACB7)),
        listOf(Color(0xFFA9DEF9), Color(0xFFE4C1F9)),
        listOf(Color(0xFFE9EDC9), Color(0xFFD4A373)),
        listOf(Color(0xFFCDB4DB), Color(0xFFFFC8DD))
    )
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
    val sectionNames = listOf("最近播放", "最近热门", "最近添加", "为你推荐", "继续收听")
    return sectionNames.mapIndexed { sectionIndex, title ->
        HomeSectionData(
            title = title,
            tallTiles = sectionIndex == 0,
            items = base.mapIndexed { itemIndex, pair ->
                SongSample(
                    title = pair.first,
                    artist = pair.second,
                    colors = palettes[(itemIndex + sectionIndex) % palettes.size],
                    seed = itemIndex + sectionIndex * 3
                )
            }
        )
    }
}

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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var playing by rememberSaveable { mutableStateOf(false) }
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
        Box(
            Modifier
                .size(40.dp)
                .drawBehind {
                    listOf(4.dp, 2.5.dp, 1.2.dp).forEachIndexed { index, spreadDp ->
                        val spread = spreadDp.toPx()
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.012f - index * 0.002f),
                            topLeft = Offset(-spread, -spread),
                            size = Size(size.width + spread * 2f, size.height + spread * 2f),
                            cornerRadius = CornerRadius(11.dp.toPx(), 11.dp.toPx())
                        )
                    }
                }
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFD8E8FF))
        )

        Column(
            Modifier
                .height(36.dp)
                .weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            BasicText(
                "Ritim Draft",
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
                "Liquid preview",
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
                onClick = { playing = !playing }
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

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offsetY
                    alpha = 0.90f + 0.10f * progress.value
                }
                .background(Color(0xFFFDFDFD))
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(width = 184.dp, height = 276.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFD8E8FF), Color(0xFFFFCAD4))
                            )
                        )
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.28f),
                            radius = size.minDimension * 0.26f,
                            center = Offset(size.width * 0.32f, size.height * 0.28f)
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.07f),
                            radius = size.minDimension * 0.38f,
                            center = Offset(size.width * 0.80f, size.height * 0.78f)
                        )
                    }
                }

                BasicText(
                    "Ritim Draft",
                    modifier = Modifier.padding(top = 26.dp),
                    style = TextStyle(
                        color = Color(0xFF111315),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                BasicText(
                    "Liquid preview",
                    modifier = Modifier.padding(top = 8.dp),
                    style = TextStyle(
                        color = Color(0xFF737B82),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
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
private fun PlayPauseGlyph(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF111315)
) {
    Canvas(modifier) {
        if (playing) {
            val strokeWidth = 4.dp.toPx()
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
