package com.safecruise.app.data.repository

import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriverProfileRepositoryTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        DriverProfileRepository.resetForTesting()
    }

    @After
    fun tearDown() {
        DriverProfileRepository.resetForTesting()
    }

    @Test
    fun testDefaultDriverNameIsVaishnav() {
        assertEquals("Vaishnav", DriverProfileRepository.DEFAULT_DRIVER_NAME)
        assertEquals("Vaishnav", DriverProfileRepository.driverName.value)
    }

    @Test
    fun testValidateNameSuccess() {
        val result = DriverProfileRepository.validateName("Vaishnav")
        assertTrue(result.isSuccess)
        assertEquals("Vaishnav", result.getOrNull())
    }

    @Test
    fun testValidateNameTrimsWhitespace() {
        val result = DriverProfileRepository.validateName("   Vaishnav Kumar   ")
        assertTrue(result.isSuccess)
        assertEquals("Vaishnav Kumar", result.getOrNull())
    }

    @Test
    fun testValidateNameEmptyFails() {
        val emptyResult = DriverProfileRepository.validateName("")
        assertTrue(emptyResult.isFailure)
        assertEquals("Driver name cannot be empty.", emptyResult.exceptionOrNull()?.message)

        val whitespaceResult = DriverProfileRepository.validateName("    ")
        assertTrue(whitespaceResult.isFailure)
        assertEquals("Driver name cannot be empty.", whitespaceResult.exceptionOrNull()?.message)

        val nullResult = DriverProfileRepository.validateName(null)
        assertTrue(nullResult.isFailure)
    }

    @Test
    fun testValidateNameMaxLengthEnforced() {
        val exact40Chars = "A".repeat(40)
        val validResult = DriverProfileRepository.validateName(exact40Chars)
        assertTrue(validResult.isSuccess)
        assertEquals(40, validResult.getOrNull()?.length)

        val tooLong41Chars = "A".repeat(41)
        val invalidResult = DriverProfileRepository.validateName(tooLong41Chars)
        assertTrue(invalidResult.isFailure)
        assertEquals("Driver name cannot exceed 40 characters.", invalidResult.exceptionOrNull()?.message)
    }

    @Test
    fun testUpdateDriverNamePersistsAndUpdatesStateImmediately() {
        DriverProfileRepository.setSharedPreferencesForTesting(fakePrefs)

        val updateResult = DriverProfileRepository.updateDriverName(null, "Vaishnav R")
        assertTrue(updateResult.isSuccess)
        assertEquals("Vaishnav R", updateResult.getOrNull())

        // In-memory state updated immediately
        assertEquals("Vaishnav R", DriverProfileRepository.driverName.value)

        // Key persisted scoped to driver ID 408
        val saved = fakePrefs.getString("driver_name_408", null)
        assertEquals("Vaishnav R", saved)
    }

    @Test
    fun testUpdateDriverNameScopedToDriverIdWithoutModifyingOtherDrivers() {
        DriverProfileRepository.setSharedPreferencesForTesting(fakePrefs)

        // Pre-populate another driver's profile
        fakePrefs.putString("driver_name_999", "Another Driver")

        val result = DriverProfileRepository.updateDriverName(null, "Vaishnav")
        assertTrue(result.isSuccess)

        // Verify other driver untouched
        assertEquals("Another Driver", fakePrefs.getString("driver_name_999", null))
        assertEquals("Vaishnav", fakePrefs.getString("driver_name_408", null))
    }

    @Test
    fun testInvalidNameUpdateRetainsPreviousName() {
        DriverProfileRepository.setSharedPreferencesForTesting(fakePrefs)
        DriverProfileRepository.updateDriverName(null, "Vaishnav Initial")
        assertEquals("Vaishnav Initial", DriverProfileRepository.driverName.value)

        // Attempt invalid update with blank name
        val invalidResult = DriverProfileRepository.updateDriverName(null, "   ")
        assertTrue(invalidResult.isFailure)

        // Previous name retained
        assertEquals("Vaishnav Initial", DriverProfileRepository.driverName.value)
        assertEquals("Vaishnav Initial", fakePrefs.getString("driver_name_408", null))
    }

    @Test
    fun testInitRestoresPersistedNameOnStartup() {
        fakePrefs.putString("driver_name_408", "Vaishnav Restored")
        DriverProfileRepository.setSharedPreferencesForTesting(fakePrefs)

        // Simulate new session startup by reading from prefs
        val mockContext = null // init handles customPrefs directly
        DriverProfileRepository.resetForTesting()
        DriverProfileRepository.setSharedPreferencesForTesting(fakePrefs)
        
        // Mock init call using dummy context since customPrefs is set
        val constructor = android.content.ContextWrapper::class.java.getDeclaredConstructor(android.content.Context::class.java)
        constructor.isAccessible = true
        val dummyContext = constructor.newInstance(null)
        DriverProfileRepository.init(dummyContext)

        assertEquals("Vaishnav Restored", DriverProfileRepository.driverName.value)
    }

    @Test
    fun testGracefulFailureWhenPersistenceFails() {
        val failingPrefs = FailingSharedPreferences()
        DriverProfileRepository.setSharedPreferencesForTesting(failingPrefs)

        val result = DriverProfileRepository.updateDriverName(null, "New Name")
        assertTrue(result.isFailure)

        // Retains default name
        assertEquals("Vaishnav", DriverProfileRepository.driverName.value)
    }
}

/**
 * Lightweight in-memory fake for SharedPreferences in JVM unit tests.
 */
open class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    fun putString(key: String, value: String?) {
        if (value != null) data[key] = value else data.remove(key)
    }

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor(data)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val backingMap: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val tempMap = mutableMapOf<String, Any?>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) tempMap[key] = value
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) tempMap[key] = null
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            tempMap.clear()
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            for ((k, v) in tempMap) {
                if (v == null) backingMap.remove(k) else backingMap[k] = v
            }
        }
    }
}

/**
 * Failing SharedPreferences to test persistence exception handling.
 */
class FailingSharedPreferences : FakeSharedPreferences() {
    override fun edit(): SharedPreferences.Editor {
        throw RuntimeException("Storage I/O error or permission denied")
    }
}
