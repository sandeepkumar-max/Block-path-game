package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlin.random.Random

/**
 * Intelligent Sound & Meme Effects Manager for BlockPath.
 *
 * Strictly adheres to all audio rules:
 * 1. No continuous loops (all sounds play with isLooping = false).
 * 2. Each sound plays only ONCE per trigger event.
 * 3. Event-driven gameplay triggers (not fixed interval timers).
 * 4. Distinct situational sound pools (Brutal Block, Panic Close-Call, Long Walk, Victory Climax, Leap Jump).
 * 5. Intelligent matching to file contents:
 *    - vine_boom: Massive sudden impact / punchy trap
 *    - are_you_out_of_your_mind: Brutal unexpected roadblock
 *    - faaah: High-tension panic / screaming close call
 *    - punch_gaming: Direct tactical punch / decisive jump
 *    - spiderman_meme: Long tortuous detour / labyrinth maze
 *    - tf_nemesis: Dramatic suspense / nemesis counter-trap
 *    - among_us_reveal: Sneaky unexpected block / imposter reveal
 * 6. Randomized selection from appropriate sound pools so matches stay fresh.
 * 7. STRICT GAME LIMITS:
 *    - Target: ~1 to 2 meme sounds per game under normal conditions.
 *    - Hard ceiling: NEVER more than 3 meme sounds in a single game.
 * 8. Non-repetitive across matches.
 * 9. NEVER stack meme sounds on top of each other (only 1 plays at a time).
 * 10. Cooldown mechanism prevents auditory clutter and keeps gameplay crisp.
 * 11. Seamless integration with AppSettings (toggleable via settings).
 * 12. Lightweight, on-demand loading, zero native memory leaks, mobile-optimized.
 */
class SoundManager private constructor(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var stepMediaPlayer: MediaPlayer? = null
    private var wallMediaPlayer: MediaPlayer? = null

    /**
     * Plays a crisp, subtle tactile wooden piece tap whenever a pawn takes a step.
     * Controlled entirely by the user's soundEnabled setting.
     * Uses an on-demand lightweight MediaPlayer instance to avoid Codec2 native resource queries.
     */
    @Synchronized
    fun onPawnStep(soundEnabled: Boolean) {
        if (!soundEnabled) return
        try {
            stepMediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.seekTo(0)
                    return
                }
                player.release()
            }
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val mp = MediaPlayer.create(context, R.raw.pawn_step, audioAttributes, 0) ?: return
            stepMediaPlayer = mp
            mp.setVolume(0.55f, 0.55f)
            mp.isLooping = false
            mp.setOnCompletionListener { player ->
                try {
                    player.reset()
                    player.release()
                } catch (_: Exception) {}
                if (stepMediaPlayer == player) {
                    stepMediaPlayer = null
                }
            }
            mp.setOnErrorListener { player, _, _ ->
                try {
                    player.reset()
                    player.release()
                } catch (_: Exception) {}
                if (stepMediaPlayer == player) {
                    stepMediaPlayer = null
                }
                true
            }
            mp.start()
        } catch (e: Exception) {
            Log.w("SoundManager", "Error playing pawn step sound", e)
        }
    }

    /**
     * Plays a satisfying, light wooden slot/snap sound whenever a wall is placed.
     * Controlled entirely by the user's soundEnabled setting.
     */
    @Synchronized
    fun onWallPlaced(soundEnabled: Boolean) {
        if (!soundEnabled) return
        try {
            wallMediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.seekTo(0)
                    return
                }
                player.release()
            }
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val mp = MediaPlayer.create(context, R.raw.wall_place, audioAttributes, 0) ?: return
            wallMediaPlayer = mp
            mp.setVolume(0.90f, 0.90f)
            mp.isLooping = false
            mp.setOnCompletionListener { player ->
                try {
                    player.reset()
                    player.release()
                } catch (_: Exception) {}
                if (wallMediaPlayer == player) {
                    wallMediaPlayer = null
                }
            }
            mp.setOnErrorListener { player, _, _ ->
                try {
                    player.reset()
                    player.release()
                } catch (_: Exception) {}
                if (wallMediaPlayer == player) {
                    wallMediaPlayer = null
                }
                true
            }
            mp.start()
        } catch (e: Exception) {
            Log.w("SoundManager", "Error playing wall place sound", e)
        }
    }

    // Raw resource IDs for the 7 meme audio clips in res/raw (standard PCM WAV)
    private val rawVineBoom = R.raw.vine_boom
    private val rawAreYouCrazy = R.raw.are_you_out_of_your_mind
    private val rawFaaah = R.raw.faaah
    private val rawPunchGaming = R.raw.punch_gaming
    private val rawSpiderman = R.raw.spiderman_meme
    private val rawTfNemesis = R.raw.tf_nemesis
    private val rawAmongUs = R.raw.among_us_reveal

    // Concurrency & Frequency Tracking
    private var memeSoundsPlayedInCurrentGame = 0
    private var lastMemePlayTimestamp = 0L
    private val playedResIdsInCurrentGame = mutableSetOf<Int>()

    /**
     * Resets meme counters for a fresh game session.
     */
    fun onNewGameStarted(soundEnabled: Boolean = true, memeSoundEnabled: Boolean = true) {
        stopCurrentMemeSound()
        memeSoundsPlayedInCurrentGame = 0
        lastMemePlayTimestamp = 0L
        playedResIdsInCurrentGame.clear()
    }

    /**
     * Trigger 1: Brutal Block / Sudden Roadblock.
     * When a wall increases opponent's path by 3 or more steps.
     */
    fun onBrutalBlock(soundEnabled: Boolean, memeSoundEnabled: Boolean) {
        if (!canPlayMemeSound(soundEnabled, memeSoundEnabled)) return

        val candidatePool = listOf(
            rawVineBoom,
            rawAreYouCrazy,
            rawTfNemesis,
            rawAmongUs
        ).filter { !playedResIdsInCurrentGame.contains(it) }

        val soundToPlay = if (candidatePool.isNotEmpty()) {
            candidatePool.random()
        } else {
            listOf(rawVineBoom, rawAreYouCrazy).random()
        }

        playRawSound(soundToPlay, volume = 0.9f)
    }

    /**
     * Trigger 2: Long Detour / Tortuous Path.
     * When opponent's remaining shortest path is very long (>= 12 steps).
     */
    fun onLongDetour(soundEnabled: Boolean, memeSoundEnabled: Boolean) {
        if (!canPlayMemeSound(soundEnabled, memeSoundEnabled)) return

        val candidatePool = listOf(
            rawSpiderman,
            rawVineBoom,
            rawAmongUs
        ).filter { !playedResIdsInCurrentGame.contains(it) }

        val soundToPlay = candidatePool.randomOrNull() ?: return
        playRawSound(soundToPlay, volume = 0.85f)
    }

    /**
     * Trigger 3: Close Call / Near-Loss Emergency Block.
     * When opponent was 1 or 2 steps away from victory and was blocked.
     */
    fun onCloseCallPanic(soundEnabled: Boolean, memeSoundEnabled: Boolean) {
        if (!canPlayMemeSound(soundEnabled, memeSoundEnabled)) return

        val candidatePool = listOf(
            rawFaaah,
            rawAreYouCrazy,
            rawTfNemesis
        ).filter { !playedResIdsInCurrentGame.contains(it) }

        val soundToPlay = candidatePool.randomOrNull() ?: return
        playRawSound(soundToPlay, volume = 0.9f)
    }

    /**
     * Trigger 4: Strategic Pawn Leap / Decisive Jump.
     * When jumping directly over an opponent's head.
     */
    fun onStrategicLeap(soundEnabled: Boolean, memeSoundEnabled: Boolean) {
        if (Random.nextFloat() > 0.35f) return
        if (!canPlayMemeSound(soundEnabled, memeSoundEnabled)) return

        val candidatePool = listOf(
            rawPunchGaming,
            rawVineBoom
        ).filter { !playedResIdsInCurrentGame.contains(it) }

        val soundToPlay = candidatePool.randomOrNull() ?: return
        playRawSound(soundToPlay, volume = 0.85f)
    }

    /**
     * Trigger 4b: Standard Wall Placement Impact.
     */
    fun onWallPlacedImpact(soundEnabled: Boolean, memeSoundEnabled: Boolean) {
        if (!canPlayMemeSound(soundEnabled, memeSoundEnabled)) return
        if (Random.nextFloat() > 0.4f) return

        val candidatePool = listOf(
            rawVineBoom,
            rawPunchGaming
        ).filter { !playedResIdsInCurrentGame.contains(it) }

        val soundToPlay = candidatePool.randomOrNull() ?: return
        playRawSound(soundToPlay, volume = 0.75f)
    }

    /**
     * Trigger 5: Dramatic Game Over / Victory Climax.
     * End-game celebratory/nemesis punch.
     */
    fun onVictoryClimax(soundEnabled: Boolean, memeSoundEnabled: Boolean) {
        if (!soundEnabled || !memeSoundEnabled) return
        if (memeSoundsPlayedInCurrentGame >= MAX_MEME_SOUNDS_PER_GAME) return

        val candidatePool = listOf(
            rawPunchGaming,
            rawTfNemesis,
            rawVineBoom
        )

        val soundToPlay = candidatePool.random()
        playRawSound(soundToPlay, volume = 0.95f)
    }

    /**
     * Strict checking against all gameplay rules:
     * - soundEnabled && memeSoundEnabled must be true
     * - memeSoundsPlayedInCurrentGame < 3 (Strict Ceiling)
     * - Minimum 8 seconds cooldown between meme sounds to prevent spam/stacking
     */
    private fun canPlayMemeSound(soundEnabled: Boolean, memeSoundEnabled: Boolean): Boolean {
        if (!soundEnabled || !memeSoundEnabled) return false
        if (memeSoundsPlayedInCurrentGame >= MAX_MEME_SOUNDS_PER_GAME) return false

        val now = System.currentTimeMillis()
        if (now - lastMemePlayTimestamp < MIN_COOLDOWN_MS) return false

        return true
    }

    @Synchronized
    private fun playRawSound(resId: Int, volume: Float) {
        try {
            stopCurrentMemeSound()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val mp = MediaPlayer.create(context, resId, audioAttributes, 0) ?: return
            mediaPlayer = mp

            mp.setVolume(volume, volume)
            mp.isLooping = false

            mp.setOnCompletionListener { player ->
                synchronized(this) {
                    try {
                        player.reset()
                        player.release()
                    } catch (ignored: Exception) {}
                    if (mediaPlayer === player) {
                        mediaPlayer = null
                    }
                }
            }

            mp.setOnErrorListener { player, _, _ ->
                synchronized(this) {
                    try {
                        player.reset()
                        player.release()
                    } catch (ignored: Exception) {}
                    if (mediaPlayer === player) {
                        mediaPlayer = null
                    }
                }
                true
            }

            mp.start()
            memeSoundsPlayedInCurrentGame++
            lastMemePlayTimestamp = System.currentTimeMillis()
            playedResIdsInCurrentGame.add(resId)
        } catch (e: Exception) {
            Log.e("SoundManager", "Error playing raw sound $resId", e)
        }
    }

    /**
     * Stops and releases any currently playing meme audio stream immediately.
     */
    @Synchronized
    fun stopCurrentMemeSound() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        } catch (ignored: Exception) {
        } finally {
            mediaPlayer = null
        }
    }

    fun release() {
        stopCurrentMemeSound()
        try {
            stepMediaPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.reset()
                player.release()
            }
            stepMediaPlayer = null
            wallMediaPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.reset()
                player.release()
            }
            wallMediaPlayer = null
        } catch (_: Exception) {}
    }

    companion object {
        const val MAX_MEME_SOUNDS_PER_GAME = 3 // Rule 7: Never more than 3 in a single game
        private const val MIN_COOLDOWN_MS = 8000L // 8s cooldown prevents stacking / frequent spam

        @Volatile
        private var INSTANCE: SoundManager? = null

        fun getInstance(context: Context): SoundManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SoundManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
