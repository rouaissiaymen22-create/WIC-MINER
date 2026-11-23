package com.wicminer.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class Sha256dResult(val hashes: Long, val hashesPerSecond: Double)

/**
 * A bounded local SHA-256d throughput test.
 * It hashes a synthetic 80-byte header and is NOT a Bitcoin mining job.
 */
object Sha256dBenchmark {
    suspend fun run(durationMillis: Long = 5_000L): Sha256dResult = withContext(Dispatchers.Default) {
        require(durationMillis in 1_000L..5_000L) { "Test duration must be between 1 and 5 seconds" }
        val digest = MessageDigest.getInstance("SHA-256")
        val header = ByteArray(80)
        // Fixed test data; the nonce is encoded in Bitcoin's little-endian header format.
        header[0] = 0x20
        val nonce = ByteBuffer.wrap(header, 76, 4).order(ByteOrder.LITTLE_ENDIAN)
        val started = System.nanoTime()
        val deadline = started + durationMillis * 1_000_000L
        var count = 0L
        while (System.nanoTime() < deadline) {
            nonce.putInt(76, count.toInt())
            val first = digest.digest(header)
            digest.digest(first)
            count++
            if ((count and 0x0fffL) == 0L) coroutineContext.ensureActive()
        }
        val elapsed = (System.nanoTime() - started).coerceAtLeast(1L) / 1_000_000_000.0
        Sha256dResult(count, count / elapsed)
    }
}
