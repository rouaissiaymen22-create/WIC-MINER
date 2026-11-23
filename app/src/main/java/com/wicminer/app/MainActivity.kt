package com.wicminer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Ink = Color(0xFF090B10)
private val Panel = Color(0xFF141821)
private val Gold = Color(0xFFF0B90B)
private val Muted = Color(0xFF8C95A5)
private val White = Color(0xFFF5F6F8)

data class PoolStat(val name: String, val blocks: String, val rank: String)
data class PublicMiningData(
    val btcUsd: String = "Unavailable",
    val blockHeight: String = "Unavailable",
    val fastestFee: String = "Unavailable",
    val pools: List<PoolStat> = emptyList(),
    val updated: String = "Not updated"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WicMinerApp() }
    }
}

private suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10000
        readTimeout = 10000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "WIC-MINER-Android/0.2")
    }
    try {
        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private suspend fun loadPublicMiningData(): PublicMiningData = withContext(Dispatchers.IO) {
    var price = "Unavailable"
    var height = "Unavailable"
    var fee = "Unavailable"
    var pools = emptyList<PoolStat>()

    try {
        val prices = JSONObject(getText("https://mempool.space/api/v1/prices"))
        val usd = prices.optDouble("USD", Double.NaN)
        if (!usd.isNaN()) price = "$" + "%,.0f".format(usd)
    } catch (_: Exception) { }

    try {
        height = getText("https://mempool.space/api/blocks/tip/height").trim()
    } catch (_: Exception) { }

    try {
        val fees = JSONObject(getText("https://mempool.space/api/v1/fees/recommended"))
        val fastest = fees.optInt("fastestFee", -1)
        if (fastest >= 0) fee = "$fastest sat/vB"
    } catch (_: Exception) { }

    try {
        val response = getText("https://mempool.space/api/v1/mining/pools/24h")
        val array = when {
            response.trimStart().startsWith("[") -> JSONArray(response)
            else -> {
                val obj = JSONObject(response)
                obj.optJSONArray("pools") ?: obj.optJSONArray("data") ?: JSONArray()
            }
        }
        val result = mutableListOf<PoolStat>()
        for (i in 0 until minOf(array.length(), 8)) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name", item.optString("slug", "Unknown pool"))
            val blocks = item.optString("blockCount", item.optString("blocks", "—"))
            val rank = item.optString("rank", (i + 1).toString())
            result.add(PoolStat(name, blocks, rank))
        }
        pools = result
    } catch (_: Exception) { }

    PublicMiningData(
        btcUsd = price,
        blockHeight = height,
        fastestFee = fee,
        pools = pools,
        updated = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
    )
}

@Composable
fun WicMinerApp() {
    var tab by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf(PublicMiningData()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        loadError = false
        val result = loadPublicMiningData()
        data = result
        loadError = result.btcUsd == "Unavailable" && result.blockHeight == "Unavailable"
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Ink, surface = Panel, onPrimary = Ink, onBackground = White, onSurface = White)) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    val labels = listOf("Home", "Miner", "Pools", "Safety", "Settings")
                    val icons = listOf(Icons.Default.Dashboard, Icons.Default.Cloud, Icons.Default.Groups, Icons.Default.Shield, Icons.Default.Settings)
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icons[i], contentDescription = label) },
                            label = { Text(label, fontSize = 9.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold, selectedTextColor = Gold, indicatorColor = Color(0xFF34301D), unselectedIconColor = Muted, unselectedTextColor = Muted)
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("WIC", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                            Text("MINER", color = White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        }
                        Surface(color = Color(0xFF282516), shape = RoundedCornerShape(50)) {
                            Text("LIVE DATA", color = Gold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                when (tab) {
                    0 -> {
                        item { HeroCard() }
                        item { SectionTitle("BITCOIN NETWORK · PUBLIC DATA") }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard("BTC price", data.btcUsd, "USD · public market reference", Modifier.weight(1f))
                            MetricCard("Block height", data.blockHeight, "Latest confirmed block", Modifier.weight(1f))
                        } }
                        item { MetricCard("Fastest fee", data.fastestFee, "Recommended network fee", Modifier.fillMaxWidth()) }
                        item { RefreshRow(loading, data.updated, loadError) { scope.launch { refresh() } } }
                        item { Notice("These are public Bitcoin network indicators, not your personal mining hashrate, balance, or earnings.") }
                    }
                    1 -> {
                        item { ZpoolSettings() }
                    }
                    2 -> {
                        item { SectionTitle("MINING POOL OBSERVER") }
                        item { Text("Public pool rankings · trailing 24 hours", color = Muted, fontSize = 12.sp) }
                        if (data.pools.isEmpty()) {
                            item { Notice("Pool rankings are unavailable right now. Refresh when a network connection is available.") }
                        } else {
                            items(data.pools) { pool ->
                                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(color = Color(0xFF282516), shape = RoundedCornerShape(10.dp)) {
                                            Text("#${pool.rank}", color = Gold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(pool.name, color = White, fontWeight = FontWeight.SemiBold)
                                            Text("Blocks found in 24h: ${pool.blocks}", color = Muted, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        item { RefreshRow(loading, data.updated, loadError) { scope.launch { refresh() } } }
                        item { Notice("Pool rankings describe public network observations. They do not mean you are connected to a pool or earning BTC.") }
                    }
                    3 -> {
                        item { SectionTitle("DEVICE PROTECTION") }
                        item { SafetyRow("Local mining", "Manual foreground SHA-256d sessions · up to 10 minutes", Icons.Default.Memory) }
                        item { SafetyRow("Thermal protection", "Stops at battery temperature ≥30°C (when reported by Android)", Icons.Default.Thermostat) }
                        item { SafetyRow("Battery protection", "Stops at battery level ≤15%", Icons.Default.BatteryFull) }
                        item { SafetyRow("Background activity", "Disabled in this version", Icons.Default.Bedtime) }
                        item { SafetyRow("Emergency stop", "Stop mining button is available during every session", Icons.Default.StopCircle) }
                    }
                    else -> {
                        item { SectionTitle("APP SETTINGS · SECURITY") }
                        item { Notice("Pool: Zpool SHA-256 over TLS. The configured c=BTC option selects BTC payout; it does not guarantee direct Bitcoin-block mining because Zpool may route SHA-256 work across compatible markets.") }
                        item { SafetyRow("Application", "WIC MINER · v0.3.4", Icons.Default.Info) }
                        item { SafetyRow("Mining mode", "Manual foreground attempts only · no background service", Icons.Default.Memory) }
                        item { SafetyRow("Network", "TLS certificate and hostname verification enabled", Icons.Default.Lock) }
                        item { SafetyRow("Data source", "mempool.space public API", Icons.Default.Public) }
                        item { SafetyRow("Security", "Public payout address only · never enter seed phrases or private keys", Icons.Default.Lock) }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
fun RefreshRow(loading: Boolean, updated: String, error: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (error) "Network data unavailable" else "Updated: $updated", color = Muted, fontSize = 11.sp)
        }
        TextButton(onClick = onRefresh, enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.size(15.dp), color = Gold, strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, contentDescription = null, tint = Gold)
            Spacer(Modifier.width(6.dp))
            Text("Refresh", color = Gold)
        }
    }
}

@Composable
fun HeroCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1A13)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("BITCOIN MINING CONTROL", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Your mining, your control.", color = White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp)
            Text("Connect securely to the configured SHA-256 pool, receive jobs, and run short user-started share searches.", color = Muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Gold, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("POOL CONNECTION · MANUAL FOREGROUND MINING", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 5.dp))
}

@Composable
fun MetricCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Text(value, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
fun Notice(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("IMPORTANT", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(message, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun SafetyRow(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(13.dp))
            Column {
                Text(title, color = White, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Muted, fontSize = 12.sp)
            }
        }
    }
}


@Composable
fun ZpoolSettings() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wic_miner_settings", android.content.Context.MODE_PRIVATE) }
    var wallet by remember { mutableStateOf(prefs.getString("payout_wallet", "") ?: "") }
    var saved by remember { mutableStateOf(wallet.isNotBlank()) }
    var message by remember { mutableStateOf(if (saved) "Wallet saved on this device" else "Enter your public BTC payout address") }
    var stratumStatus by remember { mutableStateOf("Not connected") }
    var difficulty by remember { mutableStateOf("—") }
    var lastJob by remember { mutableStateOf("—") }
    var extranonceInfo by remember { mutableStateOf("—") }
    var extranonce1 by remember { mutableStateOf("") }
    var extranonce2Size by remember { mutableIntStateOf(0) }
    var currentJob by remember { mutableStateOf<StratumJob?>(null) }
    var currentDifficulty by remember { mutableDoubleStateOf(0.0) }
    var mining by remember { mutableStateOf(false) }
    var miningJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var thermalMonitorJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var sessionDurationMillis by remember { mutableLongStateOf(600_000L) }
    var stopReason by remember { mutableStateOf("") }
    var hashesDone by remember { mutableLongStateOf(0L) }
    var shareCount by remember { mutableIntStateOf(0) }
    var acceptedShares by remember { mutableIntStateOf(0) }
    var rejectedShares by remember { mutableIntStateOf(0) }
    val eventLogs = remember { mutableStateListOf<String>() }
    fun addEvent(message: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        eventLogs.add(0, "$stamp  $message")
        while (eventLogs.size > 40) eventLogs.removeAt(eventLogs.lastIndex)
    }
    var connecting by remember { mutableStateOf(false) }
    var authorized by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val valid = wallet.matches(Regex("^(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,60}$"))
    val client = remember {
        ZpoolStratumClient(object : ZpoolStratumClient.Listener {
            override fun onStatus(message: String) {
                scope.launch(Dispatchers.Main) {
                    stratumStatus = message
                    addEvent("POOL · $message")
                    if (message.startsWith("Authorized")) authorized = true
                    if (message.contains("closed") || message.contains("error")) authorized = false
                }
            }
            override fun onDifficulty(value: Double) {
                if (currentDifficulty > 0.0 && value != currentDifficulty && mining) {
                    miningJob?.cancel()
                    mining = false
                    stratumStatus = "Pool difficulty changed · restart the mining attempt"
                }
                currentDifficulty = value
                difficulty = value.toString()
            }
            override fun onSession(extranonce1Value: String, extranonce2SizeValue: Int) {
                val rotated = extranonce1.isNotBlank() &&
                    (extranonce1 != extranonce1Value || extranonce2Size != extranonce2SizeValue)
                if (rotated) {
                    miningJob?.cancel()
                    mining = false
                    currentJob = null
                    lastJob = "Waiting for fresh job after extranonce rotation"
                }
                extranonce1 = extranonce1Value
                extranonce2Size = extranonce2SizeValue
                extranonceInfo = "$extranonce1Value · $extranonce2SizeValue bytes"
            }
            override fun onJob(job: StratumJob) {
                if (job.cleanJobs) {
                    miningJob?.cancel()
                    mining = false
                }
                currentJob = job
                lastJob = job.jobId + if (job.cleanJobs) " · clean" else ""
            }
            override fun onShareResult(accepted: Boolean, detail: String) {
                scope.launch(Dispatchers.Main) {
                    stratumStatus = detail
                    if (accepted) acceptedShares++ else rejectedShares++
                    addEvent("${if (accepted) "SHARE ACCEPTED" else "SHARE REJECTED"} · $detail")
                }
            }
        })
    }
    DisposableEffect(client) { onDispose { client.close() } }

    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Zpool SHA-256", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Zpool SHA-256 · BTC payout option (c=BTC)", color = Muted, fontSize = 12.sp)
            Text("Server", color = Muted, fontSize = 11.sp)
            Text("sha256.eu.mine.zpool.ca:3333", color = White, fontWeight = FontWeight.SemiBold)
            Text("TLS endpoint: sha256.eu.mine.zpool.ca:53333", color = Muted, fontSize = 11.sp)
            OutlinedTextField(
                value = wallet,
                onValueChange = { wallet = it.trim(); saved = false; message = "Unsaved changes" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("BTC payout address") },
                singleLine = true,
                isError = wallet.isNotBlank() && !valid,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    focusedLabelColor = Gold,
                    cursorColor = Gold
                )
            )
            Text("Password / payout option: c=BTC", color = Muted, fontSize = 12.sp)
            Button(
                onClick = {
                    prefs.edit().putString("payout_wallet", wallet).apply()
                    saved = true
                    message = "Wallet saved locally on this device"
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save payout address")
            }
            Text(message, color = if (saved) Gold else Muted, fontSize = 12.sp)
            Divider(color = Color(0xFF303744))
            Text("STRATUM V1 · TLS HANDSHAKE", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Connection: $stratumStatus", color = Muted, fontSize = 12.sp)
            Text("Pool difficulty: $difficulty", color = Muted, fontSize = 12.sp)
            Text("Latest job: $lastJob", color = Muted, fontSize = 12.sp)
            Text("Extranonce: $extranonceInfo", color = Muted, fontSize = 12.sp)
            Text("Shares · accepted: $acceptedShares / rejected: $rejectedShares / submitted: $shareCount", color = Muted, fontSize = 12.sp)
            Text("Hashes in last run: %,d".format(hashesDone), color = Muted, fontSize = 12.sp)
            Text("EVENT LOG · LATEST 12", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (eventLogs.isEmpty()) {
                        Text("No events yet · connect to Zpool to begin logging", color = Muted, fontSize = 11.sp)
                    } else {
                        eventLogs.take(12).forEach { entry ->
                            Text(entry, color = if (entry.contains("ACCEPTED")) Color(0xFF42C98A) else if (entry.contains("REJECTED") || entry.contains("ERROR")) Color(0xFFFF7777) else Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
            Button(
                onClick = {
                    if (authorized || connecting) {
                        stopReason = "Pool disconnected · mining stopped"
                        miningJob?.cancel()
                        thermalMonitorJob?.cancel()
                        mining = false
                        client.close()
                        authorized = false
                        connecting = false
                        stratumStatus = stopReason
                    } else {
                        scope.launch {
                            connecting = true
                            authorized = false
                            stratumStatus = "Connecting securely…"
                            try {
                                client.connect(
                                    host = "sha256.eu.mine.zpool.ca",
                                    port = 53333,
                                    wallet = wallet,
                                    password = "c=BTC",
                                    tls = true
                                )
                            } catch (e: Exception) {
                                stratumStatus = "Connection failed: ${e.message ?: "unknown error"}"
                            } finally {
                                connecting = false
                            }
                        }
                    }
                },
                enabled = (valid && saved && !connecting) || authorized,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if (authorized) Color(0xFF9B3434) else Gold, contentColor = if (authorized) White else Ink)
            ) {
                Text(if (connecting) "Connecting…" else if (authorized) "Disconnect" else "Test secure pool connection")
            }
            Text("SESSION DURATION", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("10 minutes · fixed session", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = {
                    if (mining) {
                        stopReason = "Mining session stopped by user"
                        miningJob?.cancel()
                        thermalMonitorJob?.cancel()
                        mining = false
                        stratumStatus = stopReason
                    } else {
                        val job = currentJob
                        if (!authorized || job == null || extranonce1.isBlank() || currentDifficulty <= 0.0) {
                            stratumStatus = "Connect first and wait for a valid job and difficulty"
                        } else {
                            stopReason = ""
                            miningJob = scope.launch {
                                mining = true
                                stratumStatus = "Mining session started · foreground only"
                                try {
                                    val result = Sha256dShareMiner.run(
                                        job = job,
                                        extranonce1 = extranonce1,
                                        extranonce2Size = extranonce2Size,
                                        difficulty = currentDifficulty,
                                        durationMillis = sessionDurationMillis
                                    )
                                    hashesDone = result.hashes
                                    stratumStatus = "Session complete · %.2f kH/s".format(result.hashesPerSecond / 1000.0)
                                    if (result.foundShare) {
                                        client.submitShare(
                                            wallet = wallet,
                                            jobId = job.jobId,
                                            extranonce2Hex = result.extranonce2Hex!!,
                                            nTime = job.nTime,
                                            nonceHex = result.nonceHex!!
                                        )
                                        shareCount++
                                        addEvent("SHARE SUBMITTED · job ${job.jobId} · waiting for pool response")
                                        stratumStatus = "Share submitted · waiting for pool response"
                                    } else {
                                        stratumStatus = "No share found in selected session · %.2f kH/s".format(result.hashesPerSecond / 1000.0)
                                    }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    stratumStatus = stopReason.ifBlank { "Mining session cancelled" }
                                    throw e
                                } catch (e: Exception) {
                                    stratumStatus = "Mining error: ${e.message ?: "unknown"}"
                                } finally {
                                    mining = false
                                    thermalMonitorJob?.cancel()
                                }
                            }
                            thermalMonitorJob = scope.launch {
                                while (mining) {
                                    val battery = context.registerReceiver(
                                        null,
                                        android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                                    )
                                    val tempTenths = battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
                                    val level = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                                    val scale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                                    val batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1
                                    if (tempTenths >= 300) {
                                        stopReason = "Safety stop · battery temperature reached 30°C"
                                        miningJob?.cancel()
                                        break
                                    }
                                    if (batteryPercent in 0..15) {
                                        stopReason = "Safety stop · battery level is 15% or lower"
                                        miningJob?.cancel()
                                        break
                                    }
                                    kotlinx.coroutines.delay(10_000L)
                                }
                            }
                        }
                    }
                },
                enabled = (authorized && currentJob != null && currentDifficulty > 0.0) || mining,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if (mining) Color(0xFF9B3434) else Gold, contentColor = if (mining) White else Ink)
            ) {
                Text(if (mining) "Stop mining session" else "Start ${sessionDurationMillis / 60_000L}-minute mining session")
            }
            Text("Foreground-only SHA-256d session · one CPU worker · fixed 10-minute session. Automatic safety stop at battery temperature ≥30°C (when reported by Android) or battery ≤15%. Stop manually at any time. Shares are submitted only if they meet the current pool difficulty. No background mining service is enabled.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun MinerPanel() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("ZPOOL SHA-256 · MAINNET POOL CONNECTION")
        ZpoolSettings()
        Notice("BTC payout is selected with c=BTC. Zpool is a multipool: it may direct SHA-256 work to compatible coins and pay the proceeds in BTC. Phone CPU mining is not economically competitive with ASIC hardware.")
    }
}
