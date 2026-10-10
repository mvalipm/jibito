package ir.jibito.app.ui.welcome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** صفحه‌ی توضیح نوتیف فقط وقتی می‌آید که اندروید واقعاً اجازه بخواهد و هنوز داده نشده باشد */
class NotificationIntroTest {

    @Test
    fun shownOnAndroid13AndLaterWhenNotGranted() {
        assertTrue(notificationIntroNeeded(sdkInt = 33, granted = false))
        assertTrue(notificationIntroNeeded(sdkInt = 36, granted = false))
    }

    @Test
    fun notShownWhenAlreadyGranted() {
        assertFalse(notificationIntroNeeded(sdkInt = 33, granted = true))
        assertFalse(notificationIntroNeeded(sdkInt = 36, granted = true))
    }

    /** پیش از اندروید ۱۳ نوتیف اجازه نمی‌خواهد؛ صفحه‌ای که دکمه‌اش کاری نمی‌کند نباید بیاید */
    @Test
    fun notShownBeforeAndroid13() {
        assertFalse(notificationIntroNeeded(sdkInt = 32, granted = false))
        assertFalse(notificationIntroNeeded(sdkInt = 26, granted = false))
    }
}
