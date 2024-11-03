package dev.polisan.piled

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.core.content.IntentCompat.getParcelableArrayExtra
import dev.polisan.piled.PiLED.sendSuspend

class NFCReader : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            val ndefMessages = getParcelableArrayExtra(intent, NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            if (ndefMessages != null) {
                val ndefMessage = ndefMessages[0] as NdefMessage
                val ndefRecord = ndefMessage.records[0]
                if (ndefRecord.tnf == NdefRecord.TNF_WELL_KNOWN && ndefRecord.type.contentEquals(NdefRecord.RTD_URI)) {
                    val uriPayload = ndefRecord.toUri().toString()
                    if (uriPayload == "piled://room_presence") {
                        Log.d("NFC", "piled://room_presence NFC tag detected!")
                        sendSuspend()
                    }
                }
            }
        }
        finish()
    }

}
