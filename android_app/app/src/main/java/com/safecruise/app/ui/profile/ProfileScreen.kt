package com.safecruise.app.ui.profile

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.safecruise.app.R
import com.safecruise.app.data.model.TripRecord
import com.safecruise.app.data.repository.DriverProfileRepository
import com.safecruise.app.ui.theme.BrightNeonGreen
import com.safecruise.app.ui.theme.DarkBackground
import com.safecruise.app.ui.theme.DarkCardBorder
import com.safecruise.app.ui.theme.DarkHudCard
import com.safecruise.app.ui.theme.DarkHudSubCard
import com.safecruise.app.ui.theme.HighRiskRed
import com.safecruise.app.ui.theme.TextBlack
import com.safecruise.app.ui.theme.TextMutedGrey
import com.safecruise.app.ui.theme.TextWhite
import java.util.Locale

@Composable
fun ProfileScreen(
    driverName: String = DriverProfileRepository.driverName.value,
    tripHistory: List<TripRecord> = emptyList(),
    currentDistanceKm: Float = 0f,
    currentDurationSec: Long = 0L,
    onSaveDriverName: (String) -> Result<String> = { DriverProfileRepository.updateDriverName(null, it) },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var candidateName by remember(driverName) { mutableStateOf(driverName) }
    var editErrorMessage by remember { mutableStateOf<String?>(null) }

    var autoLogEnabled by remember { mutableStateOf(true) }
    var soundAlertsEnabled by remember { mutableStateOf(true) }
    var highPrecisionGps by remember { mutableStateOf(true) }

    // Dynamically calculate career stats from real recorded trips + active drive
    val totalRecordedDistance = tripHistory.sumOf { it.distanceKm.toDouble() }.toFloat() + currentDistanceKm
    val totalRecordedSeconds = tripHistory.sumOf { it.durationSec } + currentDurationSec
    val totalTripsCount = tripHistory.size + (if (currentDistanceKm > 0.05f) 1 else 0)

    val cleanTripsCount = tripHistory.count { it.status == "Optimal" || it.riskScore < 25 }
    val safetyScore = if (tripHistory.isNotEmpty()) {
        val avgRisk = tripHistory.map { it.riskScore }.average().toInt()
        (100 - avgRisk).coerceIn(60, 100)
    } else 100

    val safetyGrade = when {
        safetyScore >= 90 -> "A+"
        safetyScore >= 80 -> "A"
        safetyScore >= 70 -> "B"
        else -> "C"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DRIVER PROFILE",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "DRIVE.NET TELEMETRY ID",
                    color = BrightNeonGreen,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .background(Color(0xFF1E2533), CircleShape)
                    .size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // DRIVER BADGE CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.2.dp, DarkCardBorder, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkHudCard)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_avatar),
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .border(2.dp, BrightNeonGreen, CircleShape)
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.clickable {
                                    candidateName = driverName
                                    editErrorMessage = null
                                    showEditDialog = true
                                }
                            ) {
                                Text(
                                    text = driverName,
                                    color = TextWhite,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Driver",
                                    tint = BrightNeonGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                IconButton(
                                    onClick = {
                                        candidateName = driverName
                                        editErrorMessage = null
                                        showEditDialog = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Driver Name",
                                        tint = BrightNeonGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .background(Color(0x3300FF66), RoundedCornerShape(6.dp))
                                    .border(1.dp, BrightNeonGreen, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "SAFETY SCORE: $safetyScore / 100 • $safetyGrade",
                                    color = BrightNeonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Text(
                                text = "Drive.Net Connected • Driver #${DriverProfileRepository.DRIVER_ID}",
                                color = TextMutedGrey,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // CAREER STATS (Derived from real trips)
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
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "CAREER TELEMETRY STATS",
                            color = TextMutedGrey,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )

                        val distanceDisplay = String.format(Locale.US, "%.2f km", totalRecordedDistance)
                        val hours = totalRecordedSeconds / 3600
                        val mins = (totalRecordedSeconds % 3600) / 60
                        val secs = totalRecordedSeconds % 60
                        val timeDisplay = when {
                            hours > 0 -> String.format(Locale.US, "%d.%01d hrs", hours, mins / 6)
                            mins > 0 -> String.format(Locale.US, "%d min", mins)
                            else -> String.format(Locale.US, "%d sec", secs)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ProfileStatBox(title = "TOTAL DISTANCE", value = distanceDisplay)
                            ProfileStatBox(title = "TOTAL TIME", value = timeDisplay)
                        }

                        val cleanPct = if (totalTripsCount > 0) {
                            String.format(Locale.US, "%.0f%%", (cleanTripsCount.toFloat() / totalTripsCount) * 100f)
                        } else "100%"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ProfileStatBox(title = "TRIPS MONITORED", value = "$totalTripsCount ${if (totalTripsCount == 1) "Trip" else "Trips"}")
                            ProfileStatBox(title = "CLEAN DRIVES", value = cleanPct)
                        }
                    }
                }
            }

            // VEHICLE & SYSTEM PREFERENCES
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
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "PREFERENCES & TELEMETRY",
                            color = TextMutedGrey,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )

                        PreferenceToggle(
                            title = "Auto-Log Trips",
                            subtitle = "Automatically record drives when vehicle moves",
                            checked = autoLogEnabled,
                            onCheckedChange = { autoLogEnabled = it }
                        )

                        PreferenceToggle(
                            title = "High Risk Audio Chimes",
                            subtitle = "Sound warning chime on harsh maneuvers",
                            checked = soundAlertsEnabled,
                            onCheckedChange = { soundAlertsEnabled = it }
                        )

                        PreferenceToggle(
                            title = "High Precision GNSS (10Hz)",
                            subtitle = "Maximum satellite precision sampling",
                            checked = highPrecisionGps,
                            onCheckedChange = { highPrecisionGps = it }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        // COMPACT DRIVER NAME EDIT DIALOG
        if (showEditDialog) {
            Dialog(onDismissRequest = { showEditDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .border(1.5.dp, DarkCardBorder, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkHudCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "EDIT DRIVER NAME",
                                color = TextWhite,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "DRIVE.NET PROFILE • ID #${DriverProfileRepository.DRIVER_ID}",
                                color = BrightNeonGreen,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedTextField(
                            value = candidateName,
                            onValueChange = {
                                if (it.length <= DriverProfileRepository.MAX_NAME_LENGTH) {
                                    candidateName = it
                                    editErrorMessage = null
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrightNeonGreen,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedContainerColor = DarkHudSubCard,
                                unfocusedContainerColor = DarkHudSubCard,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                cursorColor = BrightNeonGreen,
                                errorBorderColor = HighRiskRed,
                                errorContainerColor = DarkHudSubCard
                            ),
                            isError = editErrorMessage != null,
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (editErrorMessage != null) {
                                        Text(
                                            text = editErrorMessage ?: "",
                                            color = HighRiskRed,
                                            fontSize = 11.sp
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }
                                    Text(
                                        text = "${candidateName.trim().length}/${DriverProfileRepository.MAX_NAME_LENGTH}",
                                        color = TextMutedGrey,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showEditDialog = false },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .border(1.dp, Color(0xFF232C3D), RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF19202E))
                            ) {
                                Text(
                                    text = "CANCEL",
                                    color = TextMutedGrey,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    val validation = DriverProfileRepository.validateName(candidateName)
                                    if (validation.isFailure) {
                                        editErrorMessage = validation.exceptionOrNull()?.message ?: "Invalid name"
                                    } else {
                                        val saveResult = onSaveDriverName(validation.getOrThrow())
                                        if (saveResult.isSuccess) {
                                            showEditDialog = false
                                            editErrorMessage = null
                                        } else {
                                            editErrorMessage = saveResult.exceptionOrNull()?.message ?: "Failed to save profile name"
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrightNeonGreen)
                            ) {
                                Text(
                                    text = "SAVE",
                                    color = TextBlack,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatBox(title: String, value: String) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .border(1.dp, Color(0xFF1E2636), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkHudSubCard)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = TextMutedGrey,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = value,
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun PreferenceToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextWhite,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TextMutedGrey,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextBlack,
                checkedTrackColor = BrightNeonGreen,
                uncheckedThumbColor = TextMutedGrey,
                uncheckedTrackColor = Color(0xFF202836)
            )
        )
    }
}
