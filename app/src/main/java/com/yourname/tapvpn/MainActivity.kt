package com.yourname.tapvpn

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError
import com.yourname.tapvpn.ui.theme.TapVPNTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat


import com.yourname.tapvpn.consent.ConsentManager
import com.google.android.gms.ads.MobileAds

import androidx.core.content.edit
import android.widget.Toast
import com.yourname.tapvpn.TunnelManager
import com.yourname.tapvpn.notify.NotificationHelper



private const val TAG = "TapVPN"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        // ✅ show splash first
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // ✅ tell Android to fit windows normally
        WindowCompat.setDecorFitsSystemWindows(window, true)


        // Android 13+ needs runtime permission for notifications
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 1001)
            }
        }

        Log.d("TapVPN", "Supported ABIs: " + android.os.Build.SUPPORTED_ABIS?.joinToString())

        // create notification channel once
        NotificationHelper.createChannel(this)

        MobileAds.initialize(this)
        val prefs = getSharedPreferences("tapvpn", MODE_PRIVATE)

        // ✅ Request consent before initializing ads
        ConsentManager.requestConsent(this) { canRequestAds ->
            // Initialize Mobile Ads SDK after consent flow finishes
            MobileAds.initialize(this)

            setContent {
                val connectedState = remember { mutableStateOf(false) }
                val sessionRemainingState = remember { mutableIntStateOf(0) }
                val rewardedAdState = remember { mutableStateOf<RewardedAd?>(null) }
                val interstitialAdState = remember { mutableStateOf<InterstitialAd?>(null) }
                val isAdPlaying = remember { mutableStateOf(false) }
                var sessionExpiredFlag by remember { mutableStateOf(false) }
                val isConnecting = remember { mutableStateOf(false) }


                // ✅ Listen for VPN tunnel state changes
                LaunchedEffect(Unit) {
                    TunnelManager.registerStateListener { isUp ->
                        if (!isUp && connectedState.value) {
                            // VPN dropped unexpectedly
                            connectedState.value = false
                            sessionRemainingState.intValue = 0
                            prefs.edit { remove("sessionEndTime") }

                            // notify user when VPN drops
                            NotificationHelper.showSessionExpired(this@MainActivity)

                            sessionExpiredFlag = true
                        }
                    }
                }

                // ✅ Bootstrap UI state from saved sessionEndTime at app start
                LaunchedEffect(Unit) {
                    val endTime = prefs.getLong("sessionEndTime", 0L)
                    val now = System.currentTimeMillis()
                    if (endTime > now) {
                        connectedState.value = true
                        sessionRemainingState.intValue = ((endTime - now) / 1000).toInt()
                    } else if (endTime != 0L) {
                        // session was stale — clean it up
                        prefs.edit { remove("sessionEndTime") }
                        connectedState.value = false
                        sessionRemainingState.intValue = 0
                    }
                }


                LaunchedEffect(Unit) {
                    // loadRewardedAd(this@MainActivity, rewardedAdState) // Preload Ads for Extend Button
                    // loadInterstitialAd(this@MainActivity, interstitialAdState) // Preload Ads for Connect Button
                }

                // This ensures we never load rewarded when disconnected and we auto-retry every 30s while connected.
                LaunchedEffect(connectedState.value) {
                    if (connectedState.value) {
                        // loop only while connected
                        while (connectedState.value) {
                            if (rewardedAdState.value == null) {
                                loadRewardedAd(this@MainActivity, rewardedAdState)
                            }
                            kotlinx.coroutines.delay(30_000)
                        }
                    } else {
                        // on disconnect, drop any loaded rewarded ad
                        rewardedAdState.value = null
                    }
                }

                // Marks UI as Connected & starts the 60s test timer.
                // (This is your old onSuccess body moved into a function.)
                fun finishVpnConnect() {
                    connectedState.value = true

                    // ⏱ keep 60s test mode for now
                    val endTime = System.currentTimeMillis() + 60_000
                    prefs.edit { putLong("sessionEndTime", endTime) }
                    sessionRemainingState.intValue = 60

                    // Load rewarded AFTER tunnel is up (US IP takes effect)
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(2500) // let routing settle on VPN egress
                        loadRewardedAd(this@MainActivity, rewardedAdState)
                    }

                    // make sure we’re not re-triggering the expired card
                    sessionExpiredFlag = false

                    Log.d(TAG, "finishVpnConnect(): VPN connected ✅ session=60s")
                    Toast.makeText(this@MainActivity, "VPN Connected ✅", Toast.LENGTH_SHORT).show()
                    isConnecting.value = false
                }


                // Disconnect helper for the one-button flow
                fun stopVpn() {
                    TunnelManager.disconnectTunnel(this@MainActivity)
                    connectedState.value = false
                    isConnecting.value = false
                    sessionRemainingState.intValue = 0
                    prefs.edit { remove("sessionEndTime") }
                    Toast.makeText(this@MainActivity, "VPN Disconnected ❌", Toast.LENGTH_SHORT)
                        .show()
                    Log.d(TAG, "stopVpn(): done")
                }


                // NEW startVpn handshake: VPN UP -> load/show interstitial -> then finishVpnConnect()
                fun startVpn() {
                    if (isConnecting.value) {
                        Log.d(TAG, "startVpn(): ignored, already connecting")
                        return
                    }
                    isConnecting.value = true
                    connectedState.value = false  // show "Connecting…" until we finish handshake
                    Log.d(TAG, "startVpn(): starting...")

                    TunnelManager.connectFromAsset(
                        context = this@MainActivity,
                        fileName = "wg0.conf",
                        onSuccess = {
                            Log.d(TAG, "startVpn(): tunnel UP (but not marking connected yet)")

                            // After VPN is UP, load an interstitial using VPN (US) IP, then show it.
                            CoroutineScope(Dispatchers.Main).launch {
                                // small settle delay is optional
                                delay(2000)

                                // load a fresh interstitial now (we removed preload)
                                interstitialAdState.value = null
                                loadInterstitialAd(this@MainActivity, interstitialAdState)

                                // wait up to 8s for ad to load; otherwise skip
                                val loaded = withTimeoutOrNull(8000) {
                                    while (interstitialAdState.value == null) delay(200)
                                    true
                                } ?: false

                                val ad = interstitialAdState.value
                                if (loaded && ad != null) {
                                    ad.fullScreenContentCallback =
                                        object : FullScreenContentCallback() {
                                            override fun onAdDismissedFullScreenContent() {
                                                interstitialAdState.value = null
                                                Log.d(
                                                    TAG,
                                                    "Interstitial dismissed → finishing connect"
                                                )
                                                finishVpnConnect()
                                            }

                                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                                interstitialAdState.value = null
                                                Log.d(
                                                    TAG,
                                                    "Interstitial failed to show → finishing connect"
                                                )
                                                finishVpnConnect()
                                            }
                                        }
                                    ad.show(this@MainActivity)
                                    Log.d(TAG, "Interstitial shown")
                                } else {
                                    Log.d(
                                        TAG,
                                        "No interstitial within grace window → finishing connect"
                                    )
                                    finishVpnConnect()
                                }
                            }
                        },
                        onError = { msg ->
                            Log.e(TAG, "startVpn(): VPN failed ❌ $msg")
                            Toast.makeText(this@MainActivity, "VPN Failed: $msg", Toast.LENGTH_LONG)
                                .show()
                            isConnecting.value = false
                        }
                    )
                }


                // ✅ Real-time countdown using saved endTime
                LaunchedEffect(Unit) {
                    while (true) {
                        val endTime = prefs.getLong("sessionEndTime", 0L)

                        if (connectedState.value && endTime > 0) {
                            val remaining = ((endTime - System.currentTimeMillis()) / 1000).toInt()

                            if (remaining <= 0) {
                                connectedState.value = false
                                sessionRemainingState.intValue = 0
                                prefs.edit { remove("sessionEndTime") }

                                // notify user when session ends
                                NotificationHelper.showSessionExpired(this@MainActivity)

                                sessionExpiredFlag = true // ✅ Trigger VPNHomeScreen popup
                            } else {
                                sessionRemainingState.intValue = remaining
                            }
                        }

                        delay(1000)
                    }
                }

                fun connectAndStartSession() {
                    TunnelManager.connectTunnel(
                        context = this@MainActivity,
                        onSuccess = {
                            connectedState.value = true
                            val endTime = System.currentTimeMillis() + 60_000
                            prefs.edit { putLong("sessionEndTime", endTime) }
                            sessionRemainingState.intValue = 60
                        },
                        onError = { message ->
                            Toast.makeText(
                                this@MainActivity,
                                "VPN Failed: $message",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }

                TapVPNTheme {
                    VPNHomeScreen(
                        connected = connectedState.value,
                        sessionRemainingSeconds = sessionRemainingState.intValue,
                        isAdAvailable = rewardedAdState.value != null,
                        isAdPlaying = isAdPlaying.value,
                        rewardedAd = rewardedAdState.value,
                        isConnecting = isConnecting.value,

                        onWatchAd = { onReward ->
                            val ad = rewardedAdState.value
                            if (ad != null) {
                                isAdPlaying.value = true
                                ad.show(this@MainActivity) {
                                    // Grant ONLY after the real reward
                                    rewardedAdState.value = null
                                    isAdPlaying.value = false
                                    onReward()
                                    // Do NOT call loadRewardedAd() here.
                                    // The connected-only 30s loop will load the next one after VPN is up.
                                }
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Ad not ready yet. Please try again shortly.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                // No free time when ad isn't ready.
                            }
                        },

                        onConnectTapped = {
                            if (connectedState.value) {
                                stopVpn()      // disconnect
                            } else {
                                startVpn()     // begins Connecting…; VPN UP → load/show interstitial → finishVpnConnect()
                            }
                        },


                        onUpdateConnection = { newConnected ->
                            connectedState.value = newConnected
                            if (!newConnected) {
                                // ❌ Stop VPN tunnel when user disconnects
                                TunnelManager.disconnectTunnel(this@MainActivity)
                                prefs.edit { remove("sessionEndTime") }
                            }
                        },

                        onUpdateSession = { sessionRemainingState.intValue = it },

                        sessionExpired = sessionExpiredFlag, // ✅ new flag passed into screen

                        onReconnect = {
                            prefs.edit { remove("sessionEndTime") }
                            startVpn()
                        }
                    )

                    // ✅ Reset flag once it has been used
                    LaunchedEffect(sessionExpiredFlag) {
                        if (sessionExpiredFlag) {
                            delay(300) // let screen show it
                            sessionExpiredFlag = false
                        }
                    }
                }
            }
        }
    }

    private fun loadRewardedAd(
        context: Context,
        rewardedAdState: MutableState<RewardedAd?>
    ) {
        val adRequest = ConsentManager.adRequest(this)
        RewardedAd.load(
            context,
            "ca-app-pub-3940256099942544/5224354917", // Test ID
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdMob", "🎉 Rewarded ad loaded")
                    rewardedAdState.value = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdMob", "❌ Failed to load rewarded ad: ${error.message}")
                    rewardedAdState.value = null

                    // 🔁 Retry in 30s using coroutine
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(30_000)
                        loadRewardedAd(context, rewardedAdState)
                    }
                }
            }
        )
    }

    private fun loadInterstitialAd(
        context: Context,
        interstitialAdState: MutableState<InterstitialAd?>
    ) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            "ca-app-pub-3940256099942544/1033173712", // Test ID
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("AdMob", "🎉 Interstitial ad loaded")
                    interstitialAdState.value = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdMob", "❌ Failed to load interstitial: ${error.message}")
                    interstitialAdState.value = null

                    // 🔁 Retry in 30s using coroutine
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(30_000)
                        loadInterstitialAd(context, interstitialAdState)
                    }
                }
            }
        )
    }
}