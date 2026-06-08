package com.twox2.wallet.wallet

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.twox2.wallet.crypto.AddressEncoder
import com.twox2.wallet.crypto.Secp256k1
import com.twox2.wallet.data.db.SavedAddressEntity
import com.twox2.wallet.data.db.WalletDatabase
import kotlinx.coroutines.flow.Flow

class WalletManager private constructor(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "wallet_secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val db = WalletDatabase.get(context)

    val balance: Flow<Long> = db.utxoDao().observeBalance()
    val transactions = db.walletTransactionDao().observeRecent()
    val syncState = db.syncStateDao().observe()
    val sendAddresses = db.savedAddressDao().observeByType("send")
    val receiveAddresses = db.savedAddressDao().observeByType("receive")

    fun hasWallet(): Boolean = prefs.contains(KEY_PRIVATE)

    fun createWallet(): WalletInfo {
        val (privateKey, publicKey) = Secp256k1.generateKeyPair()
        saveKeys(privateKey, publicKey)
        return getWalletInfo(privateKey, publicKey)
    }

    fun restoreFromWif(wif: String): WalletInfo {
        val trimmed = wif.trim()
        val (version, payload) = com.twox2.wallet.crypto.Base58.decodeCheck(trimmed)
        require(version == com.twox2.wallet.chain.ChainParams.WIF_VERSION) { "WIF inválido" }
        val privateKey = if (payload.size == 33 && payload[32] == 0x01.toByte()) {
            payload.copyOfRange(0, 32)
        } else {
            payload
        }
        val publicKey = Secp256k1.publicKeyFromPrivate(privateKey)
        saveKeys(privateKey, publicKey)
        return getWalletInfo(privateKey, publicKey)
    }

    fun loadWallet(): WalletInfo? {
        val privateKey = prefs.getString(KEY_PRIVATE, null)?.hexToBytes() ?: return null
        val publicKey = prefs.getString(KEY_PUBLIC, null)?.hexToBytes()
            ?: Secp256k1.publicKeyFromPrivate(privateKey)
        return getWalletInfo(privateKey, publicKey)
    }

    fun getReceiveScriptPubKey(): ByteArray {
        val wallet = loadWallet() ?: error("Carteira não inicializada")
        return AddressEncoder.addressToScriptPubKey(wallet.address)
    }

    suspend fun getAllReceiveScriptPubKeys(): List<String> {
        val addresses = db.savedAddressDao().getByType("receive")
        if (addresses.isEmpty()) {
            val wallet = loadWallet() ?: return emptyList()
            return listOf(AddressEncoder.addressToScriptPubKey(wallet.address).toHex())
        }
        return addresses.map { AddressEncoder.addressToScriptPubKey(it.address).toHex() }
    }

    suspend fun ensurePrimaryReceiveAddress(info: WalletInfo) {
        val existing = db.savedAddressDao().getByType("receive")
        if (existing.isEmpty()) {
            db.savedAddressDao().insert(
                SavedAddressEntity(
                    name = "Principal",
                    address = info.address,
                    cashAddress = info.cashAddress,
                    type = "receive",
                    isDefault = true,
                    privateKeyHex = info.privateKey.toHex(),
                    publicKeyHex = info.publicKey.toHex()
                )
            )
        }
    }

    suspend fun createReceiveAddress(name: String): SavedAddressEntity {
        val (privateKey, publicKey) = Secp256k1.generateKeyPair()
        val address = AddressEncoder.publicKeyToAddress(publicKey)
        val cashAddress = AddressEncoder.publicKeyToCashAddress(publicKey)
        val entity = SavedAddressEntity(
            name = name.trim(),
            address = address,
            cashAddress = cashAddress,
            type = "receive",
            isDefault = false,
            privateKeyHex = privateKey.toHex(),
            publicKeyHex = publicKey.toHex()
        )
        val id = db.savedAddressDao().insert(entity)
        return entity.copy(id = id)
    }

    suspend fun saveSendAddress(name: String, address: String) {
        require(AddressEncoder.isValidAddress(address)) { "Endereço inválido" }
        val cashAddress = if (address.contains(':')) address else address
        db.savedAddressDao().insert(
            SavedAddressEntity(
                name = name.trim(),
                address = address.trim(),
                cashAddress = cashAddress,
                type = "send"
            )
        )
    }

    suspend fun setDefaultReceiveAddress(id: Long) {
        db.savedAddressDao().clearDefault("receive")
        db.savedAddressDao().getById(id)?.let { entity ->
            db.savedAddressDao().update(entity.copy(isDefault = true))
        }
    }

    suspend fun deleteSavedAddress(id: Long) {
        val entity = db.savedAddressDao().getById(id) ?: return
        require(!entity.isDefault) { "Não é possível remover o endereço principal" }
        db.savedAddressDao().delete(id)
    }

    private fun saveKeys(privateKey: ByteArray, publicKey: ByteArray) {
        prefs.edit()
            .putString(KEY_PRIVATE, privateKey.toHex())
            .putString(KEY_PUBLIC, publicKey.toHex())
            .apply()
    }

    private fun getWalletInfo(privateKey: ByteArray, publicKey: ByteArray): WalletInfo {
        return WalletInfo(
            address = AddressEncoder.publicKeyToAddress(publicKey),
            cashAddress = AddressEncoder.publicKeyToCashAddress(publicKey),
            wif = AddressEncoder.privateKeyToWif(privateKey),
            publicKey = publicKey,
            privateKey = privateKey
        )
    }

    fun getDatabase() = db

    companion object {
        private const val KEY_PRIVATE = "private_key"
        private const val KEY_PUBLIC = "public_key"

        @Volatile
        private var instance: WalletManager? = null

        fun get(context: Context): WalletManager {
            return instance ?: synchronized(this) {
                instance ?: WalletManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

data class WalletInfo(
    val address: String,
    val cashAddress: String,
    val wif: String,
    val publicKey: ByteArray,
    val privateKey: ByteArray
)

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0)
    return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
