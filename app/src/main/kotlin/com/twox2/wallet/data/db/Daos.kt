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

    @Query("SELECT * FROM block_headers WHERE height = :height")
    suspend fun getByHeight(height: Int): BlockHeaderEntity?

    @Query("SELECT * FROM block_headers ORDER BY height ASC")
    fun observeAll(): Flow<List<BlockHeaderEntity>>
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
