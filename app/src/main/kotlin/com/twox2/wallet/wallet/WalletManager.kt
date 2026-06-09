package com.twox2.wallet.wallet

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.twox2.wallet.crypto.AddressEncoder
import com.twox2.wallet.crypto.Bip32
import com.twox2.wallet.crypto.isLegacyBase58Address
import com.twox2.wallet.crypto.resolveLegacyAddress
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
        val seed = Bip32.generateSeed()
        val extKey = Bip32.deriveReceiveKey(seed, 0)
        val privateKey = extKey.privateKey
        val publicKey = Secp256k1.publicKeyFromPrivate(privateKey)
        saveHdWallet(seed, 1)
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
        clearHdWallet()
        saveKeys(privateKey, publicKey)
        return getWalletInfo(privateKey, publicKey)
    }

    fun loadWallet(): WalletInfo? {
        val privateKey = prefs.getString(KEY_PRIVATE, null)?.hexToBytes() ?: return null
        val storedPublic = prefs.getString(KEY_PUBLIC, null)?.hexToBytes()
        val publicKey = when {
            storedPublic == null -> Secp256k1.publicKeyFromPrivate(privateKey)
            storedPublic.size == 65 && storedPublic[0] == 0x04.toByte() -> {
                val compressed = Secp256k1.publicKeyFromPrivate(privateKey)
                saveKeys(privateKey, compressed)
                compressed
            }
            else -> storedPublic
        }
        return getWalletInfo(privateKey, publicKey)
    }

    fun getReceiveScriptPubKey(): ByteArray {
        val wallet = loadWallet() ?: error("Carteira não inicializada")
        return AddressEncoder.addressToScriptPubKey(wallet.address)
    }

    suspend fun getAllReceiveLegacyAddresses(): List<String> {
        val wallet = loadWallet()
        val addresses = db.savedAddressDao().getByType("receive")
        if (addresses.isEmpty()) {
            return listOfNotNull(wallet?.address?.takeIf { it.isNotBlank() })
        }
        return addresses.mapNotNull { entity ->
            resolveLegacyAddress(entity.address, entity.cashAddress, entity.publicKeyHex)
                .takeIf { it.isNotBlank() }
        }.distinct()
    }

    suspend fun getAllReceiveScriptPubKeys(): List<String> {
        val addresses = db.savedAddressDao().getByType("receive")
        if (addresses.isEmpty()) {
            val wallet = loadWallet() ?: return emptyList()
            return listOf(AddressEncoder.addressToScriptPubKey(wallet.address).toHex())
        }
        return addresses.mapNotNull { entity ->
            val legacy = resolveLegacyAddress(entity.address, entity.cashAddress, entity.publicKeyHex)
            if (legacy.isBlank()) null else AddressEncoder.addressToScriptPubKey(legacy).toHex()
        }
    }

    suspend fun ensurePrimaryReceiveAddress(info: WalletInfo) {
        migrateReceiveAddresses(info)
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

    suspend fun migrateReceiveAddresses(info: WalletInfo? = loadWallet()) {
        val wallet = info ?: return
        val addresses = db.savedAddressDao().getByType("receive")
        for (entity in addresses) {
            val privateKey = entity.privateKeyHex?.hexToBytes()
                ?: if (entity.isDefault) wallet.privateKey else null
            val publicKey = when {
                entity.publicKeyHex != null -> {
                    val stored = entity.publicKeyHex.hexToBytes()
                    ensureCompressedPublicKey(stored, privateKey ?: wallet.privateKey)
                }
                privateKey != null -> Secp256k1.publicKeyFromPrivate(privateKey)
                entity.isDefault -> wallet.publicKey
                else -> continue
            }
            val legacyAddress = AddressEncoder.publicKeyToAddress(publicKey)
            val needsMigration = !isLegacyBase58Address(entity.address) ||
                entity.address != legacyAddress ||
                AddressEncoder.cashAddrToLegacyAddress(entity.address) != null
            if (needsMigration) {
                db.savedAddressDao().update(
                    entity.copy(
                        address = legacyAddress,
                        cashAddress = AddressEncoder.publicKeyToCashAddress(publicKey),
                        publicKeyHex = publicKey.toHex(),
                        privateKeyHex = privateKey?.toHex() ?: entity.privateKeyHex
                    )
                )
            }
        }
    }

    suspend fun createReceiveAddress(name: String): SavedAddressEntity {
        val (privateKey, publicKey) = deriveNextReceiveKey()
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

    suspend fun resolveSigningKey(scriptPubKeyHex: String): Pair<ByteArray, ByteArray>? {
        val normalized = scriptPubKeyHex.lowercase()
        val receiveAddresses = db.savedAddressDao().getByType("receive")
        for (entity in receiveAddresses) {
            val legacyAddress = resolveLegacyAddress(
                entity.address,
                entity.cashAddress,
                entity.publicKeyHex
            )
            if (legacyAddress.isBlank()) continue
            val script = AddressEncoder.addressToScriptPubKey(legacyAddress).toHex().lowercase()
            if (script == normalized) {
                val privateKey = entity.privateKeyHex?.hexToBytes() ?: continue
                val publicKey = entity.publicKeyHex?.hexToBytes()
                    ?: Secp256k1.publicKeyFromPrivate(privateKey)
                return privateKey to ensureCompressedPublicKey(publicKey, privateKey)
            }
        }
        val wallet = loadWallet() ?: return null
        val script = AddressEncoder.addressToScriptPubKey(wallet.address).toHex().lowercase()
        if (script == normalized) {
            return wallet.privateKey to wallet.publicKey
        }
        return null
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

    private fun deriveNextReceiveKey(): Pair<ByteArray, ByteArray> {
        val seed = prefs.getString(KEY_HD_SEED, null)?.hexToBytes()
        if (seed != null) {
            val index = prefs.getInt(KEY_HD_COUNTER, 1)
            val extKey = Bip32.deriveReceiveKey(seed, index)
            prefs.edit().putInt(KEY_HD_COUNTER, index + 1).apply()
            val privateKey = extKey.privateKey
            return privateKey to Secp256k1.publicKeyFromPrivate(privateKey)
        }
        return Secp256k1.generateKeyPair()
    }

    private fun saveHdWallet(seed: ByteArray, nextCounter: Int) {
        prefs.edit()
            .putString(KEY_HD_SEED, seed.toHex())
            .putInt(KEY_HD_COUNTER, nextCounter)
            .apply()
    }

    private fun clearHdWallet() {
        prefs.edit()
            .remove(KEY_HD_SEED)
            .remove(KEY_HD_COUNTER)
            .apply()
    }

    private fun saveKeys(privateKey: ByteArray, publicKey: ByteArray) {
        prefs.edit()
            .putString(KEY_PRIVATE, privateKey.toHex())
            .putString(KEY_PUBLIC, publicKey.toHex())
            .apply()
    }

    private fun getWalletInfo(privateKey: ByteArray, publicKey: ByteArray): WalletInfo {
        val compressedPublicKey = ensureCompressedPublicKey(publicKey, privateKey)
        return WalletInfo(
            address = AddressEncoder.publicKeyToAddress(compressedPublicKey),
            cashAddress = AddressEncoder.publicKeyToCashAddress(compressedPublicKey),
            wif = AddressEncoder.privateKeyToWif(privateKey),
            publicKey = compressedPublicKey,
            privateKey = privateKey
        )
    }

    private fun ensureCompressedPublicKey(publicKey: ByteArray, privateKey: ByteArray): ByteArray {
        return if (publicKey.size == 33 && (publicKey[0] == 0x02.toByte() || publicKey[0] == 0x03.toByte())) {
            publicKey
        } else {
            Secp256k1.publicKeyFromPrivate(privateKey)
        }
    }

    fun getDatabase() = db

    suspend fun clearChainData() {
        db.blockHeaderDao().deleteAll()
        db.utxoDao().deleteAll()
        db.walletTransactionDao().deleteAll()
        db.syncStateDao().deleteAll()
        db.savedAddressDao().deleteAll()
    }

    fun deleteWallet(context: Context) {
        prefs.edit().clear().apply()
        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    companion object {
        private const val KEY_PRIVATE = "private_key"
        private const val KEY_PUBLIC = "public_key"
        private const val KEY_HD_SEED = "hd_seed"
        private const val KEY_HD_COUNTER = "hd_counter"

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
