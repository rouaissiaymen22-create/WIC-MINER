package com.wicminer.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal Stratum V1 transport and handshake.
 * It deliberately does not build headers, hash jobs, or submit shares.
 */
class ZpoolStratumClient(private val listener: Listener) {
    interface Listener {
        fun onStatus(message: String)
        fun onDifficulty(value: Double)
        fun onSession(extranonce1: String, extranonce2Size: Int)
        fun onJob(job: StratumJob)
        fun onShareResult(accepted: Boolean, detail: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ids = AtomicInteger(1)
    private val pendingShares = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    private var readerJob: Job? = null

    suspend fun connect(
        host: String,
        port: Int,
        wallet: String,
        password: String,
        tls: Boolean = true
    ) = withContext(Dispatchers.IO) {
        close()
        require(wallet.isNotBlank()) { "Enter a BTC payout address first" }
        val connection: Socket = if (tls) {
            (SSLSocketFactory.getDefault().createSocket() as SSLSocket).apply {
                connect(InetSocketAddress(host, port), 12_000)
                soTimeout = 45_000
                sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                startHandshake()
            }
        } else {
            Socket().apply {
                connect(InetSocketAddress(host, port), 12_000)
                soTimeout = 45_000
            }
        }
        socket = connection
        val input = connection.getInputStream().bufferedReader(Charsets.UTF_8)
        val output = connection.getOutputStream().bufferedWriter(Charsets.UTF_8)
        writer = output

        try {
            listener.onStatus("TLS connected · subscribing to Stratum…")
            val subscribeId = ids.getAndIncrement()
            send(output, JSONObject()
                .put("id", subscribeId)
                .put("method", "mining.subscribe")
                .put("params", JSONArray().put("WIC-MINER/0.4.0")))
            val subscribe = awaitResponse(input, subscribeId)
            if (subscribe.opt("error") != null && !subscribe.isNull("error")) {
                error("Subscribe rejected: ${subscribe.opt("error")}")
            }
            val subscribeResult = subscribe.optJSONArray("result")
                ?: error("Invalid subscribe response")
            if (subscribeResult.length() < 3) error("Incomplete subscribe response")
            val extranonce1 = subscribeResult.optString(1)
            val extranonce2Size = subscribeResult.optInt(2, -1)
            if (extranonce1.isBlank() || extranonce2Size !in 1..16) {
                error("Invalid extranonce parameters")
            }
            listener.onSession(extranonce1, extranonce2Size)

            listener.onStatus("Subscribed · authorizing payout address…")
            val authorizeId = ids.getAndIncrement()
            send(output, JSONObject()
                .put("id", authorizeId)
                .put("method", "mining.authorize")
                .put("params", JSONArray().put(wallet).put(password)))
            val authorization = awaitResponse(input, authorizeId)
            if (authorization.opt("result") != true) {
                error("Pool authorization failed: ${authorization.opt("error") ?: "rejected"}")
            }

            listener.onStatus("Authorized · waiting for mining jobs (no hashing yet)")
            readerJob = scope.launch {
                try {
                    while (connection.isConnected && !connection.isClosed) {
                        val line = input.readLine() ?: break
                        handleNotification(line)
                    }
                    listener.onStatus("Stratum connection closed")
                } catch (e: Exception) {
                    if (!connection.isClosed) listener.onStatus("Stratum read error: ${e.message ?: "unknown"}")
                } finally {
                    close()
                }
            }
            true
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    private fun awaitResponse(input: BufferedReader, expectedId: Int): JSONObject {
        while (true) {
            val line = input.readLine() ?: error("Pool closed connection during handshake")
            val message = JSONObject(line)
            if (message.optInt("id", -1) == expectedId) return message
            handleNotification(line)
        }
    }

    private fun handleNotification(line: String) {
        val message = try { JSONObject(line) } catch (_: Exception) { return }
        val method = message.optString("method")
        if (method.isBlank()) {
            val responseId = message.optInt("id", -1)
            if (responseId >= 0 && pendingShares.remove(responseId)) {
                val accepted = message.opt("result") == true
                val detail = if (accepted) "Share accepted" else "Share rejected: ${message.opt("error") ?: "unknown reason"}"
                listener.onShareResult(accepted, detail)
            }
            return
        }
        when (method) {
            "mining.set_difficulty" -> {
                val params = message.optJSONArray("params") ?: return
                val difficulty = params.optDouble(0, Double.NaN)
                if (difficulty.isFinite() && difficulty > 0) listener.onDifficulty(difficulty)
            }
            "mining.notify" -> {
                val params = message.optJSONArray("params") ?: return
                try {
                    listener.onJob(BitcoinStratumWork.parseJob(params))
                } catch (e: Exception) {
                    listener.onStatus("Invalid mining job: ${e.message ?: "parse error"}")
                }
            }
            "mining.set_extranonce" -> {
                val params = message.optJSONArray("params") ?: return
                val nextExtranonce1 = params.optString(0)
                val nextSize = params.optInt(1, -1)
                if (nextExtranonce1.isNotBlank() && nextSize in 1..16) {
                    listener.onSession(nextExtranonce1, nextSize)
                    listener.onStatus("Pool rotated extranonce · waiting for fresh job")
                } else {
                    listener.onStatus("Pool sent invalid extranonce update")
                }
            }
        }
    }

    private fun send(output: BufferedWriter, message: JSONObject) {
        output.write(message.toString())
        output.write("\n")
        output.flush()
    }

    fun submitShare(wallet: String, jobId: String, extranonce2Hex: String, nTime: String, nonceHex: String) {
        val output = writer ?: error("Not connected to pool")
        val id = ids.getAndIncrement()
        val message = JSONObject()
            .put("id", id)
            .put("method", "mining.submit")
            .put("params", JSONArray().put(wallet).put(jobId).put(extranonce2Hex).put(nTime).put(nonceHex))
        pendingShares.add(id)
        synchronized(output) { send(output, message) }
    }

    fun close() {
        pendingShares.clear()
        readerJob?.cancel()
        readerJob = null
        try { writer?.close() } catch (_: Exception) { }
        writer = null
        try { socket?.close() } catch (_: Exception) { }
        socket = null
    }
}
