package com.example.audioambientglow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioambientglow.data.GlowThemePreset

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color(0xFF00F5FF).copy(alpha = 0.25f),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121420).copy(alpha = 0.85f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
fun MasterPowerButton(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "power_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val activeGlowColor = Color(0xFF00F5FF)
    val inactiveGlowColor = Color(0xFF333842)
    val currentColor by animateColorAsState(
        targetValue = if (isEnabled) activeGlowColor else inactiveGlowColor,
        label = "color"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    if (isEnabled) {
                        Brush.radialGradient(
                            colors = listOf(
                                currentColor.copy(alpha = glowAlpha * 0.4f),
                                Color.Transparent
                            )
                        )
                    } else {
                        Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0C14))
                    .border(
                        width = if (isEnabled) 3.dp else 2.dp,
                        color = currentColor.copy(alpha = if (isEnabled) glowAlpha else 0.4f),
                        shape = CircleShape
                    )
                    .clickable { onToggle(!isEnabled) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Master Power",
                    tint = currentColor,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
    }
}

@Composable
fun RealtimeSpectrumBarView(
    spectrumBands: FloatArray,
    bassEnergy: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF07080E))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        spectrumBands.forEachIndexed { index, energy ->
            val barColor = if (index < 8) {
                Color(0xFFFF007F) // Pink for Bass (50Hz - 250Hz)
            } else if (index < 22) {
                Color(0xFF00F5FF) // Cyan for Mid Vocals (250Hz - 1000Hz)
            } else {
                Color(0xFFFFD700) // Gold for Harmonics (1000Hz - 2000Hz)
            }

            val heightFraction = energy.coerceIn(0.04f, 1.0f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((heightFraction * 38).dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                barColor,
                                barColor.copy(alpha = 0.30f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun ParameterSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: String,
    accentColor: Color = Color(0xFF00F5FF),
    unit: String = ""
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFE2E8F0)
            )
            Surface(
                color = accentColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "$valueDisplay $unit",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color(0xFF262B3B)
            )
        )
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isGranted) Color(0xFF102820) else Color(0xFF2E1A1A))
            .clickable(enabled = !isGranted) { onGrantClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF00FF88) else Color(0xFFFF5555),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (!isGranted) {
            Surface(
                color = Color(0xFFFF007F),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "授權",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                text = "已就緒",
                color = Color(0xFF00FF88),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
