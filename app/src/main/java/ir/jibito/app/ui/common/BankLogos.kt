package ir.jibito.app.ui.common

import androidx.annotation.DrawableRes
import ir.jibito.app.R

/**
 * لوگوی رنگی بانک‌ها (design/bank_logos/vector_xml) بر اساس شناسه‌ی ثابت بانک در BankDirectory.
 * کلید پارسر به کار نمی‌آید چون چند بانک پارسر مشترک دارند (مثلاً رسالت و توسعه تعاون با پاسارگاد).
 * بانکی که این‌جا نیست، لوگو ندارد و همان آیکون خطی بانک نشان داده می‌شود.
 */
object BankLogos {

    private val BY_ID: Map<Int, Int> = mapOf(
        1 to R.drawable.ic_bank_melli,
        2 to R.drawable.ic_bank_shahr,
        3 to R.drawable.ic_bank_tejarat,
        4 to R.drawable.ic_bank_sarmayeh,
        5 to R.drawable.ic_bank_eghtesad_novin,
        6 to R.drawable.ic_bank_keshavarzi,
        7 to R.drawable.ic_bank_saderat,
        10 to R.drawable.ic_bank_maskan,
        11 to R.drawable.ic_bank_mellat,
        12 to R.drawable.ic_bank_pasargad,
        13 to R.drawable.ic_bank_tosee_taavon,
        14 to R.drawable.ic_bank_parsian,
        15 to R.drawable.ic_bank_saman,
        16 to R.drawable.ic_bank_refah,
        18 to R.drawable.ic_bank_sina,
        21 to R.drawable.ic_bank_dey,
        22 to R.drawable.ic_bank_ayandeh,
        23 to R.drawable.ic_bank_gardeshgari,
        24 to R.drawable.ic_bank_sepah,
        25 to R.drawable.ic_bank_mehr_iran,
        26 to R.drawable.ic_bank_resalat,
        29 to R.drawable.ic_bank_karafarin,
        30 to R.drawable.ic_bank_post,
        31 to R.drawable.ic_bank_iran_zamin,
        38 to R.drawable.ic_bank_khavar_mianeh,
        40 to R.drawable.ic_bank_blu,
    )

    @DrawableRes
    fun of(bankId: Int?): Int? = bankId?.let { BY_ID[it] }
}
