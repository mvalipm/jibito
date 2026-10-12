package ir.jibito.app.data.pro

/** «جیبیتوی کامل» برای همین کاربر باز است یا نه، و چرا */
sealed interface ProStatus {
    /** با یک بار خرید از کافه‌بازار، برای همیشه */
    data object Owned : ProStatus

    /** ماه اول: همه‌چیز رایگان باز است؛ [daysLeft] روز دیگر (از ۱ تا [ProRules.TRIAL_DAYS]) */
    data class Trial(val daysLeft: Int) : ProStatus

    /** ماه اول تمام شده و هنوز نخریده */
    data object Free : ProStatus
}

/** امکانات «جیبیتوی کامل» الان باز است (خریده یا هنوز ماه اول است) */
val ProStatus.unlocked: Boolean get() = this !is ProStatus.Free

/**
 * امکانات «جیبیتوی کامل». بقیه‌ی جیبیتو (ثبت خودکار خرج، دسته دادن در اپ، بودجه، تراکنش‌ها، کارها، ویجت،
 * قفل اپ، پشتیبان و بازگردانی) همیشه رایگان است: داده‌ی خود کاربر هیچ‌وقت پشت پرداخت نمی‌رود.
 */
enum class ProFeature {
    /** نوتیف «مال چی بود؟» و دسته دادن از خود نوتیف (خرج‌ها همیشه خودکار ثبت می‌شوند و در اپ دسته می‌گیرند) */
    NOTIFY,

    /** «پولم تا حقوق بعدی می‌رسه؟» */
    FORECAST,

    /** «روند موجودی حساب‌ها» */
    BALANCE,

    /** «خرجت چه‌جور بود؟» (اجباری، ضروری، دلخواه) */
    NATURE,

    /** نمودار خرج ۶ و ۱۲ ماهه */
    LONG_RANGE,

    /** پوسته‌های رنگی (ماچا لاته، لوندر، گیلاسی، غروب آینده) */
    THEMES,

    /** خروجی اکسل (CSV) */
    EXPORT,
}

/** قاعده‌های «جیبیتوی کامل» (منطق خالص، بدون Context) */
object ProRules {
    /** ماه اول همه‌چیز باز است: گزارش‌ها و پیش‌بینی تا یک ماه خرج جمع نشود، چیز زیادی نشان نمی‌دهند */
    const val TRIAL_DAYS = 30

    /** پرسیدن خرید قبلی از بازار (نصب دوباره، گوشی تازه) حداکثر روزی یک بار، بدون این‌که کاربر بخواهد */
    const val CHECK_INTERVAL_MILLIS = DAY_MILLIS

    /**
     * بعد از ماه رایگان، به‌جای «مال چی بود؟» نوتیف کوتاهی می‌گوید خرج ثبت شد و جیبیتوی کامل چه دارد:
     * روزی یک بار تا کاربر بفهمد ماه رایگان تمام شده (برگه‌ی «ماه رایگانت تموم شد» را در اپ ببیند)، بعد هفته‌ای یک بار.
     */
    fun notifyInviteDue(lastInviteAt: Long, endNoticeSeen: Boolean, now: Long): Boolean =
        now - lastInviteAt >= if (endNoticeSeen) 7 * DAY_MILLIS else DAY_MILLIS

    fun status(owned: Boolean, trialStartedAt: Long, now: Long): ProStatus {
        if (owned) return ProStatus.Owned
        val end = trialStartedAt + TRIAL_DAYS * DAY_MILLIS
        if (now >= end) return ProStatus.Free
        // روزهای باقی‌مانده، رو به بالا: «۱ روز مونده» تا آخرین لحظه. ساعت گوشی عقب رفته؟ بیشتر از یک ماه نه
        val left = ((end - now + DAY_MILLIS - 1) / DAY_MILLIS).toInt()
        return ProStatus.Trial(left.coerceIn(1, TRIAL_DAYS))
    }
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
