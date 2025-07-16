package com.example.remote

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import javax.jmdns.JmDNS
import java.net.InetAddress
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class MainActivity : AppCompatActivity() {
    private var tvIp: String? = null
    private var wsClient: WebSocketClient? = null
    private var jmDNS: JmDNS? = null
    private val serviceType = "_samsungremote._tcp.local."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnDiscover = findViewById<Button>(R.id.btn_discover)
        val btnConnect = findViewById<Button>(R.id.btn_connect)
        val editIp = findViewById<EditText>(R.id.edit_ip)

        btnDiscover.setOnClickListener {
            discoverTv { ip ->
                runOnUiThread { editIp.setText(ip) }
            }
        }

        btnConnect.setOnClickListener {
            tvIp = editIp.text.toString()
            connectToTv(tvIp)
        }

        setButtonListeners()
    }

    private fun discoverTv(onFound: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                jmDNS = JmDNS.create(InetAddress.getLocalHost())
                val services = jmDNS!!.list(serviceType)
                if (services.isNotEmpty()) {
                    val ip = services[0].inetAddresses.firstOrNull()?.hostAddress
                    if (ip != null) onFound(ip)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "No Samsung TV found", Toast.LENGTH_SHORT).show()
                    }
                }
                jmDNS?.close()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Discovery error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun connectToTv(ip: String?) {
        if (ip.isNullOrEmpty()) {
            Toast.makeText(this, "Enter TV IP", Toast.LENGTH_SHORT).show()
            return
        }
        val wsUrl = "ws://$ip:8001/api/v2/channels/samsung.remote.control?name=U2Ftc3VuZ1JlbW90ZQ=="
        wsClient = object : WebSocketClient(URI(wsUrl)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Connected to TV", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onMessage(message: String?) {}
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Disconnected", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onError(ex: Exception?) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Connection error: ${ex?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        wsClient?.connect()
    }

    private fun setButtonListeners() {
        val buttonMap = mapOf(
            R.id.btn_power to "KEY_POWER",
            R.id.btn_volume_up to "KEY_VOLUP",
            R.id.btn_volume_down to "KEY_VOLDOWN",
            R.id.btn_channel_up to "KEY_CHUP",
            R.id.btn_channel_down to "KEY_CHDOWN",
            R.id.btn_up to "KEY_UP",
            R.id.btn_down to "KEY_DOWN",
            R.id.btn_left to "KEY_LEFT",
            R.id.btn_right to "KEY_RIGHT",
            R.id.btn_ok to "KEY_ENTER",
            R.id.btn_back to "KEY_RETURN",
            R.id.btn_home to "KEY_HOME"
        )
        for ((btnId, key) in buttonMap) {
            findViewById<Button>(btnId)?.setOnClickListener {
                sendKey(key)
            }
        }
    }

    private fun sendKey(key: String) {
        if (wsClient == null || !wsClient!!.isOpen) {
            Toast.makeText(this, "Not connected to TV", Toast.LENGTH_SHORT).show()
            return
        }
        val cmd = """{
            "method": "ms.remote.control",
            "params": {
                "Cmd": "Click",
                "DataOfCmd": "$key",
                "Option": "false",
                "TypeOfRemote": "SendRemoteKey"
            }
        }"""
        wsClient?.send(cmd)
    }

    override fun onDestroy() {
        super.onDestroy()
        wsClient?.close()
        jmDNS?.close()
    }
} 