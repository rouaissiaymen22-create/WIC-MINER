package com.wicminer.app

import org.json.JSONArray
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

data class StratumJob(
    val jobId: String,
    val previousHash: String,
    val coinbasePart1: String,
    val coinbasePart2: String,
    val merkleBranches: List<String>,
    val version: String,
    val nBits: String,
    val nTime: String,
    val cleanJobs: Boolean
)

data class MiningWork(
    val job: StratumJob,
    val extranonce2: ByteArray,
    val header: ByteArray
)

object BitcoinStratumWork {
    private val diff1Target = BigInteger("00000000FFFF0000000000000000000000000000000000000000000000000000", 16)

    fun parseJob(params: JSONArray): StratumJob {
        require(params.length() >= 9) { "Stratum job has fewer than 9 fields" }
        val branchesJson = params.optJSONArray(4) ?: JSONArray()
        val branches = (0 until branchesJson.length()).map { branchesJson.getString(it) }
        val job = StratumJob(
            jobId = params.getString(0),
            previousHash = params.getString(1),
            coinbasePart1 = params.getString(2),
            coinbasePart2 = params.getString(3),
            merkleBranches = branches,
            version = params.getString(5),
            nBits = params.getString(6),
            nTime = params.getString(7),
            cleanJobs = params.optBoolean(8, false)
        )
        requireHex(job.previousHash, 64, "previous hash")
        requireHex(job.version, 8, "version")
        requireHex(job.nBits, 8, "nBits")
        requireHex(job.nTime, 8, "nTime")
        requireHex(job.coinbasePart1, null, "coinbase prefix")
        requireHex(job.coinbasePart2, null, "coinbase suffix")
        job.merkleBranches.forEach { requireHex(it, 64, "merkle branch") }
        return job
    }

    fun buildHeader(job: StratumJob, extranonce1: String, extranonce2: ByteArray, nonce: Long): ByteArray {
        require(nonce in 0..0xffffffffL)
        val coinbase = hex(job.coinbasePart1) + hex(extranonce1) + extranonce2 + hex(job.coinbasePart2)
        var merkle = doubleSha256(coinbase)
        for (branch in job.merkleBranches) {
            merkle = doubleSha256(merkle + hex(branch))
        }

        val header = ByteArray(80)
        putLittleEndianHex(header, 0, job.version)
        // Stratum encodes previous-hash words in the opposite byte order from the header.
        putBytes(header, 4, swapWords(hex(job.previousHash)))
        // The merkle root is serialized in the byte order used by the Bitcoin header.
        putBytes(header, 36, merkle.reversedArray())
        putLittleEndianHex(header, 68, job.nTime)
        putLittleEndianHex(header, 72, job.nBits)
        ByteBuffer.wrap(header, 76, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(76, nonce.toInt())
        return header
    }

    fun buildHeaderTemplate(job: StratumJob, extranonce1: String, extranonce2: ByteArray): ByteArray =
        buildHeader(job, extranonce1, extranonce2, 0L)

    fun hashMeetsDifficulty(header: ByteArray, difficulty: Double): Boolean =
        hashMeetsDifficultyHash(doubleSha256(header), difficulty)

    fun hashMeetsDifficultyHash(hash: ByteArray, difficulty: Double): Boolean {
        require(hash.size == 32 && difficulty > 0.0 && difficulty.isFinite())
        val value = BigInteger(1, hash.reversedArray())
        val target = diff1Target.toBigDecimal()
            .divide(java.math.BigDecimal.valueOf(difficulty), 0, java.math.RoundingMode.DOWN)
            .toBigInteger()
        return value <= target
    }

    fun hashRate(header: ByteArray): ByteArray = doubleSha256(header)

    fun nextExtranonce2(current: ByteArray): ByteArray? {
        val next = current.copyOf()
        for (i in next.lastIndex downTo 0) {
            val value = (next[i].toInt() and 0xff) + 1
            next[i] = value.toByte()
            if (value <= 0xff) return next
        }
        return null
    }

    fun nonceHex(nonce: Long): String = "%08x".format(nonce and 0xffffffffL)

    private fun putLittleEndianHex(target: ByteArray, offset: Int, value: String) {
        val bytes = hex(value).reversedArray()
        putBytes(target, offset, bytes)
    }

    private fun putBytes(target: ByteArray, offset: Int, bytes: ByteArray) {
        require(offset >= 0 && offset + bytes.size <= target.size)
        bytes.copyInto(target, offset)
    }

    private fun swapWords(bytes: ByteArray): ByteArray {
        require(bytes.size % 4 == 0)
        val out = ByteArray(bytes.size)
        for (i in bytes.indices step 4) {
            out[i] = bytes[i + 3]
            out[i + 1] = bytes[i + 2]
            out[i + 2] = bytes[i + 1]
            out[i + 3] = bytes[i]
        }
        return out
    }

    private fun doubleSha256(bytes: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(digest.digest(bytes))
    }

    private fun requireHex(value: String, exactLength: Int?, label: String) {
        require(value.length % 2 == 0 && value.matches(Regex("[0-9a-fA-F]*"))) { "Invalid $label hex" }
        if (exactLength != null) require(value.length == exactLength) { "Invalid $label length" }
    }

    private fun hex(value: String): ByteArray {
        requireHex(value, null, "hex value")
        return ByteArray(value.length / 2) { i -> value.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
