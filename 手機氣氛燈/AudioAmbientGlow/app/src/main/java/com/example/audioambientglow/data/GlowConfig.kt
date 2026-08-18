package com.example.audioambientglow.data

enum class GlowDisplayMode(val displayName: String, val description: String) {
    ALWAYS_OVERLAY("全時音樂流光 (推薦)", "播放音樂時邊框持續流動狂飆，支援亮屏操作與息屏掛機 (推薦)"),
    AOD_ONLY("僅息屏 AOD 音樂模式", "僅在進入息屏 AOD 模式時呈現純黑邊框流光"),
    SCREEN_ON_ONLY("僅亮屏模式", "僅在手機亮屏解鎖使用時呈現邊框流光")
}

enum class GlowThemePreset(val displayName: String, val description: String) {
    CYBERPUNK("賽博龐克", "霓虹青 + 螢光粉 (經典雙色流光)"),
    SUNSET_GOLD("落日金光", "紫紅熾熱 + 耀眼橙金 (暖色流光)"),
    AURORA_BOREALIS("極光綠境", "深邃翡翠 + 夢幻幽紫 (極光夜空)"),
    SYNTHWAVE("復古合成波", "80s 復古粉 + 皇家電光藍 + 金黃"),
    RAINBOW_360("全彩光譜閉環", "360° 連續無縫漸變純色流光"),
    CRIMSON_PULSE("深紅脈動", "烈焰赤紅 + 熔岩琥珀 (重低音狂暴)"),
    CUSTOM("自訂多色", "自由調配專屬四角閉環色盤")
}

enum class AudioSourceType(val displayName: String, val description: String) {
    SYSTEM_MEDIA_PLAYBACK("系統音樂播放感知 (免麥克風)", "純系統媒體音訊感知，免開麥克風，無隱私指示燈干擾，通話零衝突 (推薦)")
}

data class GlowConfig(
    val isEnabled: Boolean = true,
    val displayMode: GlowDisplayMode = GlowDisplayMode.ALWAYS_OVERLAY,
    val glowThicknessDp: Float = 16f,
    val bloomFeatheringDp: Float = 28f,
    val cornerRadiusDp: Float = 36f,
    val baseSpeed: Float = 0.6f,
    val bassSpeedMultiplier: Float = 3.5f,
    val dynamicHueRangeDeg: Float = 60f,
    val attackTimeSeconds: Float = 0.9f,
    val decayTimeSeconds: Float = 1.1f,
    val noiseGateThreshold: Float = 0.06f,
    val themePreset: GlowThemePreset = GlowThemePreset.CYBERPUNK,
    val audioSourceType: AudioSourceType = AudioSourceType.SYSTEM_MEDIA_PLAYBACK,
    val dynamicHueShiftEnabled: Boolean = true,
    val amoledPureBlackBackground: Boolean = true, // AMOLED 0% power black canvas for AOD
    val antiBurnInEnabled: Boolean = true,         // Periodic pixel shifting for AMOLED protection
    val customColorA: Long = 0xFF00F5FFL,          // Neon Cyan
    val customColorB: Long = 0xFFFF007FL,          // Neon Pink
    val customColorC: Long = 0xFFFFD700L,          // Neon Gold
    val customColorD: Long = 0xFF7928CAL           // Neon Purple
)
