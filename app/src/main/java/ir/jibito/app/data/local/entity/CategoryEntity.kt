package ir.jibito.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** دسته‌بندی خرج (غذا، رفت‌وآمد، ...). طبق سند معماری بخش ۴. */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val colorHex: String? = null,
    val isArchived: Boolean = false,
)
