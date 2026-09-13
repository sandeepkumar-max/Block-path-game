package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import kotlin.random.Random

data class VoiceChatState(
    val isMicConnected: Boolean = false,
    val isMicMuted: Boolean = false,
    val isSpeakerMuted: Boolean = false,
    val isRemoteVoiceActive: Boolean = false,
    val errorMessage: String? = null
)

sealed class MatchmakingState {
    object Idle : MatchmakingState()
    data class Searching(val elapsedSeconds: Int, val statusText: String) : MatchmakingState()
    data class HostingRoom(val roomCode: String, val statusText: String) : MatchmakingState()
    data class JoiningRoom(val roomCode: String, val statusText: String) : MatchmakingState()
    data class Matched(
        val opponentName: String,
        val opponentAvatar: String = "♟",
        val ping: Int,
        val isRealPeer: Boolean,
        val roomCode: String? = null,
        val isHost: Boolean = true,
        val timerEnabled: Boolean = false
    ) : MatchmakingState()
    data class Error(val message: String) : MatchmakingState()
}

/**
 * Manages WebRTC Peer-to-Peer connections using PeerJS client in a background WebView.
 * Provides direct player connections without any server-side backend.
 * Supports:
 *  1. Quick Matchmaking (Random Duel) with decentralized Host/Guest pairing
 *  2. Custom Room Codes (Host / Join Friend) with synchronized room timer settings
 *  3. Real-time Player Profile & Role handshake (Host=P1, Guest=P2)
 *  4. Live Voice Chat (P2P Audio Stream via PeerJS MediaConnection) with hardware AEC & Mute/Speaker toggles
 */
class PeerJsWebRtcManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var matchmakingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _matchmakingState = MutableStateFlow<MatchmakingState>(MatchmakingState.Idle)
    val matchmakingState: StateFlow<MatchmakingState> = _matchmakingState.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceChatState())
    val voiceState: StateFlow<VoiceChatState> = _voiceState.asStateFlow()

    private var onRemoteActionCallback: ((String) -> Unit)? = null
    var onOpponentProfileUpdated: ((String, String) -> Unit)? = null
    var onPingUpdated: ((Int) -> Unit)? = null
    var onConnectionNotice: ((String) -> Unit)? = null
    var onReconnected: (() -> Unit)? = null

    private var isConnectedToRealPeer = false
    private var isTwoWayHandshakeComplete = false
    private var connectedTimestamp: Long = 0L
    private var activeOpponentName = "Opponent"
    private var activeOpponentAvatar = "♟"
    private var myProfile: UserProfile = UserProfile()
    private var currentTimerEnabled = false

    private var heartbeatJob: Job? = null
    private var disconnectGraceJob: Job? = null
    private var lastHeartbeatAckTime: Long = 0L
    private var isGracePeriodActive = false

    private val OPPONENT_NAMES = listOf(
        "Aarav_Sharma",
        "Rohan_Verma",
        "Priya_Singh07",
        "Rahul_Kumar99",
        "Ananya_Patel",
        "Aditya_Raj",
        "Vikram_Verma",
        "Neha_Gupta21",
        "Kunal_Mehta",
        "Sneha_Roy",
        "Amit_Yadav98",
        "Pooja_Mishra",
        "Dev_Choudhary",
        "Ishaan_V",
        "Kavya_Nair",
        "Manish_Tiwari",
        "Ritik_Joshi",
        "Siddharth_R",
        "Divya_Reddy",
        "Deepak_Saini",
        "Akash_Pandey",
        "Varun_Malhotra",
        "Ayush_Mishra",
        "Shreya_Ghosh",
        "Harsh_Vardhan"
    )

    private val AI_AVATARS = listOf("🦁", "⚡", "🤖", "⚔️", "👑", "🎯", "🚀", "🐺")

    private var isPageReady = false

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(onReady: () -> Unit) {
        if (webView != null && isPageReady) {
            onReady()
            return
        }
        mainHandler.post {
            try {
                if (webView != null && isPageReady) {
                    onReady()
                    return@post
                }
                isPageReady = false
                webView = WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            try {
                                request.grant(request.resources)
                                Log.d("PeerJsWebRtc", "Granted WebView media permissions: ${request.resources.joinToString()}")
                            } catch (e: Exception) {
                                Log.e("PeerJsWebRtc", "Error granting WebView media permissions directly", e)
                                try {
                                    mainHandler.post { request.grant(request.resources) }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d("PeerJsWebRtc", "PeerJS HTML page loaded successfully")
                            isPageReady = true
                            onReady()
                        }
                    }
                    addJavascriptInterface(WebRtcJsBridge(), "AndroidWebRTC")
                    onResume()
                    resumeTimers()
                    loadDataWithBaseURL("https://0.peerjs.com", PEERJS_HTML, "text/html", "UTF-8", null)
                }
            } catch (e: Exception) {
                Log.e("PeerJsWebRtc", "Error initializing WebView for PeerJS", e)
            }
        }
    }

    /**
     * Start quick matchmaking: attempts PeerJS WebRTC discovery.
     * If no peer is found within 10 seconds, seamlessly fallback to realistic AI player.
     */
    fun startMatchmaking(profile: UserProfile, timerEnabled: Boolean = false, onActionReceived: (String) -> Unit) {
        this.myProfile = profile
        this.currentTimerEnabled = timerEnabled
        this.onRemoteActionCallback = onActionReceived
        isConnectedToRealPeer = false
        matchmakingJob?.cancel()

        _matchmakingState.value = MatchmakingState.Searching(0, "Searching for an opponent...")

        ensureWebView {
            mainHandler.post {
                val lobbySlot = (1..3).random()
                val profileJson = JSONObject().apply {
                    put("name", profile.name)
                    put("avatar", profile.avatar)
                    put("winRate", profile.winRate)
                }.toString()
                val escapedProfile = JSONObject.quote(profileJson)
                webView?.evaluateJavascript("javascript:initQuickMatch($lobbySlot, $escapedProfile, $timerEnabled);", null)
            }
        }

        matchmakingJob = scope.launch {
            for (sec in 1..10) {
                delay(1000)
                if (_matchmakingState.value is MatchmakingState.Matched) {
                    return@launch
                }
                val status = when (sec) {
                    in 1..3 -> "Finding nearby players..."
                    in 4..7 -> "Connecting to opponent..."
                    else -> "Preparing the board..."
                }
                _matchmakingState.value = MatchmakingState.Searching(sec, status)
            }

            if (_matchmakingState.value !is MatchmakingState.Matched) {
                val fakeOpponent = OPPONENT_NAMES.random()
                val fakeAvatar = AI_AVATARS.random()
                val fakePing = Random.nextInt(28, 54)
                activeOpponentName = fakeOpponent
                activeOpponentAvatar = fakeAvatar
                isConnectedToRealPeer = false

                Log.d("PeerJsWebRtc", "No peer found within 10s. Seamlessly switching to AI: $fakeOpponent")
                _matchmakingState.value = MatchmakingState.Matched(
                    opponentName = fakeOpponent,
                    opponentAvatar = fakeAvatar,
                    ping = fakePing,
                    isRealPeer = false,
                    isHost = true,
                    timerEnabled = timerEnabled
                )
            }
        }
    }

    /**
     * Create a private room with a 4-digit room code for playing with a friend.
     */
    fun createCustomRoom(roomCode: String, profile: UserProfile, timerEnabled: Boolean = false, onActionReceived: (String) -> Unit) {
        this.myProfile = profile
        this.currentTimerEnabled = timerEnabled
        this.onRemoteActionCallback = onActionReceived
        isConnectedToRealPeer = false
        matchmakingJob?.cancel()

        _matchmakingState.value = MatchmakingState.HostingRoom(roomCode, "Opening room $roomCode...")

        ensureWebView {
            mainHandler.post {
                val profileJson = JSONObject().apply {
                    put("name", profile.name)
                    put("avatar", profile.avatar)
                    put("winRate", profile.winRate)
                }.toString()
                val escapedProfile = JSONObject.quote(profileJson)
                webView?.evaluateJavascript("javascript:createRoom('$roomCode', $escapedProfile, $timerEnabled);", null)
            }
        }
    }

    /**
     * Join an existing private room created by a friend.
     */
    fun joinCustomRoom(roomCode: String, profile: UserProfile, onActionReceived: (String) -> Unit) {
        this.myProfile = profile
        this.currentTimerEnabled = false
        this.onRemoteActionCallback = onActionReceived
        isConnectedToRealPeer = false
        matchmakingJob?.cancel()

        _matchmakingState.value = MatchmakingState.JoiningRoom(roomCode, "Connecting to room $roomCode...")

        ensureWebView {
            mainHandler.post {
                val profileJson = JSONObject().apply {
                    put("name", profile.name)
                    put("avatar", profile.avatar)
                    put("winRate", profile.winRate)
                }.toString()
                val escapedProfile = JSONObject.quote(profileJson)
                webView?.evaluateJavascript("javascript:joinRoom('$roomCode', $escapedProfile);", null)
            }
        }

        // Timeout check if room code is invalid
        matchmakingJob = scope.launch {
            delay(14000)
            if (_matchmakingState.value is MatchmakingState.JoiningRoom) {
                _matchmakingState.value = MatchmakingState.Error("Could not reach room $roomCode. Please verify the code.")
            }
        }
    }

    fun disconnectAndResetAll(notifyOpponent: Boolean = true) {
        matchmakingJob?.cancel()
        matchmakingJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        disconnectGraceJob?.cancel()
        disconnectGraceJob = null
        isGracePeriodActive = false

        stopVoiceChat()

        _matchmakingState.value = MatchmakingState.Idle
        val wasConnected = isConnectedToRealPeer && isTwoWayHandshakeComplete
        isConnectedToRealPeer = false
        isTwoWayHandshakeComplete = false
        activeOpponentName = "Opponent"
        activeOpponentAvatar = "♟"
        onRemoteActionCallback = null

        mainHandler.post {
            try {
                if (notifyOpponent && wasConnected) {
                    val quitMsg = JSONObject().apply { 
                        put("type", "OPPONENT_QUIT")
                        put("intentional", true)
                    }.toString()
                    val escaped = JSONObject.quote(quitMsg)
                    webView?.evaluateJavascript("javascript:sendGameAction($escaped);", null)
                }
                webView?.evaluateJavascript("javascript:disconnect($notifyOpponent);", null)
                mainHandler.postDelayed({
                    try {
                        isPageReady = false
                        webView?.stopLoading()
                        webView?.destroy()
                        webView = null
                        Log.d("PeerJsWebRtc", "WebView destroyed cleanly. P2P disconnected completely.")
                    } catch (e: Exception) {
                        Log.e("PeerJsWebRtc", "Error tearing down WebView", e)
                    }
                }, 180)
            } catch (e: Exception) {
                Log.e("PeerJsWebRtc", "Error in disconnectAndResetAll", e)
            }
        }
    }

    fun cancelMatchmaking() {
        disconnectAndResetAll(notifyOpponent = false)
    }

    fun startVoiceChat() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = !_voiceState.value.isSpeakerMuted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager?.availableCommunicationDevices ?: emptyList()
                val headset = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                val targetDevice = if (!_voiceState.value.isSpeakerMuted) {
                    headset ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                } else null
                if (targetDevice != null) {
                    audioManager?.setCommunicationDevice(targetDevice)
                }
            }
        } catch (e: Exception) {
            Log.e("PeerJsWebRtc", "Error setting audio manager mode for voice", e)
        }
        ensureWebView {
            mainHandler.post {
                try {
                    webView?.evaluateJavascript("javascript:startVoiceChat();", null)
                } catch (e: Exception) {
                    Log.e("PeerJsWebRtc", "Error invoking startVoiceChat", e)
                }
            }
        }
    }

    fun setMicMuted(muted: Boolean) {
        _voiceState.update { it.copy(isMicMuted = muted) }
        mainHandler.post {
            try {
                webView?.evaluateJavascript("javascript:setMicMuted($muted);", null)
            } catch (e: Exception) {
                Log.e("PeerJsWebRtc", "Error invoking setMicMuted", e)
            }
        }
    }

    fun setSpeakerMuted(muted: Boolean) {
        _voiceState.update { it.copy(isSpeakerMuted = muted) }
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.isSpeakerphoneOn = !muted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!muted) {
                    val devices = audioManager?.availableCommunicationDevices ?: emptyList()
                    val headset = devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                    val targetDevice = headset ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (targetDevice != null) {
                        audioManager?.setCommunicationDevice(targetDevice)
                    }
                } else {
                    audioManager?.clearCommunicationDevice()
                }
            }
        } catch (e: Exception) {
            Log.e("PeerJsWebRtc", "Error configuring speakerphone state", e)
        }
        mainHandler.post {
            try {
                webView?.evaluateJavascript("javascript:setSpeakerMuted($muted);", null)
            } catch (e: Exception) {
                Log.e("PeerJsWebRtc", "Error invoking setSpeakerMuted", e)
            }
        }
    }

    fun stopVoiceChat() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager?.clearCommunicationDevice()
            }
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e("PeerJsWebRtc", "Error resetting audio mode", e)
        }
        _voiceState.value = VoiceChatState()
        mainHandler.post {
            try {
                webView?.evaluateJavascript("javascript:stopVoiceChat();", null)
            } catch (e: Exception) {
                Log.e("PeerJsWebRtc", "Error invoking stopVoiceChat", e)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        disconnectGraceJob?.cancel()
        isGracePeriodActive = false
        lastHeartbeatAckTime = System.currentTimeMillis()

        heartbeatJob = scope.launch {
            while (isActive && isConnectedToRealPeer) {
                delay(2000)
                if (isConnectedToRealPeer) {
                    mainHandler.post {
                        try {
                            webView?.evaluateJavascript("javascript:sendHeartbeat();", null)
                        } catch (e: Exception) {
                            Log.e("PeerJsWebRtc", "Error executing heartbeat", e)
                        }
                    }

                    // If no heartbeats acknowledged for over 10 seconds, start reconnection grace period
                    val silentMs = System.currentTimeMillis() - lastHeartbeatAckTime
                    if (silentMs > 10000 && !isGracePeriodActive && isTwoWayHandshakeComplete) {
                        triggerDisconnectionGracePeriod("⚠️ Connection interrupted. Reconnecting...")
                    }
                }
            }
        }
    }

    private fun triggerDisconnectionGracePeriod(reason: String) {
        if (!isConnectedToRealPeer || !isTwoWayHandshakeComplete || isGracePeriodActive) return
        isGracePeriodActive = true
        Log.w("PeerJsWebRtc", "Connection grace period started: $reason")
        onConnectionNotice?.invoke(reason)

        disconnectGraceJob?.cancel()
        disconnectGraceJob = scope.launch {
            // 12-second grace countdown for transient network drop / cellular tower handoff / Wi-Fi blip
            for (sec in 12 downTo 1) {
                delay(1000)
                if (!isGracePeriodActive || !isConnectedToRealPeer) {
                    return@launch
                }
                onConnectionNotice?.invoke("⚠️ Reconnecting to opponent (${sec}s)...")
            }

            if (isGracePeriodActive && isConnectedToRealPeer) {
                Log.e("PeerJsWebRtc", "Reconnection grace period expired. Declaring connection lost.")
                isConnectedToRealPeer = false
                isGracePeriodActive = false
                heartbeatJob?.cancel()
                val quitPayload = JSONObject().apply {
                    put("type", "OPPONENT_QUIT")
                    put("reason", "TIMEOUT")
                }.toString()
                onRemoteActionCallback?.invoke(quitPayload)
            }
        }
    }

    fun notifyActionReceived() {
        lastHeartbeatAckTime = System.currentTimeMillis()
        if (isGracePeriodActive) {
            isGracePeriodActive = false
            disconnectGraceJob?.cancel()
            Log.d("PeerJsWebRtc", "Connection verified alive - grace period cancelled")
            onReconnected?.invoke()
        }
    }

    fun sendGameAction(actionJson: String) {
        if (!isConnectedToRealPeer) return
        mainHandler.post {
            try {
                val escaped = JSONObject.quote(actionJson)
                webView?.evaluateJavascript("javascript:sendGameAction($escaped);", null)
            } catch (e: Exception) {
                Log.e("PeerJsWebRtc", "Failed to send action over WebRTC", e)
            }
        }
    }

    inner class WebRtcJsBridge {
        @JavascriptInterface
        fun onPeerReady(id: String) {
            Log.d("PeerJsWebRtc", "Peer ready with ID: $id")
        }

        @JavascriptInterface
        fun onRoomReady(roomCode: String) {
            Log.d("PeerJsWebRtc", "Room ready: $roomCode")
            scope.launch(Dispatchers.Main) {
                _matchmakingState.value = MatchmakingState.HostingRoom(
                    roomCode = roomCode,
                    statusText = "Waiting for friend to join room $roomCode..."
                )
            }
        }

        @JavascriptInterface
        fun onMatched(opponentPeerId: String, isHost: Boolean, timerEnabled: Boolean) {
            Log.d("PeerJsWebRtc", "WebRTC Direct Player Connected! Peer: $opponentPeerId, isHost: $isHost, timer: $timerEnabled")
            scope.launch(Dispatchers.Main) {
                matchmakingJob?.cancel()
                isConnectedToRealPeer = true
                connectedTimestamp = System.currentTimeMillis()
                lastHeartbeatAckTime = System.currentTimeMillis()
                startHeartbeat()

                val opponentName = if (activeOpponentName != "Opponent") activeOpponentName else OPPONENT_NAMES.random()
                val realPing = Random.nextInt(20, 36)
                val curState = _matchmakingState.value
                if (curState !is MatchmakingState.Matched) {
                    _matchmakingState.value = MatchmakingState.Matched(
                        opponentName = opponentName,
                        opponentAvatar = activeOpponentAvatar,
                        ping = realPing,
                        isRealPeer = true,
                        isHost = isHost,
                        timerEnabled = timerEnabled
                    )
                }
            }
        }

        @JavascriptInterface
        fun onRemoteProfileReceived(name: String, avatar: String, winRate: Int, remoteIsHost: Boolean, timerEnabled: Boolean) {
            Log.d("PeerJsWebRtc", "Remote profile received: $name, avatar: $avatar, remoteIsHost: $remoteIsHost, timer: $timerEnabled")
            scope.launch(Dispatchers.Main) {
                isTwoWayHandshakeComplete = true
                activeOpponentName = name
                activeOpponentAvatar = avatar
                onOpponentProfileUpdated?.invoke(name, avatar)
                notifyActionReceived()

                val curState = _matchmakingState.value
                val myHostRole = !remoteIsHost
                if (curState !is MatchmakingState.Matched) {
                    _matchmakingState.value = MatchmakingState.Matched(
                        opponentName = name,
                        opponentAvatar = avatar,
                        ping = 30,
                        isRealPeer = true,
                        isHost = myHostRole,
                        timerEnabled = timerEnabled
                    )
                }
            }
        }

        @JavascriptInterface
        fun onHeartbeatAck(rtt: Int) {
            scope.launch(Dispatchers.Main) {
                lastHeartbeatAckTime = System.currentTimeMillis()
                if (isGracePeriodActive) {
                    isGracePeriodActive = false
                    disconnectGraceJob?.cancel()
                    onReconnected?.invoke()
                }
                onPingUpdated?.invoke(rtt)
            }
        }

        @JavascriptInterface
        fun onIceState(state: String) {
            Log.d("PeerJsWebRtc", "WebRTC ICE state changed: $state")
            scope.launch(Dispatchers.Main) {
                if (state == "failed") {
                    if (isConnectedToRealPeer && isTwoWayHandshakeComplete && !isGracePeriodActive) {
                        triggerDisconnectionGracePeriod("⚠️ Network reconnecting...")
                    }
                } else if (state == "connected" || state == "completed") {
                    notifyActionReceived()
                }
            }
        }

        @JavascriptInterface
        fun onConnClosed() {
            Log.d("PeerJsWebRtc", "WebRTC peer connection closed by peer or network")
            scope.launch(Dispatchers.Main) {
                if (isConnectedToRealPeer && isTwoWayHandshakeComplete && !isGracePeriodActive) {
                    triggerDisconnectionGracePeriod("⚠️ Connection lost. Waiting for opponent...")
                }
            }
        }

        @JavascriptInterface
        fun onDataReceived(data: String) {
            Log.d("PeerJsWebRtc", "WebRTC data received: $data")
            scope.launch(Dispatchers.Main) {
                notifyActionReceived()
                onRemoteActionCallback?.invoke(data)
            }
        }

        @JavascriptInterface
        fun onVoiceStateChanged(hasStream: Boolean, isMuted: Boolean) {
            Log.d("PeerJsWebRtc", "Local voice state: hasStream=$hasStream, isMuted=$isMuted")
            scope.launch(Dispatchers.Main) {
                _voiceState.update {
                    it.copy(
                        isMicConnected = hasStream,
                        isMicMuted = isMuted,
                        errorMessage = null
                    )
                }
            }
        }

        @JavascriptInterface
        fun onRemoteVoiceState(isActive: Boolean) {
            Log.d("PeerJsWebRtc", "Remote voice state: isActive=$isActive")
            scope.launch(Dispatchers.Main) {
                _voiceState.update {
                    it.copy(isRemoteVoiceActive = isActive)
                }
            }
        }

        @JavascriptInterface
        fun onSpeakerStateChanged(isMuted: Boolean) {
            Log.d("PeerJsWebRtc", "Speaker muted state: $isMuted")
            scope.launch(Dispatchers.Main) {
                _voiceState.update {
                    it.copy(isSpeakerMuted = isMuted)
                }
            }
        }

        @JavascriptInterface
        fun onVoiceError(error: String) {
            Log.e("PeerJsWebRtc", "Voice error from JS: $error")
            scope.launch(Dispatchers.Main) {
                _voiceState.update {
                    it.copy(errorMessage = error)
                }
            }
        }

        @JavascriptInterface
        fun onPeerError(error: String) {
            Log.w("PeerJsWebRtc", "Network notice: $error")
            scope.launch(Dispatchers.Main) {
                val state = _matchmakingState.value
                if (state is MatchmakingState.JoiningRoom) {
                    _matchmakingState.value = MatchmakingState.Error("Room not found or friend is not online. Please check the code.")
                }
            }
        }

        @JavascriptInterface
        fun onDisconnected() {
            Log.d("PeerJsWebRtc", "WebRTC peer disconnected")
            scope.launch(Dispatchers.Main) {
                if (isConnectedToRealPeer && isTwoWayHandshakeComplete && !isGracePeriodActive) {
                    triggerDisconnectionGracePeriod("⚠️ Connection lost. Waiting for opponent...")
                }
            }
        }
    }

    fun cleanup() {
        disconnectAndResetAll(notifyOpponent = false)
    }

    companion object {
        private const val PEERJS_HTML = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <script src="https://unpkg.com/peerjs@1.5.4/dist/peerjs.min.js"></script>
</head>
<body>
<script>
    var peer = null;
    var activeConn = null;
    var myProfile = { name: "Player", avatar: "♟", winRate: 50 };
    var currentTimerEnabled = false;

    // Live Voice Chat Variables
    var localVoiceStream = null;
    var activeVoiceCall = null;
    var isMicMuted = false;
    var isSpeakerMuted = false;
    var isVoiceRequested = false;
    var remoteAudioElem = null;
    var webAudioCtx = null;
    var webAudioSource = null;
    var webAudioGain = null;

    var rtcIceServers = [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' },
        { urls: 'stun:stun2.l.google.com:19302' },
        { urls: 'stun:stun3.l.google.com:19302' },
        { urls: 'stun:stun4.l.google.com:19302' }
    ];

    function createPeerConfig() {
        return {
            debug: 1,
            pingInterval: 5000,
            config: {
                iceServers: rtcIceServers
            }
        };
    }

    // Opus SDP Optimization: Discontinuous Transmission (DTX) suppresses room noise when silent,
    // In-band Forward Error Correction (FEC) prevents robotic packet loss artifacts,
    // and mono speech encoding (32 kbps) eliminates comb-filtering.
    function optimizeOpusSdp(sdp) {
        if (!sdp) return sdp;
        try {
            var lines = sdp.split('\r\n');
            var opusPt = null;
            for (var i = 0; i < lines.length; i++) {
                var m = lines[i].match(/^a=rtpmap:(\d+) opus\/48000/i);
                if (m) {
                    opusPt = m[1];
                    break;
                }
            }
            if (!opusPt) return sdp;

            var fmtpFound = false;
            for (var j = 0; j < lines.length; j++) {
                if (lines[j].indexOf('a=fmtp:' + opusPt) === 0) {
                    fmtpFound = true;
                    var currentParams = lines[j];
                    if (currentParams.indexOf('usedtx=') === -1) currentParams += ';usedtx=1';
                    if (currentParams.indexOf('useinbandfec=') === -1) currentParams += ';useinbandfec=1';
                    if (currentParams.indexOf('stereo=') === -1) currentParams += ';stereo=0;sprop-stereo=0';
                    if (currentParams.indexOf('maxaveragebitrate=') === -1) currentParams += ';maxaveragebitrate=32000';
                    lines[j] = currentParams;
                    break;
                }
            }
            if (!fmtpFound) {
                for (var k = 0; k < lines.length; k++) {
                    if (lines[k].indexOf('a=rtpmap:' + opusPt) === 0) {
                        lines.splice(k + 1, 0, 'a=fmtp:' + opusPt + ' minptime=10;useinbandfec=1;usedtx=1;stereo=0;sprop-stereo=0;maxaveragebitrate=32000');
                        break;
                    }
                }
            }
            return lines.join('\r\n');
        } catch(e) {
            return sdp;
        }
    }

    function ensureRemoteAudioElement() {
        if (!remoteAudioElem) {
            remoteAudioElem = document.getElementById("remoteVoiceAudio");
            if (!remoteAudioElem) {
                remoteAudioElem = document.createElement("audio");
                remoteAudioElem.id = "remoteVoiceAudio";
                remoteAudioElem.autoplay = true;
                remoteAudioElem.playsinline = true;
                remoteAudioElem.volume = 1.0;
                // Muted so audio element doesn't collide with WebAudio DSP filter output
                remoteAudioElem.muted = true;
                document.body.appendChild(remoteAudioElem);
            }
        }
        return remoteAudioElem;
    }

    function setupWebAudioPipeline(stream) {
        try {
            var AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (!AudioContextClass) {
                if (remoteAudioElem) remoteAudioElem.muted = isSpeakerMuted;
                return;
            }
            if (!webAudioCtx || webAudioCtx.state === 'closed') {
                webAudioCtx = new AudioContextClass();
            }
            if (webAudioCtx.state === 'suspended') {
                webAudioCtx.resume();
            }
            if (stream) {
                if (webAudioSource) {
                    try { webAudioSource.disconnect(); } catch(e){}
                }
                webAudioSource = webAudioCtx.createMediaStreamSource(stream);

                // 1. High-Pass Filter (85 Hz): Cuts AC hum, fan rumble, phone handling noise
                var highpass = webAudioCtx.createBiquadFilter();
                highpass.type = "highpass";
                highpass.frequency.value = 85;
                highpass.Q.value = 0.707;

                // 2. Low-Pass Filter (7200 Hz): Eliminates high-frequency hiss, static & electronic buzz
                var lowpass = webAudioCtx.createBiquadFilter();
                lowpass.type = "lowpass";
                lowpass.frequency.value = 7200;
                lowpass.Q.value = 0.707;

                // 3. Peaking Vocal Presence Filter (2400 Hz, +3.5 dB): Enhances speech intelligibility
                var vocalPresence = webAudioCtx.createBiquadFilter();
                vocalPresence.type = "peaking";
                vocalPresence.frequency.value = 2400;
                vocalPresence.gain.value = 3.5;
                vocalPresence.Q.value = 1.1;

                // 4. Dynamics Compressor: Levels voice, prevents clipping, controls noise bursts
                var compressor = webAudioCtx.createDynamicsCompressor();
                compressor.threshold.value = -24;
                compressor.knee.value = 10;
                compressor.ratio.value = 3.5;
                compressor.attack.value = 0.005;
                compressor.release.value = 0.09;

                // 5. Clean Output Gain
                webAudioGain = webAudioCtx.createGain();
                webAudioGain.gain.value = isSpeakerMuted ? 0.0 : 1.15;

                // Connect DSP pipeline
                webAudioSource.connect(highpass);
                highpass.connect(lowpass);
                lowpass.connect(vocalPresence);
                vocalPresence.connect(compressor);
                compressor.connect(webAudioGain);
                webAudioGain.connect(webAudioCtx.destination);

                if (remoteAudioElem) {
                    remoteAudioElem.muted = true;
                }
                console.log("Studio voice clarity DSP pipeline active with noise reduction");
            }
        } catch(e) {
            console.warn("setupWebAudioPipeline notice:", e);
            if (remoteAudioElem) remoteAudioElem.muted = isSpeakerMuted;
        }
    }

    function hookVoiceCall(call) {
        if (!call) return;
        activeVoiceCall = call;
        call.on('stream', function(remoteStream) {
            console.log("Remote voice audio stream received!");
            var audio = ensureRemoteAudioElement();
            audio.srcObject = remoteStream;
            audio.volume = 1.0;
            audio.muted = true; // WebAudio DSP plays the clean, filtered stream
            try {
                var p = audio.play();
                if (p && p.catch) p.catch(function(e) { console.warn("Audio play notice:", e); });
            } catch(e) {}

            setupWebAudioPipeline(remoteStream);

            if (window.AndroidWebRTC) {
                window.AndroidWebRTC.onRemoteVoiceState(true);
            }
        });

        call.on('close', function() {
            console.log("Voice call closed");
            if (window.AndroidWebRTC) {
                window.AndroidWebRTC.onRemoteVoiceState(false);
            }
            activeVoiceCall = null;
        });

        call.on('error', function(err) {
            console.error("Voice call error", err);
            if (window.AndroidWebRTC) {
                window.AndroidWebRTC.onVoiceError(err.type || err.message || "Voice call error");
            }
        });
    }

    function callPeerIfReady() {
        if (!peer || !activeConn || !activeConn.peer) return;
        if (!localVoiceStream) return;
        try {
            console.log("Initiating voice call to peer:", activeConn.peer);
            if (activeVoiceCall) {
                try { activeVoiceCall.close(); } catch(e){}
                activeVoiceCall = null;
            }
            var call = peer.call(activeConn.peer, localVoiceStream, { sdpTransform: optimizeOpusSdp });
            if (call) {
                hookVoiceCall(call);
            }
            try {
                activeConn.send(JSON.stringify({ type: "VOICE_SIGNAL", action: "VOICE_CALL_STARTED" }));
            } catch(e) {}
        } catch(e) {
            console.error("callPeerIfReady error", e);
        }
    }

    function startVoiceChat() {
        isVoiceRequested = true;
        if (localVoiceStream) {
            localVoiceStream.getAudioTracks().forEach(function(t) { t.enabled = !isMicMuted; });
            if (activeVoiceCall && activeVoiceCall.peerConnection) {
                var pc = activeVoiceCall.peerConnection;
                var track = localVoiceStream.getAudioTracks()[0];
                var senders = (pc && pc.getSenders) ? pc.getSenders() : [];
                var audioSender = senders.find(function(s) { return s.track && s.track.kind === 'audio'; });
                if (audioSender && audioSender.replaceTrack && track) {
                    audioSender.replaceTrack(track).then(function() {
                        console.log("Replaced audio track on active call");
                    }).catch(function() {
                        callPeerIfReady();
                    });
                } else {
                    callPeerIfReady();
                }
            } else {
                callPeerIfReady();
            }
            if (window.AndroidWebRTC) {
                window.AndroidWebRTC.onVoiceStateChanged(true, isMicMuted);
            }
            return;
        }

        if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
            navigator.mediaDevices.getUserMedia({
                audio: {
                    echoCancellation: true,
                    noiseSuppression: true,
                    autoGainControl: true,
                    channelCount: 1,
                    sampleRate: 48000,
                    sampleSize: 16
                },
                video: false
            }).then(function(stream) {
                console.log("Local microphone stream acquired successfully");
                localVoiceStream = stream;
                if (isMicMuted) {
                    localVoiceStream.getAudioTracks().forEach(function(t) { t.enabled = false; });
                }
                if (window.AndroidWebRTC) {
                    window.AndroidWebRTC.onVoiceStateChanged(true, isMicMuted);
                }
                if (activeVoiceCall && activeVoiceCall.peerConnection) {
                    var pc = activeVoiceCall.peerConnection;
                    var track = stream.getAudioTracks()[0];
                    var senders = (pc && pc.getSenders) ? pc.getSenders() : [];
                    var audioSender = senders.find(function(s) { return s.track && s.track.kind === 'audio'; });
                    if (audioSender && audioSender.replaceTrack && track) {
                        audioSender.replaceTrack(track).then(function() {
                            console.log("Replaced track with fresh mic stream");
                        }).catch(function() {
                            callPeerIfReady();
                        });
                    } else {
                        callPeerIfReady();
                    }
                } else {
                    callPeerIfReady();
                }
            }).catch(function(err) {
                console.error("Failed to acquire mic stream", err);
                if (window.AndroidWebRTC) {
                    window.AndroidWebRTC.onVoiceError(err.name || err.message || "Microphone permission denied");
                }
            });
        }
    }

    function setMicMuted(muted) {
        isMicMuted = !!muted;
        if (localVoiceStream) {
            localVoiceStream.getAudioTracks().forEach(function(t) {
                t.enabled = !isMicMuted;
            });
        }
        if (window.AndroidWebRTC) {
            window.AndroidWebRTC.onVoiceStateChanged(localVoiceStream != null, isMicMuted);
        }
    }

    function setSpeakerMuted(muted) {
        isSpeakerMuted = !!muted;
        if (webAudioGain && webAudioCtx) {
            try {
                webAudioGain.gain.setValueAtTime(isSpeakerMuted ? 0.0 : 1.15, webAudioCtx.currentTime);
            } catch(e) {
                webAudioGain.gain.value = isSpeakerMuted ? 0.0 : 1.15;
            }
        }
        if (remoteAudioElem) {
            if (!webAudioCtx || webAudioCtx.state === 'closed') {
                remoteAudioElem.muted = isSpeakerMuted;
            } else {
                remoteAudioElem.muted = true;
            }
        }
        if (window.AndroidWebRTC) {
            window.AndroidWebRTC.onSpeakerStateChanged(isSpeakerMuted);
        }
    }

    function stopVoiceChat() {
        isVoiceRequested = false;
        if (localVoiceStream) {
            localVoiceStream.getTracks().forEach(function(t) { t.stop(); });
            localVoiceStream = null;
        }
        if (activeVoiceCall) {
            try { activeVoiceCall.close(); } catch(e){}
            activeVoiceCall = null;
        }
        if (remoteAudioElem) {
            remoteAudioElem.srcObject = null;
        }
        if (webAudioSource) {
            try { webAudioSource.disconnect(); } catch(e){}
            webAudioSource = null;
        }
        if (webAudioCtx && webAudioCtx.state !== 'closed') {
            try { webAudioCtx.suspend(); } catch(e){}
        }
        try {
            if (activeConn) {
                activeConn.send(JSON.stringify({ type: "VOICE_SIGNAL", action: "VOICE_CALL_STOPPED" }));
            }
        } catch(e) {}
        if (window.AndroidWebRTC) {
            window.AndroidWebRTC.onVoiceStateChanged(false, false);
            window.AndroidWebRTC.onRemoteVoiceState(false);
        }
    }

    function attachPeerCallListener(targetPeer) {
        if (!targetPeer) return;
        targetPeer.on('call', function(incomingCall) {
            console.log("Incoming voice call from:", incomingCall.peer);
            hookVoiceCall(incomingCall);
            if (localVoiceStream) {
                incomingCall.answer(localVoiceStream, { sdpTransform: optimizeOpusSdp });
            } else {
                incomingCall.answer(undefined, { sdpTransform: optimizeOpusSdp });
                if (isVoiceRequested) {
                    startVoiceChat();
                }
            }
        });
    }

    function initQuickMatch(lobbySlot, profileJson, timerEnabled) {
        try {
            if (profileJson) {
                try { myProfile = JSON.parse(profileJson); } catch(e){}
            }
            currentTimerEnabled = !!timerEnabled;
            if (peer) {
                try { peer.destroy(); } catch(e){}
            }
            var hostTargetId = "bp_lobby_duel_v4_" + lobbySlot;

            peer = new Peer(hostTargetId, createPeerConfig());
            attachPeerCallListener(peer);

            peer.on('open', function(id) {
                if (window.AndroidWebRTC) {
                    window.AndroidWebRTC.onPeerReady(id);
                }
            });

            peer.on('disconnected', function() {
                console.log("Peer disconnected from signaling. Auto-reconnecting...");
                if (peer && !peer.destroyed) {
                    try { peer.reconnect(); } catch(e) {}
                }
            });

            peer.on('connection', function(conn) {
                setupConnection(conn, true);
            });

            peer.on('error', function(err) {
                var errType = err.type || "";
                if (errType === 'unavailable-id') {
                    try { peer.destroy(); } catch(e){}
                    var guestId = "bp_guest_" + Math.floor(10000 + Math.random() * 90000);
                    peer = new Peer(guestId, createPeerConfig());
                    attachPeerCallListener(peer);
                    peer.on('open', function(id) {
                        var conn = peer.connect(hostTargetId, { reliable: true });
                        setupConnection(conn, false);
                    });
                    peer.on('disconnected', function() {
                        if (peer && !peer.destroyed) { try { peer.reconnect(); } catch(e) {} }
                    });
                    peer.on('error', function(innerErr) {
                        if (window.AndroidWebRTC) {
                            window.AndroidWebRTC.onPeerError(innerErr.type || "guest_conn_err");
                        }
                    });
                } else {
                    if (window.AndroidWebRTC) {
                        window.AndroidWebRTC.onPeerError(errType || err.message || "peer_err");
                    }
                }
            });
        } catch(err) {
            if (window.AndroidWebRTC) {
                window.AndroidWebRTC.onPeerError(err.toString());
            }
        }
    }

    function createRoom(roomCode, profileJson, timerEnabled) {
        try {
            if (profileJson) {
                try { myProfile = JSON.parse(profileJson); } catch(e){}
            }
            currentTimerEnabled = !!timerEnabled;
            if (peer) {
                try { peer.destroy(); } catch(e){}
            }
            var myId = "bp_room_v4_" + roomCode;
            peer = new Peer(myId, createPeerConfig());
            attachPeerCallListener(peer);

            peer.on('open', function(id) {
                if (window.AndroidWebRTC) {
                    window.AndroidWebRTC.onRoomReady(roomCode);
                }
            });

            peer.on('disconnected', function() {
                console.log("Host disconnected from signaling. Auto-reconnecting...");
                if (peer && !peer.destroyed) {
                    try { peer.reconnect(); } catch(e) {}
                }
            });

            peer.on('connection', function(conn) {
                setupConnection(conn, true);
            });

            peer.on('error', function(err) {
                if (window.AndroidWebRTC) {
                    window.AndroidWebRTC.onPeerError(err.type || err.message || "peer_err");
                }
            });
        } catch(err) {
            if (window.AndroidWebRTC) {
                window.AndroidWebRTC.onPeerError(err.toString());
            }
        }
    }

    function joinRoom(roomCode, profileJson) {
        try {
            if (profileJson) {
                try { myProfile = JSON.parse(profileJson); } catch(e){}
            }
            if (peer) {
                try { peer.destroy(); } catch(e){}
            }
            var myId = "bp_join_v4_" + roomCode + "_" + Math.floor(1000 + Math.random() * 9000);
            var hostId = "bp_room_v4_" + roomCode;

            peer = new Peer(myId, createPeerConfig());
            attachPeerCallListener(peer);

            peer.on('open', function(id) {
                if (window.AndroidWebRTC) {
                    window.AndroidWebRTC.onPeerReady(id);
                }
                var conn = peer.connect(hostId, { reliable: true });
                setupConnection(conn, false);
            });

            peer.on('disconnected', function() {
                console.log("Guest disconnected from signaling. Auto-reconnecting...");
                if (peer && !peer.destroyed) {
                    try { peer.reconnect(); } catch(e) {}
                }
            });

            peer.on('error', function(err) {
                if (window.AndroidWebRTC) {
                    window.AndroidWebRTC.onPeerError(err.type || err.message || "peer_err");
                }
            });
        } catch(err) {
            if (window.AndroidWebRTC) {
                window.AndroidWebRTC.onPeerError(err.toString());
            }
        }
    }

    function setupConnection(conn, isHost) {
        conn.on('open', function() {
            activeConn = conn;
            var handshake = {
                type: "PROFILE_HANDSHAKE",
                name: myProfile.name,
                avatar: myProfile.avatar,
                winRate: myProfile.winRate,
                isHost: isHost,
                timerEnabled: currentTimerEnabled
            };
            try { conn.send(JSON.stringify(handshake)); } catch(e){}

            if (window.AndroidWebRTC) {
                window.AndroidWebRTC.onMatched(conn.peer, isHost, currentTimerEnabled);
            }

            if (isVoiceRequested && localVoiceStream) {
                callPeerIfReady();
            }
        });

        conn.on('data', function(data) {
            var rawStr = typeof data === 'string' ? data : JSON.stringify(data);
            try {
                var parsed = JSON.parse(rawStr);
                if (parsed) {
                    if (parsed.type === "HEARTBEAT_PING") {
                        try {
                            conn.send(JSON.stringify({
                                type: "HEARTBEAT_PONG",
                                t: parsed.t
                            }));
                        } catch(e) {}
                        return;
                    }
                    if (parsed.type === "HEARTBEAT_PONG") {
                        var now = Date.now();
                        var rtt = Math.max(10, Math.min(999, Math.round(now - (parsed.t || now))));
                        if (window.AndroidWebRTC) {
                            window.AndroidWebRTC.onHeartbeatAck(rtt);
                        }
                        return;
                    }
                    if (parsed.type === "OPPONENT_QUIT") {
                        if (window.AndroidWebRTC) {
                            window.AndroidWebRTC.onDataReceived(rawStr);
                        }
                        return;
                    }
                    if (parsed.type === "VOICE_SIGNAL") {
                        if (parsed.action === "VOICE_CALL_STARTED") {
                            if (window.AndroidWebRTC) {
                                window.AndroidWebRTC.onRemoteVoiceState(true);
                            }
                            if (isVoiceRequested && localVoiceStream && !activeVoiceCall) {
                                callPeerIfReady();
                            }
                        } else if (parsed.action === "VOICE_CALL_STOPPED") {
                            if (window.AndroidWebRTC) {
                                window.AndroidWebRTC.onRemoteVoiceState(false);
                            }
                        }
                        return;
                    }
                    if (parsed.type === "PROFILE_HANDSHAKE") {
                        if (window.AndroidWebRTC) {
                            var remoteIsHost = !!parsed.isHost;
                            var effectiveTimer = remoteIsHost ? (!!parsed.timerEnabled) : currentTimerEnabled;
                            window.AndroidWebRTC.onRemoteProfileReceived(
                                parsed.name || "Opponent",
                                parsed.avatar || "♟",
                                parsed.winRate || 50,
                                remoteIsHost,
                                effectiveTimer
                            );
                        }
                        return;
                    }
                }
            } catch(e) {}

            if (window.AndroidWebRTC) {
                window.AndroidWebRTC.onDataReceived(rawStr);
            }
        });

        conn.on('close', function() {
            if (window.AndroidWebRTC) {
                window.AndroidWebRTC.onConnClosed();
            }
        });

        conn.on('error', function(err) {
            if (window.AndroidWebRTC) {
                window.AndroidWebRTC.onPeerError(err.type || err.message || "conn_err");
            }
        });

        try {
            if (conn.peerConnection) {
                conn.peerConnection.oniceconnectionstatechange = function() {
                    var state = conn.peerConnection.iceConnectionState;
                    if (window.AndroidWebRTC) {
                        window.AndroidWebRTC.onIceState(state);
                    }
                };
            }
        } catch(e) {}
    }

    function sendHeartbeat() {
        try {
            if (activeConn && activeConn.open) {
                activeConn.send(JSON.stringify({
                    type: "HEARTBEAT_PING",
                    t: Date.now()
                }));
            } else if (activeConn && activeConn.dataChannel && activeConn.dataChannel.readyState === 'open') {
                activeConn.dataChannel.send(JSON.stringify({
                    type: "HEARTBEAT_PING",
                    t: Date.now()
                }));
            }
        } catch(e) {}
    }

    function sendGameAction(actionJson) {
        try {
            if (activeConn && activeConn.open) {
                activeConn.send(actionJson);
            } else if (activeConn && activeConn.dataChannel && activeConn.dataChannel.readyState === 'open') {
                activeConn.dataChannel.send(actionJson);
            }
        } catch(e) {
            console.error("sendGameAction error", e);
        }
    }

    function disconnect(notifyOpponent) {
        try {
            stopVoiceChat();
            if (activeConn) {
                if (notifyOpponent) {
                    try { 
                        activeConn.send(JSON.stringify({ 
                            type: "OPPONENT_QUIT",
                            intentional: true
                        })); 
                    } catch(e) {}
                }
                try { activeConn.close(); } catch(e) {}
                activeConn = null;
            }
            if (peer) {
                try { peer.destroy(); } catch(e) {}
                peer = null;
            }
        } catch(e) {}
    }
</script>
</body>
</html>
"""
    }
}
