package com.twox2.wallet.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_headers")
data class BlockHeaderEntity(
    @PrimaryKey val height: Int,
    val hash: String,
    val prevHash: String,
    val merkleRoot: String,
    val time: Long,
    val bits: Long,
    val nonce: Long,
    val version: Int
)

@Entity(tableName = "utxos")
data class UtxoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txHash: String,
    val outputIndex: Int,
    val value: Long,
    val scriptPubKey: String,
    val blockHeight: Int,
    val spent: Boolean = false
)

@Entity(tableName = "wallet_transactions")
data class WalletTransactionEntity(
    @PrimaryKey val txHash: String,
    val blockHeight: Int,
    val timestamp: Long,
    val amount: Long,
    val fee: Long,
    val type: String,
    val address: String,
    val confirmations: Int
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val bestHeight: Int,
    val bestHash: String,
    val isSyncing: Boolean,
    val progress: Int,
    val peerHost: String?
)
