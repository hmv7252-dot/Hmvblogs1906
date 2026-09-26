package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameMode
import com.example.model.GameState
import java.util.Locale

@Composable
fun TopBarControls(
    gameState: GameState,
    onBackClick: () -> Unit,
    onPauseClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .testTag("back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when (gameState.gameMode) {
                        GameMode.CAMPAIGN -> gameState.campaignLevel?.title ?: "Campaign"
                        GameMode.ENDLESS -> "Endless (Stage ${gameState.endlessLevelNumber})"
                        GameMode.DAILY -> "Daily Challenge"
                        GameMode.TIME_ATTACK -> "Time Attack"
                        GameMode.ZEN -> "Zen Mode"
                        GameMode.IMAGE_PUZZLE -> "Image Puzzle"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "${gameState.boardSize.label} • ${gameState.difficulty.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPauseClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .testTag("pause_button")
                ) {
                    Icon(
                        imageVector = if (gameState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (gameState.isPaused) "Resume" else "Pause",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Stat Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timer Card (or Countdown for Time Attack)
            val isTimeAttack = (gameState.gameMode == GameMode.TIME_ATTACK)
            val timerText = if (isTimeAttack) {
                val sec = gameState.timeAttackRemainingSeconds
                String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
            } else {
                val sec = gameState.timerSeconds
                String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
            }

            StatBadge(
                icon = Icons.Default.AvTimer,
                title = if (isTimeAttack) "TIME LEFT" else "TIME",
                value = timerText,
                iconColor = if (isTimeAttack && gameState.timeAttackRemainingSeconds <= 10) Color(0xFFFF1744) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            // Move Counter Card
            StatBadge(
                icon = Icons.Default.ElectricBolt,
                title = "MOVES",
                value = "${gameState.moveCount}",
                iconColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )

            // Mode specific card: Star rating or Endless Score
            if (gameState.gameMode == GameMode.ENDLESS) {
                StatBadge(
                    icon = Icons.Default.Stars,
                    title = "SCORE",
                    value = "${gameState.endlessScore}",
                    iconColor = Color(0xFFFFD700),
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Star Rating
                val currentStars = calculateCurrentStars(gameState)
                StarsBadge(
                    stars = currentStars,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // "Perfect Solve" Live Efficiency Tracker Pill
        PerfectSolveTracker(
            optimalMoves = gameState.optimalMoves,
            currentMoves = gameState.moveCount,
            efficiency = gameState.efficiencyPercent
        )
    }
}

@Composable
private fun StatBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun StarsBadge(
    stars: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "RATING",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(horizontalArrangement = Arrangement.Center) {
                for (i in 1..3) {
                    Icon(
                        imageVector = if (i <= stars) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = null,
                        tint = if (i <= stars) Color(0xFFFFD700) else Color.Gray.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PerfectSolveTracker(
    optimalMoves: Int,
    currentMoves: Int,
    efficiency: Int
) {
    val efficiencyColor by animateColorAsState(
        targetValue = when {
            efficiency >= 95 -> Color(0xFF00E676)
            efficiency >= 75 -> Color(0xFFFFD700)
            else -> Color(0xFFFF9100)
        },
        label = "efficiency_color"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "💡 Optimal Target:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$optimalMoves moves",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Efficiency Pill
            Surface(
                color = efficiencyColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, efficiencyColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "$efficiency% Efficiency",
                    color = efficiencyColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

private fun calculateCurrentStars(gameState: GameState): Int {
    val optimal = gameState.optimalMoves
    if (optimal <= 0) return 3
    val moves = gameState.moveCount
    val ratio = if (moves == 0) 1.0f else (moves.toFloat() / optimal.toFloat())
    return when {
        ratio <= 1.25f -> 3
        ratio <= 1.8f -> 2
        else -> 1
    }
}
