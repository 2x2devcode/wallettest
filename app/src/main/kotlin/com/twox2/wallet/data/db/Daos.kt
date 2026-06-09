package com.twox2.wallet.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockHeaderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(headers: List<BlockHeaderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(header: BlockHeaderEntity)

    @Query("SELECT * FROM block_headers ORDER BY height DESC LIMIT 1")
    suspend fun getTip(): BlockHeaderEntity?

    @Query("SELECT COUNT(*) FROM block_headers")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM block_headers")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(MAX(height), 0) FROM block_headers")
    fun observeTipHeight(): Flow<Int>

    @Query("SELECT * FROM block_headers WHERE height = :height")
    suspend fun getByHeight(height: Int): BlockHeaderEntity?

    @Query("SELECT * FROM block_headers ORDER BY height ASC")
    fun observeAll(): Flow<List<BlockHeaderEntity>>

    @Query("DELETE FROM block_headers")
    suspend fun deleteAll()

    @Query("DELETE FROM block_headers WHERE height > :height")
    suspend fun deleteAbove(height: Int)
}

@Dao
interface UtxoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(utxo: UtxoEntity)

    @Query("SELECT * FROM utxos WHERE spent = 0 ORDER BY value DESC")
    suspend fun getUnspent(): List<UtxoEntity>

    @Query("SELECT COALESCE(SUM(value), 0) FROM utxos WHERE spent = 0")
    fun observeBalance(): Flow<Long>

    @Query("UPDATE utxos SET spent = 1 WHERE txHash = :txHash AND outputIndex = :index")
    suspend fun markSpent(txHash: String, index: Int)

    @Query("SELECT * FROM utxos WHERE scriptPubKey = :scriptHex AND spent = 0")
    suspend fun getByScript(scriptHex: String): List<UtxoEntity>

    @Query("SELECT * FROM utxos WHERE txHash = :txHash AND outputIndex = :outputIndex LIMIT 1")
    suspend fun findByOutpoint(txHash: String, outputIndex: Int): UtxoEntity?
}

@Dao
interface WalletTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: WalletTransactionEntity)

    @Query("SELECT * FROM wallet_transactions ORDER BY timestamp DESC LIMIT 50")
    fun observeRecent(): Flow<List<WalletTransactionEntity>>

    @Query("SELECT * FROM wallet_transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WalletTransactionEntity>>

    @Query("SELECT * FROM wallet_transactions WHERE type = :type ORDER BY timestamp DESC")
    fun observeByType(type: String): Flow<List<WalletTransactionEntity>>

    @Query("SELECT COUNT(*) > 0 FROM wallet_transactions WHERE txHash = :txHash")
    suspend fun exists(txHash: String): Boolean
}

@Dao
interface SyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE id = 1")
    fun observe(): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun get(): SyncStateEntity?
}

@Dao
interface SavedAddressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(address: SavedAddressEntity): Long

    @Update
    suspend fun update(address: SavedAddressEntity)

    @Query("SELECT * FROM saved_addresses WHERE type = :type ORDER BY isDefault DESC, name ASC")
    fun observeByType(type: String): Flow<List<SavedAddressEntity>>

    @Query("SELECT * FROM saved_addresses WHERE type = :type ORDER BY isDefault DESC, name ASC")
    suspend fun getByType(type: String): List<SavedAddressEntity>

    @Query("SELECT * FROM saved_addresses WHERE id = :id")
    suspend fun getById(id: Long): SavedAddressEntity?

    @Query("DELETE FROM saved_addresses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE saved_addresses SET isDefault = 0 WHERE type = :type")
    suspend fun clearDefault(type: String)
}
