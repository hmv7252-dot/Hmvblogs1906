package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BoardSize
import com.example.model.GameMode
import com.example.model.GameState
import com.example.model.ImagePreset
import com.example.model.TileTheme
import kotlin.math.roundToInt

@Composable
fun PuzzleBoardView(
    gameState: GameState,
    tileTheme: TileTheme,
    onTileClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val boardSize = gameState.boardSize
    val n = boardSize.dimension
    val tiles = gameState.tiles

    // Infinite transition for hint pulse
    val infiniteTransition = rememberInfiniteTransition(label = "hint_pulse")
    val hintScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hint_scale"
    )

    val hintGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hint_alpha"
    )

    // Board container styling based on theme
    val boardBorderColor = when (tileTheme) {
        TileTheme.CYBER_NEON -> Color(0xFF00E5FF).copy(alpha = 0.6f)
        TileTheme.CLASSIC_WOOD -> Color(0xFF6D4C41)
        TileTheme.OBSIDIAN_GOLD -> Color(0xFFFFD700).copy(alpha = 0.7f)
        TileTheme.CANDY_PASTEL -> Color(0xFFFF80AB).copy(alpha = 0.5f)
        TileTheme.RETRO_ARCADE -> Color(0xFF00E676).copy(alpha = 0.8f)
        TileTheme.EMERALD_JADE -> Color(0xFF1DE9B6).copy(alpha = 0.6f)
    }

    val boardBgBrush = when (tileTheme) {
        TileTheme.CYBER_NEON -> Brush.verticalGradient(listOf(Color(0xFF0B0E1A), Color(0xFF141829)))
        TileTheme.CLASSIC_WOOD -> Brush.verticalGradient(listOf(Color(0xFF2E1C14), Color(0xFF1D110C)))
        TileTheme.OBSIDIAN_GOLD -> Brush.verticalGradient(listOf(Color(0xFF0D0D12), Color(0xFF15151C)))
        TileTheme.CANDY_PASTEL -> Brush.verticalGradient(listOf(Color(0xFFFFF0F5), Color(0xFFFFE4E1)))
        TileTheme.RETRO_ARCADE -> Brush.verticalGradient(listOf(Color(0xFF001100), Color(0xFF051D05)))
        TileTheme.EMERALD_JADE -> Brush.verticalGradient(listOf(Color(0xFF021B1A), Color(0xFF062E2C)))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(12.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp), spotColor = boardBorderColor)
            .clip(RoundedCornerShape(20.dp))
            .background(boardBgBrush)
            .border(2.5.dp, boardBorderColor, RoundedCornerShape(20.dp))
            .padding(8.dp)
            .testTag("puzzle_board_container")
    ) {
        val boardWidthPx = constraints.maxWidth.toFloat()
        val tileSizePx = boardWidthPx / n
        val tileGapDp = if (n >= 6) 3.dp else 5.dp

        // Render each slot or animated tile
        for (index in tiles.indices) {
            val tileValue = tiles[index]
            val row = index / n
            val col = index % n

            val isHint = (index == gameState.hintTileIndex)

            if (tileValue != 0) {
                // Determine target correct position for image puzzle slicing
                val originalRow = (tileValue - 1) / n
                val originalCol = (tileValue - 1) % n

                TileItemView(
                    tileValue = tileValue,
                    boardSize = boardSize,
                    tileTheme = tileTheme,
                    isHint = isHint,
                    hintScale = if (isHint) hintScale else 1f,
                    hintGlowAlpha = if (isHint) hintGlowAlpha else 0f,
                    gameMode = gameState.gameMode,
                    showNumberOverlay = gameState.showNumberOverlay,
                    selectedPreset = gameState.selectedImagePreset,
                    customBitmap = gameState.customImageBitmap,
                    originalRow = originalRow,
                    originalCol = originalCol,
                    onClick = { onTileClick(index) },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (col * tileSizePx).roundToInt(),
                                (row * tileSizePx).roundToInt()
                            )
                        }
                        .size(
                            width = (maxWidth / n) - (tileGapDp * 0.5f),
                            height = (maxHeight / n) - (tileGapDp * 0.5f)
                        )
                        .padding(tileGapDp * 0.5f)
                )
            } else {
                // Empty slot subtle inset indicator
                EmptySlotView(
                    tileTheme = tileTheme,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (col * tileSizePx).roundToInt(),
                                (row * tileSizePx).roundToInt()
                            )
                        }
                        .size(
                            width = (maxWidth / n) - (tileGapDp * 0.5f),
                            height = (maxHeight / n) - (tileGapDp * 0.5f)
                        )
                        .padding(tileGapDp * 0.5f)
                )
            }
        }
    }
}

@Composable
fun TileItemView(
    tileValue: Int,
    boardSize: BoardSize,
    tileTheme: TileTheme,
    isHint: Boolean,
    hintScale: Float,
    hintGlowAlpha: Float,
    gameMode: GameMode,
    showNumberOverlay: Boolean,
    selectedPreset: ImagePreset?,
    customBitmap: Bitmap?,
    originalRow: Int,
    originalCol: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerRadius = when {
        boardSize.dimension >= 6 -> 8.dp
        boardSize.dimension == 5 -> 12.dp
        else -> 16.dp
    }

    val fontSize = when {
        boardSize.dimension >= 6 -> 18.sp
        boardSize.dimension == 5 -> 24.sp
        boardSize.dimension == 4 -> 30.sp
        else -> 38.sp
    }

    val isImageMode = (gameMode == GameMode.IMAGE_PUZZLE)

    Box(
        modifier = modifier
            .testTag("tile_${tileValue}")
            .shadow(
                elevation = if (isHint) 10.dp else 4.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = if (isHint) Color.Yellow else Color.Black
            )
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isImageMode) {
            // Render Image slice (Preset art or Custom photo bitmap)
            ImageTileSlice(
                boardDimension = boardSize.dimension,
                originalRow = originalRow,
                originalCol = originalCol,
                preset = selectedPreset,
                customBitmap = customBitmap,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Render Themed Tile Background
            ThemedTileBackground(
                tileValue = tileValue,
                tileTheme = tileTheme,
                boardSize = boardSize,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Hint Glow Border
        if (isHint) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        3.dp,
                        Color(0xFFFFEA00).copy(alpha = hintGlowAlpha),
                        RoundedCornerShape(cornerRadius)
                    )
            )
        }

        // Tile Number (always shown in standard modes, toggled in image mode)
        if (!isImageMode || showNumberOverlay) {
            ThemedTileText(
                tileValue = tileValue,
                tileTheme = tileTheme,
                fontSize = fontSize,
                isImageMode = isImageMode
            )
        }
    }
}

@Composable
private fun ThemedTileBackground(
    tileValue: Int,
    tileTheme: TileTheme,
    boardSize: BoardSize,
    modifier: Modifier = Modifier
) {
    when (tileTheme) {
        TileTheme.CYBER_NEON -> {
            val hueOffset = (tileValue * 25) % 360
            val gradient = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF1E2640),
                    Color(0xFF14192C)
                )
            )
            Box(
                modifier = modifier
                    .background(gradient)
                    .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            )
        }
        TileTheme.CLASSIC_WOOD -> {
            val woodShade = if (tileValue % 2 == 0) Color(0xFF8D5B2F) else Color(0xFF794B24)
            Box(
                modifier = modifier
                    .background(
                        Brush.linearGradient(
                            listOf(woodShade, woodShade.copy(alpha = 0.85f))
                        )
                    )
                    .border(1.5.dp, Color(0xFFD7CCC8).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            )
        }
        TileTheme.OBSIDIAN_GOLD -> {
            Box(
                modifier = modifier
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF2B2B38), Color(0xFF161620))
                        )
                    )
                    .border(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            )
        }
        TileTheme.CANDY_PASTEL -> {
            val candyColors = listOf(
                listOf(Color(0xFFFF80AB), Color(0xFFFF4081)),
                listOf(Color(0xFF80D8FF), Color(0xFF40C4FF)),
                listOf(Color(0xFFA7FFEB), Color(0xFF64FFDA)),
                listOf(Color(0xFFFFD180), Color(0xFFFFAB40)),
                listOf(Color(0xFFEA80FC), Color(0xFFE040FB))
            )
            val pair = candyColors[tileValue % candyColors.size]
            Box(
                modifier = modifier
                    .background(Brush.linearGradient(pair))
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            )
        }
        TileTheme.RETRO_ARCADE -> {
            Box(
                modifier = modifier
                    .background(Color(0xFF002200))
                    .border(2.dp, Color(0xFF00E676), RoundedCornerShape(8.dp))
            )
        }
        TileTheme.EMERALD_JADE -> {
            Box(
                modifier = modifier
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF00897B), Color(0xFF004D40))
                        )
                    )
                    .border(1.5.dp, Color(0xFF80CBC4).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
private fun ThemedTileText(
    tileValue: Int,
    tileTheme: TileTheme,
    fontSize: androidx.compose.ui.unit.TextUnit,
    isImageMode: Boolean
) {
    if (isImageMode) {
        // High contrast badge for image mode overlay
        Surface(
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(2.dp)
        ) {
            Text(
                text = "$tileValue",
                color = Color.White,
                fontSize = fontSize * 0.75f,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        return
    }

    val textColor = when (tileTheme) {
        TileTheme.CYBER_NEON -> Color(0xFF00E5FF)
        TileTheme.CLASSIC_WOOD -> Color(0xFFFFECB3)
        TileTheme.OBSIDIAN_GOLD -> Color(0xFFFFD700)
        TileTheme.CANDY_PASTEL -> Color.White
        TileTheme.RETRO_ARCADE -> Color(0xFF00E676)
        TileTheme.EMERALD_JADE -> Color(0xFFE0F2F1)
    }

    Text(
        text = "$tileValue",
        color = textColor,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        fontFamily = if (tileTheme == TileTheme.RETRO_ARCADE) FontFamily.Monospace else FontFamily.Default,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ImageTileSlice(
    boardDimension: Int,
    originalRow: Int,
    originalCol: Int,
    preset: ImagePreset?,
    customBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    if (customBitmap != null) {
        // Draw cropped portion of custom user bitmap
        Canvas(modifier = modifier) {
            val srcWidth = customBitmap.width.toFloat() / boardDimension
            val srcHeight = customBitmap.height.toFloat() / boardDimension
            val srcLeft = (originalCol * srcWidth).toInt()
            val srcTop = (originalRow * srcHeight).toInt()

            val srcW = srcWidth.toInt().coerceAtMost(customBitmap.width - srcLeft)
            val srcH = srcHeight.toInt().coerceAtMost(customBitmap.height - srcTop)

            if (srcW > 0 && srcH > 0) {
                try {
                    val cropped = Bitmap.createBitmap(customBitmap, srcLeft, srcTop, srcW, srcH)
                    drawImage(
                        image = cropped.asImageBitmap(),
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                    )
                } catch (_: Exception) {}
            }
        }
    } else {
        // Draw rich procedural geometric scene for preset
        val gradientColors = preset?.colorsGradient?.map { Color(it) }
            ?: listOf(Color(0xFF7B1FA2), Color(0xFF512DA8), Color(0xFF00E5FF))

        Canvas(modifier = modifier) {
            // Draw gradient slice corresponding to tile coordinate
            val totalW = size.width * boardDimension
            val totalH = size.height * boardDimension
            val startOffset = Offset(
                x = -(originalCol * size.width),
                y = -(originalRow * size.height)
            )

            drawRect(
                brush = Brush.linearGradient(
                    colors = gradientColors,
                    start = startOffset,
                    end = Offset(startOffset.x + totalW, startOffset.y + totalH)
                )
            )

            // Decorative geometric accents for distinct visual identity
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = minOf(size.width, size.height) * 0.35f,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
private fun EmptySlotView(
    tileTheme: TileTheme,
    modifier: Modifier = Modifier
) {
    val slotBorderColor = when (tileTheme) {
        TileTheme.CYBER_NEON -> Color(0xFF00E5FF).copy(alpha = 0.15f)
        TileTheme.CLASSIC_WOOD -> Color(0xFF3E2723).copy(alpha = 0.4f)
        TileTheme.OBSIDIAN_GOLD -> Color(0xFFFFD700).copy(alpha = 0.15f)
        TileTheme.CANDY_PASTEL -> Color(0xFFFF80AB).copy(alpha = 0.15f)
        TileTheme.RETRO_ARCADE -> Color(0xFF00E676).copy(alpha = 0.15f)
        TileTheme.EMERALD_JADE -> Color(0xFF1DE9B6).copy(alpha = 0.15f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .border(1.dp, slotBorderColor, RoundedCornerShape(12.dp))
    )
}
