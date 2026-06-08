package com.twox2.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.screens.HistoryScreen
import com.twox2.wallet.ui.screens.OverviewScreen
import com.twox2.wallet.ui.screens.ReceiveScreen
import com.twox2.wallet.ui.screens.SendScreen
import com.twox2.wallet.ui.screens.SettingsScreen
import com.twox2.wallet.ui.screens.WelcomeScreen
import com.twox2.wallet.ui.theme.GreenAccent
import com.twox2.wallet.ui.theme.SurfaceDark
import com.twox2.wallet.ui.theme.TealPrimary
import com.twox2.wallet.ui.theme.TextMuted
import com.twox2.wallet.ui.theme.TwoX2WalletTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: WalletViewModel = viewModel()
            val darkTheme by viewModel.darkTheme.collectAsState()
            TwoX2WalletTheme(darkTheme = darkTheme) {
                WalletApp(viewModel)
            }
        }
    }
}

@Composable
fun WalletApp(viewModel: WalletViewModel) {
    val hasWallet by viewModel.hasWallet.collectAsState()
    val snackbarMessage by viewModel.snackbar.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    if (!hasWallet) {
        WelcomeScreen(viewModel)
        return
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSend by rememberSaveable { mutableStateOf(false) }

    val tabs = listOf(
        TabItem("Dashboard", Icons.Default.AccountBalanceWallet),
        TabItem("Transactions", Icons.Default.PieChart),
        TabItem("Receive", Icons.Default.QrCode),
        TabItem("Settings", Icons.Default.Settings)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = com.twox2.wallet.ui.theme.BackgroundDark,
        bottomBar = {
            if (!showSend) {
                NavigationBar(containerColor = SurfaceDark) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GreenAccent,
                                selectedTextColor = GreenAccent,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = TealPrimary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                showSend -> SendScreen(
                    viewModel = viewModel,
                    onBack = {
                        showSend = false
                        viewModel.resetSendState()
                    }
                )
                selectedTab == 0 -> OverviewScreen(
                    viewModel = viewModel,
                    onNavigateToSend = { showSend = true },
                    onNavigateToReceive = { selectedTab = 2 },
                    onNavigateToHistory = { selectedTab = 1 }
                )
                selectedTab == 1 -> HistoryScreen(viewModel)
                selectedTab == 2 -> ReceiveScreen(viewModel)
                selectedTab == 3 -> SettingsScreen(viewModel)
            }
        }
    }
}

private data class TabItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
