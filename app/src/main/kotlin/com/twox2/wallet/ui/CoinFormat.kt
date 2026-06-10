package com.twox2.wallet.ui

import com.twox2.wallet.chain.ChainParams
import kotlin.math.pow

object CoinFormat {
    private const val DISPLAY_DECIMALS = 4

    /**
     * Trunca (não arredonda) satoshis para até [decimalPlaces] casas decimais.
     */
    fun truncateSatoshisToCoins(satoshis: Long, decimalPlaces: Int = DISPLAY_DECIMALS): String {
        if (decimalPlaces <= 0) {
            return (satoshis / ChainParams.COIN).toString()
        }
        val factor = 10.0.pow(decimalPlaces).toLong()
        require(ChainParams.COIN % factor == 0L) { "COIN incompatível com casas decimais" }
        val divisor = ChainParams.COIN / factor
        val whole = satoshis / ChainParams.COIN
        val remainder = satoshis % ChainParams.COIN
        val frac = remainder / divisor
        val fracStr = frac.toString().padStart(decimalPlaces, '0').trimEnd('0')
        return if (fracStr.isEmpty()) whole.toString() else "$whole.$fracStr"
    }

    fun formatWithCurrency(satoshis: Long, decimalPlaces: Int = DISPLAY_DECIMALS): String {
        return "${truncateSatoshisToCoins(satoshis, decimalPlaces)} ${ChainParams.CURRENCY}"
    }

    fun formatAmount(satoshis: Long, decimalPlaces: Int = DISPLAY_DECIMALS): String {
        return truncateSatoshisToCoins(satoshis, decimalPlaces)
    }

    fun coinsToSatoshisTruncated(coins: Double): Long {
        val factor = 10.0.pow(DISPLAY_DECIMALS).toLong()
        val scaled = (coins * factor).toLong()
        return scaled * (ChainParams.COIN / factor)
    }
}
