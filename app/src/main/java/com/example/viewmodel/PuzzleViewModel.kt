package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.entities.BoardSizeRecord
import com.example.data.entities.DailyRecord
import com.example.data.entities.GameStats
import com.example.data.entities.LevelProgress
import com.example.data.entities.PlayerSettings
import com.example.data.repository.PuzzleRepository
import com.example.engine.PuzzleGenerator
import com.example.engine.PuzzleSolver
import com.example.engine.SoundManager
import com.example.model.BoardSize
import com.example.model.CampaignLevel
import com.example.model.Difficulty
import com.example.model.GameMode
import com.example.model.GameState
import com.example.model.ImagePreset
import com.example.model.TileTheme
import com.example.security.AntiCheatManager
import com.example.security.ScoreValidator
import com.example.security.SecureStorageManager
import com.example.security.SecurityManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen {
    HOME,
    GAME,
    CAMPAIGN_MAP,
    IMAGE_SELECT,
    STATS,
    SETTINGS
}

class PuzzleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PuzzleRepository
    val soundManager = SoundManager()

    // Screen navigation
    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Game state
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    // Preferences & Settings
    private val _tileTheme = MutableStateFlow(TileTheme.CYBER_NEON)
    val tileTheme: StateFlow<TileTheme> = _tileTheme.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isSfxEnabled = MutableStateFlow(true)
    val isSfxEnabled: StateFlow<Boolean> = _isSfxEnabled.asStateFlow()

    private val _isBgmEnabled = MutableStateFlow(false)
    val isBgmEnabled: StateFlow<Boolean> = _isBgmEnabled.asStateFlow()

    private val _showNumberOverlay = MutableStateFlow(true)
    val showNumberOverlay: StateFlow<Boolean> = _showNumberOverlay.asStateFlow()

    // Database flows
    val allBoardSizeRecords: StateFlow<List<BoardSizeRecord>>
    val allLevelProgress: StateFlow<List<LevelProgress>>
    private val _bonusStars = MutableStateFlow(0)
    val totalStarsEarned: StateFlow<Int>
    val completedLevelsCount: StateFlow<Int>
    val allStats: StateFlow<List<GameStats>>
    val allDailyRecords: StateFlow<List<DailyRecord>>

    private var timerJob: Job? = null

    init {
        val db = AppDatabase.getDatabase(application)
        repository = PuzzleRepository(db.puzzleDao())

        allBoardSizeRecords = repository.allBoardSizeRecords.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        allLevelProgress = repository.allLevelProgress.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        totalStarsEarned = kotlinx.coroutines.flow.combine(
            repository.totalStars,
            _bonusStars
        ) { stars, bonus ->
            (stars ?: 0) + bonus
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        completedLevelsCount = repository.completedLevelsCount.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), 0
        )
        allStats = repository.allStats.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )
        allDailyRecords = repository.allDailyRecords.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
        )

        // Load securely stored bonus stars from encrypted vault
        val initialBonus = SecureStorageManager.getSecureInt("vault_bonus_stars", 0)
        _bonusStars.value = initialBonus

        // Load saved settings from Room database
        viewModelScope.launch {
            val savedSettings = repository.getPlayerSettings()
            if (savedSettings != null) {
                val theme = try {
                    TileTheme.valueOf(savedSettings.tileThemeName)
                } catch (e: Exception) {
                    TileTheme.CYBER_NEON
                }
                _tileTheme.value = theme
                _isDarkMode.value = savedSettings.isDarkMode
                _isSfxEnabled.value = savedSettings.isSfxEnabled
                _isBgmEnabled.value = savedSettings.isBgmEnabled
                _showNumberOverlay.value = savedSettings.showNumberOverlay

                soundManager.setSfxEnabled(savedSettings.isSfxEnabled)
                soundManager.setBgmEnabled(savedSettings.isBgmEnabled)
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // ----------------------------------------------------
    // GAME INITIALIZATION METHODS
    // ----------------------------------------------------

    fun startCampaignLevel(levelNumber: Int) {
        val completed = completedLevelsCount.value
        if (!ScoreValidator.validateCampaignLevelAccess(levelNumber, completed)) {
            SecurityManager.notifySecurityWarning("Campaign level $levelNumber is locked.")
            return
        }
        val campaignLevel = PuzzleGenerator.getCampaignLevel(levelNumber)
        val (board, optimal) = PuzzleGenerator.generateSolvableBoard(
            size = campaignLevel.boardSize,
            difficulty = campaignLevel.difficulty,
            seed = (levelNumber * 7919L)
        )

        _gameState.value = GameState(
            boardSize = campaignLevel.boardSize,
            difficulty = campaignLevel.difficulty,
            gameMode = GameMode.CAMPAIGN,
            tiles = board,
            emptyIndex = board.indexOf(0),
            moveCount = 0,
            timerSeconds = 0,
            isSolved = false,
            optimalMoves = optimal,
            initialBoard = board,
            moveHistory = emptyList(),
            campaignLevel = campaignLevel
        )
        startTimer()
        navigateTo(Screen.GAME)
    }

    fun startEndlessMode(resetStreak: Boolean = false) {
        val currentStreak = if (resetStreak) 0 else _gameState.value.endlessStreak
        val levelNum = if (resetStreak) 1 else _gameState.value.endlessLevelNumber + 1
        val currentScore = if (resetStreak) 0 else _gameState.value.endlessScore

        // Determine size & difficulty based on level
        val size = when {
            levelNum <= 3 -> BoardSize.SIZE_3X3
            levelNum <= 8 -> BoardSize.SIZE_4X4
            levelNum <= 15 -> BoardSize.SIZE_5X5
            levelNum <= 25 -> BoardSize.SIZE_6X6
            else -> BoardSize.SIZE_7X7
        }
        val difficulty = when {
            levelNum % 4 == 1 -> Difficulty.EASY
            levelNum % 4 == 2 -> Difficulty.MEDIUM
            levelNum % 4 == 3 -> Difficulty.HARD
            else -> Difficulty.EXPERT
        }

        val (board, optimal) = PuzzleGenerator.generateSolvableBoard(size, difficulty)

        _gameState.value = GameState(
            boardSize = size,
            difficulty = difficulty,
            gameMode = GameMode.ENDLESS,
            tiles = board,
            emptyIndex = board.indexOf(0),
            moveCount = 0,
            timerSeconds = 0,
            isSolved = false,
            optimalMoves = optimal,
            initialBoard = board,
            moveHistory = emptyList(),
            endlessLevelNumber = levelNum,
            endlessScore = currentScore,
            endlessStreak = currentStreak
        )
        startTimer()
        navigateTo(Screen.GAME)
    }

    fun startDailyChallenge() {
        val dateStr = PuzzleGenerator.getTodayDateString()
        val seed = PuzzleGenerator.getDailySeed(dateStr)
        val size = BoardSize.SIZE_4X4
        val difficulty = Difficulty.HARD

        val (board, optimal) = PuzzleGenerator.generateSolvableBoard(
            size = size,
            difficulty = difficulty,
            seed = seed
        )

        _gameState.value = GameState(
            boardSize = size,
            difficulty = difficulty,
            gameMode = GameMode.DAILY,
            tiles = board,
            emptyIndex = board.indexOf(0),
            moveCount = 0,
            timerSeconds = 0,
            isSolved = false,
            optimalMoves = optimal,
            initialBoard = board,
            moveHistory = emptyList(),
            isDailyChallenge = true,
            dailyDateString = dateStr
        )
        startTimer()
        navigateTo(Screen.GAME)
    }

    fun startTimeAttackMode(
        boardSize: BoardSize = BoardSize.SIZE_3X3,
        difficulty: Difficulty = Difficulty.MEDIUM
    ) {
        val (board, optimal) = PuzzleGenerator.generateSolvableBoard(boardSize, difficulty)
        val startingSeconds = when (boardSize) {
            BoardSize.SIZE_3X3 -> 45
            BoardSize.SIZE_4X4 -> 90
            BoardSize.SIZE_5X5 -> 150
            BoardSize.SIZE_6X6 -> 240
            BoardSize.SIZE_7X7 -> 360
        }

        _gameState.value = GameState(
            boardSize = boardSize,
            difficulty = difficulty,
            gameMode = GameMode.TIME_ATTACK,
            tiles = board,
            emptyIndex = board.indexOf(0),
            moveCount = 0,
            timerSeconds = 0,
            timeAttackRemainingSeconds = startingSeconds,
            isSolved = false,
            optimalMoves = optimal,
            initialBoard = board,
            moveHistory = emptyList()
        )
        startTimer()
        navigateTo(Screen.GAME)
    }

    fun startZenMode(
        boardSize: BoardSize = BoardSize.SIZE_4X4,
        difficulty: Difficulty = Difficulty.MEDIUM
    ) {
        val (board, optimal) = PuzzleGenerator.generateSolvableBoard(boardSize, difficulty)
        _gameState.value = GameState(
            boardSize = boardSize,
            difficulty = difficulty,
            gameMode = GameMode.ZEN,
            tiles = board,
            emptyIndex = board.indexOf(0),
            moveCount = 0,
            timerSeconds = 0,
            isSolved = false,
            optimalMoves = optimal,
            initialBoard = board,
            moveHistory = emptyList()
        )
        stopTimer()
        navigateTo(Screen.GAME)
    }

    fun startImagePuzzle(
        preset: ImagePreset? = null,
        customBitmap: Bitmap? = null,
        boardSize: BoardSize = BoardSize.SIZE_3X3,
        difficulty: Difficulty = Difficulty.MEDIUM
    ) {
        val (board, optimal) = PuzzleGenerator.generateSolvableBoard(boardSize, difficulty)
        _gameState.value = GameState(
            boardSize = boardSize,
            difficulty = difficulty,
            gameMode = GameMode.IMAGE_PUZZLE,
            tiles = board,
            emptyIndex = board.indexOf(0),
            moveCount = 0,
            timerSeconds = 0,
            isSolved = false,
            optimalMoves = optimal,
            initialBoard = board,
            moveHistory = emptyList(),
            selectedImagePreset = preset ?: PuzzleGenerator.PRESET_IMAGES[0],
            customImageBitmap = customBitmap,
            showNumberOverlay = _showNumberOverlay.value
        )
        startTimer()
        navigateTo(Screen.GAME)
    }

    fun startQuickPlay(boardSize: BoardSize, difficulty: Difficulty) {
        val (board, optimal) = PuzzleGenerator.generateSolvableBoard(boardSize, difficulty)
        _gameState.value = GameState(
            boardSize = boardSize,
            difficulty = difficulty,
            gameMode = GameMode.CAMPAIGN,
            tiles = board,
            emptyIndex = board.indexOf(0),
            moveCount = 0,
            timerSeconds = 0,
            isSolved = false,
            optimalMoves = optimal,
            initialBoard = board,
            moveHistory = emptyList()
        )
        startTimer()
        navigateTo(Screen.GAME)
    }

    // ----------------------------------------------------
    // IN-GAME INTERACTIONS
    // ----------------------------------------------------

    fun moveTile(tileIndex: Int) {
        val state = _gameState.value
        if (state.isSolved || state.isPaused) return

        val emptyIndex = state.emptyIndex
        val movableIndices = PuzzleSolver.getMovableTileIndices(state.tiles, state.boardSize)

        if (!movableIndices.contains(tileIndex)) return

        // Anti-cheat verification: validate physical adjacency and movement frequency
        if (!AntiCheatManager.validateMoveAttempt(state.tiles, emptyIndex, tileIndex, state.boardSize)) {
            return
        }

        // Sound effect
        val pitch = 0.9f + (tileIndex % state.boardSize.dimension) * 0.08f
        soundManager.playTileMove(pitch)

        // Make move
        val newTiles = state.tiles.toMutableList()
        newTiles[emptyIndex] = newTiles[tileIndex]
        newTiles[tileIndex] = 0

        val newHistory = state.moveHistory + listOf(state.tiles)
        val newMoveCount = state.moveCount + 1
        val solved = PuzzleSolver.isSolved(newTiles)

        // If Time Attack, add bonus seconds per move or solve
        var remainingSeconds = state.timeAttackRemainingSeconds
        if (state.gameMode == GameMode.TIME_ATTACK && solved) {
            remainingSeconds += 20
        }

        var starRating = 0
        if (solved) {
            stopTimer()
            starRating = calculateStarRating(state, newMoveCount)
            handleGameVictory(state, newMoveCount, state.timerSeconds, starRating)
        }

        _gameState.value = state.copy(
            tiles = newTiles,
            emptyIndex = tileIndex,
            moveCount = newMoveCount,
            moveHistory = newHistory,
            isSolved = solved,
            starRatingEarned = starRating,
            hintTileIndex = null,
            hintTargetIndex = null,
            timeAttackRemainingSeconds = remainingSeconds
        )
    }

    private fun calculateStarRating(state: GameState, moves: Int): Int {
        val optimal = state.optimalMoves
        if (optimal <= 0) return 3
        val ratio = moves.toFloat() / optimal.toFloat()
        return when {
            ratio <= 1.25f -> 3
            ratio <= 1.8f -> 2
            else -> 1
        }
    }

    private fun handleGameVictory(
        state: GameState,
        moves: Int,
        timeSeconds: Int,
        stars: Int
    ) {
        val validStars = ScoreValidator.validateStarRating(stars)

        // Comprehensive AntiCheat validation before recording scores & stars
        if (!AntiCheatManager.validateGameCompletion(state, moves, timeSeconds, validStars)) {
            SecurityManager.notifySecurityWarning("Game victory could not be validated.")
            return
        }

        val isPerfect = moves <= state.optimalMoves
        soundManager.playVictoryFanfare(isPerfect)

        viewModelScope.launch {
            // Save specific board size record (best time, best moves, win stats)
            repository.recordBoardSolve(
                boardSize = state.boardSize,
                moves = moves,
                timeSeconds = timeSeconds,
                isPerfect = isPerfect
            )

            // Save game mode overall stats
            repository.recordGameModeWin(
                modeKey = state.gameMode.name,
                moves = moves,
                timeSeconds = timeSeconds,
                isPerfect = isPerfect,
                endlessStreak = state.endlessStreak
            )

            when (state.gameMode) {
                GameMode.CAMPAIGN -> {
                    state.campaignLevel?.let { level ->
                        repository.saveLevelProgress(
                            levelId = level.levelNumber,
                            stars = stars,
                            moves = moves,
                            timeSeconds = timeSeconds
                        )
                    }
                }
                GameMode.DAILY -> {
                    repository.saveDailyRecord(
                        DailyRecord(
                            dateString = state.dailyDateString,
                            moves = moves,
                            timeSeconds = timeSeconds,
                            stars = stars,
                            isPerfectSolve = isPerfect
                        )
                    )
                }
                GameMode.ENDLESS -> {
                    val streakBonus = (state.endlessStreak + 1) * 100
                    val moveBonus = maxOf(0, (state.optimalMoves * 2 - moves) * 10)
                    val newScore = state.endlessScore + 500 + streakBonus + moveBonus
                    _gameState.update {
                        it.copy(
                            endlessScore = newScore,
                            endlessStreak = it.endlessStreak + 1
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun undoMove() {
        val state = _gameState.value
        if (state.moveHistory.isEmpty() || state.isSolved) return

        soundManager.playUndo()
        val previousTiles = state.moveHistory.last()
        val updatedHistory = state.moveHistory.dropLast(1)

        _gameState.value = state.copy(
            tiles = previousTiles,
            emptyIndex = previousTiles.indexOf(0),
            moveCount = maxOf(0, state.moveCount - 1),
            moveHistory = updatedHistory,
            hintTileIndex = null,
            hintTargetIndex = null
        )
    }

    fun requestHint() {
        val state = _gameState.value
        if (state.isSolved) return

        val bestMove = PuzzleSolver.findBestNextMove(state.tiles, state.boardSize)
        if (bestMove != null) {
            soundManager.playHint()
            _gameState.value = state.copy(
                hintTileIndex = bestMove,
                hintTargetIndex = state.emptyIndex
            )
        }
    }

    fun rewardBonusStars(amount: Int) {
        val validated = AntiCheatManager.validateBonusStarAward(amount)
        if (validated > 0) {
            val updated = _bonusStars.value + validated
            _bonusStars.value = updated
            SecureStorageManager.putSecureInt("vault_bonus_stars", updated)
            soundManager.playHint()
        }
    }

    fun executeRewardedHint() {
        requestHint()
        rewardBonusStars(10)
    }

    fun shuffleBoard() {
        val state = _gameState.value
        soundManager.playShuffle()
        val (board, optimal) = PuzzleGenerator.generateSolvableBoard(
            size = state.boardSize,
            difficulty = state.difficulty
        )

        _gameState.value = state.copy(
            tiles = board,
            emptyIndex = board.indexOf(0),
            moveCount = 0,
            timerSeconds = 0,
            isSolved = false,
            optimalMoves = optimal,
            initialBoard = board,
            moveHistory = emptyList(),
            hintTileIndex = null,
            hintTargetIndex = null
        )
        startTimer()
    }

    fun restartCurrentGame() {
        val state = _gameState.value
        if (state.initialBoard.isEmpty()) return

        soundManager.playShuffle()
        _gameState.value = state.copy(
            tiles = state.initialBoard,
            emptyIndex = state.initialBoard.indexOf(0),
            moveCount = 0,
            timerSeconds = 0,
            isSolved = false,
            moveHistory = emptyList(),
            hintTileIndex = null,
            hintTargetIndex = null
        )
        startTimer()
    }

    fun nextLevel() {
        val state = _gameState.value
        when (state.gameMode) {
            GameMode.CAMPAIGN -> {
                val nextLvl = (state.campaignLevel?.levelNumber ?: 1) + 1
                startCampaignLevel(nextLvl)
            }
            GameMode.ENDLESS -> {
                startEndlessMode(resetStreak = false)
            }
            else -> {
                startQuickPlay(state.boardSize, state.difficulty)
            }
        }
    }

    fun togglePause() {
        val state = _gameState.value
        val newPaused = !state.isPaused
        _gameState.value = state.copy(isPaused = newPaused)
        if (newPaused) {
            stopTimer()
        } else {
            startTimer()
        }
    }

    // ----------------------------------------------------
    // SETTINGS / THEME CONTROLS
    // ----------------------------------------------------

    fun setTheme(theme: TileTheme) {
        _tileTheme.value = theme
        persistSettings()
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
        persistSettings()
    }

    fun toggleSfx() {
        val newSfx = !_isSfxEnabled.value
        _isSfxEnabled.value = newSfx
        soundManager.setSfxEnabled(newSfx)
        persistSettings()
    }

    fun toggleBgm() {
        val newBgm = !_isBgmEnabled.value
        _isBgmEnabled.value = newBgm
        soundManager.setBgmEnabled(newBgm)
        persistSettings()
    }

    fun toggleNumberOverlay() {
        val newOverlay = !_showNumberOverlay.value
        _showNumberOverlay.value = newOverlay
        _gameState.update { it.copy(showNumberOverlay = newOverlay) }
        persistSettings()
    }

    private fun persistSettings() {
        viewModelScope.launch {
            repository.savePlayerSettings(
                PlayerSettings(
                    id = 1,
                    tileThemeName = _tileTheme.value.name,
                    isDarkMode = _isDarkMode.value,
                    isSfxEnabled = _isSfxEnabled.value,
                    isBgmEnabled = _isBgmEnabled.value,
                    showNumberOverlay = _showNumberOverlay.value
                )
            )
        }
    }

    fun setSelectedImagePreset(preset: ImagePreset) {
        _gameState.update {
            it.copy(selectedImagePreset = preset, customImageBitmap = null)
        }
    }

    fun setCustomImageBitmap(bitmap: Bitmap?) {
        _gameState.update {
            it.copy(customImageBitmap = bitmap)
        }
    }

    // ----------------------------------------------------
    // TIMER ENGINE
    // ----------------------------------------------------

    private fun startTimer() {
        stopTimer()
        AntiCheatManager.onNewGameStarted()
        if (_gameState.value.gameMode == GameMode.ZEN) return

        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val state = _gameState.value
                if (!state.isPaused && !state.isSolved) {
                    if (state.gameMode == GameMode.TIME_ATTACK) {
                        val remaining = state.timeAttackRemainingSeconds - 1
                        if (remaining <= 0) {
                            _gameState.value = state.copy(
                                timeAttackRemainingSeconds = 0,
                                isSolved = false,
                                isPaused = true
                            )
                            stopTimer()
                        } else {
                            _gameState.value = state.copy(
                                timerSeconds = state.timerSeconds + 1,
                                timeAttackRemainingSeconds = remaining
                            )
                        }
                    } else {
                        _gameState.value = state.copy(
                            timerSeconds = state.timerSeconds + 1
                        )
                    }
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        soundManager.stopAmbientBgm()
    }
}
