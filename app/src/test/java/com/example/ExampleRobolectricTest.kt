package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.NotificationSoundMode
import com.example.model.ObserverStation
import com.example.security.SecurityVaultManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name and verify encrypted observer and sound mode vault`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Satellite Tracker", appName)

        val vault = SecurityVaultManager(context)
        val customStation = ObserverStation(
            name = "Bhubaneswar Observatory",
            region = "India",
            latitudeDeg = 20.2961,
            longitudeDeg = 85.8245,
            altitudeMeters = 45.0,
            isGpsDerived = false
        )
        vault.saveEncryptedObserverStation(customStation)
        val loaded = vault.loadEncryptedObserverStation()
        assertNotNull(loaded)
        assertEquals("Bhubaneswar Observatory", loaded.name)
        assertEquals(20.2961, loaded.latitudeDeg, 0.001)
        assertEquals(85.8245, loaded.longitudeDeg, 0.001)

        // Verify NotificationSoundMode and AlertLeadTimeMinutes encrypted persistence
        vault.saveNotificationSoundMode(NotificationSoundMode.SYSTEM_DEFAULT)
        assertEquals(NotificationSoundMode.SYSTEM_DEFAULT, vault.loadNotificationSoundMode())

        vault.saveNotificationSoundMode(NotificationSoundMode.FEMALE_VOICE_ANNOUNCER)
        assertEquals(NotificationSoundMode.FEMALE_VOICE_ANNOUNCER, vault.loadNotificationSoundMode())

        vault.saveAlertLeadTimeMinutes(5)
        assertEquals(5, vault.loadAlertLeadTimeMinutes())
    }
}
