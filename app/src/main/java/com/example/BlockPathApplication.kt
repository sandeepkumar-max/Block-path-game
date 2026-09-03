package com.example

import android.app.Application
import android.util.Log

class BlockPathApplication : Application() {

    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    lateinit var rewardedAdManager: RewardedAdManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Always instantiate ad managers safely
        appOpenAdManager = AppOpenAdManager(this)
        rewardedAdManager = RewardedAdManager(this)

        /* ========================================================================= */
        /* [LOCATION 1: ADMOB INITIALIZATION]                                        */
        /* Emulator crash se bachne ke liye yeh AdMob initialization comment kiya hai*/
        /* Jab aapko ads chalu karne ho, tab bas neeche ke block ko UNCOMMENT kar de */
        /* ========================================================================= */
        /*
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.google.android.gms.ads.MobileAds.initialize(this@BlockPathApplication) { initializationStatus ->
                    Log.d("BlockPathApp", "MobileAds initialized: $initializationStatus")
                }
            } catch (e: Exception) {
                Log.w("BlockPathApp", "Error initializing MobileAds", e)
            }
        }
        appOpenAdManager.loadAd(this)
        rewardedAdManager.loadAd()
        */
        /* ========================================================================= */
    }
}
