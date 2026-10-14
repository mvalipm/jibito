package ir.jibito.app.notify

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ir.jibito.app.R

/**
 * روی صفحه‌ی قفل، نوتیف‌های جیبیتو مبلغ، اسم فروشگاه و موجودی را نشان نمی‌دهند؛ فقط یک «اعلان تازه».
 * بعد از باز کردن قفل گوشی همان نوتیف کامل دیده می‌شود (اندروید خودش نسخه‌ی عمومی را جایگزین می‌کند).
 */
fun NotificationCompat.Builder.hideOnLockScreen(context: Context, channelId: String): NotificationCompat.Builder =
    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_jibito)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notif_public_text))
                .setColor(ContextCompat.getColor(context, R.color.jibito_primary))
                .build()
        )
