package com.wicminer.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.coroutineContext

data class MiningAttemptResult(
    val hashes: Long,
    val hashesPerSecond: Double,
    val extranonce2Hex: String?,
    val nonceHex: String?
) {
    val foundShare: Boolean get() = extranonce2Hex != null && nonceHex != null
}

/**
 * A single-thread, foreground-only SHA-256d share search.
 * The caller must keep it explicitly user-started and cancellable.
 */
object Sha256dShareMiner {
    suspend fun run(
        job: StratumJob,
        extranonce1: String,
        extranonce2Size: Int,
        difficulty: Double,
        durationMillis: Long = 15_000L
    ): MiningAttemptResult = withContext(Dispatchers.Default) {
        require(extranonce2Size in 1..16)
        require(difficulty > 0.0 && difficulty.isFinite())
        require(durationMillis in 1_000L..30_000L)

        val digest = MessageDigest.getInstance("SHA-256")
        var extranonce2 = ByteArray(extranonce2Size)
        var header = BitcoinStratumWork.buildHeaderTemplate(job, extranonce1, extranonce2)
        val nonceBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val started = System.nanoTime()
        val deadline = started + durationMillis * 1_000_000L
        var hashes = 0L
        var nonce = 0L

        while (System.nanoTime() < deadline) {
            nonceBuffer.putInt(76, nonce.toInt())
            val first = digest.digest(header)
            val hash = digest.digest(first)
            hashes++

            if (BitcoinStratumWork.hashMeetsDifficultyHash(hash, difficulty)) {
                val elapsed = (System.nanoTime() - started).coerceAtLeast(1L) / 1_000_000_000.0
                return@withContext MiningAttemptResult(
                    hashes,
                    hashes / elapsed,
                    extranonce2.joinToString("") { "%02x".format(it) },
                    String.format(Locale.US, "%08x", nonce)
                )
            }

            nonce++
            if (nonce > 0xffffffffL) {
                nonce = 0L
                extranonce2 = BitcoinStratumWork.nextExtranonce2(extranonce2)
                    ?: break
                header = BitcoinStratumWork.buildHeaderTemplate(job, extranonce1, extranonce2)
            }
            if ((hashes and 0x07ffL) == 0L) coroutineContext.ensureActive()
        }

        val elapsed = (System.nanoTime() - started).coerceAtLeast(1L) / 1_000_000_000.0
        MiningAttemptResult(hashes, hashes / elapsed, null, null)
    }
}
