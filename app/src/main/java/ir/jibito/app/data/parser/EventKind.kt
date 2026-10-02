package ir.jibito.app.data.parser

/**
 * «نوع رویداد» یک تراکنش، جدا از دسته (سند EventTaxonomy، بخش ۳).
 * دسته می‌گوید پول برای چه بود (خوراک، اجاره...)؛ نوع می‌گوید چه اتفاقی در بانک افتاد (خرید کارتی، خودپرداز...).
 * از روی نوع، دو خطای پرهزینه‌ی خلاصه‌ی ماه درست می‌شود:
 * - برداشت از خودپرداز خرج نیست (پول فقط از حساب به جیب رفته)
 * - برگشت پول درآمد نیست
 *
 * کدها در دیتابیس ذخیره می‌شوند؛ هرگز عوض نشوند (فقط کد تازه اضافه شود).
 */
enum class EventKind(val code: Int) {
    UNKNOWN(0),
    PURCHASE(1),
    TRANSFER(2),
    CASH_WITHDRAWAL(3),
    BILL_PAYMENT(4),
    FEE(5),
    REFUND(6);

    companion object {
        fun of(code: Int): EventKind = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/**
 * تشخیص نوع رویداد از متن پیامک (نرمال‌شده) + جهت + اتصال به رمز دوم.
 *
 * قانون‌های سند EventTaxonomy که این‌جا رعایت شده‌اند:
 * - نوعی که با جهت جور نیست، UNKNOWN می‌ماند (مثلاً «خودپرداز» روی واریز، «برگشت» روی برداشت)؛ حدس زده نمی‌شود.
 * - چند نشانه با هم: اولویت ثابت و معنادار، نه ترتیب تصادفی قانون‌ها
 *   (برگشت ← خودپرداز ← قبض ← انتقال ← خرید ← کارمزد). مثلاً «انتقال ... کارمزد: ۵۰۰۰» انتقال است، نه کارمزد.
 * - کلمه‌ی کم‌اطمینان (مثل «پرداخت» یا «برداشت» خالی) نوع نمی‌سازد؛ UNKNOWN بهتر از حدس است.
 */
object EventKindClassifier {

    /**
     * کلمه‌ی کامل («پایا» نه «پایان»، «شبا» نه «شبانه»، «پوز» نه «پوزش»).
     * \b جاوا حروف فارسی را حرف حساب نمی‌کند، برای همین با \p{L}.
     */
    private fun word(w: String) = "(?<!\\p{L})$w(?!\\p{L})"

    // فاصله یا نیم‌فاصله بین کلمه‌ها (پیامک‌ها هر دو را دارند)
    private const val S = "[\\s\u200c]*"

    private val refund = Regex("برگشت${S}(وجه|پول|مبلغ|خرید|تراکنش)|عودت|استرداد|بازگشت${S}وجه|reversal|refund", RegexOption.IGNORE_CASE)
    // «برگشت چک» برگشت پول نیست
    private val bouncedCheque = Regex("برگشت${S}(چک|خورد)")
    private val cash = Regex("خودپرداز|عابر${S}بانک|\\bATM\\b|برداشت${S}(وجه${S})?نقد|وجه${S}نقد", RegexOption.IGNORE_CASE)
    private val bill = Regex("قبض|شناسه${S}پرداخت")
    private val transfer = Regex("کارت${S}به${S}کارت|انتقال|حواله|${word("پایا")}|${word("ساتنا")}|${word("شبا")}", RegexOption.IGNORE_CASE)
    private val purchase = Regex("خرید|${word("پوز")}|\\bPOS\\b|پذیرنده|فروشگاه|اینترنتی", RegexOption.IGNORE_CASE)
    private val fee = Regex("کارمزد")

    /**
     * @param text متن نرمال‌شده‌ی پیامک (SmsTextNormalizer)
     * @param linkedOtp رمز دومی که این برداشت به آن وصل شده (اگر شده)
     */
    fun classify(text: String, type: FlowType, linkedOtp: PurchaseOtp? = null): EventKind {
        val isWithdrawal = type == FlowType.WITHDRAWAL

        if (refund.containsMatchIn(text) && !bouncedCheque.containsMatchIn(text)) {
            return if (isWithdrawal) EventKind.UNKNOWN else EventKind.REFUND
        }
        // رمز دوم صریح‌ترین نشانه است: رمزِ انتقال ← انتقال؛ رمزِ خرید ← خرید
        if (linkedOtp != null && isWithdrawal) {
            return if (linkedOtp.isTransfer) EventKind.TRANSFER else EventKind.PURCHASE
        }
        if (cash.containsMatchIn(text)) return if (isWithdrawal) EventKind.CASH_WITHDRAWAL else EventKind.UNKNOWN
        if (bill.containsMatchIn(text)) return if (isWithdrawal) EventKind.BILL_PAYMENT else EventKind.UNKNOWN
        if (transfer.containsMatchIn(text)) return EventKind.TRANSFER
        // واریزی که «خرید» دارد معمولاً لغو یا برگشت خرید است؛ بدون کلمه‌ی صریح برگشت، حدس نمی‌زنیم
        if (purchase.containsMatchIn(text)) return if (isWithdrawal) EventKind.PURCHASE else EventKind.UNKNOWN
        if (fee.containsMatchIn(text)) return if (isWithdrawal) EventKind.FEE else EventKind.UNKNOWN
        return EventKind.UNKNOWN
    }
}
