package com.twox2.wallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twox2.wallet.WalletApplication
import com.twox2.wallet.chain.ChainParams
import com.twox2.wallet.data.db.SyncStateEntity
import com.twox2.wallet.data.db.WalletTransactionEntity
import com.twox2.wallet.sync.SyncEngine
import com.twox2.wallet.sync.SyncProgress
import com.twox2.wallet.wallet.WalletInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeeTier(val label: String, val feePerByte: Long) {
    FAST("Rápida", 20L),
    MEDIUM("Média", 10L),
    SLOW("Lenta", 5L)
}

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as WalletApplication).repository

    private val _hasWallet = MutableStateFlow(repository.hasWallet())
    val hasWallet: StateFlow<Boolean> = _hasWallet.asStateFlow()

    private val _wallet = MutableStateFlow(repository.getWallet())
    val wallet: StateFlow<WalletInfo?> = _wallet.asStateFlow()

    private val _darkTheme = MutableStateFlow(repository.isDarkTheme)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    val balance = repository.balance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val transactions = repository.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions = repository.allTransactions
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

    init {
        if (_hasWallet.value) {
            startAutoSync()
        }
    }

    fun createWallet() {
        val info = repository.createWallet()
        _wallet.value = info
        _hasWallet.value = true
        startAutoSync()
    }

    fun restoreWallet(wif: String) {
        viewModelScope.launch {
            runCatching {
                val info = repository.restoreWallet(wif.trim())
                _wallet.value = info
                _hasWallet.value = true
                startAutoSync()
            }.onFailure {
                _snackbar.value = it.message ?: "Falha ao restaurar carteira"
            }
        }
    }

    fun startAutoSync() {
        repository.startAutoSync()
    }

    fun send(toAddress: String, amount: String, feeTier: FeeTier) {
        viewModelScope.launch {
            _sendState.value = SendState.Loading
            val amountValue = amount.replace(",", ".").toDoubleOrNull()
            if (amountValue == null || amountValue <= 0) {
                _sendState.value = SendState.Error("Valor inválido")
                return@launch
            }
            val result = repository.sendCoins(toAddress, amountValue, feeTier)
            _sendState.value = result.fold(
                onSuccess = { SendState.Success(it) },
                onFailure = { SendState.Error(it.message ?: "Erro ao enviar") }
            )
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

    fun pendingBalance(txs: List<WalletTransactionEntity>): Long {
        return txs.filter { it.blockHeight < 0 && it.amount > 0 }
            .sumOf { it.amount }
    }
}

sealed class SendState {
    data object Idle : SendState()
    data object Loading : SendState()
    data class Success(val txHash: String) : SendState()
    data class Error(val message: String) : SendState()
}
