package com.pijlpuzzel.game

import android.app.Activity
import android.util.Log
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Alles rond advertenties: toestemming vragen (verplicht in de EU), banner,
 * tussendoor-advertentie en beloningsadvertentie.
 */
class AdManager(private val activity: Activity, private val bannerContainer: FrameLayout) {

    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
    private val started = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var banner: AdView? = null

    val privacyOptionsRequired: Boolean
        get() = consent.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    val rewardedReady: Boolean get() = rewarded != null

    fun start(onConsentResolved: () -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                error?.let { Log.w(TAG, "Toestemmingsformulier: ${it.message}") }
                if (consent.canRequestAds()) startAds()
                onConsentResolved()
            }
        }, { error ->
            Log.w(TAG, "Toestemming ophalen mislukt: ${error.message}")
            onConsentResolved()
        })
        // Toestemming uit een vorige sessie: meteen beginnen.
        if (consent.canRequestAds()) startAds()
    }

    fun showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            error?.let { Log.w(TAG, "Privacy-opties: ${it.message}") }
        }
    }

    private fun startAds() {
        if (!started.compareAndSet(false, true)) return
        MobileAds.initialize(activity) {}
        loadBanner()
        loadInterstitial()
        loadRewarded()
    }

    private fun loadBanner() {
        val metrics = activity.resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        banner = AdView(activity).apply {
            adUnitId = BuildConfig.BANNER_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        }
        bannerContainer.removeAllViews()
        bannerContainer.addView(banner)
        banner?.loadAd(AdRequest.Builder().build())
    }

    private fun loadInterstitial() {
        InterstitialAd.load(activity, BuildConfig.INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    Log.w(TAG, "Interstitial niet geladen: ${error.message}")
                }
            })
    }

    private fun loadRewarded() {
        RewardedAd.load(activity, BuildConfig.REWARDED_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null
                    Log.w(TAG, "Rewarded niet geladen: ${error.message}")
                }
            })
    }

    /** Toont een tussendoor-advertentie als die klaarstaat; [then] loopt altijd daarna. */
    fun showInterstitial(then: () -> Unit) {
        val ad = interstitial ?: return then()
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { loadInterstitial(); then() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { loadInterstitial(); then() }
        }
        ad.show(activity)
    }

    /** Toont een beloningsadvertentie. [onReward] loopt alleen als de speler hem heeft uitgekeken. */
    fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewarded ?: return onUnavailable()
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewarded()
                if (earned) onReward()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadRewarded()
                onUnavailable()
            }
        }
        ad.show(activity) { earned = true }
    }

    fun pause() = banner?.pause()
    fun resume() = banner?.resume()
    fun destroy() = banner?.destroy()

    companion object {
        private const val TAG = "AdManager"
    }
}
