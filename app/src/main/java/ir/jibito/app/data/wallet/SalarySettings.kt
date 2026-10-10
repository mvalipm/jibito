package ir.jibito.app.data.wallet

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** جواب کاربر به «این واریز حقوقته؟»: حقوق تأییدشده و واریزکننده‌هایی که گفته «نه» */
data class SalaryChoice(val confirmed: Salary?, val dismissed: Set<String>)

/**
 * حقوقی که کاربر تأیید کرده (برای «پولم تا حقوق بعدی می‌رسه؟») و پیشنهادهای ردشده؛ با پشتیبان منتقل می‌شود
 * (BackupManager.BACKED_UP_PREFS) تا بعد از بازگردانی سؤال «این واریز حقوقته؟» دوباره نیاید.
 * مبلغ و روزِ ذخیره‌شده فقط پشتوانه‌اند: تا وقتی همان واریز هنوز تشخیص داده می‌شود، عددهای تازه‌ی آن به کار می‌رود.
 */
class SalarySettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _choice = MutableStateFlow(load())
    val choice: StateFlow<SalaryChoice> = _choice.asStateFlow()

    /** «آره، حقوقمه» */
    fun confirm(salary: Salary) {
        prefs.edit {
            putString(KEY_KEY, salary.key)
            putInt(KEY_BANK, salary.bankId)
            putLong(KEY_AMOUNT, salary.amountRial)
            putInt(KEY_DAY, salary.dayOfMonth)
        }
        _choice.value = load()
    }

    /** «نه» (یا «این حقوقم نیست» برای حقوقی که قبلاً تأیید شده): دیگر پیشنهاد نمی‌شود */
    fun dismiss(key: String) {
        val confirmed = _choice.value.confirmed
        prefs.edit {
            putStringSet(KEY_DISMISSED, _choice.value.dismissed + key)
            if (confirmed?.key == key) {
                remove(KEY_KEY)
                remove(KEY_BANK)
                remove(KEY_AMOUNT)
                remove(KEY_DAY)
            }
        }
        _choice.value = load()
    }

    /** «برگردون»: جواب‌های ذخیره‌شده دقیقاً همان‌طور که پیش از جواب دادن بود */
    fun restore(previous: SalaryChoice) {
        prefs.edit {
            val confirmed = previous.confirmed
            if (confirmed == null) {
                remove(KEY_KEY)
                remove(KEY_BANK)
                remove(KEY_AMOUNT)
                remove(KEY_DAY)
            } else {
                putString(KEY_KEY, confirmed.key)
                putInt(KEY_BANK, confirmed.bankId)
                putLong(KEY_AMOUNT, confirmed.amountRial)
                putInt(KEY_DAY, confirmed.dayOfMonth)
            }
            putStringSet(KEY_DISMISSED, previous.dismissed)
        }
        _choice.value = load()
    }

    private fun load(): SalaryChoice {
        val key = prefs.getString(KEY_KEY, null)
        val confirmed = key?.let {
            Salary(it, prefs.getInt(KEY_BANK, 0), prefs.getLong(KEY_AMOUNT, 0), prefs.getInt(KEY_DAY, 1))
        }
        return SalaryChoice(confirmed, prefs.getStringSet(KEY_DISMISSED, emptySet()).orEmpty().toSet())
    }

    companion object {
        /** در پشتیبان‌گیری هم ذخیره می‌شود (BackupManager.BACKED_UP_PREFS) */
        const val PREFS = "salary"
        private const val KEY_KEY = "key"
        private const val KEY_BANK = "bank"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_DAY = "day"
        private const val KEY_DISMISSED = "dismissed"
    }
}
