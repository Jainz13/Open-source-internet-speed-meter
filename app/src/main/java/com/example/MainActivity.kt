package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startMonitorService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onStart = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        this,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    startMonitorService()
                                } else {
                                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                startMonitorService()
                            }
                        },
                        onStop = { stopMonitorService() }
                    )
                }
            }
        }
    }

    private fun startMonitorService() {
        val intent = Intent(this, NetworkMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopMonitorService() {
        val intent = Intent(this, NetworkMonitorService::class.java).apply {
            action = NetworkMonitorService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier, onStart: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var unitPref by remember { mutableStateOf(prefs.getString("PREF_UNIT", "BYTES") ?: "BYTES") }
    var displayPref by remember { mutableStateOf(prefs.getString("PREF_DISPLAY", "DOWN") ?: "DOWN") }

    fun updateUnitPref(newVal: String) {
        unitPref = newVal
        prefs.edit().putString("PREF_UNIT", newVal).apply()
    }

    fun updateDisplayPref(newVal: String) {
        displayPref = newVal
        prefs.edit().putString("PREF_DISPLAY", newVal).apply()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Network Speed Monitor",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Display Preference Selection
        Text("Show in Status Bar:", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = displayPref == "DOWN", onClick = { updateDisplayPref("DOWN") })
                Text("Download")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = displayPref == "UP", onClick = { updateDisplayPref("UP") })
                Text("Upload")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = displayPref == "TOTAL", onClick = { updateDisplayPref("TOTAL") })
                Text("Total")
            }
        }

        // Unit Preference Selection
        Text("Speed Unit:", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = unitPref == "BYTES", onClick = { updateUnitPref("BYTES") })
                Text("MB/s (Bytes)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = unitPref == "BITS", onClick = { updateUnitPref("BITS") })
                Text("Mbps (Bits)")
            }
        }

        Button(
            onClick = onStart,
            modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(0.8f)
        ) {
            Text("Start Monitoring")
        }
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Stop Monitoring")
        }
    }
}

