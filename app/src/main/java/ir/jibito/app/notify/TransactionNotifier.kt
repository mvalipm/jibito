package ir.jibito.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ir.jibito.app.MainActivity
import ir.jibito.app.R
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/**
 * نوتیفیکیشن «این خرج مال چی بود؟» (یا برای واریز: «این پول از کجا اومد؟») با ۲ دکمه‌ی دسته و «دسته‌ی دیگر…».
 *
 * قانون‌ها:
 * - فقط برای تراکنش‌های تازه (حداکثر ۳۰ دقیقه‌ی اخیر) که هنوز دسته ندارند و قبلاً نوتیفیکیشن نگرفته‌اند.
 * - اگر بعداً معلوم شد خرید ناموفق بوده (پول برگشته)، نوتیفیکیشنش پاک می‌شود.
 * - چند سؤال هم‌زمان در یک گروه با یک خلاصه می‌آیند و فقط یک بار صدا می‌دهند.
 * - بعد از زدن دکمه‌ی دسته، چند ثانیه «✓ ثبت شد · برگردون» دیده می‌شود.
 */
class TransactionNotifier(
    private val context: Context,
    private val db: AppDatabase,
) {
    private val dao = db.transactionFlowDao()

    suspend fun processRecent() {
        ensureChannel()
        val now = System.currentTimeMillis()
        val recent = dao.recentSmsFlows(since = now - RECENT_WINDOW_MILLIS)
        val toAsk = mutableListOf<TransactionFlowEntity>()
        val removed = mutableSetOf<Int>()

        for (flow in recent) {
            when {
                flow.isFailedPurchase -> {
                    Notify.cancel(context, notificationId(flow.id))
                    removed += notificationId(flow.id)
                }
                // انتقال به حساب خودم (کارت یادگرفته‌شده) ← سؤال «مال چی بود؟» لازم نیست
                flow.transferState == TransactionFlowEntity.TRANSFER_SELF -> {
                    if (flow.notifiedAt == null) {
                        dao.markNotified(flow.id, now)
                    } else {
                        Notify.cancel(context, notificationId(flow.id))
                        removed += notificationId(flow.id)
                    }
                }
                flow.notifiedAt != null -> Unit
                // بی‌دسته ← «مال چی بود؟» با دکمه‌ها
                flow.categoryId == null -> toAsk += flow
                // اپ خودش دسته گذاشته ← یک خبر آرام: «✓ رفت‌وآمد (خودکار)»؛ برای تغییر، روی آن بزن
                flow.isAutoCategorized -> {
                    if (showAutoConfirm(flow)) dao.markNotified(flow.id, now)
                }
                // کاربر خودش قبل از نوتیفیکیشن دسته داده ← دیگر لازم نیست
                else -> dao.markNotified(flow.id, now)
            }
        }
        if (toAsk.isEmpty()) {
            if (removed.isNotEmpty()) refreshSummary(alert = false, removed = removed)
            return
        }

        // چند سؤال با هم (مثلاً چند خرید پشت سر هم): فقط خلاصه‌ی گروه صدا بدهد، نه تک‌تک
        val grouped = (activeQuestions().keys - removed).size + toAsk.size >= 2
        val added = mutableMapOf<Int, String>()
        for (flow in toAsk.sortedBy { it.dateEpoch }) {
            if (ask(flow, silentInGroup = grouped)) {
                dao.markNotified(flow.id, now)
                added[notificationId(flow.id)] = line(flow)
            }
        }
        refreshSummary(alert = added.isNotEmpty(), added = added, removed = removed)
    }

    /** دوباره پرسیدن (مثلاً بعد از «برگردون»)؛ بی‌صدا، چون کاربر همین الان با آن کار داشته */
    suspend fun askAgain(transactionId: Long) {
        val flow = dao.byId(transactionId) ?: return
        ensureChannel()
        if (ask(flow, silentInGroup = false, silent = true)) {
            refreshSummary(alert = false, added = mapOf(notificationId(transactionId) to line(flow)))
        }
    }

    /** بعد از زدن دکمه‌ی دسته: «✓ خوراک ثبت شد» با «برگردون»، که چند ثانیه بعد خودش می‌رود */
    suspend fun showChosen(transactionId: Long, categoryId: Long) {
        val flow = dao.byId(transactionId) ?: return
        val category = db.categoryDao().byId(categoryId) ?: return
        val label = listOfNotNull(category.icon, category.name).joinToString(" ")
        ensureChannel()
        val shown = Notify.post(context, notificationId(transactionId)) { redacted ->
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_jibito)
                .setContentTitle(context.getString(R.string.notif_chosen, label))
                .apply { if (!redacted) setContentText(line(flow)) }
                .setContentIntent(openTransaction(transactionId))
                .addAction(0, context.getString(R.string.notif_undo), CategoryActionReceiver.undoIntent(context, transactionId))
                .setAutoCancel(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(CONFIRM_MILLIS)
                .setColor(ContextCompat.getColor(context, R.color.jibito_primary))
        }
        // بدون اجازه‌ی نوتیفیکیشن، دست‌کم سؤال جواب‌داده‌شده برداشته شود
        if (!shown) Notify.cancel(context, notificationId(transactionId))
        refreshSummary(alert = false, removed = setOf(notificationId(transactionId)))
    }

    private suspend fun ask(flow: TransactionFlowEntity, silentInGroup: Boolean, silent: Boolean = false): Boolean {
        if (!Notify.canPost(context)) return false
        val isDeposit = flow.flowType == FlowType.DEPOSIT.code
        val categories = pickCategories(flow)
        val question = context.getString(if (isDeposit) R.string.notif_question_income else R.string.notif_question)
        val fullTitle = line(flow)

        return Notify.post(context, notificationId(flow.id)) { redacted ->
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_jibito)
                // روی صفحه‌ی قفل: بدون مبلغ و بانک
                .setContentTitle(
                    if (redacted) context.getString(if (isDeposit) R.string.notif_redacted_deposit else R.string.notif_redacted_withdrawal)
                    else fullTitle
                )
                .setContentText(question)
                .setContentIntent(openTransaction(flow.id))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setColor(ContextCompat.getColor(context, R.color.jibito_primary))
                .setWhen(flow.dateEpoch)
                .setShowWhen(true)
                .setGroup(GROUP_KEY)
                .addExtras(android.os.Bundle().apply {
                    putBoolean(EXTRA_QUESTION, true)
                    putString(EXTRA_LINE, fullTitle)
                })
                .apply {
                    if (silentInGroup) setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                    if (silent) setSilent(true).setOnlyAlertOnce(true)
                    for (category in categories) {
                        addAction(
                            0,
                            listOfNotNull(category.icon, category.name).joinToString(" "),
                            CategoryActionReceiver.pendingIntent(context, flow.id, category.id),
                        )
                    }
                    // هیچ‌کدام نبود؟ ← مستقیم برگه‌ی انتخاب دسته‌ی همین تراکنش
                    addAction(0, context.getString(R.string.notif_other_category), openTransaction(flow.id))
                }
        }
    }

    /**
     * خلاصه‌ی گروه سؤال‌ها: «۳ تراکنش منتظر دسته» با فهرست مبلغ‌ها.
     * با کمتر از ۲ سؤال باز، خلاصه برداشته می‌شود.
     * @param alert true فقط وقتی سؤال تازه‌ای آمده (یک صدا برای کل گروه)
     * @param added / [removed] همین الان فرستاده یا برداشته شدند (فهرست اندروید ممکن است هنوز به‌روز نباشد)
     */
    private fun refreshSummary(alert: Boolean, added: Map<Int, String> = emptyMap(), removed: Set<Int> = emptySet()) {
        val questions = (activeQuestions() + added) - removed
        if (questions.size < 2) {
            Notify.cancel(context, SUMMARY_ID)
            return
        }
        val lines = questions.values.toList()
        val title = Jalali.toPersianDigits(context.getString(R.string.notif_group_title, questions.size))
        Notify.post(context, SUMMARY_ID) { redacted ->
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_jibito)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.notif_group_text))
                .apply {
                    if (!redacted) {
                        setStyle(
                            NotificationCompat.InboxStyle()
                                .setBigContentTitle(title)
                                .also { style -> lines.forEach { style.addLine(it) } }
                        )
                    }
                    if (!alert) setOnlyAlertOnce(true).setSilent(true)
                }
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setColor(ContextCompat.getColor(context, R.color.jibito_primary))
        }
    }

    /** سؤال‌های «مال چی بود؟» که الان در نوار نوتیفیکیشن هستند: شناسه ← خط مبلغ */
    private fun activeQuestions(): Map<Int, String> =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.id != SUMMARY_ID && it.notification.extras.getBoolean(EXTRA_QUESTION) }
            .associate { it.id to it.notification.extras.getString(EXTRA_LINE).orEmpty() }

    /** «−۸۵۰ هزار تومان · برداشت · ملت» — مثل فهرست تراکنش‌ها، نه «خرید از …» */
    private fun line(flow: TransactionFlowEntity): String {
        val isDeposit = flow.flowType == FlowType.DEPOSIT.code
        val amount = (if (isDeposit) "+" else "−") + Money.toman(flow.amount)
        val kind = context.getString(if (isDeposit) R.string.tx_deposit else R.string.tx_withdrawal)
        return listOfNotNull(amount, kind, BankDirectory.byId(flow.bankId)?.name).joinToString(" · ")
    }

    private fun openTransaction(transactionId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        notificationId(transactionId),
        MainActivity.openTransactionIntent(context, transactionId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        SUMMARY_ID,
        android.content.Intent(context, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private suspend fun showAutoConfirm(flow: TransactionFlowEntity): Boolean {
        if (!Notify.canPost(context)) return false
        ensureAutoChannel()
        val isDeposit = flow.flowType == FlowType.DEPOSIT.code
        val amount = (if (isDeposit) "+" else "−") + Money.toman(flow.amount)
        val title = listOfNotNull(amount, flow.merchant).joinToString(" · ")
        val category = flow.categoryId?.let { db.categoryDao().byId(it) }
            ?.let { listOfNotNull(it.icon, it.name).joinToString(" ") }
            ?: return false
        return Notify.post(context, notificationId(flow.id)) { redacted ->
            NotificationCompat.Builder(context, AUTO_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_jibito)
                .setContentTitle(
                    if (redacted) context.getString(if (isDeposit) R.string.notif_redacted_deposit else R.string.notif_redacted_withdrawal)
                    else title
                )
                .setContentText(context.getString(R.string.notif_auto_text, category))
                .setContentIntent(openTransaction(flow.id))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setColor(ContextCompat.getColor(context, R.color.jibito_primary))
                .setWhen(flow.dateEpoch)
                .setShowWhen(true)
        }
    }

    private fun ensureAutoChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(AUTO_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                AUTO_CHANNEL_ID,
                context.getString(R.string.notif_auto_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_auto_channel_desc) }
        )
    }

    /** ۲ دکمه: اول دسته‌ی پیشنهادی (از مقصد خرید)، بعد پرکاربردترین دسته‌های خود کاربر. سومی «دسته‌ی دیگر…» است. */
    private suspend fun pickCategories(flow: TransactionFlowEntity): List<CategoryEntity> {
        // برداشت ← دسته‌های خرج، واریز ← دسته‌های درآمد
        val byUsage = db.categoryDao().byUsage(flow.flowType)
        val suggested = flow.suggestedCategory?.let { name -> byUsage.firstOrNull { it.name == name } }
        return (listOfNotNull(suggested) + byUsage.filter { !it.name.startsWith("سایر") })
            .distinctBy { it.id }
            .take(2)
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notif_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "transactions"
        const val AUTO_CHANNEL_ID = "auto_categorized"
        private const val RECENT_WINDOW_MILLIS = 30 * 60 * 1000L
        private const val CONFIRM_MILLIS = 6_000L
        private const val GROUP_KEY = "ir.jibito.app.QUESTIONS"
        private const val SUMMARY_ID = 700_000_100
        private const val EXTRA_QUESTION = "ir.jibito.app.question"
        private const val EXTRA_LINE = "ir.jibito.app.line"

        fun notificationId(transactionId: Long): Int = (transactionId % Int.MAX_VALUE).toInt()
    }
}
