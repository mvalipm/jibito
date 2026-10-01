package ir.jibito.app.di

import android.content.Context
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.repository.TransactionRepository
import ir.jibito.app.data.repository.TransactionRepositoryImpl
import ir.jibito.app.data.sms.SmsReader

/**
 * جای ساختن اشیای اصلی اپ (دیتابیس، Repository ها).
 * فعلاً دستی و ساده؛ طبق سند معماری بعداً با Koin جایگزین می‌شود (فقط همین فایل عوض می‌شود).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val transactionRepository: TransactionRepository by lazy {
        TransactionRepositoryImpl(database, SmsReader(appContext))
    }
}
