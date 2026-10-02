package ir.jibito.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.jibito.app.MainActivity
import ir.jibito.app.R
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.util.Money

/**
 * نوتیفیکیشن «این خرج مال چی بود؟» (یا برای واریز: «این پول از کجا اومد؟») با ۳ دکمه‌ی دسته.
 *
 * قانون‌ها:
 * - فقط برای تراکنش‌های تازه (حداکثر ۳۰ دقیقه‌ی اخیر) که هنوز دسته ندارند و قبلاً نوتیفیکیشن نگرفته‌اند.
 * - اگر بعداً معلوم شد خرید ناموفق بوده (پول برگشته)، نوتیفیکیشنش پاک می‌شود.
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
        val manager = NotificationManagerCompat.from(context)

        for (flow in recent) {
            when {
                flow.isFailedPurchase -> manager.cancel(notificationId(flow.id))
                // انتقال به حساب خودم (کارت یادگرفته‌شده) ← سؤال «مال چی بود؟» لازم نیست
                flow.transferState == TransactionFlowEntity.TRANSFER_SELF -> {
                    if (flow.notifiedAt == null) dao.markNotified(flow.id, now) else manager.cancel(notificationId(flow.id))
                }
                flow.notifiedAt != null -> Unit
                // بی‌دسته ← «مال چی بود؟» با دکمه‌ها
                flow.categoryId == null -> {
                    if (show(flow)) dao.markNotified(flow.id, now)
                }
                // اپ خودش دسته گذاشته ← یک خبر آرام: «✓ رفت‌وآمد (خودکار)»؛ برای تغییر، روی آن بزن
                flow.isAutoCategorized -> {
                    if (showAutoConfirm(flow)) dao.markNotified(flow.id, now)
                }
                // کاربر خودش قبل از نوتیفیکیشن دسته داده ← دیگر لازم نیست
                else -> dao.markNotified(flow.id, now)
            }
        }
    }

    private suspend fun show(flow: TransactionFlowEntity): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val isDeposit = flow.flowType == FlowType.DEPOSIT.code
        val amount = (if (isDeposit) "+" else "−") + Money.toman(flow.amount)
        val bankName = BankDirectory.byId(flow.bankId)?.name
        // مثل فهرست تراکنش‌ها: «برداشت» یا «واریز»، نه «خرید از …»
        val kind = context.getString(if (isDeposit) R.string.tx_deposit else R.string.tx_withdrawal)
        val title = listOfNotNull(amount, kind, bankName).joinToString(" · ")

        val openApp = PendingIntent.getActivity(
            context,
            notificationId(flow.id),
            MainActivity.openTransactionIntent(context, flow.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jibito)
            .setContentTitle(title)
            .setContentText(context.getString(if (isDeposit) R.string.notif_question_income else R.string.notif_question))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(ContextCompat.getColor(context, R.color.jibito_primary))
            .setWhen(flow.dateEpoch)
            .setShowWhen(true)

        for (category in pickCategories(flow)) {
            builder.addAction(
                0,
                listOfNotNull(category.icon, category.name).joinToString(" "),
                CategoryActionReceiver.pendingIntent(context, flow.id, category.id),
            )
        }

        NotificationManagerCompat.from(context).notify(notificationId(flow.id), builder.build())
        return true
    }

    private suspend fun showAutoConfirm(flow: TransactionFlowEntity): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        ensureAutoChannel()
        val isDeposit = flow.flowType == FlowType.DEPOSIT.code
        val amount = (if (isDeposit) "+" else "−") + Money.toman(flow.amount)
        val title = listOfNotNull(amount, flow.merchant).joinToString(" · ")
        val category = flow.categoryId?.let { db.categoryDao().byId(it) }
            ?.let { listOfNotNull(it.icon, it.name).joinToString(" ") }
            ?: return false
        val openApp = PendingIntent.getActivity(
            context,
            notificationId(flow.id),
            MainActivity.openTransactionIntent(context, flow.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, AUTO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jibito)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_auto_text, category))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(ContextCompat.getColor(context, R.color.jibito_primary))
            .setWhen(flow.dateEpoch)
            .setShowWhen(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(flow.id), notification)
        return true
    }

    private fun ensureAutoChannel() {
        if (Build.VERSION.SDK_INT < 26) return
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

    /** ۳ دکمه: اول دسته‌ی پیشنهادی (از مقصد خرید)، بعد پرکاربردترین دسته‌های خود کاربر. */
    private suspend fun pickCategories(flow: TransactionFlowEntity): List<CategoryEntity> {
        // برداشت ← دسته‌های خرج، واریز ← دسته‌های درآمد
        val byUsage = db.categoryDao().byUsage(flow.flowType)
        val suggested = flow.suggestedCategory?.let { name -> byUsage.firstOrNull { it.name == name } }
        return (listOfNotNull(suggested) + byUsage.filter { !it.name.startsWith("سایر") })
            .distinctBy { it.id }
            .take(3)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
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

        fun notificationId(transactionId: Long): Int = (transactionId % Int.MAX_VALUE).toInt()
    }
}
