package dev.polisan.piled

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.polisan.piled.PiLED.appContext
import dev.polisan.piled.PiLED.defaultIp
import dev.polisan.piled.PiLED.defaultPort
import dev.polisan.piled.PiLED.isConnected
import dev.polisan.piled.ui.theme.PiLEDTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

var openSettings by mutableStateOf(false)
    private set

class MainActivity : ComponentActivity() {

    private val mainActivityContext = this

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PiLED.initialize(this)
        setContent {
            PiLEDTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("PiLED") },
                            actions = {
                                SettingsIcon { openSettings = true }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    if (openSettings) {
                        SettingsUI(
                            onClose = { openSettings = false },
                            context = this
                        )
                    } else {
                        MainLayout(context = this, modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}


@Composable
fun SettingsIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(id = R.drawable.baseline_settings_24),
            contentDescription = "Settings"
        )
    }
}


@Composable
fun MainLayout(context: Context, modifier: Modifier = Modifier) {
    var isLoading by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(TextFieldValue(getSetting(context, "piled_ip"))) }
    var port by remember { mutableStateOf(TextFieldValue(getSetting(context, "piled_port"))) }
    val coroutineScope = rememberCoroutineScope()

    if (!isConnected) {
        ConnectUI(
            address = address,
            onAddressChange = { address = it },
            port = port,
            onPortChange = { port = it },
            isLoading = isLoading,
            onConnect = {
                coroutineScope.launch {
                    isLoading = true
                    PiLED.connect(address.text, port.text)
                    isLoading = false
                }
            }
        )
    } else {
        MainUI(
            color = PiLED.currentColor,
            onDisconnect = {
                PiLED.disconnect()
            },
            onRequestColor = {
                coroutineScope.launch {
                    PiLED.requestColor()
                }
            }
        )
    }
}


@Composable
fun ConnectUI(
    address: TextFieldValue,
    onAddressChange: (TextFieldValue) -> Unit,
    port: TextFieldValue,
    onPortChange: (TextFieldValue) -> Unit,
    isLoading: Boolean,
    onConnect: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onConnect, enabled = !isLoading) {
                Text("Connect")
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000))
                    .wrapContentSize(Alignment.Center)
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun MainUI(
    color: Color,
    onDisconnect: () -> Unit,
    onRequestColor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CurrentColor(color = color)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestColor) {
            Text("Request Current Color")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDisconnect) {
            Text("Disconnect")
        }
    }
}

@Composable
fun CurrentColor(color: Color) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(color)
            .padding(16.dp)
    )
}

@Composable
fun SettingsUI(onClose: () -> Unit, context: Context) {
    var sharedSecret by remember { mutableStateOf(TextFieldValue(getSetting(context, "shared_secret"))) }
    var ip by remember { mutableStateOf(TextFieldValue(getSetting(context, "piled_ip"))) }
    var port by remember { mutableStateOf(TextFieldValue(getSetting(context, "piled_port"))) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = sharedSecret,
            onValueChange = { sharedSecret = it },
            label = { Text("Shared Secret") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.width(4.dp))
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("PiLED IP") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.width(4.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("PiLED Port") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row() {
            OutlinedButton(onClick = onClose) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = {
                saveSetting(context, "shared_secret", sharedSecret.text)
                saveSetting(context, "piled_ip", ip.text)
                saveSetting(context, "piled_port", port.text)
                defaultIp = ip.text
                defaultPort = port.text
                onClose()
            }) {
                Text("Save")
            }
        }
    }
}

private fun saveSetting(context: Context, setting: String, value: String) {
    val prefs = context.getSharedPreferences("PiLEDPrefs", Context.MODE_PRIVATE)
    prefs.edit().putString(setting, value).apply()
}

private fun getSetting(context: Context, setting: String): String {
    val prefs = context.getSharedPreferences("PiLEDPrefs", Context.MODE_PRIVATE)
    return prefs.getString(setting, "") ?: ""
}