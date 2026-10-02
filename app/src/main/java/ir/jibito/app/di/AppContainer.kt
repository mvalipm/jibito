package ir.jibito.app.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ir.jibito.app.widget.SpendWidget
import ir.jibito.app.data.backup.BackupManager
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.security.AppLockSettings
import ir.jibito.app.data.repository.BudgetRepository
import ir.jibito.app.data.repository.BudgetRepositoryImpl
import ir.jibito.app.data.repository.RecurringRepository
import ir.jibito.app.data.repository.ReviewRepository
import ir.jibito.app.notify.RecurringReminder
import ir.jibito.app.data.repository.ReviewRepositoryImpl
import ir.jibito.app.data.repository.TransactionRepository
import ir.jibito.app.data.repository.TransactionRepositoryImpl
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.SyncState
import ir.jibito.app.notify.BudgetAlerter
import ir.jibito.app.data.bank.CustomInstitutions
import ir.jibito.app.data.category.CategoryDisplaySettings
import ir.jibito.app.ui.theme.ThemeSettings

/**
 * جای ساختن اشیای اصلی اپ (دیتابیس، Repository ها).
 * فعلاً دستی و ساده؛ طبق سند معماری بعداً با Koin جایگزین می‌شود (فقط همین فایل عوض می‌شود).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** کارهای مشترک کل اپ (مثلاً فهرست مشترک تراکنش‌ها)؛ تا اپ زنده است زنده می‌ماند */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    /** چند لایه از دسته‌ها و کدام دسته‌های اصلی در برگه‌ی انتخاب دیده شوند */
    val categoryDisplay: CategoryDisplaySettings by lazy { CategoryDisplaySettings(appContext) }

    /** بانک‌ها و موسسه‌هایی که کاربر خودش اضافه کرده */
    val customInstitutions: CustomInstitutions by lazy { CustomInstitutions(appContext) }

    /** پوسته‌ی اپ (مرجانی، گرم، سرد) */
    val themeSettings: ThemeSettings by lazy { ThemeSettings(appContext) }

    /** قفل اپ با قفل گوشی */
    val appLockSettings: AppLockSettings by lazy { AppLockSettings(appContext) }

    /** پشتیبان‌گیری رمزدار و بازگردانی */
    val backupManager: BackupManager by lazy { BackupManager(appContext, database) }

    /** پرداخت‌های تکراری و یادآوری‌شان */
    val recurringRepository: RecurringRepository by lazy { RecurringRepository(database) }
    val recurringReminder: RecurringReminder by lazy { RecurringReminder(appContext, database) }

    val budgetAlerter: BudgetAlerter by lazy { BudgetAlerter(appContext, database) }

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepositoryImpl(
            db = database,
            smsReader = SmsReader(appContext),
            syncState = SyncState(appContext),
            onCategoryChanged = { onDataChanged() },
            onSynced = { SpendWidget.refresh(appContext) },
            appScope = appScope,
        )
    }

    val reviewRepository: ReviewRepository by lazy {
        ReviewRepositoryImpl(database, onTransactionAdded = { onDataChanged() })
    }

    val budgetRepository: BudgetRepository by lazy {
        BudgetRepositoryImpl(database, onBudgetsChanged = { onDataChanged() })
    }

    /** بعد از هر تغییر در خرج‌ها یا بودجه‌ها: هشدار بودجه + به‌روز کردن ویجت */
    suspend fun onDataChanged() {
        budgetAlerter.check()
        SpendWidget.refresh(appContext)
    }

    /** ویجت را بیرون از صفحه‌ها به‌روز می‌کند (مثلاً بعد از روشن/خاموش کردن قفل اپ) */
    fun refreshWidget() {
        appScope.launch { SpendWidget.refresh(appContext) }
    }
}
