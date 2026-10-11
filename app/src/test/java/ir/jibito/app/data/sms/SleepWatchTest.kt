package ir.jibito.app.data.sms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.repository.SOURCE_SMS_AUTO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «گوشی منو خوابونده؟»: کدام خرج «دیر» است و کی کارت می‌آید */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepWatchTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    private fun flow(
        ageMillis: Long,
        type: FlowType = FlowType.WITHDRAWAL,
        merchant: String? = "اسنپ",
        failed: Boolean = false,
        transfer: Int = TransactionFlowEntity.TRANSFER_NONE,
    ) = TransactionFlowEntity(
        smsId = 1, bankId = 1, flowType = type.code, amount = 1_000_000, remainAfter = null,
        dateEpoch = now - ageMillis, merchant = merchant, suggestedCategory = null, isFailedPurchase = failed,
        categoryId = null, description = null, smsContent = "", source = SOURCE_SMS_AUTO, transferState = transfer,
    )

    @Test
    fun `فقط خرج واقعی که بیش از نیم ساعت بعد پیدا شده دیر است`() {
        val finds = SleepWatch.lateFinds(
            listOf(
                flow(2 * 60 * minute),
                flow(10 * minute),
                flow(3 * 60 * minute, type = FlowType.DEPOSIT),
                flow(3 * 60 * minute, failed = true),
                flow(3 * 60 * minute, transfer = TransactionFlowEntity.TRANSFER_SELF),
            ),
            now,
        )
        assertEquals(1, finds.size)
        assertEquals(2 * 60 * minute, finds[0].lagMillis)
        assertEquals("اسنپ", finds[0].label)
    }

    @Test
    fun `بی اسم فروشگاه، اسم بانک`() {
        val find = SleepWatch.lateFinds(listOf(flow(60 * minute, merchant = null)), now).single()
        assertNotNull(find.label)
    }

    @Test
    fun `یک خرج دیر اتفاقی است؛ دو تا در هفته کارت را می‌آورد`() {
        val one = listOf(LateFind(now - day, 2 * 60 * minute, "اسنپ"))
        assertNull(SleepWatch.alertOf(one, now, resetAt = 0, snoozedUntil = 0))
        val two = one + LateFind(now - 2 * day, 45 * minute, null)
        val alert = SleepWatch.alertOf(two, now, resetAt = 0, snoozedUntil = 0)!!
        assertEquals(2, alert.count)
        // نمونه: تازه‌ترینِ اسم‌دار
        assertEquals("اسنپ", alert.example.label)
    }

    @Test
    fun `قدیمی‌تر از هفته، بعد از «انجام دادم» و زمان «فعلاً نه» حساب نمی‌شوند`() {
        val finds = listOf(LateFind(now - 8 * day, minute * 40, "الف"), LateFind(now - day, minute * 40, "ب"))
        assertNull(SleepWatch.alertOf(finds, now, resetAt = 0, snoozedUntil = 0))
        val recent = listOf(LateFind(now - 3 * day, minute * 40, "الف"), LateFind(now - day, minute * 40, "ب"))
        assertNotNull(SleepWatch.alertOf(recent, now, resetAt = 0, snoozedUntil = 0))
        assertNull(SleepWatch.alertOf(recent, now, resetAt = now - 2 * day, snoozedUntil = 0))
        assertNull(SleepWatch.alertOf(recent, now, resetAt = 0, snoozedUntil = now + day))
    }

    @Test
    fun `ذخیره و خواندن، حتی با اسم خالی یا خط عمودی`() {
        val finds = listOf(LateFind(1, 2, "کافه | لمیز"), LateFind(3, 4, null))
        val back = SleepWatch.decode(SleepWatch.encode(finds))
        assertEquals(listOf(LateFind(1, 2, "کافه   لمیز"), LateFind(3, 4, null)), back)
        assertEquals(emptyList<LateFind>(), SleepWatch.decode(null))
    }

    @Test
    fun `روی گوشی، ثبت و «انجام دادم» و «فعلاً نه»`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("sleep_watch", Context.MODE_PRIVATE).edit().clear().commit()
        val watch = SleepWatch(context)
        watch.record(listOf(LateFind(now - day, 2 * 60 * minute, "اسنپ"), LateFind(now - minute, 50 * minute, "افق کوروش")), now)
        assertEquals(2, watch.alert(now)?.count)
        // «انجام دادم»: قبلی‌ها دیگر شمرده نمی‌شوند؛ دو خرج دیرِ تازه کارت را برمی‌گرداند
        watch.markFixed(now)
        assertNull(watch.alert(now + minute))
        watch.record(listOf(LateFind(now + 2 * minute, 40 * minute, "اسنپ"), LateFind(now + 2 * minute, 35 * minute, null)), now + 2 * minute)
        assertEquals(2, watch.alert(now + 3 * minute)?.count)
        // «فعلاً نه»: دو هفته ساکت
        watch.snooze(now + 3 * minute)
        assertNull(watch.alert(now + 4 * minute))
    }
}
