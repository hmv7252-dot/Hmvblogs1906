package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.GameState
import com.example.model.TileTheme
import com.example.ui.components.BottomActionButtons
import com.example.ui.components.PuzzleBoardView
import com.example.ui.components.TopBarControls
import com.example.ui.components.VictoryDialog

@Composable
fun GameScreen(
    gameState: GameState,
    tileTheme: TileTheme,
    onTileClick: (Int) -> Unit,
    onBackClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHintClick: () -> Unit,
    onUndoClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRestartClick: () -> Unit,
    onToggleNumberOverlay: () -> Unit,
    onNextLevel: () -> Unit,
    onHome: () -> Unit,
    onBonusReward: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showPreviewDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar & Live Stats
            TopBarControls(
                gameState = gameState,
                onBackClick = onBackClick,
                onPauseClick = onPauseClick,
                onSettingsClick = onSettingsClick
            )

            // Center Puzzle Board
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                PuzzleBoardView(
                    gameState = gameState,
                    tileTheme = tileTheme,
                    onTileClick = onTileClick
                )

                // Paused Overlay
                if (gameState.isPaused && !gameState.isSolved) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .testTag("pause_overlay"),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "GAME PAUSED",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onPauseClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("resume_button")
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Resume Game")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onBackClick,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.Home, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Exit to Menu")
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Control Action Bar
            BottomActionButtons(
                gameState = gameState,
                onHintClick = onHintClick,
                onUndoClick = onUndoClick,
                onShuffleClick = onShuffleClick,
                onRestartClick = onRestartClick,
                onToggleNumberOverlay = onToggleNumberOverlay,
                onPreviewImageClick = { showPreviewDialog = true }
            )
        }

        // Victory Dialog when puzzle is solved
        if (gameState.isSolved) {
            VictoryDialog(
                gameState = gameState,
                onNextLevel = onNextLevel,
                onReplay = onRestartClick,
                onHome = onHome,
                onBonusReward = onBonusReward
            )
        }
    }
}
