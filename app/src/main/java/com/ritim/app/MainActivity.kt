package com.ritim.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        HomeSurface(
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
private fun HomeSurface(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier
) {
    val sectionTitle = when (selectedTabIndex) {
        1 -> "音乐库"
        2 -> "我的"
        else -> "主页"
    }

    Column(
        modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF4F7F8),
                        Color(0xFFE9F0ED),
                        Color(0xFFF8F1F2)
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 22.dp)
            .padding(bottom = 108.dp)
    ) {
        BasicText(
            "Ritim",
            style = TextStyle(
                color = Color(0xFF111315),
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        Spacer(Modifier.height(10.dp))
        BasicText(
            sectionTitle,
            style = TextStyle(
                color = Color(0xFF687076),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(Modifier.height(28.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CoverTile(
                title = "Morning Pulse",
                gradient = listOf(Color(0xFF0088FF), Color(0xFFB6F1D7)),
                modifier = Modifier.weight(1f)
            )
            CoverTile(
                title = "City Tempo",
                gradient = listOf(Color(0xFFFF6B5E), Color(0xFFFFD166)),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CoverTile(
                title = "Soft Focus",
                gradient = listOf(Color(0xFF2D3142), Color(0xFFBFC0C0)),
                modifier = Modifier.weight(1f)
            )
            CoverTile(
                title = "Late Signals",
                gradient = listOf(Color(0xFF7B61FF), Color(0xFFEAF5F1)),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CoverTile(
    title: String,
    gradient: List<Color>,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(gradient))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                val left = size.width * 0.18f
                val right = size.width * 0.82f
                for (index in 0..4) {
                    val y = size.height * (0.32f + index * 0.09f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.24f + index * 0.04f),
                        start = Offset(left, y),
                        end = Offset(right, y + if (index % 2 == 0) 18.dp.toPx() else -14.dp.toPx()),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        BasicText(
            title,
            style = TextStyle(
                color = Color(0xFF171A1D),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun RitimBottomBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFF0088FF)
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
            tint = accentColor,
            blurRadius = 8.dp,
            refractionHeight = 18.dp,
            refractionAmount = 22.dp,
            height = navHeight,
            horizontalPadding = 0.dp
        ) {
            SearchGlyph(
                modifier = Modifier.size(24.dp),
                color = Color.White
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
