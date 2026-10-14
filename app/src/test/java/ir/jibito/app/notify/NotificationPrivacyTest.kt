package ir.jibito.app.notify

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** روی صفحه‌ی قفل مبلغ و اسم فروشگاه نباید دیده شود: نسخه‌ی عمومی نوتیف فقط یک اعلان کلی است. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationPrivacyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `نوتیف خصوصی است و نسخه‌ی عمومی مبلغ و فروشگاه ندارد`() {
        val notification = NotificationCompat.Builder(context, "test")
            .setSmallIcon(R.drawable.ic_stat_jibito)
            .setContentTitle("−۱۸۵ هزار · کافه لمیز")
            .setContentText("مال چی بود؟")
            .hideOnLockScreen(context, "test")
            .build()

        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notification.visibility)
        val public = notification.publicVersion
        assertNotNull(public)
        val title = public!!.extras.getCharSequence("android.title").toString()
        val text = public.extras.getCharSequence("android.text").toString()
        assertEquals(context.getString(R.string.app_name), title)
        assertFalse((title + text).contains("لمیز"))
        assertFalse((title + text).contains("۱۸۵"))
    }
}
