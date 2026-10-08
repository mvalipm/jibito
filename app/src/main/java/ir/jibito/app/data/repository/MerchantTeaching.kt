package ir.jibito.app.data.repository

import androidx.room.withTransaction
import ir.jibito.app.data.category.CategoryLearning
import ir.jibito.app.data.category.CategorySuggester
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.MerchantRuleEntity
import ir.jibito.app.data.parser.MerchantExtractor
import ir.jibito.app.data.parser.MerchantRules

/**
 * درس «اسم فروشگاه کدومه؟»: کاربر خطِ اسم فروشگاه را در پیامک رمز دوم ([fromOtp]) یا خود پیامک تراکنش نشان داده.
 * [lineIndex] شماره‌ی خط در MerchantRules.lines است.
 */
data class MerchantLesson(val fromOtp: Boolean, val lineIndex: Int)

/**
 * اسم طرف حسابی که کاربر گفته را روی تراکنش می‌گذارد (با دسته‌ی پیشنهادی‌اش)،
 * و اگر درس داده، شکل پیامک را یاد می‌گیرد و همان‌جا روی تراکنش‌های بی‌اسمِ همان بانک هم اعمال می‌کند.
 * خواندن‌های بعدی پیامک‌ها هم همین شکل را به کار می‌برند (SmsReader).
 */
internal class MerchantTeaching(private val db: AppDatabase) {

    private val dao = db.transactionFlowDao()

    /** @return چند تراکنش دیگر با همین درس اسم گرفتند */
    suspend fun teach(transactionId: Long, merchant: String, lesson: MerchantLesson?): Int {
        val row = dao.byId(transactionId) ?: return 0
        val name = MerchantExtractor.clean(merchant) ?: merchant.trim().take(MAX_NAME).takeIf { it.isNotEmpty() } ?: return 0
        val learning = CategoryLearning(db)
        val now = System.currentTimeMillis()
        var others = 0
        db.withTransaction {
            dao.setMerchant(transactionId, name, suggestionFor(learning, name, row.flowType), now)
            val bankId = row.bankId ?: return@withTransaction
            val text = if (lesson?.fromOtp == true) row.otpBody else row.smsContent
            val skeleton = lesson?.let { MerchantRules.skeleton(text.orEmpty(), it.lineIndex) } ?: return@withTransaction
            val reviewDao = db.reviewDao()
            reviewDao.deleteMerchantRule(bankId, skeleton)
            reviewDao.insertMerchantRule(MerchantRuleEntity(bankId = bankId, skeleton = skeleton, createdAt = now))
            for (other in dao.withoutMerchant(bankId)) {
                if (other.id == transactionId) continue
                val found = MerchantRules.match(other.otpBody.orEmpty(), skeleton)
                    ?: MerchantRules.match(other.smsContent.orEmpty(), skeleton)
                    ?: continue
                dao.setMerchant(other.id, found, suggestionFor(learning, found, other.flowType), now)
                others++
            }
        }
        return others
    }

    /** اول آن‌چه از انتخاب‌های خود کاربر برای همین طرف حساب یاد گرفته شده، بعد پیشنهاد کلمه‌ای (اسنپ مارکت ← سوپرمارکت) */
    private suspend fun suggestionFor(learning: CategoryLearning, merchant: String, flowType: Int): String? =
        learning.decide(merchant, flowType)?.let { learning.nameOf(it.categoryId) } ?: CategorySuggester.suggest(merchant)

    private companion object {
        const val MAX_NAME = 40
    }
}
