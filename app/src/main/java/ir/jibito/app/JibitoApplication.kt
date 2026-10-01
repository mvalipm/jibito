package ir.jibito.app

import android.app.Application
import ir.jibito.app.di.AppContainer

/** کلاس اصلی اپ؛ فقط یک AppContainer برای کل اپ نگه می‌دارد. */
class JibitoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
