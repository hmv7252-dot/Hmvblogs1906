package com.example.model

enum class BoardSize(val dimension: Int, val label: String, val tag: String) {
    SIZE_3X3(3, "3 × 3", "3x3"),
    SIZE_4X4(4, "4 × 4", "4x4"),
    SIZE_5X5(5, "5 × 5", "5x5"),
    SIZE_6X6(6, "6 × 6", "6x6"),
    SIZE_7X7(7, "7 × 7", "7x7");

    val totalTiles: Int get() = dimension * dimension
    val emptyValue: Int get() = 0 // 0 represents the blank space

    companion object {
        fun fromDimension(dim: Int): BoardSize {
            return entries.find { it.dimension == dim } ?: SIZE_4X4
        }
    }
}

enum class Difficulty(val label: String, val baseShuffleMoves: Int, val iconName: String) {
    EASY("Easy", 15, "🟢"),
    MEDIUM("Medium", 35, "🟡"),
    HARD("Hard", 75, "🔴"),
    EXPERT("Expert", 150, "🟣")
}

enum class GameMode(
    val title: String,
    val description: String,
    val iconEmoji: String,
    val isTimed: Boolean,
    val hasStarRating: Boolean
) {
    CAMPAIGN(
        title = "Campaign",
        description = "1000+ hand-crafted chapters from Apprentice to Grandmaster",
        iconEmoji = "🗺️",
        isTimed = true,
        hasStarRating = true
    ),
    ENDLESS(
        title = "Endless",
        description = "Solve continuous puzzles with increasing difficulty & streaks",
        iconEmoji = "♾️",
        isTimed = true,
        hasStarRating = false
    ),
    DAILY(
        title = "Daily Challenge",
        description = "A unique puzzle every day. Keep your winning streak alive!",
        iconEmoji = "📅",
        isTimed = true,
        hasStarRating = true
    ),
    TIME_ATTACK(
        title = "Time Attack",
        description = "Race against the clock! Earn extra seconds for every solve",
        iconEmoji = "⚡",
        isTimed = true,
        hasStarRating = false
    ),
    ZEN(
        title = "Zen Mode",
        description = "No timer, no pressure. Relaxing ambient soundscapes & pure logic",
        iconEmoji = "🧘",
        isTimed = false,
        hasStarRating = false
    ),
    IMAGE_PUZZLE(
        title = "Image Puzzle",
        description = "Turn scenic illustrations or your custom photos into sliding art",
        iconEmoji = "🖼️",
        isTimed = true,
        hasStarRating = true
    )
}

enum class TileTheme(
    val displayName: String,
    val previewColorHex: Long,
    val isDark: Boolean
) {
    CYBER_NEON("Cyber Neon", 0xFF00E5FF, true),
    CLASSIC_WOOD("Walnut Wood", 0xFFB07238, false),
    OBSIDIAN_GOLD("Obsidian Gold", 0xFFFFD700, true),
    CANDY_PASTEL("Candy Pastel", 0xFFFF80AB, false),
    RETRO_ARCADE("Retro Arcade", 0xFF00E676, true),
    EMERALD_JADE("Emerald Jade", 0xFF1DE9B6, true)
}
