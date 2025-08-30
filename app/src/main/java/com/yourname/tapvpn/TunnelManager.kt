package com.yourname.tapvpn

import android.content.Context
import android.util.Log
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.Tunnel.State
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

private const val TAG = "TapVPN"

/**
 * Set this to true while testing without native libs / real server.
 * When you're ready to use the real backend, set to false and add the native libraries.
 */
private const val USE_FAKE_BACKEND = true

object TunnelManager {
    private var backend: Backend? = null   // unused in FAKE mode
    private var fakeConnected: Boolean = false

    // Listener for tunnel state changes (UP/DOWN) → feeds your UI
    private var stateListener: ((Boolean) -> Unit)? = null

    fun registerStateListener(listener: (Boolean) -> Unit) {
        stateListener = listener
        Log.d(TAG, "registerStateListener(): listener registered")
    }

    private fun notifyStateChanged(newState: Tunnel.State) {
        val isUp = newState == State.UP
        Log.d(TAG, "notifyStateChanged(): $newState (isUp=$isUp)")
        stateListener?.invoke(isUp)
    }

    // --- Helpers (used in REAL mode) ---

    private fun readAssetFile(context: Context, fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

    // --- Connect using .conf from assets ---

    fun connectFromAsset(
        context: Context,
        fileName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (USE_FAKE_BACKEND) {
            // ✅ FAKE: simulate a successful connect after ~0.8s
            Log.d(TAG, "[FAKE] connectFromAsset(): simulating connect ($fileName)")
            CoroutineScope(Dispatchers.Main).launch {
                delay(800)
                fakeConnected = true
                notifyStateChanged(State.UP)
                Log.d(TAG, "[FAKE] connectFromAsset(): connected")
                onSuccess()
            }
            return
        }

        // --- REAL path (requires native libs) ---
        try {
            val configText = readAssetFile(context, fileName)
            val config = Config.parse(configText.reader().buffered())

            val tunnel = object : Tunnel {
                override fun getName(): String = "TapVPN"
                override fun onStateChange(newState: State) {
                    Log.d(TAG, "Tunnel.onStateChange(): $newState")
                    notifyStateChanged(newState)
                }
            }

            // TODO: init GoBackend here when switching off FAKE mode
            // backend = GoBackend(context.applicationContext)
            // backend?.setState(tunnel, State.UP, config)

            onSuccess() // call after real setState succeeds
        } catch (e: Exception) {
            Log.e(TAG, "connectFromAsset(): error ❌ ${e.message}", e)
            onError(e.message ?: "Unknown error")
        }
    }

    // Alternative connect that reads "wg0.conf" via Reader
    fun connectTunnel(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (USE_FAKE_BACKEND) {
            Log.d(TAG, "[FAKE] connectTunnel(): simulating connect (wg0.conf)")
            CoroutineScope(Dispatchers.Main).launch {
                delay(800)
                fakeConnected = true
                notifyStateChanged(State.UP)
                Log.d(TAG, "[FAKE] connectTunnel(): connected")
                onSuccess()
            }
            return
        }

        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("wg0.conf")))
            val config = Config.parse(reader)

            val tunnel = object : Tunnel {
                override fun getName(): String = "TapVPN"
                override fun onStateChange(newState: State) {
                    Log.d(TAG, "Tunnel.onStateChange(): $newState")
                    notifyStateChanged(newState)
                }
            }

            // TODO: init GoBackend and setState here in REAL mode

            onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "connectTunnel(): error ❌ ${e.message}", e)
            onError(e.message ?: "Unknown error")
        }
    }

    // Disconnect (DOWN)
    fun disconnectTunnel(context: Context) {
        if (USE_FAKE_BACKEND) {
            Log.d(TAG, "[FAKE] disconnectTunnel(): simulating disconnect")
            CoroutineScope(Dispatchers.Main).launch {
                delay(200)
                fakeConnected = false
                notifyStateChanged(State.DOWN)
                Log.d(TAG, "[FAKE] disconnectTunnel(): disconnected")
            }
            return
        }

        try {
            val empty = Config.Builder().build()
            val tunnel = object : Tunnel {
                override fun getName(): String = "TapVPN"
                override fun onStateChange(newState: State) {
                    Log.d(TAG, "Tunnel.onStateChange(): $newState")
                    notifyStateChanged(newState)
                }
            }

            // TODO: backend?.setState(tunnel, State.DOWN, empty) in REAL mode
        } catch (e: Exception) {
            Log.e(TAG, "disconnectTunnel(): error ❌ ${e.message}", e)
        }
    }
}
