package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Google AdMob App Open Ads for BlockPath.
 *
 * Rules:
 * 1. Cold Start: When the app launches, it stays on the Splash Screen.
 *    If an ad is loaded (or loads within 3.5s), it is displayed BEFORE entering the game.
 *    Once the user closes the ad (or timeout occurs), the game opens smoothly.
 * 2. In-Game Immunity: While the user is in the app, NO ad will ever randomly pop up or interrupt gameplay!
 * 3. App Resume: When the user minimizes or closes the app and opens it again,
 *    an App Open ad is shown immediately on resume.
 */
class AppOpenAdManager(private val application: BlockPathApplication) :
    Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AppOpenAdManager"
        // User's AdMob App Open Ad Unit ID:
        const val AD_UNIT_ID = "ca-app-pub-3271133689051975/4432349347"

        /* ========================================================================= */
        /* [LOCATION 3: ADMOB MASTER TOGGLE]                                         */
        /* Set IS_ADMOB_ENABLED = true to activate AdMob ads for production!         */
        /* Currently set to false to prevent emulator crashes.                       */
        /* ========================================================================= */
        const val IS_ADMOB_ENABLED = false
    }

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    var isShowingAd = false
    private var loadTime: Long = 0
    private var currentActivity: Activity? = null

    // State flags to prevent ads from interrupting gameplay
    var isSplashActive = true
    var hasShownFirstLaunchAd = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var splashAdCallback: (() -> Unit)? = null

    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Preloads an App Open Ad.
     */
    fun loadAd(context: Context, onLoaded: (() -> Unit)? = null) {
        if (!IS_ADMOB_ENABLED) {
            return
        }
        if (isLoadingAd || isAdAvailable()) {
            return
        }

        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App Open Ad loaded successfully.")
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    onLoaded?.invoke()

                    // If splash screen is currently waiting for the ad to load, show it now!
                    if (isSplashActive && !hasShownFirstLaunchAd) {
                        currentActivity?.let { activity ->
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                val callback = splashAdCallback
                                splashAdCallback = null
                                showAd(activity, onDismiss = callback)
                            }
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "App Open Ad failed to load: ${loadAdError.message}")
                    isLoadingAd = false
                    // If splash is waiting and load failed, proceed to home
                    if (isSplashActive && !hasShownFirstLaunchAd) {
                        val callback = splashAdCallback
                        splashAdCallback = null
                        callback?.invoke()
                    }
                }
            }
        )
    }

    /**
     * Checks if a cached ad is available and not expired (less than 4 hours old).
     */
    fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference: Long = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    /**
     * Called by SplashScreen on cold start.
     * Waits up to [timeoutMillis] for an ad to be ready and shows it before moving to HomeScreen.
     * If timeout expires or ad fails, smoothly proceeds to HomeScreen and will NEVER interrupt the user later.
     */
    fun showSplashAd(
        activity: Activity,
        timeoutMillis: Long = 3200L,
        onProceedToGame: () -> Unit
    ) {
        if (!IS_ADMOB_ENABLED) {
            isSplashActive = false
            hasShownFirstLaunchAd = true
            onProceedToGame()
            return
        }
        isSplashActive = true
        currentActivity = activity

        val completed = AtomicBoolean(false)
        fun finishSplashOnce() {
            if (completed.compareAndSet(false, true)) {
                isSplashActive = false
                hasShownFirstLaunchAd = true
                splashAdCallback = null
                activity.runOnUiThread {
                    onProceedToGame()
                }
            }
        }

        // 1. If ad is ALREADY loaded, show it right now on splash!
        if (isAdAvailable()) {
            Log.d(TAG, "Ad available immediately on splash. Showing now.")
            showAd(activity, onDismiss = { finishSplashOnce() })
            return
        }

        // 2. Otherwise, wait up to timeoutMillis for ad to load
        splashAdCallback = {
            finishSplashOnce()
        }

        // Safety timeout: if ad doesn't load within timeoutMillis, enter game smoothly
        mainHandler.postDelayed({
            if (!completed.get() && !isShowingAd) {
                Log.d(TAG, "Splash ad timeout ($timeoutMillis ms). Entering game without showing ad.")
                finishSplashOnce()
            }
        }, timeoutMillis)

        // Trigger load if not already loading
        loadAd(activity)
    }

    /**
     * Shows the App Open ad with callback when dismissed or failed.
     */
    private fun showAd(activity: Activity, onDismiss: (() -> Unit)?) {
        if (isShowingAd) {
            Log.d(TAG, "Ad is already displaying.")
            return
        }

        if (!isAdAvailable()) {
            onDismiss?.invoke()
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App Open Ad dismissed.")
                appOpenAd = null
                isShowingAd = false
                hasShownFirstLaunchAd = true
                onDismiss?.invoke()
                // Preload next ad in background for future app resumes
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "App Open Ad failed to show: ${adError.message}")
                appOpenAd = null
                isShowingAd = false
                hasShownFirstLaunchAd = true
                onDismiss?.invoke()
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open Ad showing full screen.")
                isShowingAd = true
                hasShownFirstLaunchAd = true
            }
        }

        isShowingAd = true
        appOpenAd?.show(activity)
    }

    // Called automatically when app comes back to foreground from background
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (!IS_ADMOB_ENABLED) {
            return
        }
        // Only show resume ad if splash is already finished and app was minimized
        if (!isSplashActive && hasShownFirstLaunchAd && !isShowingAd) {
            currentActivity?.let { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    if (isAdAvailable()) {
                        Log.d(TAG, "App resumed from background, displaying preloaded App Open Ad.")
                        showAd(activity, onDismiss = null)
                    } else {
                        // Preload for next time
                        loadAd(activity)
                    }
                }
            }
        }
    }

    // Activity Lifecycle Callbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
}
