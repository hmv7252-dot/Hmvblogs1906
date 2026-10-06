package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.LevelPlayManager
import com.example.security.RewardValidator
import com.example.security.SecurityManager
import com.example.ui.components.LevelPlayBannerAd
import com.example.ui.screens.CampaignMapScreen
import com.example.ui.screens.GameScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageSelectScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.screens.StatsScreen
import com.example.ui.theme.SlideMasterTheme
import com.example.viewmodel.PuzzleViewModel
import com.example.viewmodel.Screen

class MainActivity : ComponentActivity() {

    private val viewModel: PuzzleViewModel by viewModels()

    private fun showAdAndExecute(action: () -> Unit) {
        if (LevelPlayManager.canShowInterstitial()) {
            LevelPlayManager.showInterstitial(this@MainActivity) {
                action()
            }
        } else {
            LevelPlayManager.loadInterstitial()
            action()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pre-create WebView cache directory to eliminate Chromium directory enumeration warning
        try {
            val webViewJsCache = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!webViewJsCache.exists()) {
                webViewJsCache.mkdirs()
            }
        } catch (_: Exception) {
            // Non-fatal directory initialization
        }

        // Initialize Unity LevelPlay SDK exactly once at app launch
        LevelPlayManager.initialize(this)

        // Initialize Application Security & Anti-Cheat subsystems
        SecurityManager.initialize(this)

        lifecycleScope.launch {
            SecurityManager.securityAlerts.collect { alertMessage ->
                Toast.makeText(this@MainActivity, alertMessage, Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val tileTheme by viewModel.tileTheme.collectAsStateWithLifecycle()
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val gameState by viewModel.gameState.collectAsStateWithLifecycle()
            val isSfxEnabled by viewModel.isSfxEnabled.collectAsStateWithLifecycle()
            val isBgmEnabled by viewModel.isBgmEnabled.collectAsStateWithLifecycle()
            val totalStars by viewModel.totalStarsEarned.collectAsStateWithLifecycle()
            val completedLevelsCount by viewModel.completedLevelsCount.collectAsStateWithLifecycle()
            val levelProgressList by viewModel.allLevelProgress.collectAsStateWithLifecycle()
            val dailyRecords by viewModel.allDailyRecords.collectAsStateWithLifecycle()
            val boardSizeRecords by viewModel.allBoardSizeRecords.collectAsStateWithLifecycle()

            var showSettingsDialog by remember { mutableStateOf(false) }

            SlideMasterTheme(darkTheme = isDarkMode) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        // Banner ad on suitable non-gameplay screens without blocking controls
                        if (currentScreen != Screen.GAME) {
                            LevelPlayBannerAd()
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "screen_transition"
                        ) { screen ->
                            when (screen) {
                                Screen.HOME -> {
                                    HomeScreen(
                                        totalStars = totalStars,
                                        completedLevelsCount = completedLevelsCount,
                                        boardSizeRecords = boardSizeRecords,
                                        onStartCampaign = {
                                            val nextLvl = completedLevelsCount + 1
                                            showAdAndExecute {
                                                viewModel.startCampaignLevel(nextLvl)
                                            }
                                        },
                                        onStartEndless = {
                                            showAdAndExecute {
                                                viewModel.startEndlessMode(resetStreak = true)
                                            }
                                        },
                                        onStartDaily = {
                                            showAdAndExecute {
                                                viewModel.startDailyChallenge()
                                            }
                                        },
                                        onStartTimeAttack = { size, diff ->
                                            showAdAndExecute {
                                                viewModel.startTimeAttackMode(size, diff)
                                            }
                                        },
                                        onStartZen = { size, diff ->
                                            showAdAndExecute {
                                                viewModel.startZenMode(size, diff)
                                            }
                                        },
                                        onOpenImageSelect = { viewModel.navigateTo(Screen.IMAGE_SELECT) },
                                        onQuickPlay = { size, diff ->
                                            showAdAndExecute {
                                                viewModel.startQuickPlay(size, diff)
                                            }
                                        },
                                        onOpenCampaignMap = { viewModel.navigateTo(Screen.CAMPAIGN_MAP) },
                                        onOpenStats = { viewModel.navigateTo(Screen.STATS) },
                                        onOpenSettings = { showSettingsDialog = true }
                                    )
                                }
                                Screen.GAME -> {
                                    GameScreen(
                                        gameState = gameState,
                                        tileTheme = tileTheme,
                                        onTileClick = { index -> viewModel.moveTile(index) },
                                        onBackClick = {
                                            showAdAndExecute {
                                                viewModel.navigateTo(Screen.HOME)
                                            }
                                        },
                                        onPauseClick = { viewModel.togglePause() },
                                        onSettingsClick = { showSettingsDialog = true },
                                        onHintClick = {
                                            val activity = this@MainActivity
                                            if (LevelPlayManager.isRewardedReady()) {
                                                val rewardToken = RewardValidator.createRewardToken()
                                                LevelPlayManager.showRewarded(
                                                    activity = activity,
                                                    onReward = {
                                                        if (RewardValidator.validateRewardedAdClaim(rewardToken)) {
                                                            viewModel.requestHint()
                                                            Toast.makeText(activity, "Hint unlocked!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            SecurityManager.notifySecurityWarning("Reward verification failed.")
                                                        }
                                                    }
                                                )
                                            } else {
                                                LevelPlayManager.loadRewarded()
                                                Toast.makeText(activity, "Rewarded ad is loading, please try again in a moment...", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onUndoClick = { viewModel.undoMove() },
                                        onShuffleClick = {
                                            showAdAndExecute {
                                                viewModel.shuffleBoard()
                                            }
                                        },
                                        onRestartClick = {
                                            showAdAndExecute {
                                                viewModel.restartCurrentGame()
                                            }
                                        },
                                        onToggleNumberOverlay = { viewModel.toggleNumberOverlay() },
                                        onNextLevel = {
                                            showAdAndExecute {
                                                viewModel.nextLevel()
                                            }
                                        },
                                        onHome = {
                                            showAdAndExecute {
                                                viewModel.navigateTo(Screen.HOME)
                                            }
                                        },
                                        onBonusReward = {
                                            val token = RewardValidator.createRewardToken()
                                            if (RewardValidator.consumeRewardToken(token)) {
                                                viewModel.rewardBonusStars(50)
                                            }
                                        }
                                    )
                                }
                                Screen.CAMPAIGN_MAP -> {
                                    CampaignMapScreen(
                                        totalStars = totalStars,
                                        levelProgressList = levelProgressList,
                                        onSelectLevel = { levelNum ->
                                            showAdAndExecute {
                                                viewModel.startCampaignLevel(levelNum)
                                            }
                                        },
                                        onBack = { viewModel.navigateTo(Screen.HOME) }
                                    )
                                }
                                Screen.IMAGE_SELECT -> {
                                    ImageSelectScreen(
                                        onStartGame = { preset, bitmap, size, diff ->
                                            showAdAndExecute {
                                                viewModel.startImagePuzzle(preset, bitmap, size, diff)
                                            }
                                        },
                                        onBack = { viewModel.navigateTo(Screen.HOME) }
                                    )
                                }
                                Screen.STATS -> {
                                    StatsScreen(
                                        totalStars = totalStars,
                                        completedLevelsCount = completedLevelsCount,
                                        dailyRecords = dailyRecords,
                                        boardSizeRecords = boardSizeRecords,
                                        onBack = { viewModel.navigateTo(Screen.HOME) }
                                    )
                                }
                                Screen.SETTINGS -> {
                                    showSettingsDialog = true
                                    viewModel.navigateTo(Screen.HOME)
                                }
                            }
                        }

                        // Settings Dialog Modal
                        if (showSettingsDialog) {
                            SettingsDialog(
                                selectedTheme = tileTheme,
                                isDarkMode = isDarkMode,
                                isSfxEnabled = isSfxEnabled,
                                isBgmEnabled = isBgmEnabled,
                                onThemeSelect = { theme -> viewModel.setTheme(theme) },
                                onToggleDarkMode = { viewModel.toggleDarkMode() },
                                onToggleSfx = { viewModel.toggleSfx() },
                                onToggleBgm = { viewModel.toggleBgm() },
                                onDismiss = { showSettingsDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LevelPlayManager.onResume(this)
    }

    override fun onPause() {
        super.onPause()
        LevelPlayManager.onPause(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        LevelPlayManager.onDestroy(this)
    }
}
