package ir.jibito.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.SmsFlowKey
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.local.entity.TransactionWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionFlowDao {

    @Query(
        """
        SELECT t.*, c.name AS categoryName
        FROM transaction_flows t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.isDeleted = 0
        ORDER BY t.dateEpoch DESC
        """
    )
    fun observeAll(): Flow<List<TransactionWithCategory>>

    @Query("SELECT id, smsId, categoryId, isDeleted FROM transaction_flows WHERE smsId IS NOT NULL")
    suspend fun smsKeys(): List<SmsFlowKey>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<TransactionFlowEntity>)

    @Update
    suspend fun updateAll(items: List<TransactionFlowEntity>)

    @Query("UPDATE transaction_flows SET isDeleted = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Long>, now: Long)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY id")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insertAll(items: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}
