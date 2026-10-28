package com.safecruise.app.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecruise.app.R
import com.safecruise.app.data.model.TelemetryPoint
import com.safecruise.app.ui.theme.BrightNeonGreen
import com.safecruise.app.ui.theme.TextWhite
import java.util.Locale
import kotlin.math.abs

@Composable
fun SafeCruiseTopHeader(
    mlEngineStatus: String,
    telemetry: TelemetryPoint?,
    satellitesCount: Int,
    hdop: Float,
    locationLabel: String,
    v2vStatus: String = "OFFLINE",
    v2vCount: Int = 0,
    onAvatarClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 1. TOP APP BAR (LOGO, "DRIVE.NET", ML & V2V STATUS, AVATAR)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Group: Logo & Titles
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_safe_cruise_logo),
                    contentDescription = "Drive.Net Logo",
                    modifier = Modifier.size(38.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "DRIVE.NET",
                        color = TextWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(BrightNeonGreen, CircleShape)
                            )
                            Text(
                                text = "ML: $mlEngineStatus",
                                color = BrightNeonGreen,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.4.sp
                            )
                        }

                        val v2vColor = when (v2vStatus) {
                            "CONNECTED" -> BrightNeonGreen
                            "SHARING_PAUSED" -> Color(0xFFFFB300)
                            else -> Color(0xFF8896A8)
                        }
                        val v2vLabel = when (v2vStatus) {
                            "CONNECTED" -> if (v2vCount > 0) "V2V: $v2vCount NEARBY" else "V2V: ACTIVE"
                            "SHARING_PAUSED" -> "V2V: PAUSED"
                            else -> "V2V: OFFLINE"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(v2vColor, CircleShape)
                            )
                            Text(
                                text = v2vLabel,
                                color = v2vColor,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
            }

            // Right Group: Circular User Avatar
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_avatar),
                    contentDescription = "User Profile",
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. HUD SUBHEADER OVERLAY ROW 1 (LOCATION NAME | SATELLITES & HDOP)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Location Text with weight so it never overlaps sats count
            Text(
                text = locationLabel.uppercase(Locale.US),
                color = Color(0xFFE2EAF4),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Satellites & HDOP Chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.5.dp)
                        .background(BrightNeonGreen, CircleShape)
                )
                Text(
                    text = String.format(Locale.US, "%d SATS  •  %.1fM HDOP", satellitesCount, hdop),
                    color = BrightNeonGreen,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. HUD SUBHEADER OVERLAY ROW 2 (LAT/LON COORDINATES | ALTITUDE BADGE)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val lat = telemetry?.latitude ?: 0.0
            val lng = telemetry?.longitude ?: 0.0
            val coordText = if (lat != 0.0 && lng != 0.0) {
                val latDir = if (lat >= 0) "N" else "S"
                val lngDir = if (lng >= 0) "E" else "W"
                String.format(
                    Locale.US,
                    "LAT: %.4f° %s  LON: %.4f° %s",
                    abs(lat), latDir, abs(lng), lngDir
                )
            } else {
                "LAT: --.----°  LON: --.----°"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Coordinates",
                    tint = BrightNeonGreen,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = coordText,
                    color = Color(0xFFD6E0EC),
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Altitude Badge
            val alt = telemetry?.altitude ?: 0.0
            Box(
                modifier = Modifier
                    .background(Color(0xCC121620), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF232A38), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "ALT: %.1fm", alt),
                    color = Color(0xFFE2EAF4),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
