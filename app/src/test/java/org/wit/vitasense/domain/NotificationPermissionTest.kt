package org.wit.vitasense.domain

import android.Manifest
import android.app.Application
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.wit.vitasense.background.ReminderWorker

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class NotificationPermissionTest {
    @Test fun deniedPermissionBlocksDelivery() {
        val context=RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(ReminderWorker.allowed(context))
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReminderWorker.channels(context)
        assertTrue(ReminderWorker.allowed(context))
    }
}
