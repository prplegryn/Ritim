package com.ritim.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

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

    Box(
        Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        RitimPageBackground(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        )

        RitimBottomBar(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { selectedTabIndex = it },
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun RitimPageBackground(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier
) {
    val visual = when (selectedTabIndex) {
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

    Box(
        modifier
            .background(Brush.verticalGradient(visual.gradient))
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

private data class PageVisual(
    val gradient: List<Color>,
    val line: Color,
    val accent: Color
)

@Composable
private fun RitimBottomBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFF0088FF)
    val containerColor = Color(0xFFFAFAFA).copy(0.4f)
    val navHeight = 64.dp

    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
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
            onClick = {},
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
            pressScale = 0.94f
        ) {
            SearchGlyph(
                modifier = Modifier.size(24.dp),
                color = Color(0xFF1E1E1E)
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
