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
        "seed5.quimeralabs.org"
    )

    val FIXED_PEERS = listOf(
        // IPv4
        "144.91.107.244",
        "144.91.108.135",
        "145.128.186.211",
        "149.102.139.53",
        "156.67.27.88",
        "161.97.176.125",
        "173.63.31.178",
        "179.189.35.51",
        "179.218.97.47",
        "185.159.75.36",
        "185.220.236.2",
        "186.205.64.140",
        "188.253.115.13",
        "188.253.115.15",
        "191.254.16.149",
        "203.17.244.244",
        "37.212.35.160",
        "38.242.236.173",
        "43.159.43.193",
        "75.119.137.26",
        "77.237.232.84",
        "78.26.151.215",
        "81.0.248.213",
        "82.165.184.234",
        "85.209.89.98",
        "86.82.39.57",
        "91.239.42.121",
        // IPv6
        "2804:14d:5c44:82e9:218e:791:793f:b1df",
        "2804:14d:5c9b:8495:2505:502c:d706:620d",
        "2804:14d:5c9b:8495:450c:917d:92bf:fd5f",
        "2804:14d:5c9b:8495:60e6:52af:d64:6dd0",
        "2804:14d:5c9b:8495:c1e:4947:ffe3:527e",
        "2804:7f6:a408:57ef:890c:8a2b:f5ae:f9f5",
        "2804:f04:9002:3b00:24f1:d0f1:8242:5f63",
        "2804:f04:9002:3b00:b199:2c3f:1a8f:1695",
        "2a02:a45e:ae86:0:94ad:7046:ef66:dce",
        "2a02:c204:2324:9987::1",
        "2a02:c207:2304:1129::1",
        "2a02:c207:2311:2716::1",
        "2a02:c207:2323:884::1",
        "2a09:bac1:61c0:60::387:3b"
    )

    const val COINBASE_MATURITY = 24
    const val TARGET_SPACING_SECONDS = 120
}
