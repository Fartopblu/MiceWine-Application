package com.micewine.emu

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.Socket
import kotlin.system.exitProcess

class EntryPoint {
    companion object {
        const val ACTION_START = "com.micewine.emu.EntryPoint.ACTION_START"
        private const val PORT = 7892
        private val MAGIC = "0xDEADBEEF".toByteArray()
        private var handler: Handler? = Handler(Looper.getMainLooper())

        init {
            System.loadLibrary("Xlorie")
        }

        suspend fun startX11(args: Array<String>?, context: Context) {
            withContext(Dispatchers.IO) {
                if (!start(args)) {
                    exitProcess(1)
                }
                spawnListeningThread()
                sendBroadcastDelayed(context)
            }
        }

        // In some cases Android Activity part can not connect opened port.
        // In this case opened port works like a lock file.
        private fun sendBroadcastDelayed(context: Context) {
            if (!connected()) sendBroadcast(context)
            handler!!.postDelayed({ sendBroadcastDelayed(context) }, 1000)
        }

        private fun spawnListeningThread() {
            Thread { listenForConnections(PORT, MAGIC) }.start()
        }

        private fun sendBroadcast(context: Context) {
            val intent = Intent(ACTION_START)

            context.sendBroadcast(intent)
        }

        fun requestConnection() {
            System.err.println("Requesting connection...")
            Thread { // New thread is needed to avoid android.os.NetworkOnMainThreadException
                try {
                    Socket("127.0.0.1", PORT).use { socket ->
                        socket.getOutputStream().write(MAGIC)
                    }
                } catch (e: ConnectException) {
                    if (e.message != null && e.message!!.contains("Connection refused")) {
                        Log.e(
                            "CmdEntryPoint",
                            "ECONNREFUSED: Connection has been refused by the server"
                        )
                    } else Log.e(
                        "CmdEntryPoint",
                        "Something went wrong when we requested connection",
                        e
                    )
                } catch (e: Exception) {
                    Log.e("CmdEntryPoint", "Something went wrong when we requested connection", e)
                }
            }.start()
        }

        @JvmStatic
        external fun start(args: Array<String>?): Boolean
        @JvmStatic
        private external fun connected(): Boolean
        @JvmStatic
        external fun windowChanged(surface: Surface)
        @JvmStatic
        external fun getXConnection(): ParcelFileDescriptor
        @JvmStatic
        external fun getLogcatOutput(): ParcelFileDescriptor
        @JvmStatic
        private external fun listenForConnections(port: Int, bytes: ByteArray)
    }
}
