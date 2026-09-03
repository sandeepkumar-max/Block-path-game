package com.example

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Manages Google AdMob Rewarded Video Ads for BlockPath.
 *
 * Rules:
 * 1. Mode: Only available when playing vs Computer (AI mode).
 * 2. Condition: When the player runs out of walls (0 walls left).
 * 3. Reward: Watching a rewarded video ad grants exactly +2 extra walls.
 * 4. Limit: Can be claimed at most ONCE per game.
 */
class RewardedAdManager(private val context: Context) {

    companion object {
        private const val TAG = "RewardedAdManager"
        // User's AdMob Rewarded Ad Unit ID:
        const val AD_UNIT_ID = "ca-app-pub-3271133689051975/9187846997"
    }

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    /**
     * Preloads a Rewarded Ad.
     */
    fun loadAd(onLoaded: (() -> Unit)? = null) {
        if (!AppOpenAdManager.IS_ADMOB_ENABLED) {
            return
        }
        if (isLoading || rewardedAd != null) {
            return
        }
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded Ad loaded successfully.")
                    rewardedAd = ad
                    isLoading = false
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded Ad failed to load: ${error.message}")
                    rewardedAd = null
                    isLoading = false
                }
            }
        )
    }

    /**
     * Checks if ad is ready to show.
     */
    fun isAdAvailable(): Boolean {
        return !AppOpenAdManager.IS_ADMOB_ENABLED || rewardedAd != null
    }

    /**
     * Shows the rewarded video ad to the user.
     * When completed, invokes [onRewardEarned].
     */
    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdClosed: (() -> Unit)? = null
    ) {
        // Safe development/emulator fallback: simulate rewarded ad if AdMob is disabled
        if (!AppOpenAdManager.IS_ADMOB_ENABLED) {
            Toast.makeText(activity, "🎬 Ad Complete! +2 Extra Walls Unlocked", Toast.LENGTH_LONG).show()
            onRewardEarned()
            onAdClosed?.invoke()
            return
        }

        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad dismissed.")
                    rewardedAd = null
                    onAdClosed?.invoke()
                    // Preload for future games
                    loadAd()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.e(TAG, "Failed to show rewarded ad: ${error.message}")
                    rewardedAd = null
                    Toast.makeText(activity, "Failed to load video. Please try again.", Toast.LENGTH_SHORT).show()
                    onAdClosed?.invoke()
                    loadAd()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad displayed.")
                }
            }

            ad.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                activity.runOnUiThread {
                    onRewardEarned()
                }
            }
        } else {
            Toast.makeText(activity, "Loading video ad, please tap again in a moment...", Toast.LENGTH_SHORT).show()
            loadAd()
        }
    }
}
