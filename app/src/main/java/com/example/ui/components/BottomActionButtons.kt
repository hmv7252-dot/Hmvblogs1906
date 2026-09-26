package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameMode
import com.example.model.GameState

@Composable
fun BottomActionButtons(
    gameState: GameState,
    onHintClick: () -> Unit,
    onUndoClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRestartClick: () -> Unit,
    onToggleNumberOverlay: () -> Unit,
    onPreviewImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canUndo = gameState.moveHistory.isNotEmpty() && !gameState.isSolved
    val isImageMode = (gameState.gameMode == GameMode.IMAGE_PUZZLE)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hint Button
            ActionButtonItem(
                icon = Icons.Default.Lightbulb,
                label = "Hint",
                tint = Color(0xFFFFD600),
                enabled = !gameState.isSolved,
                onClick = onHintClick,
                testTag = "hint_action_button",
                badgeText = "AD"
            )

            // Undo Button
            ActionButtonItem(
                icon = Icons.AutoMirrored.Filled.Undo,
                label = "Undo",
                tint = MaterialTheme.colorScheme.primary,
                enabled = canUndo,
                onClick = onUndoClick,
                testTag = "undo_action_button"
            )

            // Shuffle Button
            ActionButtonItem(
                icon = Icons.Default.Shuffle,
                label = "Shuffle",
                tint = MaterialTheme.colorScheme.secondary,
                enabled = !gameState.isSolved,
                onClick = onShuffleClick,
                testTag = "shuffle_action_button"
            )

            // Restart Button
            ActionButtonItem(
                icon = Icons.Default.Refresh,
                label = "Restart",
                tint = MaterialTheme.colorScheme.tertiary,
                enabled = gameState.moveCount > 0,
                onClick = onRestartClick,
                testTag = "restart_action_button"
            )

            // Optional Image Mode controls
            if (isImageMode) {
                ActionButtonItem(
                    icon = Icons.Default.Pin,
                    label = if (gameState.showNumberOverlay) "Hide #" else "Show #",
                    tint = Color(0xFF00E5FF),
                    enabled = true,
                    onClick = onToggleNumberOverlay,
                    testTag = "toggle_numbers_button"
                )
            }
        }
    }
}

@Composable
private fun ActionButtonItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    badgeText: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) tint.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f)
                    )
                    .testTag(testTag)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (enabled) tint else Color.Gray.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }

            if (badgeText != null && enabled) {
                Surface(
                    color = Color(0xFFEF4444),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(top = 1.dp, end = 1.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray
        )
    }
}
