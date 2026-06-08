package com.twox2.wallet.chain

object ChainParams {
    const val PROTOCOL_VERSION = 70026
    const val MIN_PEER_PROTO_VERSION = 60016
    const val USER_AGENT = "/2x2Wallet:1.0.0/"

    const val P2P_PORT = 15190
    const val RPC_PORT = 15189
    const val COIN = 100_000_000L
    const val CURRENCY = "2X2"

    val MESSAGE_START = byteArrayOf(0x32, 0x78, 0x32, 0x43) // "2x2C"

    const val GENESIS_HASH =
        "00000eea834a06692bc4f56d6f0061631c72fd75431ce9e5d7f3b9d712dc3a9b"

    const val ADDRESS_VERSION: Byte = 3
    const val WIF_VERSION: Byte = 0x80.toByte()
    const val CASHADDR_PREFIX = "2x2coin"

    val DNS_SEEDS = listOf(
        "seed.quimeralabs.org",
        "seed1.quimeralabs.org",
        "seed2.quimeralabs.org",
        "seed3.quimeralabs.org",
        "seed4.quimeralabs.org",
        "seed5.quimeralabs.org",
        "161.97.176.125",
        "77.237.232.84",
        "75.119.137.26",
        "149.102.139.53",
        "144.91.107.244",
        "38.242.236.173"
    )

    const val COINBASE_MATURITY = 24
    const val TARGET_SPACING_SECONDS = 120
}
