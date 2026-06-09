package com.twox2.wallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twox2.wallet.WalletApplication
import com.twox2.wallet.chain.ChainParams
import com.twox2.wallet.crypto.AddressEncoder
import com.twox2.wallet.data.db.SavedAddressEntity
import com.twox2.wallet.data.db.SyncStateEntity
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.sync.SyncEngine
import com.twox2.wallet.sync.SyncProgress
import com.twox2.wallet.wallet.WalletInfo
import com.twox2.wallet.wallet.WalletRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class FeeTier(val label: String, val feePerByte: Long, val displayFeeCoins: Double) {
    STANDARD("Standard", 5L, 0.001),
    FAST("Fast", 10L, 0.002),
    PRIORITY("Priority", 20L, 0.005)
}

enum class AddressValidation {
    NONE, VALID, INVALID
}

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as WalletApplication).repository

    private val _hasWallet = MutableStateFlow(repository.hasWallet())
    val hasWallet: StateFlow<Boolean> = _hasWallet.asStateFlow()

    private val _wallet = MutableStateFlow(repository.getWallet())
    val wallet: StateFlow<WalletInfo?> = _wallet.asStateFlow()

    private val _darkTheme = MutableStateFlow(repository.isDarkTheme)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _explorerBlockCount = MutableStateFlow<Int?>(null)
    val explorerBlockCount: StateFlow<Int?> = _explorerBlockCount.asStateFlow()

    private val _explorerLoading = MutableStateFlow(false)
    val explorerLoading: StateFlow<Boolean> = _explorerLoading.asStateFlow()

    val balance = repository.balance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val transactions = repository.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sendAddresses = repository.sendAddresses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val receiveAddresses = repository.receiveAddresses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncState: StateFlow<SyncStateEntity?> = repository.syncState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val syncProgress: StateFlow<SyncProgress> = SyncEngine.syncProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncProgress())

    val blockCount = repository.blockCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState = _sendState.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    private var verificationJob: Job? = null

    init {
        if (_hasWallet.value) {
            viewModelScope.launch {
                repository.ensurePrimaryReceiveAddress()
                _wallet.value = repository.getWallet()
            }
            startAutoSync()
            observeSyncForPeriodicVerification()
        }
    }

    fun createWallet() {
        viewModelScope.launch {
            val info = repository.createWallet()
            _wallet.value = info
            _hasWallet.value = true
            startAutoSync()
            observeSyncForPeriodicVerification()
        }
    }

    fun restoreWallet(wif: String) {
        viewModelScope.launch {
            runCatching {
                val info = repository.restoreWallet(wif.trim())
                _wallet.value = info
                _hasWallet.value = true
                startAutoSync()
                observeSyncForPeriodicVerification()
            }.onFailure {
                _snackbar.value = it.message ?: "Falha ao restaurar carteira"
            }
        }
    }

    private fun observeSyncForPeriodicVerification() {
        viewModelScope.launch {
            syncProgress.collect { progress ->
                if (progress.isSynced && verificationJob?.isActive != true) {
                    startPeriodicVerification()
                }
            }
        }
    }

    private fun startPeriodicVerification() {
        verificationJob?.cancel()
        verificationJob = viewModelScope.launch {
            while (isActive && _hasWallet.value && syncProgress.value.isSynced) {
                delay(30_000)
                if (!_hasWallet.value || !syncProgress.value.isSynced) break
                runCatching { repository.verifySync() }
            }
        }
    }

    fun startAutoSync() {
        repository.startAutoSync()
    }

    fun validateAddress(address: String): AddressValidation {
        val trimmed = address.trim()
        if (trimmed.isBlank()) return AddressValidation.NONE
        return if (AddressEncoder.isValidAddress(trimmed)) AddressValidation.VALID else AddressValidation.INVALID
    }

    fun send(toAddress: String, amount: String, feeTier: FeeTier) {
        viewModelScope.launch {
            _sendState.value = SendState.Loading
            val amountValue = amount.replace(",", ".").toDoubleOrNull()
            if (amountValue == null || amountValue <= 0) {
                _sendState.value = SendState.Error("Valor inválido")
                return@launch
            }
            if (amountValue < WalletRepository.MIN_SEND_COINS) {
                _sendState.value = SendState.Error("Envio mínimo: ${WalletRepository.MIN_SEND_COINS} ${ChainParams.CURRENCY}")
                return@launch
            }
            if (amountValue > WalletRepository.MAX_SEND_COINS) {
                _sendState.value = SendState.Error("Envio máximo: ${WalletRepository.MAX_SEND_COINS} ${ChainParams.CURRENCY}")
                return@launch
            }
            if (!AddressEncoder.isValidAddress(toAddress.trim())) {
                _sendState.value = SendState.Error("Endereço inválido")
                return@launch
            }
            val result = repository.sendCoins(toAddress, amountValue, feeTier)
            _sendState.value = result.fold(
                onSuccess = { SendState.Success(it) },
                onFailure = { SendState.Error(it.message ?: "Erro ao enviar") }
            )
        }
    }

    fun saveSendAddress(name: String, address: String) {
        viewModelScope.launch {
            runCatching {
                repository.saveSendAddress(name, address)
                _snackbar.value = "Endereço salvo"
            }.onFailure {
                _snackbar.value = it.message ?: "Falha ao salvar endereço"
            }
        }
    }

    fun createReceiveAddress(name: String) {
        viewModelScope.launch {
            runCatching {
                repository.createReceiveAddress(name)
                _snackbar.value = "Endereço de depósito criado"
            }.onFailure {
                _snackbar.value = it.message ?: "Falha ao criar endereço"
            }
        }
    }

    fun selectReceiveAddress(id: Long) {
        viewModelScope.launch {
            runCatching { repository.setDefaultReceiveAddress(id) }
        }
    }

    fun deleteSavedAddress(id: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteSavedAddress(id)
                _snackbar.value = "Endereço removido"
            }.onFailure {
                _snackbar.value = it.message ?: "Falha ao remover endereço"
            }
        }
    }

    fun refreshExplorerBlockCount() {
        viewModelScope.launch {
            _explorerLoading.value = true
            _explorerBlockCount.value = repository.fetchExplorerBlockCount()
            _explorerLoading.value = false
        }
    }

    fun resetSendState() {
        _sendState.value = SendState.Idle
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    fun showMessage(message: String) {
        _snackbar.value = message
    }

    fun setDarkTheme(enabled: Boolean) {
        repository.isDarkTheme = enabled
        _darkTheme.value = enabled
    }

    fun formatBalance(satoshis: Long): String {
        val coins = satoshis.toDouble() / ChainParams.COIN
        return "%.8f %s".format(coins, ChainParams.CURRENCY)
    }

    fun formatBalanceShort(satoshis: Long): String {
        val coins = satoshis.toDouble() / ChainParams.COIN
        val formatted = if (coins >= 1000) {
            "%,.2f".format(coins)
        } else {
            "%.2f".format(coins)
        }
        return "$formatted ${ChainParams.CURRENCY}"
    }

    fun formatUsdEstimate(satoshis: Long): String {
        val coins = satoshis.toDouble() / ChainParams.COIN
        val usd = coins * 0.10
        return "≈ $%.2f USD".format(usd)
    }

    fun pendingBalance(txs: List<WalletTransactionEntity>): Long {
        return txs.filter { it.blockHeight < 0 && it.amount > 0 }
            .sumOf { it.amount }
    }

    fun selectedReceiveAddress(addresses: List<SavedAddressEntity>): SavedAddressEntity? {
        return addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()
    }
}

sealed class SendState {
    data object Idle : SendState()
    data object Loading : SendState()
    data class Success(val txHash: String) : SendState()
    data class Error(val message: String) : SendState()
}
