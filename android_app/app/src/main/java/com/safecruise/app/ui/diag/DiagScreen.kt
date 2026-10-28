package com.safecruise.app.ui.diag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecruise.app.data.model.TelemetryPoint
import com.safecruise.app.ui.theme.BrightNeonGreen
import com.safecruise.app.ui.theme.DarkBackground
import com.safecruise.app.ui.theme.DarkCardBorder
import com.safecruise.app.ui.theme.DarkHudCard
import com.safecruise.app.ui.theme.DarkHudSubCard
import com.safecruise.app.ui.theme.HighRiskOrange
import com.safecruise.app.ui.theme.HighRiskRed
import com.safecruise.app.ui.theme.TextBlack
import com.safecruise.app.ui.theme.TextMutedGrey
import com.safecruise.app.ui.theme.TextWhite
import java.util.Locale

@Composable
fun DiagScreen(
    telemetry: TelemetryPoint?,
    satellitesCount: Int,
    hdop: Float,
    bufferSize: Int,
    bufferSpanSeconds: Float,
    mlEngineStatus: String,
    currentServerUrl: String,
    harshBrakingThreshold: Float = -3.4f,
    harshAccelThreshold: Float = 3.4f,
    harshSwerveThreshold: Float = 3.4f,
    activeScenario: String = "NONE",
    onSetScenario: (String) -> Unit = {},
    onHarshBrakingChange: (Float) -> Unit = {},
    onHarshAccelChange: (Float) -> Unit = {},
    onHarshSwerveChange: (Float) -> Unit = {},
    onServerUrlChange: (String) -> Unit,
    onPingServer: (String, (Boolean, String) -> Unit) -> Unit
) {
    var serverUrlInput by remember { mutableStateOf(currentServerUrl) }
    var pingResultText by remember { mutableStateOf<String?>(null) }
    var pingSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isPinging by remember { mutableStateOf(false) }

    var brakeVal by remember(harshBrakingThreshold) { mutableFloatStateOf(harshBrakingThreshold) }
    var accelVal by remember(harshAccelThreshold) { mutableFloatStateOf(harshAccelThreshold) }
    var swerveVal by remember(harshSwerveThreshold) { mutableFloatStateOf(harshSwerveThreshold) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // DEV / DIAGNOSTICS SCREEN HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DEV & DIAGNOSTICS",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "ENGINE TUNABLES & V2V SIMULATION",
                    color = BrightNeonGreen,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .background(Color(0x3300FF66), RoundedCornerShape(10.dp))
                    .border(1.dp, BrightNeonGreen, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "DEV MODE",
                    color = BrightNeonGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. LIVE V2V & AI SCENARIO SIMULATOR
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.2.dp, BrightNeonGreen, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkHudCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = BrightNeonGreen,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "V2V NEARBY VEHICLE & AI SCENARIOS",
                                color = TextWhite,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        Text(
                            text = "Inject real-time nearby vehicles within 100m to test HUD dot tracking, ML risk reaction, and 3-second hazard alert pop-ups:",
                            color = TextMutedGrey,
                            fontSize = 11.sp
                        )

                        // Scenario Pills
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (activeScenario == "AGGRESSIVE_TAILGATER") Color(0x33FF3333) else Color(0xFF1E2533),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (activeScenario == "AGGRESSIVE_TAILGATER") HighRiskRed else Color(0xFF2C374A),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSetScenario("AGGRESSIVE_TAILGATER") }
                                        .padding(vertical = 9.dp, horizontal = 10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "⚠️ Tailgater Behind",
                                            color = if (activeScenario == "AGGRESSIVE_TAILGATER") HighRiskRed else TextWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "42m back • +18 km/h fast",
                                            color = TextMutedGrey,
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (activeScenario == "AGGRESSIVE_SWERVE_AHEAD") Color(0x33FF9100) else Color(0xFF1E2533),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (activeScenario == "AGGRESSIVE_SWERVE_AHEAD") HighRiskOrange else Color(0xFF2C374A),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSetScenario("AGGRESSIVE_SWERVE_AHEAD") }
                                        .padding(vertical = 9.dp, horizontal = 10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "⚠️ Swerve Ahead",
                                            color = if (activeScenario == "AGGRESSIVE_SWERVE_AHEAD") HighRiskOrange else TextWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "65m ahead • Erratic",
                                            color = TextMutedGrey,
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (activeScenario == "PEACEFUL_CONVOY") Color(0x3300FF66) else Color(0xFF1E2533),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (activeScenario == "PEACEFUL_CONVOY") BrightNeonGreen else Color(0xFF2C374A),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSetScenario("PEACEFUL_CONVOY") }
                                        .padding(vertical = 9.dp, horizontal = 10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "🟢 Normal Traffic (2)",
                                            color = if (activeScenario == "PEACEFUL_CONVOY") BrightNeonGreen else TextWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "55m ahead & 72m back",
                                            color = TextMutedGrey,
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color(0xFF1E2533), RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0xFF2C374A), RoundedCornerShape(10.dp))
                                        .clickable { onSetScenario("CLEAR") }
                                        .padding(vertical = 9.dp, horizontal = 10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "↺ Clear Vehicles",
                                            color = TextMutedGrey,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Remove all simulated dots",
                                            color = Color(0xFF6B7A90),
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. DEVELOPER CALIBRATION & RISK THRESHOLD TUNER
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkHudCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = BrightNeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "CALIBRATION & THRESHOLD TUNING",
                                color = TextWhite,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        // Brake slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Harsh Braking Sensitivity", color = TextMutedGrey, fontSize = 11.5.sp)
                                Text(
                                    String.format(Locale.US, "%.1f m/s²", brakeVal),
                                    color = HighRiskRed,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = brakeVal,
                                onValueChange = {
                                    brakeVal = it
                                    onHarshBrakingChange(it)
                                },
                                valueRange = -5.0f..-1.8f,
                                colors = SliderDefaults.colors(
                                    thumbColor = HighRiskRed,
                                    activeTrackColor = HighRiskRed
                                )
                            )
                        }

                        // Accel slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Harsh Acceleration Sensitivity", color = TextMutedGrey, fontSize = 11.5.sp)
                                Text(
                                    String.format(Locale.US, "+%.1f m/s²", accelVal),
                                    color = HighRiskOrange,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = accelVal,
                                onValueChange = {
                                    accelVal = it
                                    onHarshAccelChange(it)
                                },
                                valueRange = 1.8f..5.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = HighRiskOrange,
                                    activeTrackColor = HighRiskOrange
                                )
                            )
                        }

                        // Swerve slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Lateral Swerve / Cornering", color = TextMutedGrey, fontSize = 11.5.sp)
                                Text(
                                    String.format(Locale.US, "%.1f m/s²", swerveVal),
                                    color = BrightNeonGreen,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = swerveVal,
                                onValueChange = {
                                    swerveVal = it
                                    onHarshSwerveChange(it)
                                },
                                valueRange = 1.8f..5.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = BrightNeonGreen,
                                    activeTrackColor = BrightNeonGreen
                                )
                            )
                        }

                        // Reset button for tunables
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            PresetPill("Reset Defaults") {
                                brakeVal = -3.4f
                                accelVal = 3.4f
                                swerveVal = 3.4f
                                onHarshBrakingChange(-3.4f)
                                onHarshAccelChange(3.4f)
                                onHarshSwerveChange(3.4f)
                            }
                        }
                    }
                }
            }

            // 2. FASTAPI ML ENGINE & SERVER CONFIGURATION CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkHudCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = "Network",
                                tint = BrightNeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "BACKEND SERVER CONFIGURATION",
                                color = TextWhite,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = {
                                serverUrlInput = it
                                onServerUrlChange(it)
                            },
                            label = { Text("Drive.Net Backend URL", color = TextMutedGrey) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = BrightNeonGreen,
                                unfocusedBorderColor = Color(0xFF2B3648),
                                focusedLabelColor = BrightNeonGreen
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quick presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresetPill("USB / ADB (127.0.0.1)") {
                                serverUrlInput = "http://127.0.0.1:8000/"
                                onServerUrlChange(serverUrlInput)
                            }
                            PresetPill("Wi-Fi (10.60.0.224)") {
                                serverUrlInput = "http://10.60.0.224:8000/"
                                onServerUrlChange(serverUrlInput)
                            }
                            PresetPill("Emulator") {
                                serverUrlInput = "http://10.0.2.2:8000/"
                                onServerUrlChange(serverUrlInput)
                            }
                        }

                        // Ping Test Button
                        Button(
                            onClick = {
                                isPinging = true
                                onPingServer(serverUrlInput) { success, msg ->
                                    isPinging = false
                                    pingSuccess = success
                                    pingResultText = msg
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrightNeonGreen,
                                contentColor = TextBlack
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isPinging) "TESTING CONNECTION..." else "PING BACKEND SERVER",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                letterSpacing = 0.8.sp
                            )
                        }

                        // Ping Result Box
                        pingResultText?.let { result ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (pingSuccess == true) Color(0x2200FF66) else Color(0x22FF3333),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (pingSuccess == true) BrightNeonGreen else HighRiskRed,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (pingSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (pingSuccess == true) BrightNeonGreen else HighRiskRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = result,
                                        color = TextWhite,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. LIVE INERTIAL & IMU SENSORS
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkHudCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = BrightNeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "INERTIAL MEASUREMENT UNIT (IMU)",
                                color = TextWhite,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }

                        val rawX = telemetry?.rawAccelX ?: 0f
                        val rawY = telemetry?.rawAccelY ?: 0f
                        val rawZ = telemetry?.rawAccelZ ?: 9.81f
                        val gyro = telemetry?.gyroRate ?: 0f

                        DiagRow("Raw Accel X", String.format(Locale.US, "%.2f m/s²", rawX))
                        DiagRow("Raw Accel Y", String.format(Locale.US, "%.2f m/s²", rawY))
                        DiagRow("Raw Accel Z", String.format(Locale.US, "%.2f m/s²", rawZ))
                        DiagRow("Linear Accel Filtered", String.format(Locale.US, "%.3f g", (telemetry?.accelXFiltered ?: 0f)))
                        DiagRow("Gyroscope Turn Rate", String.format(Locale.US, "%.2f rad/s", gyro))
                    }
                }
            }

            // 4. GNSS & GPS RECEIVER STATUS
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkHudCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "GNSS / GPS POSITIONING",
                            color = TextWhite,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )

                        DiagRow("Satellites Locked", "$satellitesCount SATS")
                        DiagRow("Horizontal Precision", String.format(Locale.US, "%.1f M (HDOP)", hdop))
                        DiagRow("Latitude", String.format(Locale.US, "%.5f°", telemetry?.latitude ?: 0.0))
                        DiagRow("Longitude", String.format(Locale.US, "%.5f°", telemetry?.longitude ?: 0.0))
                        DiagRow("Altitude", String.format(Locale.US, "%.1f m", telemetry?.altitude ?: 0.0))
                    }
                }
            }

            // 5. ROLLING BUFFER STATUS
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkHudCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "ML ROLLING BUFFER STATUS",
                            color = TextWhite,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )

                        DiagRow("Buffered Samples", "$bufferSize / 600 points")
                        DiagRow("Window Span", String.format(Locale.US, "%.1f s / 60.0s", bufferSpanSeconds))
                        DiagRow("Required Min History", "30.0 seconds")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextMutedGrey,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = Color(0xFFDDE6F0),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PresetPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xFF1E2533), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF2C374A), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = BrightNeonGreen,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
