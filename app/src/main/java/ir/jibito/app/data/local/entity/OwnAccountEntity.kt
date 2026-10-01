package ir.jibito.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * کارت/حسابی که کاربر گفته مال خودش است (از نسخه‌ی ۸ دیتابیس).
 * merchant همان «طرف حساب» برداشت است، مثلاً «کارت/حساب …1234».
 * برداشت‌های بعدی به این مقصد، خودکار «انتقال به خودم» می‌شوند.
 */
@Entity(tableName = "own_accounts")
data class OwnAccountEntity(
    @PrimaryKey val merchant: String,
    val createdAt: Long,
)
