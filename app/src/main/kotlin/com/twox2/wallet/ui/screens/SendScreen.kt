package com.twox2.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twox2.wallet.ui.FeeTier
import com.twox2.wallet.ui.SendState
import com.twox2.wallet.ui.WalletViewModel
import com.twox2.wallet.ui.components.GradientButton
import com.twox2.wallet.ui.components.WalletHeader
import com.twox2.wallet.ui.theme.SurfaceDark
import com.twox2.wallet.ui.theme.TealPrimary
import com.twox2.wallet.ui.theme.TextMuted

@Composable
fun SendScreen(viewModel: WalletViewModel, onBack: () -> Unit = {}) {
    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedFee by remember { mutableStateOf(FeeTier.STANDARD) }
    val sendState by viewModel.sendState.collectAsState()
    val balance by viewModel.balance.collectAsState()

    val amountValue = amount.replace(",", ".").toDoubleOrNull() ?: 0.0
    val feeCoins = selectedFee.displayFeeCoins
    val total = amountValue + feeCoins

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        WalletHeader(
            title = "Send 2X2Coin",
            showBack = true,
            onBack = onBack,
            showMenu = false
        )

        StyledInputField(
            label = "Recipient Address",
            value = address,
            onValueChange = { address = it },
            placeholder = "2X2abc...xyz",
            trailing = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TealPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        StyledInputField(
            label = "Amount",
            value = amount,
            onValueChange = { amount = it },
            placeholder = "0.00",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("2X2", color = TextMuted, modifier = Modifier.padding(end = 8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, TealPrimary, RoundedCornerShape(8.dp))
                            .clickable {
                                amount = viewModel.formatBalanceShort(balance).replace(" 2X2", "")
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("MAX", color = TealPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            },
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Network Fee",
            color = TextMuted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, TealPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            Column {
                FeeTier.entries.forEachIndexed { index, tier ->
                    FeeOptionRow(
                        tier = tier,
                        selected = selectedFee == tier,
                        onSelect = { selectedFee = tier }
                    )
                    if (index < FeeTier.entries.lastIndex) {
                        HorizontalDivider(color = TealPrimary.copy(alpha = 0.2f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TransactionSummaryCard(
            amount = if (amount.isBlank()) "—" else "${amountValue} 2X2",
            fee = "${feeCoins} 2X2",
            total = if (amount.isBlank()) "—" else "${"%.3f".format(total)} 2X2"
        )

        Spacer(modifier = Modifier.height(24.dp))

        GradientButton(
            text = if (sendState is SendState.Loading) "Sending..." else "Send Transaction",
            icon = Icons.AutoMirrored.Filled.Send,
            gradient = Brush.horizontalGradient(listOf(TealPrimary, Color(0xFF22C55E))),
            onClick = { viewModel.send(address, amount, selectedFee) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        when (val state = sendState) {
            is SendState.Success -> {
                Text(
                    "Sent! TX: ${state.txHash}",
                    color = TealPrimary,
                    modifier = Modifier.padding(16.dp)
                )
            }
            is SendState.Error -> {
                Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> Unit
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StyledInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    trailing: @Composable () -> Unit,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, TealPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = textStyle.copy(color = Color.White),
                cursorBrush = SolidColor(TealPrimary),
                singleLine = true,
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = TextMuted, style = textStyle)
                    }
                    inner()
                }
            )
            trailing()
        }
    }
}

@Composable
private fun FeeOptionRow(tier: FeeTier, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = TealPrimary, unselectedColor = TextMuted)
        )
        Text(
            "${tier.label}: ${tier.displayFeeCoins} 2X2",
            color = Color.White,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun TransactionSummaryCard(amount: String, fee: String, total: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, TealPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                Text(
                    "Transaction Summary",
                    color = TealPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            SummaryRow("Amount:", amount)
            SummaryRow("Fee:", fee)
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = TealPrimary.copy(alpha = 0.2f)
            )
            SummaryRow("Total:", total, bold = true)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted)
        Text(
            value,
            color = Color.White,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
