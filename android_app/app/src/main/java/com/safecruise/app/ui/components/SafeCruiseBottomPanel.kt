package com.safecruise.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecruise.app.data.model.PredictResponse
import com.safecruise.app.data.model.TelemetryPoint
import com.safecruise.app.ui.theme.BrightNeonGreen
import com.safecruise.app.ui.theme.DarkCardBorder
import com.safecruise.app.ui.theme.DarkHudCard
import com.safecruise.app.ui.theme.DarkHudSubCard
import com.safecruise.app.ui.theme.HighRiskOrange
import com.safecruise.app.ui.theme.HighRiskRed
import com.safecruise.app.ui.theme.RecordingPillBg
import com.safecruise.app.ui.theme.RecordingPillBorder
import com.safecruise.app.ui.theme.SpeedPillGreen
import com.safecruise.app.ui.theme.TextBlack
import com.safecruise.app.ui.theme.TextMutedGrey
import com.safecruise.app.ui.theme.TextWhite
import com.safecruise.app.ui.theme.WarningOrange
import java.util.Locale

@Composable
fun SafeCruiseBottomPanel(
    isTracking: Boolean,
    telemetry: TelemetryPoint?,
    response: PredictResponse?,
    distanceKm: Float,
    tripDurationSec: Long,
    avgSpeedKph: Float,
    onToggleTracking: () -> Unit,
    onResetTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collapsible drawer for additional telemetry info
    var isTelemetryExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp))
            .border(1.2.dp, DarkCardBorder, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = DarkHudCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ROW 1: Status Pill (Left) & Speed Pill (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Recording / Standby Status Pill Badge
                Box(
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (isTracking) RecordingPillBorder else Color(0xFF282F3D),
                            RoundedCornerShape(18.dp)
                        )
                        .background(
                            if (isTracking) RecordingPillBg else Color(0xFF161A24),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 13.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.5.dp)
                                .background(
                                    if (isTracking) HighRiskOrange else Color(0xFF708095),
                                    CircleShape
                                )
                        )
                        Text(
                            text = if (isTracking) "RECORDING" else "STANDBY",
                            color = if (isTracking) HighRiskOrange else Color(0xFF8E9BAE),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Speed Pill Badge (Bright Glowing Green)
                val currentSpeed = telemetry?.speedKmh ?: 0.0f
                Box(
                    modifier = Modifier
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(18.dp), spotColor = BrightNeonGreen)
                        .background(SpeedPillGreen, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f", currentSpeed),
                            color = TextBlack,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "KM/H",
                            color = TextBlack,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(bottom = 3.5.dp)
                        )
                    }
                }
            }

            // ROW 2: Risk Score Circle Card (Left) & Clean, Readable Driving Status Card (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val score = response?.riskScore ?: (if (isTracking) 8 else 0)
                val riskLevel = response?.riskLevel ?: 0
                val ringColor = when {
                    !isTracking -> Color(0xFF333E50)
                    riskLevel == 2 -> HighRiskOrange
                    riskLevel == 1 -> WarningOrange
                    else -> BrightNeonGreen
                }

                // Left: Risk Score Card
                Card(
                    modifier = Modifier
                        .weight(0.35f)
                        .height(105.dp)
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkHudSubCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(54.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(50.dp)) {
                                val strokeWidth = 3.5.dp.toPx()
                                drawCircle(
                                    color = Color(0xFF262C3A),
                                    style = Stroke(width = strokeWidth)
                                )
                                val sweep = if (score > 0) (score / 100f) * 360f else 0f
                                if (sweep > 0) {
                                    drawArc(
                                        color = ringColor,
                                        startAngle = -90f,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                }
                            }
                            Text(
                                text = "$score",
                                color = if (score > 0) TextWhite else Color(0xFF8B97A8),
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "RISK SCORE",
                            color = TextWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.7.sp
                        )
                    }
                }

                // Right: Clean, Readable Driving Status Card (Uncluttered & simple)
                Card(
                    modifier = Modifier
                        .weight(0.65f)
                        .height(105.dp)
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkHudSubCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1. Status Title Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.5.dp)
                                    .background(
                                        when {
                                            !isTracking -> Color(0xFF708095)
                                            riskLevel == 2 -> HighRiskOrange
                                            riskLevel == 1 -> WarningOrange
                                            else -> BrightNeonGreen
                                        },
                                        CircleShape
                                    )
                            )
                            Text(
                                text = if (!isTracking) "STANDBY" else (response?.primaryStatus ?: "Safe Driving").uppercase(Locale.US),
                                color = when {
                                    !isTracking -> Color(0xFF8E9BAE)
                                    riskLevel == 2 -> HighRiskOrange
                                    riskLevel == 1 -> WarningOrange
                                    else -> BrightNeonGreen
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            )
                        }

                        // 2. Prominent, readable description showing what the user needs to know
                        val descText = if (!isTracking) {
                            "Vehicle stationary"
                        } else {
                            response?.detailDescription ?: "Smooth & steady driving"
                        }
                        Text(
                            text = descText,
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // 3. Clean status footer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (!isTracking) "Ready to monitor trip" else (response?.statusSubtitle ?: "AI Engine: Optimal"),
                                color = TextMutedGrey,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isTracking) "ACTIVE" else "IDLE",
                                color = if (isTracking) BrightNeonGreen else Color(0xFF6B7B8E),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ROW 3: START / RESET / STOP TRIP BUTTONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isTracking) {
                    // Divided into TWO sections: RESET and STOP
                    Button(
                        onClick = onResetTrip,
                        modifier = Modifier
                            .weight(0.38f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E2533),
                            contentColor = Color(0xFFE2EAF4)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF333E52))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = Color(0xFFE2EAF4),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "RESET",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    Button(
                        onClick = onToggleTracking,
                        modifier = Modifier
                            .weight(0.62f)
                            .height(48.dp)
                            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp), spotColor = HighRiskRed),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HighRiskRed,
                            contentColor = TextWhite
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .background(TextWhite, RoundedCornerShape(2.dp))
                            )
                            Text(
                                text = "STOP TRIP",
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.9.sp
                            )
                        }
                    }
                } else {
                    // Idle state: RESET button beside START TRIP button
                    Button(
                        onClick = onResetTrip,
                        modifier = Modifier
                            .weight(0.32f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF161C26),
                            contentColor = Color(0xFFB0BDCF)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF283244))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = Color(0xFFB0BDCF),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "RESET",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Button(
                        onClick = onToggleTracking,
                        modifier = Modifier
                            .weight(0.68f)
                            .height(48.dp)
                            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp), spotColor = BrightNeonGreen),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrightNeonGreen,
                            contentColor = TextBlack
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "▶",
                                color = TextBlack,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "START TRIP",
                                color = TextBlack,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.9.sp
                            )
                        }
                    }
                }
            }

            // ROW 4: TOGGLE BUTTON FOR ADDITIONAL TELEMETRY & STATS
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF131822))
                    .clickable { isTelemetryExpanded = !isTelemetryExpanded }
                    .padding(vertical = 5.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isTelemetryExpanded) "HIDE TELEMETRY & STATS" else "SHOW TELEMETRY & STATS",
                        color = BrightNeonGreen,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Icon(
                        imageVector = if (isTelemetryExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Stats",
                        tint = BrightNeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // ROW 5 & 6: ANIMATED EXPANDABLE ADDITIONAL TELEMETRY (IMU + TRIP METRICS)
            AnimatedVisibility(
                visible = isTelemetryExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    // SENSOR / IMU TELEMETRY CONTAINER
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F131C))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val rawX = telemetry?.rawAccelX ?: 0.0f
                            val rawY = telemetry?.rawAccelY ?: 0.0f
                            val rawZ = telemetry?.rawAccelZ ?: 9.81f
                            val gyroRate = telemetry?.gyroRate ?: 0.0f

                            // IMU Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "IMU:  ",
                                    color = BrightNeonGreen,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "X: %.2f  |  Y: %.2f  |  Z: %.2f m/s²",
                                        rawX, rawY, rawZ
                                    ),
                                    color = Color(0xFFD6DFEC),
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // GYRO Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = String.format(Locale.US, "GYRO: %.2f rad/s", gyroRate),
                                    color = Color(0xFFD6DFEC),
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "  •  ",
                                    color = BrightNeonGreen,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "10Hz",
                                    color = BrightNeonGreen,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 3-COLUMN TRIP METRICS (DISTANCE | TRIP TIME | AVG SPEED)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF1D2432), RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10141E))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Col 1: Distance
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "DISTANCE",
                                    color = TextMutedGrey,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(1.5.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = String.format(Locale.US, "%.2f", distanceKm),
                                        color = TextWhite,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "km",
                                        color = TextMutedGrey,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 1.dp)
                                    )
                                }
                            }

                            // Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color(0xFF222B3B))
                            )

                            // Col 2: Trip Time
                            val minutes = tripDurationSec / 60
                            val seconds = tripDurationSec % 60
                            val formattedTime = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "TRIP TIME",
                                    color = TextMutedGrey,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(1.5.dp))
                                Text(
                                    text = formattedTime,
                                    color = TextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color(0xFF222B3B))
                            )

                            // Col 3: Avg Speed
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "AVG SPEED",
                                    color = TextMutedGrey,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(1.5.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f", avgSpeedKph),
                                        color = TextWhite,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "kph",
                                        color = TextMutedGrey,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
