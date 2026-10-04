package ir.jibito.app.data.local.entity

import androidx.room.Entity
import ir.jibito.app.data.wallet.AccountLink

/**
 * تصمیم کاربر درباره‌ی یک شماره حساب/کارت (از نسخه‌ی ۱۳ دیتابیس).
 * خود تراکنش‌ها هیچ‌وقت عوض نمی‌شوند؛ فقط همین «قانون‌ها» عوض می‌شوند، پس هر تصمیمی همیشه برگشت‌پذیر است.
 *
 * - [groupAccount]: حسابی که این حساب «با آن یکی است» (نماینده‌ی گروه). برابر خود [account] یعنی حساب جداست.
 *   نماینده‌ی هر گروه همیشه به خودش اشاره می‌کند (زنجیره نداریم).
 * - [name]: اسمی که کاربر برای گروه گذاشته (فقط روی ردیف نماینده معنی دارد).
 * - [decided]: کاربر به سؤال «جدا هستند یا یکی؟» درباره‌ی این حساب جواب داده (ردیفی که فقط اسم دارد، false است).
 */
@Entity(tableName = "account_links", primaryKeys = ["bankId", "account"])
data class AccountLinkEntity(
    val bankId: Int,
    val account: String,
    val groupAccount: String,
    val name: String?,
    val decided: Boolean,
    val updatedAt: Long,
)

fun AccountLinkEntity.toLink() = AccountLink(bankId, account, groupAccount, name, decided)

fun AccountLink.toEntity(now: Long) = AccountLinkEntity(bankId, account, groupAccount, name, decided, now)
