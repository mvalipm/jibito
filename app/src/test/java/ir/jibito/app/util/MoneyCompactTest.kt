package ir.jibito.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyCompactTest {

    @Test
    fun `میلیون با یک رقم اعشار، بریده نه گرد`() {
        assertEquals("۴۸٫۹ میلیون", Money.compact(489_522_500L))   // ۴۸٬۹۵۲٬۲۵۰ تومان
        assertEquals("۱۰ میلیون", Money.compact(100_000_000L))
        assertEquals("۵۱ میلیون", Money.compact(510_838_160L))
    }

    @Test
    fun `هزار و کمتر`() {
        assertEquals("۹۸۱ هزار", Money.compact(9_811_000L))
        assertEquals("۵۰۰", Money.compact(5_000L))
        assertEquals("۰", Money.compact(0L))
    }

    @Test
    fun `میلیارد و منفی`() {
        assertEquals("۱٫۲ میلیارد", Money.compact(12_500_000_000L))
        assertEquals("−۲٫۱ میلیون", Money.compact(-21_000_000L))
    }
}
