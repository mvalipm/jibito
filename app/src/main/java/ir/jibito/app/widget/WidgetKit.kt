package ir.jibito.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import ir.jibito.app.MainActivity
import ir.jibito.app.R
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/**
 * اندازه‌ی ویجت روی صفحه‌ی اصلی؛ هر اندازه چیدمان خودش را دارد.
 * عددها کمترین پهنا و بلندی (dp) هر چیدمان‌اند: اندروید ۱۲ به بعد خودش بزرگ‌ترین چیدمانی را که جا می‌شود برمی‌دارد،
 * و پیش از آن [pick] از روی اندازه‌ای که لانچر گزارش می‌دهد همان را انتخاب می‌کند.
 */
internal enum class WidgetSize(val widthDp: Float, val heightDp: Float) {
    /** یک ردیف (۴×۱) */
    SLIM(110f, 40f),

    /** کوچک (۲×۲)، یا پهن ولی کوتاه */
    SMALL(110f, 100f),

    /** متوسط (۳×۲، ۴×۲) */
    MEDIUM(220f, 150f),

    /** بزرگ (۴×۳ و بیشتر) */
    LARGE(220f, 250f),
    ;

    companion object {
        /** صفر یعنی لانچر اندازه را نگفته ← متوسط */
        fun pick(widthDp: Int, heightDp: Int): WidgetSize = when {
            heightDp in 1 until SMALL.heightDp.toInt() -> SLIM
            widthDp in 1 until MEDIUM.widthDp.toInt() || heightDp in 1 until MEDIUM.heightDp.toInt() -> SMALL
            heightDp >= LARGE.heightDp -> LARGE
            else -> MEDIUM
        }
    }
}

/** کارهای مشترک دو ویجت (خرج امروز، موجودی کارت‌ها) */
internal object WidgetKit {

    /** مبلغ پنهان (قفل اپ یا دکمه‌ی چشم ویجت کارت‌ها) */
    const val HIDDEN = "•••••"

    /** هر ویجت را با چیدمانِ اندازه‌ی خودش به‌روز می‌کند */
    fun deliver(context: Context, manager: AppWidgetManager, ids: IntArray, build: (WidgetSize) -> RemoteViews) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val views = RemoteViews(WidgetSize.entries.associate { SizeF(it.widthDp, it.heightDp) to build(it) })
            for (id in ids) manager.updateAppWidget(id, views)
            return
        }
        val built = HashMap<WidgetSize, RemoteViews>()
        for (id in ids) {
            val options = manager.getAppWidgetOptions(id)
            val size = WidgetSize.pick(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
            )
            manager.updateAppWidget(id, built.getOrPut(size) { build(size) })
        }
    }

    /** لمس ویجت: باز کردن اپ */
    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun activity(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /**
     * رنگ نوشته از منابع (روشن/تیره): از اندروید ۱۲ خود لانچر با حالت فعلی گوشی می‌خواندش؛
     * پیش از آن همان رنگی که اپ الان می‌بیند.
     */
    fun textColor(context: Context, views: RemoteViews, id: Int, @ColorRes color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(id, "setTextColor", color)
        } else {
            views.setTextColor(id, ContextCompat.getColor(context, color))
        }
    }

    /** رنگ تصویر (رنگ بانک، رنگ دسته) با یک رنگ برای روشن و یکی برای تیره */
    fun tint(context: Context, views: RemoteViews, id: Int, light: Int, dark: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorInt(id, "setColorFilter", light, dark)
        } else {
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            views.setInt(id, "setColorFilter", if (night) dark else light)
        }
    }

    fun show(views: RemoteViews, id: Int, visible: Boolean) = views.setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)

    /** «۲۴٫۳» و «میلیون تومان» جدا (عدد درشت، واحد کوچک کنارش) */
    fun amountParts(context: Context, rial: Long): Pair<String, String> {
        val (number, unit) = Money.compactParts(rial)
        return number to listOf(unit, context.getString(R.string.unit_toman)).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** «امروز ۱۴:۲۰»، «دیروز»، «۳ روز پیش» */
    fun ago(context: Context, millis: Long, now: Long): String {
        val today = Jalali.startOfDay(now)
        val days = ((today - Jalali.startOfDay(millis)) / DAY_MILLIS.toDouble()).let { Math.round(it) }
        return when {
            days <= 0 -> context.getString(R.string.widget_today_at, Jalali.time(millis))
            days == 1L -> context.getString(R.string.widget_yesterday)
            else -> context.getString(R.string.widget_days_ago, Jalali.toPersianDigits(days.toString()))
        }
    }

    /** ویجتِ پیام (قفل اپ، هنوز داده‌ای نیست): نشان و عنوان بالا، یک جمله‌ی درشت و توضیح */
    fun message(
        context: Context,
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes big: Int,
        @StringRes body: Int,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_message).apply {
        setOnClickPendingIntent(R.id.widget_root, openApp(context))
        setImageViewResource(R.id.widget_msg_icon, icon)
        setTextViewText(R.id.widget_msg_title, context.getString(title))
        setTextViewText(R.id.widget_msg_big, context.getString(big))
        setTextViewText(R.id.widget_msg_body, context.getString(body))
    }

    /** هر دو ویجت (بعد از همگام‌سازی، تغییر دسته یا بودجه، قفل اپ، حساب‌های کیف پول) */
    suspend fun refreshAll(context: Context) {
        SpendWidget.refresh(context)
        CardsWidget.refresh(context)
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
