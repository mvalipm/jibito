package ir.jibito.app.data.export

import ir.jibito.app.data.bank.Bank
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CsvExportTest {

    private val bank = Bank(id = 1, name = "ملت", parserKey = "mellat", senders = emptySet())
    private val food = Category(id = 1, name = "غذا", icon = "🍔", colorHex = null, flowType = 2)
    private val restaurant = Category(id = 2, name = "رستوران", icon = null, colorHex = null, flowType = 2, parentId = 1)

    /** ۱۴۰۴/۰۱/۰۱ = ۲۱ مارس ۲۰۲۵ */
    private fun at(hour: Int, minute: Int) = Calendar.getInstance().apply {
        clear()
        set(2025, Calendar.MARCH, 21, hour, minute)
    }.timeInMillis

    private fun tx(
        id: Long,
        amountRial: Long,
        type: FlowType = FlowType.WITHDRAWAL,
        categoryId: Long? = null,
        merchant: String? = null,
        hour: Int = 10,
        selfTransfer: Boolean = false,
        manual: Boolean = false,
        feeRial: Long? = null,
        note: String? = null,
    ) = Transaction(
        id = id, bank = if (manual) null else bank, body = "", dateMillis = at(hour, 5),
        transaction = ParsedTransaction(type = type, amountRial = amountRial, balanceRial = null),
        merchant = merchant, suggestedCategory = null, isFailedPurchase = false,
        categoryId = categoryId, categoryName = null, categoryIcon = null, isAutoCategorized = false,
        isSelfTransfer = selfTransfer, isManual = manual, feeRial = feeRial, note = note,
    )

    private fun lines(csv: String) = csv.removePrefix("\uFEFF").trimEnd().split("\r\n")

    @Test
    fun headerBomAndRowsInDateOrder() {
        val csv = CsvExport.build(
            listOf(
                tx(2, 500_000, categoryId = 2, merchant = "اسنپ‌فود", hour = 13, feeRial = 1_000),
                tx(1, 2_000_000, type = FlowType.DEPOSIT, hour = 9),
            ),
            listOf(food, restaurant),
        )
        assertTrue(csv.startsWith("\uFEFF"))
        val l = lines(csv)
        assertEquals(CsvExport.HEADER.joinToString(","), l[0])
        assertEquals("1404/01/01,09:05,درآمد,200000,,,,ملت,,پیامک,", l[1])
        assertEquals("1404/01/01,13:05,خرج,50000,غذا,رستوران,اسنپ‌فود,ملت,100,پیامک,", l[2])
    }

    @Test
    fun selfTransferAndManualAreLabelled() {
        val l = lines(CsvExport.build(listOf(tx(1, 10, selfTransfer = true), tx(2, 10, manual = true, merchant = "نان")), emptyList()))
        assertTrue(l[1].contains("انتقال به حساب خودم"))
        assertTrue(l[2].endsWith(",نان,,,دستی,"))
    }

    @Test
    fun noteIsTheLastColumn() {
        val l = lines(CsvExport.build(listOf(tx(1, 10, merchant = "اسنپ", note = "تا فرودگاه")), emptyList()))
        assertEquals("یادداشت", CsvExport.HEADER.last())
        assertTrue(l[1].endsWith(",اسنپ,ملت,,پیامک,تا فرودگاه"))
    }

    @Test
    fun escapesCommasQuotesAndFormulas() {
        assertEquals("\"a,b\"", CsvExport.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.escape("say \"hi\""))
        // متنی که اکسل فرمول حساب می‌کند خنثی می‌شود؛ عدد منفی نه
        assertEquals("'=HYPERLINK(1)", CsvExport.escape("=HYPERLINK(1)"))
        assertEquals("-500", CsvExport.escape("-500"))
    }
}
