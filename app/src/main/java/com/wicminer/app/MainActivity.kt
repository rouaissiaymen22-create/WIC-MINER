package com.wicminer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

private val Ink = Color(0xFF090B10)
private val Panel = Color(0xFF141821)
private val Gold = Color(0xFFF0B90B)
private val Muted = Color(0xFF8C95A5)
private val White = Color(0xFFF5F6F8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WicMinerApp() }
    }
}

@Composable
fun WicMinerApp() {
    var tab by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Ink, surface = Panel, onPrimary = Ink, onBackground = White, onSurface = White)) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    val labels = listOf("Home", "Mining", "Safety", "Settings")
                    val icons = listOf(Icons.Default.Dashboard, Icons.Default.Memory, Icons.Default.Shield, Icons.Default.Settings)
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], contentDescription = label) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold, selectedTextColor = Gold, indicatorColor = Color(0xFF34301D), unselectedIconColor = Muted, unselectedTextColor = Muted))
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
                            Text("BETA · DEMO", color = Gold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                when (tab) {
                    0 -> {
                        item { HeroCard() }
                        item { SectionTitle("MINING OVERVIEW") }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard("Hashrate", "—", "No miner connected", Modifier.weight(1f))
                            MetricCard("BTC earned", "0.00000000", "Verified rewards only", Modifier.weight(1f))
                        } }
                        item { MetricCard("Mining status", "Not connected", "Connect an ASIC or pool account", Modifier.fillMaxWidth()) }
                        item { Notice("This is an initial app prototype. No Bitcoin is being mined and no rewards are generated in demo mode.") }
                    }
                    1 -> {
                        item { SectionTitle("MINING CONNECTION") }
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("External ASIC / Pool", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Pool and ASIC monitoring will be connected after selecting a supported provider. No credentials are collected in this prototype.", color = Muted, fontSize = 13.sp)
                                    OutlinedButton(onClick = { showInfo = true }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Link, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Connection setup")
                                    }
                                }
                            }
                        }
                        item { Notice("Local phone mining is not enabled. A phone cannot economically mine Bitcoin; this app does not perform proof-of-work.") }
                    }
                    2 -> {
                        item { SectionTitle("DEVICE PROTECTION") }
                        item { SafetyRow("Thermal protection", "No mining workload is active", Icons.Default.Thermostat) }
                        item { SafetyRow("Battery protection", "No background compute service", Icons.Default.BatteryFull) }
                        item { SafetyRow("Background activity", "Disabled in this prototype", Icons.Default.Bedtime) }
                        item { SafetyRow("Emergency stop", "No active mining process", Icons.Default.StopCircle) }
                    }
                    else -> {
                        item { SectionTitle("APP SETTINGS") }
                        item { SafetyRow("Application", "WIC MINER · v0.1.0", Icons.Default.Info) }
                        item { SafetyRow("Network", "Not connected", Icons.Default.WifiOff) }
                        item { SafetyRow("Wallet", "Read-only integration planned", Icons.Default.AccountBalanceWallet) }
                        item { SafetyRow("Security", "Never enter seed phrases into this app", Icons.Default.Lock) }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
            if (showInfo) {
                AlertDialog(onDismissRequest = { showInfo = false }, confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close", color = Gold) } }, title = { Text("Connection setup") }, text = { Text("Pool and ASIC integrations require a supported provider and explicit configuration. This prototype does not connect or mine yet.") }, containerColor = Panel)
            }
        }
    }
}

@Composable
fun HeroCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1A13)), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("BITCOIN MINING CONTROL", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Your mining, your control.", color = White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp)
            Text("Monitor external miners with a battery-first mobile experience.", color = Muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Gold, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("DEVICE-SAFE BY DESIGN", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
