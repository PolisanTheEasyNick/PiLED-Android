package dev.polisan.piled

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.polisan.piled.PiLED.isConnected
import dev.polisan.piled.ui.theme.PiLEDTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PiLEDTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainLayout(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainLayout(modifier: Modifier = Modifier) {
    var isLoading by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(TextFieldValue("192.168.0.4")) }
    var port by remember { mutableStateOf(TextFieldValue("3384")) }
    var color by remember { mutableStateOf(Color.Gray) }
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
                    PiLED.connect(address.text, port.text.toInt())
                    isLoading = false
                }
            }
        )
    } else {
        MainUI(
            color = color,
            onDisconnect = {
                PiLED.disconnect()
            },
            onRequestColor = {
                coroutineScope.launch {
                    color = PiLED.requestColor() ?: Color.Gray
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

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    PiLEDTheme {
        MainLayout()
    }
}
