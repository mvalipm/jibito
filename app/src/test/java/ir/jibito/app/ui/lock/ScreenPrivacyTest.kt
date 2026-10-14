package ir.jibito.app.ui.lock

import android.app.Activity
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** با قفل اپ، پنجره امن می‌شود (اسکرین‌شات و برنامه‌های اخیر)؛ بدون قفل دوباره عادی. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenPrivacyTest {

    private fun secure(activity: Activity) =
        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE

    @Test
    fun `قفل روشن ← FLAG_SECURE و خاموش ← برداشته می‌شود`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        assertEquals(0, secure(activity))
        ScreenPrivacy.apply(activity.window, hidden = true)
        assertTrue(secure(activity) != 0)
        ScreenPrivacy.apply(activity.window, hidden = false)
        assertEquals(0, secure(activity))
    }
}
