package com.example.gareter.data.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PrinterManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // UUID standard SPP (Serial Port Profile)
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Lister les imprimantes appairées
    fun getPairedPrinters(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.filter {
                it.name?.contains("Printer", ignoreCase = true) == true ||
                        it.name?.contains("Thermal", ignoreCase = true) == true
            }?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Connecter à une imprimante
    suspend fun connectToPrinter(device: BluetoothDevice): Result<BluetoothSocket> {
        return withContext(Dispatchers.IO) {
            try {
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothAdapter?.cancelDiscovery() // Stop discovery for faster connection
                socket.connect()
                Result.success(socket)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // Envoyer du texte à l'imprimante
    suspend fun printText(socket: BluetoothSocket, text: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                socket.outputStream.write(text.toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()
                Result.success(Unit)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }

    // Déconnecter
    suspend fun disconnect(socket: BluetoothSocket) {
        withContext(Dispatchers.IO) {
            try {
                socket.close()
            } catch (e: Exception) {
                // Silent close
            }
        }
    }

    // Formatter un ticket pour impression thermique (58mm)
    fun formatTicketForPrinting(
        type: String,
        price: Int,
        agentNumber: String,
        lineNumber: String,
        timestamp: Long,
        transactionId: String,
    ): String {
        val priceText = "€${price / 100}.${(price % 100).toString().padStart(2, '0')}"
        val date = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(timestamp))

        return buildString {
            appendLine("══════════════════════════")
            appendLine("   GIROUETTE BUS - TER")
            appendLine("══════════════════════════")
            appendLine()
            appendLine("TYPE : $type")
            appendLine("PRIX : $priceText")
            appendLine()
            appendLine("Agent  : #$agentNumber")
            appendLine("Ligne  : $lineNumber")
            appendLine()
            appendLine("Date   : $date")
            appendLine("Ref    : $transactionId")
            appendLine()
            appendLine("══════════════════════════")
            appendLine("   MERCI DE VOTRE VISITE")
            appendLine("══════════════════════════")
            appendLine("\n\n\n") // 3× newlines pour détacher physiquement
        }
    }

    // Vérifier si Bluetooth est disponible et activé
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
}
