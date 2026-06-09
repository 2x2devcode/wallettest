package com.twox2.wallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twox2.wallet.crypto.receiveDisplayAddress
import com.twox2.wallet.data.db.SavedAddressEntity
import com.twox2.wallet.ui.theme.GreenAccent
import com.twox2.wallet.ui.theme.SurfaceDark
import com.twox2.wallet.ui.theme.TealPrimary
import com.twox2.wallet.ui.theme.TextMuted

@Composable
fun AddressBookSection(
    title: String,
    addresses: List<SavedAddressEntity>,
    selectedId: Long?,
    onSelect: (SavedAddressEntity) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    showRadio: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (addresses.isEmpty()) {
            Text("Nenhum endereço salvo", color = TextMuted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp))
        } else {
            addresses.forEach { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable { onSelect(entry) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (entry.id == selectedId) TealPrimary.copy(alpha = 0.15f) else SurfaceDark
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showRadio) {
                            RadioButton(
                                selected = entry.id == selectedId,
                                onClick = { onSelect(entry) },
                                colors = RadioButtonDefaults.colors(selectedColor = TealPrimary)
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = if (showRadio) 0.dp else 4.dp)) {
                            Text(entry.name, fontWeight = FontWeight.SemiBold, color = GreenAccent)
                            Text(
                                shortenAddress(entry.receiveDisplayAddress()),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        if (onDelete != null && !entry.isDefault) {
                            IconButton(onClick = { onDelete(entry.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remover", tint = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shortenAddress(value: String): String {
    if (value.length <= 20) return value
    return value.take(10) + "..." + value.takeLast(10)
}
