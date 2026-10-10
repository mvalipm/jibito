package ir.jibito.app.data.local.entity

/** دسته‌ای که خود کاربر برای یک طرف حساب انتخاب کرده (برای MerchantWordModel) */
data class MerchantChoice(
    val merchant: String,
    val categoryId: Long,
)
