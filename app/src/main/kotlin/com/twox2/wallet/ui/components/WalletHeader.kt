package com.twox2.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twox2.wallet.R
import com.twox2.wallet.ui.theme.GreenAccent
import com.twox2.wallet.ui.theme.TextMuted

@Composable
fun WalletHeader(
    title: String = "2X2Coin Wallet",
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    isConnected: Boolean = false,
    showConnectionStatus: Boolean = true,
    showMenu: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack && onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Image(
                    painter = painterResource(R.drawable.logo_2x2),
                    contentDescription = "2X2 Logo",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = if (showBack) 0.dp else 12.dp)
            )
        }
        if (showConnectionStatus || showMenu) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showConnectionStatus) {
                    if (isConnected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(GreenAccent)
                        )
                        Text(
                            "Connected",
                            color = GreenAccent,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 6.dp, end = 8.dp)
                        )
                    } else {
                        Text("Offline", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (showMenu) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = TextMuted)
                }
            }
        }
    }
}
