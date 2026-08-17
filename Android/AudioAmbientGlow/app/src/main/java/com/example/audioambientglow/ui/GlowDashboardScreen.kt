package com.example.audioambientglow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.audioambientglow.audio.AudioFeatures
import com.example.audioambientglow.data.GlowConfig
import com.example.audioambientglow.data.GlowDisplayMode
import com.example.audioambientglow.data.GlowThemePreset
import com.example.audioambientglow.service.GlowTrackView
import com.example.audioambientglow.service.MediaTrackInfo
import com.example.audioambientglow.ui.components.GlassCard
import com.example.audioambientglow.ui.components.MasterPowerButton
import com.example.audioambientglow.ui.components.ParameterSlider
import com.example.audioambientglow.ui.components.RealtimeSpectrumBarView
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlowDashboardScreen(
    config: GlowConfig,
    audioFeatures: AudioFeatures,
    mediaTrackInfo: MediaTrackInfo,
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    hasMediaListenerPermission: Boolean,
    hasAudioPermission: Boolean,
    isAudioActive: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestMediaListenerPermission: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestInternalAudioCapture: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPinAodShortcut: () -> Unit,
    onConfigChange: ((GlowConfig) -> GlowConfig) -> Unit,
    onSimulateBeat: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showHelpDialog by remember { mutableStateOf(false) }

    val allPermissionsReady = hasOverlayPermission && hasAudioPermission

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Title & Badge & Help Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "手機音效氣氛燈",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFF00F5FF), Color(0xFFFF007F), Color(0xFF7928CA))
                                )
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF131929))
                                .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { showHelpDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "Help Guide",
                                tint = Color(0xFF00F5FF),
                                modifier = Modifier.padding(6.dp).size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF00F5FF), Color(0xFFFF007F))))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "144Hz 直立滿版",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // 2. Active Media Track & Real Hardware FFT Status Card
            GlassCard(borderColor = if (isAudioActive) Color(0xFF00FF88) else Color(0xFF262B3B)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isAudioActive) Color(0xFF00FF88).copy(alpha = 0.2f) else Color(0xFF1E2230)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAudioActive) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isAudioActive) Color(0xFF00FF88) else Color(0xFF8A93A4),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (mediaTrackInfo.isPlaying) mediaTrackInfo.title.ifEmpty { "音樂播放中" } else "無音樂播放中 (待命中)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isAudioActive) "🔥 100% 真實硬體輸出混音 FFT 解析中 (零合成、零假波)" else "支援 YouTube Music、Spotify、系統內建音樂",
                                color = if (isAudioActive) Color(0xFF00FF88) else Color(0xFF8A93A4),
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (isAudioActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00FF88).copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("REAL FFT", color = Color(0xFF00FF88), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // 3. Setup Wizard (新手引導授權步驟)
            GlassCard(
                borderColor = if (allPermissionsReady) Color(0xFF00FF88) else Color(0xFFFF007F)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (allPermissionsReady) Icons.Default.CheckCircle else Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = if (allPermissionsReady) Color(0xFF00FF88) else Color(0xFFFF007F)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (allPermissionsReady) "✅ 氣氛燈設定已就緒！" else "🚀 新手快速設定引導",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Step 1: Overlay Permission
                SetupStepItem(
                    stepNumber = "1",
                    title = "開啟懸浮視窗權限 (必選)",
                    desc = "允許在全螢幕與鎖定畫面上繪製滿版賽道流光",
                    isDone = hasOverlayPermission,
                    buttonText = "點擊授權",
                    onAction = onRequestOverlayPermission
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Step 2: Real System Audio Output FFT Permission
                SetupStepItem(
                    stepNumber = "2",
                    title = "開啟硬體音效輸出混音感知 (必選，100% 真實 FFT)",
                    desc = "允許系統直接讀取音樂輸出的 512 點頻譜，真實感知重低音、人聲與高頻！(通話自動避讓)",
                    isDone = hasAudioPermission,
                    buttonText = "啟用真實 FFT",
                    onAction = onRequestAudioPermission
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Step 3: Media Listener Permission
                SetupStepItem(
                    stepNumber = "3",
                    title = "開啟媒體通知感知 (推薦)",
                    desc = "同步 YouTube Music、Spotify、系統播放器之歌曲名稱",
                    isDone = hasMediaListenerPermission,
                    buttonText = "啟用同步",
                    onAction = onRequestMediaListenerPermission
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Step 4: Background Optimization
                SetupStepItem(
                    stepNumber = "4",
                    title = "系統背景高耗電放行 (防殺背景)",
                    desc = "進入應用資訊 > 電池，勾選「允許不受限制」或「允許高背景耗電」",
                    isDone = false,
                    buttonText = "前往設定",
                    onAction = onOpenAppSettings
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Desktop Widget / Shortcut Action Card
                Button(
                    onClick = onPinAodShortcut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00F5FF)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "📲 新增 AMOLED 息屏掛機「桌面小工具 / 捷徑」",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // 4. Master Switch & Mode Selector
            GlassCard(
                borderColor = if (config.isEnabled) Color(0xFF00F5FF) else Color(0xFF262B3B)
            ) {
                MasterPowerButton(
                    isEnabled = config.isEnabled,
                    onToggle = { enabled -> onConfigChange { it.copy(isEnabled = enabled) } }
                )

                Text(
                    text = "啟動模式 (Display Mode)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (mode in GlowDisplayMode.values()) {
                        val isSelected = config.displayMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF1E2638) else Color(0xFF0E111A))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF00F5FF) else Color(0xFF1E2230),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onConfigChange { it.copy(displayMode = mode) } }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.displayName,
                                    color = if (isSelected) Color(0xFF00F5FF) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = mode.description,
                                    color = Color(0xFF8A93A4),
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00F5FF))
                            }
                        }
                    }
                }
            }

            // 5. Realtime Visualizer Dynamic 32-Band Spectrum (50Hz ~ 2000Hz)
            GlassCard(
                borderColor = if (audioFeatures.rawRms > config.noiseGateThreshold) Color(0xFFFF007F) else Color(0xFF262B3B)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFFF007F), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "即時真實頻譜 (50Hz ~ 2000Hz)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A1528))
                            .border(1.dp, Color(0xFFFF007F).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .clickable { onSimulateBeat() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFFF007F), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("模擬重低音", color = Color(0xFFFF007F), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                RealtimeSpectrumBarView(
                    spectrumBands = audioFeatures.spectrumBands,
                    bassEnergy = audioFeatures.bassEnergy
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MetricChip(
                        label = "音量 RMS",
                        value = String.format(Locale.US, "%.0f%%", audioFeatures.rawRms * 100),
                        color = Color(0xFF00F5FF),
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        label = "低音 50-250",
                        value = String.format(Locale.US, "%.0f%%", audioFeatures.bassEnergy * 100),
                        color = Color(0xFFFF007F),
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        label = "人聲 250-1k",
                        value = String.format(Locale.US, "%.0f%%", audioFeatures.midEnergy * 100),
                        color = Color(0xFF7928CA),
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        label = "高頻 1k-2k",
                        value = String.format(Locale.US, "%.0f%%", audioFeatures.trebleEnergy * 100),
                        color = Color(0xFFFFB800),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 7. Theme Palette Selector
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFFFFB800))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "色彩主題預設 (Presets)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (preset in GlowThemePreset.values()) {
                        val isSelected = config.themePreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { onConfigChange { it.copy(themePreset = preset) } },
                            label = { Text(preset.displayName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00F5FF),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF131722),
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color(0xFF00F5FF) else Color(0xFF262B3B)
                            )
                        )
                    }
                }
            }

            // 8. Speed & Physics Parameters
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF00F5FF))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "流光動態與物理加速 (Speed & Dynamics)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                ParameterSlider(
                    title = "基礎流轉速度 (Base Speed)",
                    value = config.baseSpeed,
                    onValueChange = { v -> onConfigChange { it.copy(baseSpeed = v) } },
                    valueRange = 0.2f..4.0f,
                    valueDisplay = String.format(Locale.US, "%.1fx", config.baseSpeed),
                    unit = "",
                    accentColor = Color(0xFF00F5FF)
                )

                ParameterSlider(
                    title = "重低音狂飆加速倍率 (Bass Speed Boost)",
                    value = config.bassSpeedMultiplier,
                    onValueChange = { v -> onConfigChange { it.copy(bassSpeedMultiplier = v) } },
                    valueRange = 1.0f..6.0f,
                    valueDisplay = String.format(Locale.US, "%.1fx", config.bassSpeedMultiplier),
                    unit = "",
                    accentColor = Color(0xFFFF007F)
                )

                ParameterSlider(
                    title = "動態色相偏移範圍 (Dynamic Hue Swing)",
                    value = config.dynamicHueRangeDeg,
                    onValueChange = { v -> onConfigChange { it.copy(dynamicHueRangeDeg = v) } },
                    valueRange = 0f..180f,
                    valueDisplay = String.format(Locale.US, "%.0f", config.dynamicHueRangeDeg),
                    unit = "°",
                    accentColor = Color(0xFFFFB800)
                )
            }

            // 9. Display Bezel & Corner Radius
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF7928CA))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "螢幕適配與 AMOLED 護眼 (Bezel & Protection)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                ParameterSlider(
                    title = "螢幕圓角曲率半徑 (Corner Radius)",
                    value = config.cornerRadiusDp,
                    onValueChange = { v -> onConfigChange { it.copy(cornerRadiusDp = v) } },
                    valueRange = 0f..60f,
                    valueDisplay = String.format(Locale.US, "%.0f", config.cornerRadiusDp),
                    unit = "dp",
                    accentColor = Color(0xFF7928CA)
                )

                ParameterSlider(
                    title = "邊框核心厚度 (Thickness)",
                    value = config.glowThicknessDp,
                    onValueChange = { v -> onConfigChange { it.copy(glowThicknessDp = v) } },
                    valueRange = 4f..40f,
                    valueDisplay = String.format(Locale.US, "%.0f", config.glowThicknessDp),
                    unit = "dp",
                    accentColor = Color(0xFF7928CA)
                )

                ParameterSlider(
                    title = "向內高斯羽化深度 (Bloom Feathering)",
                    value = config.bloomFeatheringDp,
                    onValueChange = { v -> onConfigChange { it.copy(bloomFeatheringDp = v) } },
                    valueRange = 0f..60f,
                    valueDisplay = String.format(Locale.US, "%.0f", config.bloomFeatheringDp),
                    unit = "dp",
                    accentColor = Color(0xFF7928CA)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("AMOLED 防燒屏像素微位移", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("定時微調 1~2 像素位置，保護 AMOLED 螢幕壽命", color = Color(0xFF8A93A4), fontSize = 11.sp)
                    }
                    Switch(
                        checked = config.antiBurnInEnabled,
                        onCheckedChange = { enabled -> onConfigChange { it.copy(antiBurnInEnabled = enabled) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF7928CA))
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Help Modal Dialog
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                containerColor = Color(0xFF101422),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = Color(0xFF00F5FF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("手機音效氣氛燈 操作說明指南", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HelpSection(
                            title = "🎧 100% 真實硬體輸出混音 FFT 解析",
                            content = "• 透過系統音訊輸出混音器直接解析 512 點真實頻譜。\n• 0% 麥克風雜音、0% 偽波形合成，完美貼合你播放的每一首音樂！\n• 來電、通話 100% 避讓，通話零衝突。"
                        )
                        HelpSection(
                            title = "⚡ 144Hz 滿版賽道流光特性",
                            content = "• 四角同色閉環：保證光帶巡航時四角顏色永遠平滑過渡無色差。\n• 內向高斯羽化：光暈向螢幕中心柔和淡出，厚度 0% 處無雜色邊界。\n• 鼓點重低音狂飆：重低音轟炸時光帶極速飛馳，旋律柔和時優雅巡航。"
                        )
                        HelpSection(
                            title = "🌙 AMOLED 息屏掛機 (AOD)",
                            content = "• 點擊桌面「AMOLED 息屏」捷徑或小工具，進入純黑息屏掛機。\n• 純黑像素 0% 耗電 + 內建 AMOLED 防燒屏微位移。\n• 退出方式：在螢幕上【雙擊快速連按兩下】即可秒回桌面！"
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showHelpDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5FF))
                    ) {
                        Text("我瞭解了", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun SetupStepItem(
    stepNumber: String,
    title: String,
    desc: String,
    isDone: Boolean,
    buttonText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDone) Color(0xFF0C141E) else Color(0xFF131722))
            .border(
                1.dp,
                if (isDone) Color(0xFF00FF88).copy(alpha = 0.4f) else Color(0xFF262B3B),
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isDone) Color(0xFF00FF88) else Color(0xFF262B3B)),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                } else {
                    Text(text = stepNumber, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = if (isDone) Color(0xFF00FF88) else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(text = desc, color = Color(0xFF8A93A4), fontSize = 10.sp)
            }
        }
        if (!isDone) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5FF)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(text = buttonText, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MetricChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0E111A))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, color = Color(0xFF8A93A4), fontSize = 9.sp, maxLines = 1)
        Text(text = value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun HelpSection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, color = Color(0xFF00F5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text = content, color = Color(0xFFCCD6E0), fontSize = 12.sp, lineHeight = 16.sp)
    }
}
