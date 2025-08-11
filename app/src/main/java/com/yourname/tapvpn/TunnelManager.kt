package com.yourname.tapvpn

import android.content.Context
import android.util.Log
import com.yourname.tapvpn.backend.Backend
import com.yourname.tapvpn.backend.WgQuickBackend
import com.yourname.tapvpn.model.Tunnel
import com.yourname.tapvpn.model.Tunnel.State
import com.yourname.tapvpn.util.RootShell
import com.yourname.tapvpn.util.ToolsInstaller
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.InputStreamReader

object TunnelManager {
    private var backend: Backend? = null

    private fun initBackend(context: Context) {
        if (backend == null) {
            val shell = RootShell(context)
            backend = WgQuickBackend(
                context,
                shell,
                ToolsInstaller(context, shell)
            )
        }
    }

    // ✅ Helper to read wg0.conf from assets
    private fun readAssetFile(context: Context, fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

    // ✅ Connect to tunnel using .conf from assets
    fun connectFromAsset(
        context: Context,
        fileName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        initBackend(context)

        try {
            val configText = readAssetFile(context, fileName)
            val config = Config.parse(configText.reader().buffered())


            val tunnel = object : Tunnel {
                override fun getName(): String = "TapVPN"

                override fun onStateChange(newState: Tunnel.State) {
                    // No-op (or log)
                }
            }

            backend?.setState(tunnel, Tunnel.State.UP, config)
            onSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            onError(e.message ?: "Unknown error")
        }
    }

    // ✅ Alternative method using reader
    fun connectTunnel(context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        initBackend(context)

        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("wg0.conf")))
            val config = Config.parse(reader)

            val tunnel = object : Tunnel {
                override fun getName(): String = "TapVPN"
                override fun onStateChange(newState: Tunnel.State) {
                    // optional logging
                }
            }

            backend?.setState(tunnel, State.UP, config)
            onSuccess()
        } catch (e: Exception) {
            Log.e("TunnelManager", "❌ Failed to connect tunnel: ${e.message}", e)
            onError(e.message ?: "Unknown error")
        }
    }

    fun disconnectTunnel(context: Context) {
        initBackend(context)

        try {
            val tunnel = object : Tunnel {
                override fun getName(): String = "TapVPN"
                override fun onStateChange(newState: Tunnel.State) {
                    // optional
                }
            }

            val config = Config.Builder().build()
            backend?.setState(tunnel, State.DOWN, config)
        } catch (e: Exception) {
            Log.e("TunnelManager", "❌ Failed to disconnect tunnel: ${e.message}", e)
        }
    }
}
