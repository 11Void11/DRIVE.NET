package com.safecruise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mapbox.common.MapboxOptions
import com.safecruise.app.data.repository.DriverProfileRepository
import com.safecruise.app.ui.dashboard.DashboardScreen
import com.safecruise.app.ui.theme.SafeCruiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DriverProfileRepository.init(applicationContext)
        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)
        enableEdgeToEdge()
        setContent {
            SafeCruiseTheme {
                DashboardScreen()
            }
        }
    }
}
