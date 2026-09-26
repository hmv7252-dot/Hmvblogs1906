package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.model.BoardSize
import com.example.model.CampaignLevel
import com.example.model.Difficulty
import com.example.model.GameMode
import com.example.model.GameState
import com.example.model.TileTheme
import com.example.ui.screens.GameScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsCard
import com.example.ui.theme.SlideMasterTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      SlideMasterTheme {
        HomeScreen(
          totalStars = 45,
          completedLevelsCount = 15,
          onStartCampaign = {},
          onStartEndless = {},
          onStartDaily = {},
          onStartTimeAttack = { _, _ -> },
          onStartZen = { _, _ -> },
          onOpenImageSelect = {},
          onQuickPlay = { _, _ -> },
          onOpenCampaignMap = {},
          onOpenStats = {},
          onOpenSettings = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }

  @Test
  fun campaign_settings_initialized_screenshot() {
    composeTestRule.setContent {
      SlideMasterTheme(darkTheme = false) {
        Box(modifier = Modifier.fillMaxSize()) {
          GameScreen(
            gameState = GameState(
              boardSize = BoardSize.SIZE_3X3,
              difficulty = Difficulty.MEDIUM,
              gameMode = GameMode.CAMPAIGN,
              moveCount = 0,
              timerSeconds = 26,
              optimalMoves = 24,
              tiles = listOf(1, 2, 3, 4, 5, 6, 7, 8, 0),
              campaignLevel = CampaignLevel(
                levelNumber = 1,
                boardSize = BoardSize.SIZE_3X3,
                difficulty = Difficulty.MEDIUM,
                targetMoves3Stars = 24,
                targetMoves2Stars = 35,
                title = "Level 1",
                chapterName = "Beginner"
              )
            ),
            tileTheme = TileTheme.CYBER_NEON,
            onTileClick = {},
            onBackClick = {},
            onPauseClick = {},
            onSettingsClick = {},
            onHintClick = {},
            onUndoClick = {},
            onShuffleClick = {},
            onRestartClick = {},
            onToggleNumberOverlay = {},
            onNextLevel = {},
            onHome = {}
          )

          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.Black.copy(alpha = 0.5f))
          )

          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 8.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
          ) {
            SettingsCard(
              selectedTheme = TileTheme.CYBER_NEON,
              isDarkMode = false,
              isSfxEnabled = true,
              isBgmEnabled = false,
              onThemeSelect = {},
              onToggleDarkMode = {},
              onToggleSfx = {},
              onToggleBgm = {},
              onDismiss = {}
            )
          }
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/campaign_settings_initialized.png")
  }
}
