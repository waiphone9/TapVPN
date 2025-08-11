// ✅ VPNHomeScreen.kt — Polished Resurrection Build
@file:OptIn(ExperimentalMaterial3Api::class)

package com.yourname.tapvpn

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.rewarded.RewardedAd

import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.*

import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.unit.sp
import androidx.core.content.edit


@Composable
fun VPNHomeScreen(

    connected: Boolean,
    sessionRemainingSeconds: Int,
    isAdAvailable: Boolean,
    isAdPlaying: Boolean,
    rewardedAd: RewardedAd?,
    onWatchAd: (onReward: () -> Unit) -> Unit, // updated type
    onConnectTapped: () -> Unit,               // 👈 add this line
    onUpdateConnection: (Boolean) -> Unit,
    onUpdateSession: (Int) -> Unit,
    sessionExpired: Boolean
)


{
    var cooldownSeconds by remember { mutableIntStateOf(0) }
    var showSessionExpiredCard by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }


    // Extend Button ⏳ Cooldown Timer Effect
    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) {
            delay(1000)
            cooldownSeconds -= 1
        }
    }

    LaunchedEffect(sessionExpired) {
        if (sessionExpired) {
            showSessionExpiredCard = true
        }
    }

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("tapvpn", Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        val lastTapTime = prefs.getLong("lastExtendTime", 0L)
        val elapsed = System.currentTimeMillis() - lastTapTime
        val remaining = 600_000 - elapsed // 10 minutes in ms

        if (remaining > 0) {
            cooldownSeconds = (remaining / 1000).toInt()
        }
    }

    val minutes = sessionRemainingSeconds / 60
    val seconds = sessionRemainingSeconds % 60

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_tapvpn_logo),
                            contentDescription = "Logo",
                            modifier = Modifier.size(28.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp)) // Space
                        Text("TapVPN", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "App Info"
                        )
                    }
                }
            )
        },

        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

            }
        },


        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally

            ) {
                Spacer(modifier = Modifier.weight(1f)) // Push Content from top

                // 🌟 Central UI Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 8.dp)
                        .align(Alignment.CenterHorizontally)

                ) {
                    Icon(
                        painter = painterResource(
                            id = if (connected) R.drawable.ic_connected else R.drawable.ic_disconnected
                        ),
                        contentDescription = null,
                        tint = if (connected) Color(0xFF4CAF50) else Color.Red,
                        modifier = Modifier
                            .size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (connected) "VPN active — you’re safe!" else "You’re offline. Tap Connect to start.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (connected) Color(0xFF6D4C41) else Color(0xFF757575)
                    )
                    // … your updated Connect button
                    // … your updated Watch Ad button
                }

                var buttonPressed by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(if (buttonPressed) 0.96f else 1f)



                // Connect Button

                Button(
                    onClick = {
                        buttonPressed = true

                        if (!connected) {
                            onConnectTapped()
                        } else {
                            onUpdateConnection(false) // disconnect directly
                        }


                        CoroutineScope(Dispatchers.Main).launch {
                            delay(100)
                            buttonPressed = false
                        }
                    },

                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connected) Color(0xFFFF6B6B) else Color(0xFF0077B6),
                        contentColor = Color.White
                    ),

                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .padding(vertical = 16.dp)
                        .height(50.dp)
                        .width(220.dp),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Text(
                        text = if (connected) "Disconnect" else "Connect",
                        style = MaterialTheme.typography.titleMedium
                    )
                }



                // ⏱️ Session Time
                AnimatedVisibility(
                    visible = connected,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                )  {

                    var rewardButtonPressed by remember { mutableStateOf(false) }
                    val rewardScale by animateFloatAsState(if (rewardButtonPressed) 0.96f else 1f)
                    val isAdButtonEnabled = connected && isAdAvailable && !isAdPlaying

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Timer row stays same, no horizontalArrangement needed
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Timer,
                                    contentDescription = null,
                                    tint = Color(0xFF0077B6),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatTimeVerbose(sessionRemainingSeconds),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF546E7A)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // +30 mins button here (as per your preferred size & style)
                            Button(
                                onClick = {
                                    rewardButtonPressed = true

                                    onWatchAd {
                                        onUpdateSession(sessionRemainingSeconds + 30 * 60)
                                        cooldownSeconds = 600 // ⏳ 10 minutes = 600 seconds

                                        // Saved the last ad time

                                        val prefs = context.getSharedPreferences("tapvpn", Context.MODE_PRIVATE)
                                        prefs.edit {
                                            putLong(
                                                "lastExtendTime",
                                                System.currentTimeMillis()
                                            )
                                        }
                                    }

                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(100)
                                        rewardButtonPressed = false
                                    } },

                                enabled = isAdAvailable && !isAdPlaying && cooldownSeconds == 0,
                                shape = RoundedCornerShape(40),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF8800),
                                    disabledContainerColor = Color.LightGray,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .height(40.dp)
                                    .wrapContentWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Text(
                                    text = "Extend +30 mins",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            // ℹ️ Ad Status Below Extend Button

                            if (!isAdAvailable || isAdPlaying) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isAdPlaying) "Ad is playing..." else "Please wait… loading ad",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            if (cooldownSeconds > 0) {
                                Text(
                                    text = "Wait ${cooldownSeconds / 60}m ${cooldownSeconds % 60}s to extend again",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showSessionExpiredCard) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🔔 Session expired",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFEF6C00)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Please reconnect to continue using the VPN.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF6D4C41)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { showSessionExpiredCard = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFA726),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("OK")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ✅ 📢 bottom banner ads
                AndroidView(
                    factory = {
                        AdView(context).apply {
                            setAdSize(AdSize.BANNER)
                            adUnitId = "ca-app-pub-3940256099942544/6300978111" // Test Ad
                            loadAd(AdRequest.Builder().build())
                        }
                    },
                    modifier = Modifier
                        .wrapContentHeight()
                        .fillMaxWidth()
                    // .padding(bottom = 80.dp) // push up the banner ads from bottom
                )

                Spacer(modifier = Modifier.weight(1f)) // Push Content from bottom

                // Infomation icon on topbar

                if (showInfoDialog) {
                    AlertDialog(
                        onDismissRequest = { showInfoDialog = false },
                        confirmButton = {
                            TextButton(onClick = { showInfoDialog = false }) {
                                Text("Close")
                            }
                        },
                        title = {
                            Text("🔒 About TapVPN", fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column {
                                Text(
                                    text = "TapVPN helps you extend VPN sessions in a simple way. " +
                                            "We do not collect personal data, store logs, or monitor your activity. " +
                                            "Ads are used only to extend session time.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.DarkGray
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "📄 Privacy Policy",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "https://yourdomain.com/privacy-policy",
                                    color = Color(0xFF1E88E5)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "📧 Contact Us",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "support@yourdomain.com",
                                    color = Color(0xFF1E88E5)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text("🛠 Version: 1.0.0")
                            }
                        }
                    )
                }
            }
        }
    )

    var isAdPlaying by remember { mutableStateOf(false) }
}

fun formatTimeVerbose(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return when {
        seconds <= 0 -> "Time Left: 0 secs"
        mins > 0 -> "Time Left: ${mins} min${if (mins > 1) "s" else ""} ${secs} sec${if (secs != 1) "s" else ""}"
        else -> "Time Left: ${secs} sec${if (secs != 1) "s" else ""}"
    }
}