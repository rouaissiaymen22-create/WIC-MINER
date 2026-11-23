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
                        item { MinerPanel() }
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
                        item { SafetyRow("Local mining", "Not installed or enabled", Icons.Default.Memory) }
                        item { SafetyRow("Thermal protection", "No mining workload is active", Icons.Default.Thermostat) }
                        item { SafetyRow("Battery protection", "No background compute service", Icons.Default.BatteryFull) }
                        item { SafetyRow("Background activity", "Disabled in this version", Icons.Default.Bedtime) }
                        item { SafetyRow("Emergency stop", "No active mining process", Icons.Default.StopCircle) }
                    }
                    else -> {
                        item { SectionTitle("ZPOOL · BITCOIN MAINNET") }
                        item { ZpoolSettings() }
                        item { SafetyRow("Application", "WIC MINER · v0.3.1", Icons.Default.Info) }
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
            Text("Bitcoin SHA-256d engine validation, then verified Signet pool mining.", color = Muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Gold, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("MANUAL MINING TEST · HONEST METRICS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
@Composable
fun ZpoolSettings() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wic_miner_settings", android.content.Context.MODE_PRIVATE) }
    var wallet by remember { mutableStateOf(prefs.getString("payout_wallet", "") ?: "") }
    var saved by remember { mutableStateOf(wallet.isNotBlank()) }
    var message by remember { mutableStateOf(if (saved) "Wallet saved on this device" else "Enter your public BTC payout address") }
    val valid = wallet.matches(Regex("^(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,60}$"))

    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Zpool SHA-256", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Bitcoin Mainnet payout configuration", color = Muted, fontSize = 12.sp)
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
            Text("Configuration only: Stratum connection and accepted-share submission are not implemented yet. No mining is running.", color = Muted, fontSize = 12.sp)
        }
    }
}

fun MinerPanel() {
    var running by remember { mutableStateOf(false) }
    var hashes by remember { mutableLongStateOf(0L) }
    var rate by remember { mutableStateOf("—") }
    var status by remember { mutableStateOf("Ready · local engine test only") }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("BITCOIN SHA-256d · STAGE 1")
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Local engine test", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Runs a short SHA-256d benchmark against a test header. This is NOT connected to Bitcoin or a mining pool and cannot earn BTC.", color = Muted, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Hashes tested", color = Muted, fontSize = 11.sp)
                        Text("%,d".format(hashes), color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Measured speed", color = Muted, fontSize = 11.sp)
                        Text(rate, color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(status, color = Muted, fontSize = 12.sp)
                Button(
                    onClick = {
                        scope.launch {
                            running = true
                            status = "Running 5-second local test…"
                            try {
                                val result = Sha256dBenchmark.run()
                                hashes = result.hashes
                                rate = "%.2f kH/s".format(result.hashesPerSecond / 1000.0)
                                status = "Completed · test header only · no BTC earned"
                            } catch (_: Exception) {
                                status = "Test stopped"
                            } finally {
                                running = false
                            }
                        }
                    },
                    enabled = !running,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (running) CircularProgressIndicator(Modifier.size(17.dp), color = Ink, strokeWidth = 2.dp)
                    else Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "Testing…" else "Run 5-second test")
                }
            }
        }
        Notice("Next stage: implement Stratum job parsing, Bitcoin block-header construction, share validation and Signet pool submission. Mainnet mining stays disabled until those checks pass.")
        SafetyRow("Thermal policy", "Short manual test only · no background service", Icons.Default.Thermostat)
        SafetyRow("Wallet security", "No seed phrase or private key required", Icons.Default.Lock)
    }
}
