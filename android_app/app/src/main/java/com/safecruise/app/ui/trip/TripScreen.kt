package com.safecruise.app.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecruise.app.data.model.TripRecord
import com.safecruise.app.ui.theme.BrightNeonGreen
import com.safecruise.app.ui.theme.DarkBackground
import com.safecruise.app.ui.theme.DarkCardBorder
import com.safecruise.app.ui.theme.DarkHudCard
import com.safecruise.app.ui.theme.DarkHudSubCard
import com.safecruise.app.ui.theme.HighRiskOrange
import com.safecruise.app.ui.theme.TextMutedGrey
import com.safecruise.app.ui.theme.TextWhite
import com.safecruise.app.ui.theme.WarningOrange
import java.util.Locale

@Composable
fun TripScreen(
    isTracking: Boolean,
    currentSpeedKmh: Float,
    distanceKm: Float,
    durationSec: Long,
    avgSpeedKph: Float,
    maxSpeedKph: Float,
    tripHistory: List<TripRecord>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Screen Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TRIP ANALYTICS",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (isTracking) "ACTIVE TRIP IN PROGRESS" else "STANDBY • LAST KNOWN TRIP",
                    color = if (isTracking) BrightNeonGreen else TextMutedGrey,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .background(
                        if (isTracking) Color(0x3300FF66) else Color(0xFF1E2533),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        if (isTracking) BrightNeonGreen else Color(0xFF333E50),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isTracking) "RECORDING" else "IDLE",
                    color = if (isTracking) BrightNeonGreen else TextMutedGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // CURRENT / ACTIVE TRIP SUMMARY CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.2.dp, DarkCardBorder, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkHudCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "CURRENT TRIP METRICS",
                            color = TextMutedGrey,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )

                        // 4 Big Metrics Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricItem(
                                title = "DISTANCE",
                                value = String.format(Locale.US, "%.2f", distanceKm),
                                unit = "km",
                                icon = Icons.Default.DirectionsCar
                            )
                            val mins = durationSec / 60
                            val secs = durationSec % 60
                            MetricItem(
                                title = "DURATION",
                                value = String.format(Locale.US, "%02d:%02d", mins, secs),
                                unit = "min",
                                icon = Icons.Default.Timer
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricItem(
                                title = "AVG SPEED",
                                value = String.format(Locale.US, "%.1f", avgSpeedKph),
                                unit = "kph",
                                icon = Icons.Default.Speed
                            )
                            MetricItem(
                                title = "PEAK SPEED",
                                value = String.format(Locale.US, "%.1f", maxSpeedKph),
                                unit = "kph",
                                icon = Icons.Default.Speed
                            )
                        }
                    }
                }
            }

            // SECTION TITLE: TRIP HISTORY
            item {
                Text(
                    text = "RECENT DRIVES",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // TRIP HISTORY ITEMS
            if (tripHistory.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF1E2636), RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkHudSubCard)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "NO DRIVES RECORDED YET",
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Start a trip on the HUD to track and record your real-time drives, routes, and risk score metrics.",
                                color = TextMutedGrey,
                                fontSize = 11.5.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(tripHistory) { trip ->
                    TripHistoryItem(trip)
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun MetricItem(
    title: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .border(1.dp, Color(0xFF1E2636), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkHudSubCard)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = BrightNeonGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    color = TextMutedGrey,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    color = TextMutedGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun TripHistoryItem(trip: TripRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1B2230), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkHudCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trip.dateStr,
                    color = TextWhite,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )

                // Safety Status Pill
                val statusColor = when (trip.status) {
                    "High Risk" -> HighRiskOrange
                    "Moderate" -> WarningOrange
                    else -> BrightNeonGreen
                }
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${trip.status.uppercase(Locale.US)} • SCORE ${trip.riskScore}",
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Route from -> to
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = trip.startLocation,
                    color = Color(0xFFC8D5E5),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "to",
                    tint = TextMutedGrey,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = trip.endLocation,
                    color = Color(0xFFC8D5E5),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(Locale.US, "Dist: %.2f km", trip.distanceKm),
                    color = TextMutedGrey,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format(Locale.US, "Time: %02d:%02d", trip.durationSec / 60, trip.durationSec % 60),
                    color = TextMutedGrey,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format(Locale.US, "Avg: %.1f kph", trip.avgSpeedKph),
                    color = TextMutedGrey,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
