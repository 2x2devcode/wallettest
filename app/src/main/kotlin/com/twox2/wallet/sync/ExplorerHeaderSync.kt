package com.twox2.wallet.sync

import android.util.Log
import com.twox2.wallet.data.db.BlockHeaderDao
import com.twox2.wallet.data.db.BlockHeaderEntity
import com.twox2.wallet.network.ExplorerApi
import com.twox2.wallet.network.ExplorerBlock

object ExplorerHeaderSync {
    private const val TAG = "TwoX2ExplorerHeaders"
    private const val MAX_BLOCKS_PER_TICK = 80

    suspend fun syncToHeight(
        blockDao: BlockHeaderDao,
        networkTip: Int
    ): Int {
        val localTip = blockDao.getTip() ?: return 0
        if (localTip.height >= networkTip) return 0

        var parent = localTip
        var inserted = 0
        val targetHeight = minOf(networkTip, localTip.height + MAX_BLOCKS_PER_TICK)

        while (parent.height < targetHeight) {
            val nextHeight = parent.height + 1
            val block = ExplorerApi.getBlockAtHeight(nextHeight) ?: break
            if (!isValidSuccessor(parent, block)) {
                Log.w(TAG, "Bloco $nextHeight não encadeia com ${parent.hash}")
                break
            }

            val entity = toEntity(block, parent.hash)
            blockDao.insert(entity)
            parent = entity
            inserted++
        }

        if (inserted > 0) {
            Log.i(TAG, "Explorer headers: +$inserted blocos (altura ${parent.height})")
        }
        return inserted
    }

    private fun isValidSuccessor(parent: BlockHeaderEntity, block: ExplorerBlock): Boolean {
        if (block.height != parent.height + 1) return false
        if (block.previousBlockHash.isNotBlank() &&
            !block.previousBlockHash.equals(parent.hash, ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    private fun toEntity(block: ExplorerBlock, prevHash: String): BlockHeaderEntity {
        return BlockHeaderEntity(
            height = block.height,
            hash = block.hash,
            prevHash = block.previousBlockHash.ifBlank { prevHash },
            merkleRoot = block.merkleRoot,
            time = block.time,
            bits = parseCompactBits(block.bitsHex),
            nonce = block.nonce,
            version = block.version
        )
    }

    private fun parseCompactBits(bitsHex: String): Long {
        return bitsHex.removePrefix("0x").toLong(16)
    }
}
