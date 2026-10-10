package ir.jibito.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.OwnAccountEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.SyncState
import ir.jibito.app.domain.TransferSuggestion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «برگردون» بعد از جواب دادن به سؤال‌های تب «کارها»: انتقال به خودت و خرج یک‌باره (چه «آره» چه «نه») */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnswerUndoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepositoryImpl
    private val dao get() = db.transactionFlowDao()

    @Before
    fun setUp() {
        context.deleteDatabase(AppDatabase.NAME)
        db = AppDatabase.build(context)
        repo = TransactionRepositoryImpl(db, SmsReader(context), SyncState(context))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
    }

    private suspend fun suggestion(withdrawalId: Long, depositId: Long): TransferSuggestion {
        val all = repo.observeTransactions().first()
        return TransferSuggestion(all.single { it.id == withdrawalId }, all.single { it.id == depositId })
    }

    @Test
    fun undoYesToTransferBringsBackBothSidesAndForgetsTheNewCard() = runBlocking {
        val cat = db.categoryDao().insert(CategoryEntity(name = "خوراک", icon = "x", colorHex = "#000000", flowType = 2))
        val w = dao.insert(tx(1, flowType = 2, merchant = CARD, categoryId = cat))
        val d = dao.insert(tx(2, flowType = 1, merchant = null))
        // برداشت دیگری به همین مقصد؛ «کارت خودم» شدنش آن را هم انتقال می‌کند
        val other = dao.insert(tx(3, flowType = 2, merchant = CARD, categoryId = cat))

        val undo = repo.confirmTransfer(suggestion(w, d))
        assertEquals(TransactionFlowEntity.TRANSFER_SELF, dao.byId(w)!!.transferState)
        assertEquals(TransactionFlowEntity.TRANSFER_SELF, dao.byId(other)!!.transferState)
        assertTrue(CARD in dao.ownAccounts())

        repo.restore(undo)
        for (id in listOf(w, d, other)) {
            assertEquals(TransactionFlowEntity.TRANSFER_NONE, dao.byId(id)!!.transferState)
            assertNull(dao.byId(id)!!.transferPairId)
        }
        // دسته‌ی برداشت‌ها که انتقال شدن پاکش کرده بود، برگشته
        assertEquals(cat, dao.byId(w)!!.categoryId)
        assertEquals(cat, dao.byId(other)!!.categoryId)
        assertTrue(dao.ownAccounts().isEmpty())
    }

    @Test
    fun undoKeepsACardThatWasAlreadyKnownAsOwn() = runBlocking {
        dao.insertOwnAccount(OwnAccountEntity(CARD, 1))
        val w = dao.insert(tx(1, flowType = 2, merchant = CARD))
        val d = dao.insert(tx(2, flowType = 1, merchant = null))

        val undo = repo.confirmTransfer(suggestion(w, d))
        repo.restore(undo)

        assertEquals(TransactionFlowEntity.TRANSFER_NONE, dao.byId(w)!!.transferState)
        assertEquals(listOf(CARD), dao.ownAccounts())
    }

    @Test
    fun undoNoToTransferSuggestsItAgain() = runBlocking {
        val w = dao.insert(tx(1, flowType = 2, merchant = CARD))
        val d = dao.insert(tx(2, flowType = 1, merchant = null))

        val undo = repo.rejectTransfer(suggestion(w, d))
        assertEquals(TransactionFlowEntity.TRANSFER_REJECTED, dao.byId(w)!!.transferState)

        repo.restore(undo)
        assertEquals(TransactionFlowEntity.TRANSFER_NONE, dao.byId(w)!!.transferState)
        // برداشت دیگر هیچ‌چیزِ دیگری عوض نشده
        assertEquals(TransactionFlowEntity.TRANSFER_NONE, dao.byId(d)!!.transferState)
    }

    @Test
    fun undoOneOffAnswerAsksAgain() = runBlocking {
        val yes = dao.insert(tx(1, flowType = 2, merchant = null))
        val no = dao.insert(tx(2, flowType = 2, merchant = null))

        val undoYes = repo.setOneOff(yes, true)
        val undoNo = repo.setOneOff(no, false)
        assertEquals(TransactionFlowEntity.ONE_OFF_YES, dao.byId(yes)!!.oneOffState)
        assertEquals(TransactionFlowEntity.ONE_OFF_REJECTED, dao.byId(no)!!.oneOffState)

        repo.restore(undoYes)
        repo.restore(undoNo)
        assertEquals(TransactionFlowEntity.ONE_OFF_NONE, dao.byId(yes)!!.oneOffState)
        assertEquals(TransactionFlowEntity.ONE_OFF_NONE, dao.byId(no)!!.oneOffState)
    }

    @Test
    fun undoingOneOffDoesNotTouchTheCategoryChosenSince() = runBlocking {
        val cat = db.categoryDao().insert(CategoryEntity(name = "خانه", icon = "x", colorHex = "#000000", flowType = 2))
        val id = dao.insert(tx(1, flowType = 2, merchant = null, categoryId = cat))
        val undo = repo.setOneOff(id, true)
        repo.restore(undo)
        assertEquals(cat, dao.byId(id)!!.categoryId)
        assertEquals(TransactionFlowEntity.ONE_OFF_NONE, dao.byId(id)!!.oneOffState)
    }

    private fun tx(smsId: Long, flowType: Int, merchant: String?, categoryId: Long? = null) = TransactionFlowEntity(
        smsId = smsId,
        bankId = 1,
        flowType = flowType,
        amount = 50_000_000,
        remainAfter = null,
        dateEpoch = 1_000 + smsId,
        merchant = merchant,
        suggestedCategory = null,
        isFailedPurchase = false,
        categoryId = categoryId,
        description = null,
        smsContent = "sms $smsId",
        source = SOURCE_SMS_AUTO,
    )

    private companion object {
        const val CARD = "کارت ۴۴۱۰"
    }
}
