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

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as WalletApplication).repository

    val wallet: WalletInfo = repository.ensureWallet()

    val balance = repository.balance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val transactions = repository.transactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncState: StateFlow<SyncStateEntity?> = repository.syncState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val syncProgress: StateFlow<SyncProgress> = SyncEngine.syncProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncProgress())

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState = _sendState.asStateFlow()

    fun startSync() {
        repository.startBlockchainSync()
    }

    fun send(toAddress: String, amount: String, type: String = "transfer") {
        viewModelScope.launch {
            _sendState.value = SendState.Loading
            val amountValue = amount.replace(",", ".").toDoubleOrNull()
            if (amountValue == null || amountValue <= 0) {
                _sendState.value = SendState.Error("Valor inválido")
                return@launch
            }
            val result = repository.sendCoins(toAddress, amountValue, type)
            _sendState.value = result.fold(
                onSuccess = { SendState.Success(it) },
                onFailure = { SendState.Error(it.message ?: "Erro ao enviar") }
            )
        }
    }

    fun resetSendState() {
        _sendState.value = SendState.Idle
    }

    fun formatBalance(satoshis: Long): String {
        val coins = satoshis.toDouble() / ChainParams.COIN
        return "%.8f %s".format(coins, ChainParams.CURRENCY)
    }
}

sealed class SendState {
    data object Idle : SendState()
    data object Loading : SendState()
    data class Success(val txHash: String) : SendState()
    data class Error(val message: String) : SendState()
}
