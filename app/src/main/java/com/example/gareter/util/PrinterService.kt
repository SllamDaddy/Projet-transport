package com.example.gareter.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class PrinterService {

    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null

    val isConnected: Boolean get() = socket?.isConnected == true

    suspend fun connect(macAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            disconnect()
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: error("Bluetooth non disponible")
            val device = adapter.getRemoteDevice(macAddress)
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            s.connect()
            socket = s
            out = s.outputStream
        }
    }

    fun disconnect() {
        runCatching { out?.close() }
        runCatching { socket?.close() }
        out = null
        socket = null
    }

    suspend fun printTicket(ticket: TicketGenerator.TicketData): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val o = out ?: error("Imprimante non connectée")

                // Init
                o.write(ESC_INIT)
                o.write(LINE_FEED)

                // En-tête centré, gras, grand
                o.write(ALIGN_CENTER)
                o.write(BOLD_ON)
                o.write(FONT_BIG)
                o.writeLine(ticket.appName)
                o.write(FONT_NORMAL)
                o.write(BOLD_OFF)
                o.writeLine(ticket.lineLabel)
                o.write(LINE_FEED)

                // Séparateur
                o.writeLine("--------------------------------")

                // Type ticket
                o.write(BOLD_ON)
                o.write(FONT_MEDIUM)
                o.writeLine(ticket.typeName)
                o.write(FONT_NORMAL)
                o.write(BOLD_OFF)

                // Prix
                o.write(ALIGN_LEFT)
                o.writeLine("Prix       : ${ticket.priceText}")
                o.writeLine("Date/Heure : ${ticket.dateTime}")
                o.writeLine("Validite   : ${ticket.validityText}")
                o.writeLine(ticket.ticketNumber)

                if (ticket.carnetUnitsLeft != null) {
                    o.write(BOLD_ON)
                    o.writeLine("Unites restantes : ${ticket.carnetUnitsLeft}/10")
                    o.write(BOLD_OFF)
                }

                // QR Code
                ticket.qrContent?.let { content ->
                    o.write(LINE_FEED)
                    o.write(ALIGN_CENTER)
                    printQr(o, content)
                    o.write(ALIGN_LEFT)
                }

                // Pied de page
                o.write(LINE_FEED)
                o.write(ALIGN_CENTER)
                o.writeLine("Conservez ce ticket")
                o.writeLine("Merci de votre confiance")
                o.write(LINE_FEED)
                o.write(LINE_FEED)
                o.write(LINE_FEED)

                // Coupe papier
                o.write(CUT_PAPER)
                o.flush()
            }
        }

    // ── ESC/POS QR Code (sequence GS ( k) ────────────────────────────────────────

    private fun printQr(out: OutputStream, content: String) {
        val data = content.toByteArray(Charsets.UTF_8)
        val dataLen = data.size + 3
        val pL = (dataLen % 256).toByte()
        val pH = (dataLen / 256).toByte()

        // Model 2
        out.write(byteArrayOf(GS, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
        // Module size (4 = ~medium for 58mm)
        out.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x04))
        // Error correction level M
        out.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31))
        // Store data
        out.write(byteArrayOf(GS, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        out.write(data)
        // Print
        out.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
    }

    private fun OutputStream.writeLine(text: String) {
        write(text.toByteArray(Charsets.UTF_8))
        write(LINE_FEED)
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private val ESC_INIT    = byteArrayOf(0x1B, 0x40)
        private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
        private val ALIGN_LEFT   = byteArrayOf(0x1B, 0x61, 0x00)
        private val BOLD_ON      = byteArrayOf(0x1B, 0x45, 0x01)
        private val BOLD_OFF     = byteArrayOf(0x1B, 0x45, 0x00)
        private val FONT_BIG     = byteArrayOf(0x1B, 0x21, 0x30) // double height+width
        private val FONT_MEDIUM  = byteArrayOf(0x1B, 0x21, 0x10) // double height
        private val FONT_NORMAL  = byteArrayOf(0x1B, 0x21, 0x00)
        private val LINE_FEED    = byteArrayOf(0x0A)
        private val CUT_PAPER    = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
        private const val GS     = 0x1D.toByte()
    }
}
