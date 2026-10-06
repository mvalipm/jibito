package ir.jibito.app.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

/** چت پشتیبانی جیبیتو در پیام‌رسان بله (با شناسه، تا شماره‌ی تلفن پشتیبانی هیچ‌جا دیده نشود) */
const val SUPPORT_BALE_URL = "https://ble.ir/jibito_support"

/**
 * متن را کپی می‌کند و چت پشتیبانی را در بله باز می‌کند تا کاربر فقط بچسباند و بفرستد.
 * اگر بله (و مرورگر) نباشد، صفحه‌ی اشتراک‌گذاری باز می‌شود تا متن از دست نرود.
 */
fun openSupportChat(context: Context, text: String, copiedHint: String, chooserTitle: String, subject: String? = null) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("jibito support", text))
    val opened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, SUPPORT_BALE_URL.toUri()))
    }.isSuccess
    if (opened) {
        Toast.makeText(context, copiedHint, Toast.LENGTH_LONG).show()
    } else {
        shareText(context, text, chooserTitle, subject)
    }
}

/** صفحه‌ی اشتراک‌گذاری اندروید برای هر راه دیگری جز بله */
fun shareText(context: Context, text: String, chooserTitle: String, subject: String? = null) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    if (subject != null) send.putExtra(Intent.EXTRA_SUBJECT, subject)
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
