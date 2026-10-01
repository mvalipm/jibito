package ir.jibito.app.data.parser

import java.text.Normalizer

/**
 * متن پیامک را قبل از پارس یکدست می‌کند:
 * - شکل‌های خاص حروف عربی (مثل «ﻭﺍﺭﻳﺰ») ← حروف معمولی
 * - «ي» و «ك» عربی ← «ی» و «ک» فارسی
 * - ارقام فارسی و عربی ← ارقام انگلیسی
 * - حذف نشانه‌های نامرئی جهت متن
 */
object SmsTextNormalizer {

    fun normalize(raw: String): String {
        val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        val sb = StringBuilder(nfkc.length)
        for (ch in nfkc) {
            when (ch) {
                'ي', 'ى' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ة' -> sb.append('ه')
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                '\r' -> {}
                '‎', '‏', '‪', '‫', '‬', '‭', '‮', '⁦', '⁧', '⁨', '⁩' -> {}
                '٬' -> sb.append(',')
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim()
    }
}
