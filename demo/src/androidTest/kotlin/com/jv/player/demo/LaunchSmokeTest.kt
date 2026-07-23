package com.jv.player.demo

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation smoke test (plan §6 Wave 0: "instrumentation-on-emulator smoke").
 * Proves the empty :demo shell launches to RESUMED without crashing on a device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @Test
    fun appContextHasExpectedPackage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("com.jv.player.demo", context.packageName)
    }

    @Test
    fun mainActivityLaunchesToResumed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertNotNull(scenario)
            scenario.onActivity { activity -> assertNotNull(activity) }
        }
    }
}
