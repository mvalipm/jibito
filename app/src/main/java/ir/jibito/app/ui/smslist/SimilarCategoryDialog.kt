package ir.jibito.app.ui.smslist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import ir.jibito.app.R
import ir.jibito.app.util.Jalali

/** سؤالِ بعد از عوض کردن دسته: [count] تراکنش دیگر از همین طرف حساب هم عوض شوند؟ */
internal data class SimilarAsk(
    val transactionId: Long,
    val merchant: String,
    val categoryId: Long,
    val categoryName: String,
    val count: Int,
)

@Composable
internal fun SimilarCategoryDialog(ask: SimilarAsk, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = {
            Text(
                stringResource(R.string.similar_title, Jalali.toPersianDigits(ask.count.toString())),
                fontWeight = FontWeight.Bold,
            )
        },
        text = { Text(stringResource(R.string.similar_body, ask.merchant, ask.categoryName)) },
        confirmButton = {
            TextButton(onClick = onYes) { Text(stringResource(R.string.similar_yes), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onNo) { Text(stringResource(R.string.similar_no)) } },
    )
}
