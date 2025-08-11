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
import kotlinx.coroutines.delay
import androidx.core.content.edit
import android.widget.Toast

import com.yourname.tapvpn.TunnelManager


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MobileAds.initialize(this)
        val prefs = getSharedPreferences("tapvpn", MODE_PRIVATE)

        setContent {
            val connectedState = remember { mutableStateOf(false) }
            val sessionRemainingState = remember { mutableIntStateOf(0) }
            val rewardedAdState = remember { mutableStateOf<RewardedAd?>(null) }
            val interstitialAdState = remember { mutableStateOf<InterstitialAd?>(null) }
            val isAdPlaying = remember { mutableStateOf(false) }
            var sessionExpiredFlag by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                loadRewardedAd(this@MainActivity, rewardedAdState)
                loadInterstitialAd(this@MainActivity, interstitialAdState)
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
                        Toast.makeText(this@MainActivity, "VPN Failed: $message", Toast.LENGTH_LONG).show()
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

                    onWatchAd = { onReward ->
                        val ad = rewardedAdState.value
                        if (ad != null) {
                            isAdPlaying.value = true
                            ad.show(this@MainActivity) {
                                rewardedAdState.value = null
                                isAdPlaying.value = false
                                onReward()
                                loadRewardedAd(this@MainActivity, rewardedAdState)
                            }
                        } else {
                            onReward()
                        }
                    },

                    onConnectTapped = {
                        val ad = interstitialAdState.value
                        if (ad != null) {
                            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    interstitialAdState.value = null
                                    loadInterstitialAd(this@MainActivity, interstitialAdState)

                                    TunnelManager.connectFromAsset(
                                        context = this@MainActivity,
                                        fileName = "wg0.conf",
                                        onSuccess = {
                                            connectAndStartSession()
                                            Toast.makeText(this@MainActivity, "VPN Connected ✅", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = {
                                            Toast.makeText(this@MainActivity, "VPN Failed: $it", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }

                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                    interstitialAdState.value = null
                                    loadInterstitialAd(this@MainActivity, interstitialAdState)

                                    TunnelManager.connectFromAsset(
                                        context = this@MainActivity,
                                        fileName = "wg0.conf",
                                        onSuccess = {
                                            connectAndStartSession()
                                            Toast.makeText(this@MainActivity, "VPN Connected ✅", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = {
                                            Toast.makeText(this@MainActivity, "VPN Failed: $it", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            }
                            ad.show(this@MainActivity)
                        } else {
                            TunnelManager.connectFromAsset(
                                context = this@MainActivity,
                                fileName = "wg0.conf",
                                onSuccess = {
                                    connectAndStartSession()
                                    Toast.makeText(this@MainActivity, "VPN Connected ✅", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    Toast.makeText(this@MainActivity, "VPN Failed: $it", Toast.LENGTH_LONG).show()
                                }
                            )
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

                    sessionExpired = sessionExpiredFlag // ✅ new flag passed into screen
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

    private fun loadRewardedAd(context: Context, rewardedAdState: MutableState<RewardedAd?>) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            "ca-app-pub-3940256099942544/5224354917", // ✅ Test ID
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdMob", "🎉 Rewarded ad loaded")
                    rewardedAdState.value = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdMob", "❌ Failed to load rewarded ad: ${error.message}")
                    rewardedAdState.value = null
                }
            }
        )
    }

    private fun loadInterstitialAd(context: Context, interstitialAdState: MutableState<InterstitialAd?>) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            "ca-app-pub-3940256099942544/1033173712", // ✅ Test ID
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("AdMob", "🎉 Interstitial ad loaded")
                    interstitialAdState.value = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdMob", "❌ Failed to load interstitial: ${error.message}")
                    interstitialAdState.value = null
                }
            }
        )
    }
}