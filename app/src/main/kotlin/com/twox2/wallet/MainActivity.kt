package com.twox2.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.screens.BalanceScreen
import com.twox2.wallet.ui.screens.DepositScreen
import com.twox2.wallet.ui.screens.TransferScreen
import com.twox2.wallet.ui.screens.WithdrawScreen
import com.twox2.wallet.ui.theme.TwoX2WalletTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TwoX2WalletTheme {
                WalletApp()
            }
        }
    }
}

@Composable
fun WalletApp(viewModel: WalletViewModel = viewModel()) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        TabItem("Saldo", Icons.Default.AccountBalance),
        TabItem("Depósitos", Icons.Default.CallReceived),
        TabItem("Transferências", Icons.Default.Send),
        TabItem("Saques", Icons.Default.Wallet)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> BalanceScreen(viewModel)
                1 -> DepositScreen(viewModel)
                2 -> TransferScreen(viewModel)
                3 -> WithdrawScreen(viewModel)
            }
        }
    }
}

private data class TabItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
