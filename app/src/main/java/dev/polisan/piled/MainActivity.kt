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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.polisan.piled.PiLED.defaultIp
import dev.polisan.piled.PiLED.defaultPort
import dev.polisan.piled.PiLED.isConnected
import dev.polisan.piled.ui.theme.PiLEDTheme
import kotlinx.coroutines.launch

var openSettings by mutableStateOf(false)
    private set

class MainActivity : ComponentActivity() {

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
            modifier = modifier,
            color = PiLED.currentColor,
            onColorUpdate = { newColor ->
                coroutineScope.launch {
                    PiLED.sendColor(newColor)
                }
            },
            onDisconnect = {
                PiLED.disconnect()
            },
            onToggleSuspend = {
                coroutineScope.launch {
                    PiLED.sendSuspend()
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
    modifier: Modifier,
    color: Color,
    onColorUpdate: (Color) -> Unit,
    onDisconnect: () -> Unit,
    onToggleSuspend: () -> Unit,
) {
    var duration by remember { mutableStateOf(3.0f) }
    var speed by remember { mutableStateOf(1.0f) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(color)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            ColorSlider("Red", color.red) { newRed ->
                onColorUpdate(color.copy(red = newRed))
            }
            ColorSlider("Green", color.green) { newGreen ->
                onColorUpdate(color.copy(green = newGreen))
            }
            ColorSlider("Blue", color.blue) { newBlue ->
                onColorUpdate(color.copy(blue = newBlue))
            }
        }

        //animations section
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Animation Buttons Section
        Text(text = "Duration in seconds")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Slider(
                value = duration,
                onValueChange = { duration = it },
                valueRange = 0f..5f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                    inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                steps = 5,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "${duration.toInt()}")
        }

        Text(text = "Speed")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Slider(
                value = speed,
                onValueChange = { speed = it },
                valueRange = 0f..255f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                    inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                steps = 51,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "${speed.toInt()}")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = {
                coroutineScope.launch {
                    PiLED.startFadeAnimation(color, duration.toInt(), speed.toInt())
                }
            }) {
                Text("Start Fade Animation")
            }
            Button(onClick = {
                coroutineScope.launch {
                    Log.d("ANIM", "Start pulse anim clicked with ${color.red}, ${color.green}, ${color.blue}")
                    PiLED.startPulseAnimation(color, duration.toInt(), speed.toInt())
                }
            }) {
                Text("Start Pulse Animation")
            }
        }


        Spacer(modifier = Modifier.weight(1f))

        // Center Functional Buttons
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row{
                Button(onClick = { onColorUpdate(color) }) { //hehe
                    Text("Stop Animations")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = onToggleSuspend) {
                    Text("Toggle Suspend")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onDisconnect) {
                Text("Disconnect")
            }
        }
    }
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

@Composable
fun ColorSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text(text = label)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
                activeTrackColor = MaterialTheme.colorScheme.secondary,
                inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            modifier = Modifier.fillMaxWidth()
        )
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