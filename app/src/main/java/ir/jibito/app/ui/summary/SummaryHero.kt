package ir.jibito.app.ui.summary

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.wallet.BalanceForecast
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.ui.common.LocalHideAmounts
import ir.jibito.app.ui.main.NavIcons
import ir.jibito.app.ui.common.HIDDEN_AMOUNT
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoText
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
/** خمیدگی پایین سرصفحه */
private val CURVE = 40.dp

/**
 * سرصفحه‌ی رنگی «خلاصه» (طرح «جیبی»): رنگش حال جیب است (آروم/یواش‌تر/بیرون زد).
 * دکمه‌ی ماه، پنهان کردن مبلغ‌ها و تنظیمات (روشن/تیره در تنظیمات است)؛ عدد درشت خرج ماه؛
 * نوار بودجه با خط «امروز»؛ و یک جمله که حال جیب را می‌گوید.
 * تعیین بودجه‌ی کل: لمس «X از بودجه‌ی Y» (با مداد) یا خود عدد درشت.
 * compact: هنوز هیچ تراکنشی نیست؛ فقط ردیف بالا، تا کارت «ثبت اولین خرج» بالا بیاید و جای «۰ تومان» را نگیرد.
 */
@Composable
fun SummaryHero(
    s: MonthSummary,
    onPickMonth: (JalaliMonth) -> Unit,
    onEditBudget: () -> Unit,
    onToggleHidden: () -> Unit,
    onOpenSettings: () -> Unit = {},
    nowMillis: Long = System.currentTimeMillis(),
    /** «پولت تا حقوق می‌رسه؟»؛ فقط ماه جاری، و وقتی مبلغ‌ها پنهان نیستند */
    forecast: BalanceForecast? = null,
    onOpenForecast: () -> Unit = {},
    compact: Boolean = false,
) {
    val t = JibitoTheme.colors
    val hidden = LocalHideAmounts.current
    val isCurrent = s.month == JalaliMonth.of(nowMillis)
    val line = moodOf(s, nowMillis, forecast)
    val moodColor by animateColorAsState(
        when (line.mood) {
            Mood.CALM -> t.moodCalm
            Mood.WARN -> t.moodWarn
            Mood.OVER -> t.moodOver
        },
        animationSpec = tween(700),
        label = "mood",
    )
    val budget = s.overallBudgetRial?.takeIf { it > 0 }
    // پس‌زمینه‌ی دکمه‌ها: روی رنگ روشن سرصفحه کمی تیره‌تر (نه روشن‌تر)، تا متن سفید رویش کنتراست کافی داشته باشد
    val control = heroControlColor(t.dark)

    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val w = size.width
                val h = size.height
                val d = CURVE.toPx()
                val sx = w / 390f
                // اندازه‌های طرح برای خمیدگی ۷۶dp بودند؛ با خمیدگی کوتاه‌تر هم‌نسبت کوچک می‌شوند
                val k = d / 76.dp.toPx()
                val body = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h - d)
                    cubicTo(w, h - d + 34.dp.toPx() * k, 300 * sx, h - 14.dp.toPx() * k, w / 2, h)
                    cubicTo(90 * sx, h - 14.dp.toPx() * k, 0f, h - d + 34.dp.toPx() * k, 0f, h - d)
                    close()
                }
                drawPath(body, moodColor)
                // خط‌چین تزئینی موازی لبه‌ی پایین
                val dash = Path().apply {
                    moveTo(18 * sx, h - 82.dp.toPx() * k)
                    cubicTo(18 * sx, h - 54.dp.toPx() * k, 100 * sx, h - 32.dp.toPx() * k, w / 2, h - 20.dp.toPx() * k)
                    cubicTo(290 * sx, h - 32.dp.toPx() * k, 372 * sx, h - 54.dp.toPx() * k, 372 * sx, h - 82.dp.toPx() * k)
                }
                drawPath(
                    dash,
                    Color.White.copy(alpha = 0.4f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 8.dp.toPx())),
                    ),
                )
                // یک دایره‌ی کم‌رنگ بالای گوشه (در راست‌به‌چپ هم همان‌جای طرح)؛ دایره‌ی دوم پشت نوار بودجه بود و خواندنش را سخت می‌کرد
                drawCircle(Color.White.copy(alpha = 0.08f), radius = 40.dp.toPx(), center = Offset(330 * sx, (-6).dp.toPx()))
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = CURVE + 4.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ماه + چشم + تنظیمات (روشن/تیره کم عوض می‌شود و در تنظیمات است، نه این‌جا)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MonthPill(s.month, control, onPickMonth)
                Spacer(Modifier.weight(1f))
                HeroButton(
                    if (hidden) DesignIcons.EyeOff else DesignIcons.Eye,
                    stringResource(if (hidden) R.string.cd_show_amounts else R.string.cd_hide_amounts),
                    onToggleHidden,
                    control,
                )
                // تنظیمات دیگر تب نیست: گوشه‌ی بالا-چپ (در راست‌به‌چپ، ته ردیف)
                Spacer(Modifier.width(8.dp))
                HeroButton(NavIcons.Settings.normal, stringResource(R.string.settings_title), onOpenSettings, control)
            }
            if (compact) return@Column

            // «خرج این ماه» و عدد درشت، وسط سرصفحه
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (isCurrent) stringResource(R.string.hero_label_current) else stringResource(R.string.summary_spent, s.month.title),
                    modifier = Modifier.semantics { heading() },
                    color = Color.White,
                    style = JibitoText.lead,
                    fontWeight = FontWeight.Medium,
                )
                BigNumber(s.totalSpentRial, hidden, onEditBudget)
            }

            // نوار بودجه
            if (budget != null) {
                BudgetBar(
                    spent = (s.budgetSpentRial.toFloat() / budget).coerceIn(0f, 1f),
                    today = s.timeFraction(nowMillis),
                    marker = moodColor,
                )
                BudgetLabels(s, budget, hidden, isCurrent, nowMillis, onEditBudget)
                // خرج یک‌باره جدا از بودجه (عدد درشت بالا آن را دارد، نوار بودجه نه)
                if (s.oneOffRial > 0) {
                    Text(
                        Jalali.toPersianDigits(
                            stringResource(R.string.hero_one_off, if (hidden) HIDDEN_AMOUNT else Money.compact(s.oneOffRial))
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        style = JibitoText.small,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.overall_set_budget),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(control)
                        .clickable(onClick = onEditBudget)
                        .heightIn(min = 48.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(horizontal = 16.dp),
                    color = Color.White,
                    style = JibitoText.action,
                    fontWeight = FontWeight.Bold,
                )
            }

            // جمله‌ی حال جیب
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        // بودجه آروم ولی موجودی کم: رنگ آروم، آیکون هشدار (جمله می‌گوید کارت پایین را ببین)
                        line.attention -> DesignIcons.Warn
                        line.mood == Mood.CALM -> DesignIcons.Calm
                        line.mood == Mood.WARN -> DesignIcons.Warn
                        else -> DesignIcons.Over
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                val args = line.args.map { if (hidden && it is String) HIDDEN_AMOUNT else it }.toTypedArray()
                Text(
                    Jalali.toPersianDigits(stringResource(line.sentence, *args)),
                    color = Color.White,
                    style = JibitoText.lead,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                )
            }
            if (forecast != null && isCurrent && !hidden) HeroForecastLine(forecast, onOpenForecast)
        }
    }
}
