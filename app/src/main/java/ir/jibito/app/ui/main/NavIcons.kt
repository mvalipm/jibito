package ir.jibito.app.ui.main

import androidx.compose.ui.graphics.vector.ImageVector
import ir.jibito.app.ui.theme.DesignIcons

/** آیکون‌های نوار پایین (طرح «جیبی»)؛ تب فعال خط پررنگ‌تری دارد (۲٫۲ به‌جای ۱٫۸) */
object NavIcons {
    class NavIcon(val normal: ImageVector, val selected: ImageVector)

    private fun pair(name: String, d: String) =
        NavIcon(DesignIcons.svg(name, d), DesignIcons.svg("${name}_on", d, strokeWidth = 2.2f))

    val Summary by lazy { pair("nav_summary", DesignIcons.NAV_SUMMARY) }
    val Transactions by lazy { pair("nav_transactions", DesignIcons.NAV_TRANSACTIONS) }
    val Reports by lazy { pair("nav_reports", DesignIcons.NAV_REPORTS) }
    val Todo by lazy { pair("nav_todo", DesignIcons.NAV_TODO) }
    val Settings by lazy { pair("nav_settings", DesignIcons.NAV_SETTINGS) }
}
