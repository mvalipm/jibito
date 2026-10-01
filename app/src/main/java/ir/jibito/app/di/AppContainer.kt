package ir.jibito.app.di

import android.content.Context
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.repository.BudgetRepository
import ir.jibito.app.data.repository.BudgetRepositoryImpl
import ir.jibito.app.data.repository.ReviewRepository
import ir.jibito.app.data.repository.ReviewRepositoryImpl
import ir.jibito.app.data.repository.TransactionRepository
import ir.jibito.app.data.repository.TransactionRepositoryImpl
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.notify.BudgetAlerter

/**
 * جای ساختن اشیای اصلی اپ (دیتابیس، Repository ها).
 * فعلاً دستی و ساده؛ طبق سند معماری بعداً با Koin جایگزین می‌شود (فقط همین فایل عوض می‌شود).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val budgetAlerter: BudgetAlerter by lazy { BudgetAlerter(appContext, database) }

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepositoryImpl(
            db = database,
            smsReader = SmsReader(appContext),
            onCategoryChanged = { budgetAlerter.check() },
        )
    }

    val reviewRepository: ReviewRepository by lazy {
        ReviewRepositoryImpl(database, onTransactionAdded = { budgetAlerter.check() })
    }

    val budgetRepository: BudgetRepository by lazy {
        BudgetRepositoryImpl(database, onBudgetsChanged = { budgetAlerter.check() })
    }
}
