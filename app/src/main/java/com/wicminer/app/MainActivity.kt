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
                    val labels = listOf("Home", "Cloud", "Pools", "Safety", "Settings")
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
                        item { SectionTitle("CLOUD MINING") }
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.Cloud, tint = Gold, modifier = Modifier.size(28.dp), contentDescription = null)
                                    Text("Cloud mining connections", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    Text("No cloud provider is connected. WIC MINER will only display contract or hashrate information after a provider offers a verifiable read-only API.", color = Muted, fontSize = 13.sp)
                                    Text("No contracts are sold in this app. Never send BTC to an unknown mining service or trust guaranteed-return claims.", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        item { Notice("Cloud mining providers are independent third parties. We will assess transparency, payout proof, fees, and withdrawal terms before adding any integration.") }
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
                        item { RefreshRow(loading, data.updated, loadError) { refresh() } }
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
                        item { SectionTitle("APP SETTINGS") }
                        item { SafetyRow("Application", "WIC MINER · v0.2.0", Icons.Default.Info) }
                        item { SafetyRow("Data source", "mempool.space public API", Icons.Default.Public) }
                        item { SafetyRow("Pool account", "Not connected", Icons.Default.Groups) }
                        item { SafetyRow("Wallet", "Read-only integration planned", Icons.Default.AccountBalanceWallet) }
                        item { SafetyRow("Security", "Never enter seed phrases into this app", Icons.Default.Lock) }
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
            Text("A read-only companion for Bitcoin network and mining-pool intelligence.", color = Muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Gold, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("NO PHONE MINING · NO FAKE REWARDS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
