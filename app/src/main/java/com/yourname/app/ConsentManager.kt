package com.yourname.app.consent

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.ads.mediation.admob.AdMobAdapter   // correct import

object ConsentManager {
    private const val TAG = "Consent"
    private var consentInfo: ConsentInformation? = null
    private var _canRequestAds: Boolean = false
    private var nonPersonalized: Boolean = false  // true => use npa=1

    fun requestConsent(activity: Activity, onReady: (Boolean) -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consentInfo = UserMessagingPlatform.getConsentInformation(activity)

        consentInfo?.requestConsentInfoUpdate(
            activity,
            params,
            {
                Log.d(TAG, "Consent info updated. canRequestAds=${consentInfo?.canRequestAds()}")

                if (consentInfo?.isConsentFormAvailable == true) {
                    UserMessagingPlatform.loadConsentForm(
                        activity,
                        { form ->
                            if (consentInfo?.consentStatus != ConsentInformation.ConsentStatus.OBTAINED) {
                                form.show(activity) {
                                    updateFlags()
                                    onReady(_canRequestAds)
                                }
                            } else {
                                updateFlags()
                                onReady(_canRequestAds)
                            }
                        },
                        { formError ->
                            Log.w(TAG, "Consent form load error: ${formError.message}")
                            updateFlags()
                            onReady(_canRequestAds)
                        }
                    )
                } else {
                    updateFlags()
                    onReady(_canRequestAds)
                }
            },
            { requestError ->
                Log.w(TAG, "Consent request error: ${requestError.message}")
                updateFlags()
                onReady(_canRequestAds)
            }
        )
    }

    private fun updateFlags() {
        val info = consentInfo
        _canRequestAds = info?.canRequestAds() == true
        nonPersonalized = info?.consentStatus != ConsentInformation.ConsentStatus.OBTAINED
        Log.d(TAG, "Flags → canRequestAds=$_canRequestAds, nonPersonalized=$nonPersonalized, status=${info?.consentStatus}")
    }

    fun adRequest(context: Context): AdRequest {
        return if (nonPersonalized) {
            val extras = Bundle().apply { putString("npa", "1") }
            AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                .build()
        } else {
            AdRequest.Builder().build()
        }
    }

    fun canRequestAds(): Boolean = _canRequestAds
    fun isNonPersonalized(): Boolean = nonPersonalized
}
