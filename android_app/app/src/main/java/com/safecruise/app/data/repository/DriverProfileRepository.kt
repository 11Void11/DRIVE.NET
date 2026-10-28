package com.safecruise.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository responsible for managing and persisting the Driver Profile,
 * specifically the editable driver name, scoped to the authenticated driver ID.
 */
object DriverProfileRepository {

    const val DEFAULT_DRIVER_NAME = "Vaishnav"
    const val DRIVER_ID = "408"
    const val MAX_NAME_LENGTH = 40
    private const val PREFS_NAME = "safecruise_driver_profile"
    private const val KEY_PREFIX = "driver_name_"

    private val _driverName = MutableStateFlow(DEFAULT_DRIVER_NAME)
    val driverName: StateFlow<String> = _driverName.asStateFlow()

    private var customPrefs: SharedPreferences? = null

    /**
     * Initializes the repository with application context, loading any previously saved name.
     */
    fun init(context: Context) {
        try {
            val prefs = customPrefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = "$KEY_PREFIX$DRIVER_ID"
            val savedName = prefs.getString(key, null)
            if (!savedName.isNullOrBlank()) {
                val validation = validateName(savedName)
                if (validation.isSuccess) {
                    _driverName.value = validation.getOrThrow()
                }
            }
        } catch (e: Exception) {
            // Retain default if reading fails
            _driverName.value = DEFAULT_DRIVER_NAME
        }
    }

    /**
     * Validates candidate driver name:
     * - Must not be empty or solely whitespace
     * - Trims leading and trailing whitespace
     * - Must not exceed MAX_NAME_LENGTH characters
     */
    fun validateName(rawName: String?): Result<String> {
        if (rawName == null) {
            return Result.failure(IllegalArgumentException("Driver name cannot be empty."))
        }
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Driver name cannot be empty."))
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            return Result.failure(IllegalArgumentException("Driver name cannot exceed $MAX_NAME_LENGTH characters."))
        }
        return Result.success(trimmed)
    }

    /**
     * Updates and persists the driver name for the current driver profile.
     * Retains the existing name if validation or persistence fails.
     */
    fun updateDriverName(context: Context?, newName: String?): Result<String> {
        val validation = validateName(newName)
        if (validation.isFailure) {
            return validation
        }

        val validatedName = validation.getOrThrow()

        // Persist if context or customPrefs is provided
        val prefs = customPrefs ?: context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs != null) {
            try {
                val key = "$KEY_PREFIX$DRIVER_ID"
                val committed = prefs.edit().putString(key, validatedName).commit()
                if (!committed) {
                    return Result.failure(IllegalStateException("Failed to persist driver profile name."))
                }
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }

        // Update in-memory state immediately
        _driverName.value = validatedName
        return Result.success(validatedName)
    }

    /**
     * Injects custom SharedPreferences for unit testing.
     */
    fun setSharedPreferencesForTesting(prefs: SharedPreferences?) {
        customPrefs = prefs
    }

    /**
     * Resets repository state to defaults (useful for test isolation).
     */
    fun resetForTesting() {
        customPrefs = null
        _driverName.value = DEFAULT_DRIVER_NAME
    }
}
