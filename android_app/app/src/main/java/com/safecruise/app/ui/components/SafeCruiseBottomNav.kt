package com.safecruise.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecruise.app.ui.theme.BrightNeonGreen
import com.safecruise.app.ui.theme.DarkNavBackground

@Composable
fun SafeCruiseBottomNav(
    selectedTab: String = "HUD",
    onTabSelected: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkNavBackground)
            .border(
                width = 1.dp,
                color = Color(0xFF1B2230),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TAB 1: HUD (ACTIVE)
            val isHud = selectedTab == "HUD"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isHud) 1.5.dp else 0.dp,
                        color = if (isHud) Color(0xFF00C853) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(
                        if (isHud) Color(0x2400FF66) else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onTabSelected("HUD") }
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Concentric Target / Radar HUD Icon
                    Canvas(modifier = Modifier.size(24.dp)) {
                        val stroke = 1.8.dp.toPx()
                        val center = Offset(size.width / 2, size.height / 2)
                        // Outer ring
                        drawCircle(
                            color = BrightNeonGreen,
                            radius = size.width * 0.42f,
                            center = center,
                            style = Stroke(width = stroke)
                        )
                        // Inner ring
                        drawCircle(
                            color = BrightNeonGreen,
                            radius = size.width * 0.22f,
                            center = center,
                            style = Stroke(width = stroke)
                        )
                        // Center dot
                        drawCircle(
                            color = BrightNeonGreen,
                            radius = size.width * 0.08f,
                            center = center
                        )
                    }
                    Text(
                        text = "HUD",
                        color = BrightNeonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            // TAB 2: TRIP
            val isTrip = selectedTab == "TRIP"
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onTabSelected("TRIP") }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "TRIP",
                    tint = if (isTrip) BrightNeonGreen else Color(0xFF8896A8),
                    modifier = Modifier.size(23.dp)
                )
                Text(
                    text = "TRIP",
                    color = if (isTrip) BrightNeonGreen else Color(0xFF8896A8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            // TAB 3: LOGS
            val isLogs = selectedTab == "LOGS"
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onTabSelected("LOGS") }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AltRoute,
                    contentDescription = "LOGS",
                    tint = if (isLogs) BrightNeonGreen else Color(0xFF8896A8),
                    modifier = Modifier.size(23.dp)
                )
                Text(
                    text = "LOGS",
                    color = if (isLogs) BrightNeonGreen else Color(0xFF8896A8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            // TAB 4: DEV
            val isDev = selectedTab == "DEV" || selectedTab == "DIAG"
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onTabSelected("DEV") }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "DEV",
                    tint = if (isDev) BrightNeonGreen else Color(0xFF8896A8),
                    modifier = Modifier.size(23.dp)
                )
                Text(
                    text = "DEV",
                    color = if (isDev) BrightNeonGreen else Color(0xFF8896A8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}
