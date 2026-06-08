package com.twox2.wallet.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BlockHeaderEntity::class,
        UtxoEntity::class,
        WalletTransactionEntity::class,
        SyncStateEntity::class,
        SavedAddressEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun blockHeaderDao(): BlockHeaderDao
    abstract fun utxoDao(): UtxoDao
    abstract fun walletTransactionDao(): WalletTransactionDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun savedAddressDao(): SavedAddressDao

    companion object {
        @Volatile
        private var instance: WalletDatabase? = null

        fun get(context: Context): WalletDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WalletDatabase::class.java,
                    "2x2_wallet.db"
                ).fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
        }
    }
}
