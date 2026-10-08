package ir.jibito.app.data.wallet

import android.content.Context
import androidx.core.content.edit
import ir.jibito.app.domain.BankBalance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * حساب‌هایی که کاربر از «موجودی همه‌ی حساب‌ها» کنار گذاشته (مثلاً حساب مشترک یا قرض‌الحسنه).
 * فقط روی جمع بالای صفحه‌ی تراکنش‌ها اثر دارد؛ خود حساب و تراکنش‌هایش دست نمی‌خورند.
 * کلیدها: WalletExclusion («بانک:شماره»، و «بانک:*» برای کل بانک).
 */
class WalletSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _excluded = MutableStateFlow(load())

    /** کلید حساب‌هایی که در جمع حساب نمی‌شوند */
    val excluded: StateFlow<Set<String>> = _excluded.asStateFlow()

    /** روشن/خاموش کردن یک حساب در جمع؛ [all] همه‌ی مانده‌های فعلی (برای حساب‌های دیگرِ همان بانک) */
    fun setIncluded(balance: BankBalance, included: Boolean, all: List<BankBalance>) {
        val sameBank = all.filter { it.bank.id == balance.bank.id }.map { it.account }
        set(WalletExclusion.toggle(balance.bank.id, balance.account, included, _excluded.value, sameBank))
    }

    /** برای «برگردان»: همان حالت قبلی */
    fun set(excluded: Set<String>) {
        prefs.edit {
            putStringSet(KEY_EXCLUDED, HashSet(excluded))
            remove(KEY_LEGACY)
        }
        _excluded.value = excluded
    }

    /** نسخه‌های قبل فقط شناسه‌ی بانک نگه می‌داشتند ← همان بانک، کامل، بیرون از جمع می‌ماند */
    private fun load(): Set<String> {
        val current = prefs.getStringSet(KEY_EXCLUDED, null)?.toSet()
        val legacy = prefs.getStringSet(KEY_LEGACY, emptySet()).orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .map { WalletExclusion.wholeBank(it) }
        return current.orEmpty() + legacy
    }

    companion object {
        /** در پشتیبان‌گیری هم ذخیره می‌شود (BackupManager.BACKED_UP_PREFS) */
        const val PREFS = "wallet"
        private const val KEY_EXCLUDED = "excluded_accounts"
        /** کلید نسخه‌های قبل (شناسه‌ی بانک‌ها) */
        private const val KEY_LEGACY = "excluded_banks"
    }
}
