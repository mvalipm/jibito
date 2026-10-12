package ir.jibito.app.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import ir.jibito.app.MainActivity
import ir.jibito.app.R
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.ui.smslist.accountLabel
import ir.jibito.app.ui.smslist.bankColor
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/**
 * ظاهر ویجت «موجودی کارت‌ها» (بدون دیتابیس؛ تست اسکرین‌شات هم همین را می‌کشد).
 * یک‌ردیفه: سه کارت کنار هم. کوچک: دو کارت. متوسط: جمع و سه کارت فشرده با روند. بزرگ: جمع و چهار کارت کامل.
 */
internal object CardsViews {

    /**
     * @param lines null یعنی قفل اپ روشن است
     * @param hidden دکمه‌ی چشم: مبلغ‌ها با نقطه
     */
    fun build(context: Context, lines: List<CardLine>?, hidden: Boolean, now: Long, size: WidgetSize): RemoteViews {
        if (lines == null) {
            return WidgetKit.message(
                context, R.drawable.widget_card_icon, R.string.widget_cards_short, R.string.widget_cards_locked, R.string.widget_locked_hint,
            )
        }
        if (lines.isEmpty()) {
            return WidgetKit.message(
                context, R.drawable.widget_card_icon, R.string.widget_cards_short,
                R.string.widget_cards_empty_title, R.string.widget_cards_empty_body,
            )
        }
        val (layout, row, count) = when (size) {
            WidgetSize.SLIM -> Triple(R.layout.widget_cards_slim, R.layout.widget_card_chip, 3)
            WidgetSize.SMALL -> Triple(R.layout.widget_cards_small, R.layout.widget_card_row, 2)
            WidgetSize.MEDIUM -> Triple(R.layout.widget_cards_medium, R.layout.widget_card_row_compact, 3)
            WidgetSize.LARGE -> Triple(R.layout.widget_cards_large, R.layout.widget_card_row, 4)
        }
        val shown = lines.take(count)
        val views = RemoteViews(context.packageName, layout)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetKit.openApp(context))
        if (size == WidgetSize.SMALL) views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_cards_short))
        views.setImageViewResource(R.id.widget_eye, if (hidden) R.drawable.widget_eye_off else R.drawable.widget_eye)
        views.setContentDescription(R.id.widget_eye, context.getString(if (hidden) R.string.widget_cards_show else R.string.widget_cards_hide))
        views.setOnClickPendingIntent(R.id.widget_eye, CardsWidget.toggleIntent(context))

        if (hidden) {
            views.setTextViewText(R.id.widget_total, WidgetKit.HIDDEN)
            WidgetKit.show(views, R.id.widget_total_unit, false)
        } else {
            val (number, unit) = WidgetKit.amountParts(context, shown.sumOf { it.balanceRial })
            views.setTextViewText(R.id.widget_total, number)
            views.setTextViewText(
                R.id.widget_total_unit,
                context.getString(R.string.widget_cards_total, unit, Jalali.toPersianDigits(shown.size.toString())),
            )
        }

        // یک‌ردیفه کارت‌ها را مستقیم در خود ردیف دارد
        val container = if (size == WidgetSize.SLIM) R.id.widget_root else R.id.widget_cards
        views.removeAllViews(container)
        for (line in shown) views.addView(container, row(context, line, row, hidden, now, size))
        return views
    }

    private fun row(context: Context, line: CardLine, layout: Int, hidden: Boolean, now: Long, size: WidgetSize): RemoteViews {
        val row = RemoteViews(context.packageName, layout)
        val bank = BankDirectory.byId(line.bankId)
        row.setTextViewText(R.id.card_name, accountLabel(bank?.name.orEmpty(), line.account, line.name))
        row.setTextViewText(R.id.card_time, WidgetKit.ago(context, line.dateMillis, now))
        WidgetKit.tint(context, row, R.id.card_bar, bankColor(line.bankId, dark = false).toArgb(), bankColor(line.bankId, dark = true).toArgb())
        if (hidden) {
            row.setTextViewText(R.id.card_amount, WidgetKit.HIDDEN)
            WidgetKit.show(row, R.id.card_unit, false)
        } else {
            // ویجت کوچک جای «میلیون» کامل را ندارد: «۲۴٫۳ م» (مثل کپسول‌های کیف پول اپ)
            val text = if (size == WidgetSize.SMALL) Money.short(line.balanceRial) else Money.compact(line.balanceRial)
            val cut = text.lastIndexOf(' ')
            val (number, unit) = if (cut < 0) text to "" else text.substring(0, cut) to text.substring(cut + 1)
            row.setTextViewText(R.id.card_amount, number)
            row.setTextViewText(R.id.card_unit, unit)
            WidgetKit.show(row, R.id.card_unit, unit.isNotEmpty())
        }
        // مانده‌ی کهنه (مثل کیف پول اپ) کم‌رنگ: ممکن است دیگر درست نباشد
        if (CardLines.isStale(line, now)) {
            WidgetKit.textColor(context, row, R.id.card_name, R.color.widget_muted)
            WidgetKit.textColor(context, row, R.id.card_amount, R.color.widget_muted)
        }
        val withTrend = !hidden && (size == WidgetSize.MEDIUM || size == WidgetSize.LARGE)
        WidgetKit.show(row, R.id.card_spark, withTrend)
        if (withTrend) {
            val (w, h) = if (size == WidgetSize.MEDIUM) 40 to 16 else 46 to 18
            row.setImageViewBitmap(R.id.card_spark, WidgetCharts.spark(context, line.trend, w, h))
        }
        val delta = line.todayDeltaRial?.takeIf { !hidden && size == WidgetSize.LARGE }
        WidgetKit.show(row, R.id.card_up, delta != null && delta > 0)
        WidgetKit.show(row, R.id.card_down, delta != null && delta < 0)
        if (delta != null) {
            val text = context.getString(R.string.widget_cards_delta, (if (delta > 0) "+" else "") + Money.compact(delta))
            row.setTextViewText(if (delta > 0) R.id.card_up else R.id.card_down, text)
        }
        row.setOnClickPendingIntent(
            R.id.card_row,
            WidgetKit.activity(context, ACCOUNT_REQUEST + Math.floorMod(line.key.hashCode(), ACCOUNT_REQUEST), MainActivity.openAccountIntent(context, line.key)),
        )
        return row
    }

    /** کد درخواست PendingIntent کارت‌ها (جدا از «باز کردن اپ» با کد ۰ و ردیف‌های خرج) */
    private const val ACCOUNT_REQUEST = 200_000
}
