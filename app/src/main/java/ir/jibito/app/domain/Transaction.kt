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
    /** ثبت دستی (نقدی)، نه از پیامک */
    val isManual: Boolean = false,
    /** کارمزد انتقال (جزو همین مبلغ) */
    val feeRial: Long? = null,
    /** شناسه‌ی پیامک در گوشی (برای گزارش «اشتباه خوانده شده»: سرشماره از روی آن پیدا می‌شود) */
    val smsId: Long? = null,
    /** یادداشت خود کاربر (مثلاً از جعبه‌ی «بنویس» نوتیفیکیشن) */
    val note: String? = null,
    /** خرج یک‌باره (خرید خانه، ماشین…): در جمع هست، ولی پایه‌ی میانگین و پیش‌بینی نیست */
    val isOneOff: Boolean = false,
    /** کاربر گفته «یک‌باره نیست» ← دیگر پیشنهاد «خرج یک‌باره بود؟» برایش نمی‌آید */
    val isOneOffRejected: Boolean = false,
    /** متن پیامک رمز دومی که به این برداشت وصل شد (برای «اسم فروشگاه کدومه؟» و گزارش) */
    val otpBody: String? = null,
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
    /** شناسه‌ی ثابت دسته‌های پیش‌فرض (مثلاً «home.rent»)؛ برای دسته‌های کاربر null */
    val code: String? = null,
    /** ماهیت خرجی که کاربر انتخاب کرده؛ ۰ = پیش‌فرض (SpendNature) */
    val nature: Int = 0,
)

/** آخرین مانده‌ی یک حساب (از آخرین پیامکی که مانده داشت) */
data class BankBalance(
    val bank: Bank,
    val balanceRial: Long,
    /** زمان همان پیامک؛ مانده‌ی قدیمی ممکن است دیگر درست نباشد */
    val dateMillis: Long,
    /** شماره حساب (نماینده‌ی گروه)؛ null یعنی پیامک‌های این بانک شماره حساب ندارند و یک مانده برای کل بانک است */
    val account: String? = null,
    /** اسمی که کاربر برای این حساب گذاشته */
    val name: String? = null,
)
