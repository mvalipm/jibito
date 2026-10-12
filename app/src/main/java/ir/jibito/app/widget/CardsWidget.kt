package ir.jibito.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.edit
import ir.jibito.app.JibitoApplication
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.local.entity.toLink
import ir.jibito.app.data.wallet.AccountGrouping
import ir.jibito.app.di.AppContainer
import ir.jibito.app.util.ErrorLog
import ir.jibito.app.util.Jalali
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ویجت «موجودی کارت‌ها»: موجودی کارت‌هایی که بیشتر با آن‌ها تراکنش داری (۳۰ روز اخیر)، از آخرین پیامک بانک.
 * همان حساب‌های کیف پول اپ؛ حسابی که از جمع بیرون گذاشته شده، این‌جا هم نیست (CardLines، ظاهر: CardsViews).
 * دکمه‌ی چشم مبلغ‌ها را پنهان می‌کند و تا دوباره زده نشود پنهان می‌مانند؛ قفل اپ هم همه را پنهان می‌کند.
 */
class CardsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        async { render(context, manager, appWidgetIds) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        onUpdate(context, manager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_HIDDEN) {
            async {
                setHidden(context, !isHidden(context))
                refresh(context)
            }
            return
        }
        super.onReceive(context, intent)
    }

    private fun async(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_TOGGLE_HIDDEN = "ir.jibito.app.widget.TOGGLE_CARDS_HIDDEN"
        private const val PREFS = "widget"
        private const val KEY_HIDDEN = "cards_hidden"

        /** بیشترین تعداد کارت (اندازه‌ی بزرگ) */
        private const val MAX_CARDS = 5

        /** «پرمصرف» یعنی بیشترین تراکنش در این چند روز */
        private const val USAGE_DAYS = 30

        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, CardsWidget::class.java))
            if (ids.isEmpty()) return
            render(context, manager, ids)
        }

        fun isHidden(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDDEN, false)

        private fun setHidden(context: Context, hidden: Boolean) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_HIDDEN, hidden) }

        /** لمس دکمه‌ی چشم */
        internal fun toggleIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, CardsWidget::class.java).setAction(ACTION_TOGGLE_HIDDEN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            try {
                val container = (context.applicationContext as JibitoApplication).container
                val locked = container.appLockSettings.enabled.value
                val now = System.currentTimeMillis()
                val lines = if (locked) null else lines(container, now)
                val hidden = isHidden(context)
                WidgetKit.deliver(context, manager, ids) { size -> CardsViews.build(context, lines, hidden, now, size) }
            } catch (e: Exception) {
                ErrorLog.record(context, "cards widget", e)
            }
        }

        private suspend fun lines(container: AppContainer, now: Long): List<CardLine> {
            val accountDao = container.database.accountDao()
            val links = accountDao.links().map { it.toLink() }
            val balances = AccountGrouping.balances(accountDao.observeLatestBalances().first(), links)
                .filter { BankDirectory.byId(it.bankId) != null }
            val todayStart = Jalali.startOfDay(now)
            val week = SpendDays.dayStarts(todayStart, SpendDays.WEEK)
            // یک روز پیش از نمودار هم، برای مانده‌ی اول روز اول
            val pointsFrom = SpendDays.dayStarts(todayStart, SpendDays.WEEK + 1).first()
            return CardLines.pick(
                balances = balances,
                usage = accountDao.usageSince(SpendDays.dayStarts(todayStart, USAGE_DAYS).first()),
                points = accountDao.balancePointsSince(pointsFrom),
                links = links,
                excluded = container.walletSettings.excluded.value,
                days = week,
                end = SpendDays.nextDay(todayStart),
                limit = MAX_CARDS,
            )
        }
    }
}
