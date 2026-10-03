package ir.jibito.app.data.wallet

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * حساب‌هایی که کاربر از «موجودی همه‌ی حساب‌ها» کنار گذاشته (مثلاً حساب مشترک یا قرض‌الحسنه).
 * فقط روی جمع بالای صفحه‌ی تراکنش‌ها اثر دارد؛ خود حساب و تراکنش‌هایش دست نمی‌خورند.
 */
class WalletSettings(context: Context) {

    private val prefs = context.getSharedPreferences("wallet", Context.MODE_PRIVATE)

    private val _excluded = MutableStateFlow(
        prefs.getStringSet(KEY_EXCLUDED, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
    )
    /** شناسه‌ی بانک‌هایی که در جمع حساب نمی‌شوند */
    val excludedBanks: StateFlow<Set<Int>> = _excluded.asStateFlow()

    fun setIncluded(bankId: Int, included: Boolean) {
        val next = if (included) _excluded.value - bankId else _excluded.value + bankId
        prefs.edit().putStringSet(KEY_EXCLUDED, next.map { it.toString() }.toSet()).apply()
        _excluded.value = next
    }

    private companion object {
        const val KEY_EXCLUDED = "excluded_banks"
    }
}
