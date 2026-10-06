package ir.jibito.app.data.repository

import ir.jibito.app.data.category.CategoryDisplaySettings
import ir.jibito.app.data.category.ReplyCategoryMatcher
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.OneOffDetector
import ir.jibito.app.domain.Transaction
import ir.jibito.app.domain.TransferSuggestion
import ir.jibito.app.domain.BankBalance
import ir.jibito.app.data.transfer.TransferMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import ir.jibito.app.data.category.CreateCategoryResult

/** منبع تراکنش: خودکار از پیامک، یا تأییدشده توسط کاربر از صندوق بررسی */
const val SOURCE_SMS_AUTO = "SMS_AUTO"
const val SOURCE_SMS_MANUAL = "SMS_MANUAL"
/** ثبت دستی (نقدی یا تراکنشی که پیامک ندارد) */
const val SOURCE_MANUAL = "MANUAL"
/** پیشوند ذخیره‌ی کارمزد انتقال در ستون description */
const val FEE_PREFIX = "fee:"

/** حالت چند تراکنش پیش از یک تغییر، برای «برگردان». صفحه‌ها فقط نگهش می‌دارند و به restore می‌دهند. */
class UndoSnapshot internal constructor(internal val rows: List<TransactionFlowEntity>)

/**
 * تنها راه صفحه‌ها برای رسیدن به تراکنش‌ها (قانون سند معماری: ViewModel هرگز مستقیم Room نمی‌بیند).
 */
interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>

    fun observeCategories(): Flow<List<Category>>

    /**
     * پیامک‌ها را می‌خواند و دیتابیس را به‌روز می‌کند. تعداد تراکنش‌های جدید را برمی‌گرداند.
     * معمولاً فقط پیامک‌های تازه خوانده می‌شوند؛ بار اول، بعد از به‌روزرسانی اپ، هفته‌ای یک بار، یا با forceFull کل صندوق.
     */
    suspend fun syncFromSms(forceFull: Boolean = false): Int

    /** true وقتی خواندن پیامک‌ها در جریان است */
    val isSyncing: StateFlow<Boolean>

    /** دسته‌ی یک تراکنش را تعیین می‌کند (null = بدون دسته). */
    suspend fun setCategory(transactionId: Long, categoryId: Long?)

    /** یادداشت خود کاربر برای این تراکنش؛ متن خالی یعنی پاک شود */
    suspend fun setNote(transactionId: Long, note: String?)

    /**
     * نوشته‌ی جعبه‌ی «بنویس» نوتیفیکیشن: همیشه یادداشت می‌شود، و اگر با اطمینان به یک دسته رسید
     * (ReplyCategoryMatcher) دسته هم می‌گیرد (مثل انتخاب خود کاربر، با یادگیری). متن خالی ← null و هیچ تغییری.
     * @param maxDepth چند لایه‌ی دسته که کاربر در تنظیمات می‌بیند؛ دسته‌ی جورشده تا همان لایه بالا می‌رود («سوخت» ← «حمل‌ونقل»)
     */
    suspend fun applyNoteReply(transactionId: Long, text: String, maxDepth: Int = CategoryDisplaySettings.MAX_DEPTH): NoteReply?

    /**
     * حالت فعلی این تراکنش و هر تراکنشی که تغییر دسته یا حذفش ممکن است رویش اثر بگذارد
     * (جفت انتقالش، و تراکنش‌های بی‌دسته‌ی همان طرف حساب که یادگیری دسته عوضشان می‌کند).
     * باید قبل از تغییر گرفته شود؛ با restore همه دقیقاً به همین حالت برمی‌گردند.
     */
    suspend fun snapshotForUndo(transactionId: Long): UndoSnapshot

    /** «برگردان»: تراکنش‌های داخل snapshot را به همان حالت برمی‌گرداند */
    suspend fun restore(snapshot: UndoSnapshot)

    /** جفت‌های «برداشت ← واریزِ هم‌مبلغ» (قانون‌ها در TransferMatcher) که شاید انتقال بین حساب‌های خود کاربر باشند (تازه‌ترها اول) */
    fun observeTransferSuggestions(): Flow<List<TransferSuggestion>>

    /** «بله، انتقال به خودم بود»: هر دو تراکنش از خرج و درآمد بیرون می‌روند و کارت مقصد یاد گرفته می‌شود. */
    suspend fun confirmTransfer(suggestion: TransferSuggestion)

    /** «نه»: این برداشت دیگر به‌عنوان انتقال پیشنهاد نمی‌شود. */
    suspend fun rejectTransfer(suggestion: TransferSuggestion)

    /** علامت زدن/برداشتن دستیِ «انتقال به خودم» برای یک تراکنش */
    suspend fun setSelfTransfer(transactionId: Long, isSelfTransfer: Boolean)

    /** علامت زدن/برداشتن «خرج یک‌باره»؛ برداشتنِ علامت یعنی «یک‌باره نیست» (دیگر پیشنهاد نمی‌شود) */
    suspend fun setOneOff(transactionId: Long, isOneOff: Boolean)

    /** خریدهای اخیرِ خیلی بزرگ‌تر از معمول که شاید «خرج یک‌باره» باشند (قانون‌ها در OneOffDetector)؛ بزرگ‌ترین اول */
    fun observeOneOffSuggestions(): Flow<List<Transaction>>

    /**
     * دسته‌ی شخصی می‌سازد. parentId = null یعنی دسته‌ی اصلی جدید.
     * @param icon فقط برای دسته‌ی اصلی
     */
    suspend fun createCategory(name: String, parentId: Long?, flowType: Int, icon: String?): CreateCategoryResult

    /** حذف دسته‌ی شخصی (و زیردسته‌هایش): تراکنش‌هایشان به دسته‌ی بالاتر یا بی‌دسته می‌روند */
    suspend fun deleteCustomCategory(categoryId: Long)

    /** عوض کردن اسم دسته‌ی شخصی؛ همان قانون‌های ساختن (خالی، بلند، تکراری) */
    suspend fun renameCustomCategory(categoryId: Long, name: String): CreateCategoryResult

    /**
     * ثبت دستی یک خرج یا درآمد. شناسه‌ی تراکنش تازه را برمی‌گرداند.
     * @param note «برای چی بود؟» — مثل طرف حساب رفتار می‌کند (پیشنهاد و یادگیری دسته)
     */
    suspend fun addManual(type: FlowType, amountRial: Long, categoryId: Long?, note: String?, dateMillis: Long): Long

    /** حذف تراکنش دستی (فقط تراکنش‌های دستی؛ پیامکی‌ها از روی پیامک دوباره ساخته می‌شوند) */
    suspend fun deleteManual(transactionId: Long)

    /** پرکاربردترین دسته‌ها برای این نوع (بیشترین استفاده اول) */
    suspend fun frequentCategoryIds(flowType: Int, limit: Int): List<Long>

    /** «چقد دارم؟»: آخرین مانده‌ی هر حساب، تازه‌ترین اول */
    fun observeBankBalances(): Flow<List<BankBalance>>
}
