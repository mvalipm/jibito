package ir.jibito.app.data.category

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * تنظیم نمایش دسته‌ها در برگه‌ی انتخاب (و ثبت دستی):
 * - depth: چند لایه دیده شود — ۱ فقط دسته‌ی اصلی (یک لمس = ثبت)، ۲ تا زیردسته، ۳ همه‌ی جزئیات.
 * - hiddenRoots: دسته‌های اصلی‌ای که کاربر نمی‌خواهد ببیند (مثلاً «حیوان خانگی»).
 * فقط روی «انتخاب» اثر دارد؛ تراکنش‌ها و جمع‌های قبلی دست نمی‌خورند.
 */
class CategoryDisplaySettings(context: Context) {

    private val prefs = context.getSharedPreferences("category_display", Context.MODE_PRIVATE)

    private val _depth = MutableStateFlow(prefs.getInt(KEY_DEPTH, MAX_DEPTH).coerceIn(1, MAX_DEPTH))
    val depth: StateFlow<Int> = _depth.asStateFlow()

    private val _hiddenRoots = MutableStateFlow(
        prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    )
    val hiddenRoots: StateFlow<Set<Long>> = _hiddenRoots.asStateFlow()

    fun setDepth(depth: Int) {
        val d = depth.coerceIn(1, MAX_DEPTH)
        prefs.edit { putInt(KEY_DEPTH, d) }
        _depth.value = d
    }

    fun setRootVisible(rootId: Long, visible: Boolean) {
        val next = if (visible) _hiddenRoots.value - rootId else _hiddenRoots.value + rootId
        prefs.edit { putStringSet(KEY_HIDDEN, next.map { it.toString() }.toSet()) }
        _hiddenRoots.value = next
    }

    companion object {
        const val MAX_DEPTH = 3
        private const val KEY_DEPTH = "depth"
        private const val KEY_HIDDEN = "hidden_roots"
    }
}
