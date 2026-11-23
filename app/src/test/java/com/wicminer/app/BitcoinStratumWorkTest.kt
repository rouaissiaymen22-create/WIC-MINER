package com.wicminer.app

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.security.MessageDigest

class BitcoinStratumWorkTest {
    @Test
    fun doubleSha256MatchesKnownEmptyVector() {
        val actual = BitcoinStratumWork.hashRate(byteArrayOf()).toHex()
        assertEquals("5df6e0e2761359d30a8275058e299fcc0381534545f55cf43e41983f5d4c9456", actual)
    }

    @Test
    fun difficultyOneAcceptsExactDiffOneTargetAndRejectsNextValue() {
        val target = BigInteger("00000000FFFF0000000000000000000000000000000000000000000000000000", 16)
        val exact = target.toString(16).padStart(64, '0').hexBytes().reversedArray()
        val above = target.add(BigInteger.ONE).toString(16).padStart(64, '0').hexBytes().reversedArray()

        assertTrue(BitcoinStratumWork.hashMeetsDifficultyHash(exact, 1.0))
        assertFalse(BitcoinStratumWork.hashMeetsDifficultyHash(above, 1.0))
    }

    @Test
    fun headerHasExpectedLengthAndSerializedFieldByteOrders() {
        val job = StratumJob(
            jobId = "job-1",
            previousHash = (0 until 64).joinToString("") { "%02x".format(it) },
            coinbasePart1 = "0102",
            coinbasePart2 = "0304",
            merkleBranches = emptyList(),
            version = "01000000",
            nBits = "1d00ffff",
            nTime = "11223344",
            cleanJobs = true
        )
        val header = BitcoinStratumWork.buildHeader(job, "aabb", byteArrayOf(0xcc.toByte(), 0xdd.toByte()), 0xA1B2C3D4L)
        assertEquals(80, header.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1), header.copyOfRange(0, 4))
        assertArrayEquals(byteArrayOf(3, 2, 1, 0), header.copyOfRange(4, 8))
        val coinbase = "0102aabbccdd0304".hexBytes()
        val expectedMerkle = doubleSha256(coinbase)
        assertArrayEquals(expectedMerkle, header.copyOfRange(36, 68))
        assertArrayEquals(byteArrayOf(0x44, 0x33, 0x22, 0x11), header.copyOfRange(68, 72))
        assertArrayEquals(byteArrayOf(0xff.toByte(), 0xff.toByte(), 0, 0x1d), header.copyOfRange(72, 76))
        assertArrayEquals(byteArrayOf(0xd4.toByte(), 0xc3.toByte(), 0xb2.toByte(), 0xa1.toByte()), header.copyOfRange(76, 80))
    }

    @Test
    fun extranonceIncrementsAsBigEndianCounterAndDetectsOverflow() {
        assertArrayEquals(byteArrayOf(0x12, 0x35), BitcoinStratumWork.nextExtranonce2(byteArrayOf(0x12, 0x34)))
        assertArrayEquals(byteArrayOf(1, 0), BitcoinStratumWork.nextExtranonce2(byteArrayOf(0, 0xff.toByte())))
        assertNull(BitcoinStratumWork.nextExtranonce2(byteArrayOf(0xff.toByte(), 0xff.toByte())))
    }

    @Test
    fun parsesValidStratumNotifyAndRejectsMalformedHeaderFields() {
        val params = JSONArray().put("job").put("00".repeat(32)).put("0102").put("0304")
            .put(JSONArray()).put("01000000").put("1d00ffff").put("11223344").put(true)
        val job = BitcoinStratumWork.parseJob(params)
        assertEquals("job", job.jobId)
        assertTrue(job.cleanJobs)
        assertTrue(job.merkleBranches.isEmpty())

        val malformed = JSONArray().put("job").put("not-a-hash").put("01").put("02")
            .put(JSONArray()).put("01000000").put("1d00ffff").put("11223344").put(false)
        try {
            BitcoinStratumWork.parseJob(malformed)
            fail("Malformed previous hash must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }

    private fun doubleSha256(input: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(digest.digest(input))
    }

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
