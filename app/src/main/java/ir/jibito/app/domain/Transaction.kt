package ir.jibito.app.domain

import ir.jibito.app.data.bank.Bank
import ir.jibito.app.data.parser.ParsedTransaction

/** یک تراکنش، همان‌طور که بقیه‌ی اپ (صفحه‌ها) می‌بیند — بدون خبر از دیتابیس. */
data class Transaction(
    val id: Long,
    val bank: Bank?,
    val body: String,
    val dateMillis: Long,
    val transaction: ParsedTransaction,
    val merchant: String?,
    val suggestedCategory: String?,
    val isFailedPurchase: Boolean,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryIcon: String?,
    /** دسته را اپ خودش (از روی انتخاب‌های قبلی) گذاشته */
    val isAutoCategorized: Boolean,
    /** انتقال بین حساب‌های خود کاربر: نه خرج حساب می‌شود نه درآمد */
    val isSelfTransfer: Boolean = false,
    /** کاربر گفته «انتقال به خودم نیست» ← دیگر پیشنهاد انتقال برایش نمی‌آید */
    val isTransferRejected: Boolean = false,
    /** رنگ دسته‌ی اصلی (همان کد رنگی که در دیتابیس است؛ برای نمایش از ChartColors رد می‌شود) */
    val categoryColorHex: String? = null,
    /** ثبت دستی (نقدی)، نه از پیامک */
    val isManual: Boolean = false,
    /** کارمزد انتقال (جزو همین مبلغ) */
    val feeRial: Long? = null,
)

/** پیشنهاد: «این برداشت و این واریزِ هم‌مبلغ، انتقال بین حساب‌های خودت بود؟» */
data class TransferSuggestion(
    val withdrawal: Transaction,
    val deposit: Transaction,
)

/** یک دسته: خرج (برای برداشت) یا درآمد (برای واریز). */
data class Category(
    val id: Long,
    val name: String,
    val icon: String?,
    val colorHex: String?,
    /** ۲ = خرج، ۱ = درآمد (همان کدهای FlowType) */
    val flowType: Int,
    /** دسته‌ی بالاتر؛ null یعنی دسته‌ی اصلی */
    val parentId: Long? = null,
    /** false یعنی خرج حساب نمی‌شود (پس‌انداز، قرض دادن) */
    val countsAsSpend: Boolean = true,
    /** دسته‌ای که خود کاربر ساخته */
    val isCustom: Boolean = false,
)

/** آخرین مانده‌ی یک بانک (از آخرین پیامکی که مانده داشت) */
data class BankBalance(
    val bank: Bank,
    val balanceRial: Long,
    /** زمان همان پیامک؛ مانده‌ی قدیمی ممکن است دیگر درست نباشد */
    val dateMillis: Long,
)
