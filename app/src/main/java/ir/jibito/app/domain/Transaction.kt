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
    val categoryName: String?,
)
