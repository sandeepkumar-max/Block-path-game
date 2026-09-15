package com.example

/**
 * Supported languages for the BlockPath Interactive Tutorial.
 */
enum class TutorialLanguage(val label: String, val flag: String) {
    ENGLISH("English", "🇬🇧"),
    HINDI("हिंदी", "🇮🇳")
}

/**
 * Data holder for bilingual tutorial step texts.
 */
data class StepText(
    val title: String,
    val subtitle: String,
    val prompt: String,
    val success: String,
    val nextButtonText: String
)

object TutorialContent {

    fun getStepTitle(step: TutorialStep, lang: TutorialLanguage): String {
        return when (lang) {
            TutorialLanguage.ENGLISH -> when (step) {
                TutorialStep.PAWN_MOVE -> "1. Move Your Pawn"
                TutorialStep.PLACE_WALL -> "2. Block with Walls"
                TutorialStep.BFS_RULE -> "3. Golden Path Rule"
                TutorialStep.JUMP_OPPONENT -> "4. Leap Over Opponent"
                TutorialStep.ONLINE_MULTIPLAYER -> "5. Online Duel & Live Voice"
                TutorialStep.SETTINGS_GUIDE -> "6. Controls & Personalization"
                TutorialStep.COMPLETED -> "7. Ready to Play!"
            }
            TutorialLanguage.HINDI -> when (step) {
                TutorialStep.PAWN_MOVE -> "1. गोटी कैसे चलाएं"
                TutorialStep.PLACE_WALL -> "2. दीवार लगाकर रास्ता रोकें"
                TutorialStep.BFS_RULE -> "3. रास्ता खुला रखने का नियम"
                TutorialStep.JUMP_OPPONENT -> "4. विरोधी के ऊपर से छलांग"
                TutorialStep.ONLINE_MULTIPLAYER -> "5. ऑनलाइन मुकाबला और लाइव वॉइस"
                TutorialStep.SETTINGS_GUIDE -> "6. कंट्रोल्स और सेटिंग्स"
                TutorialStep.COMPLETED -> "7. खेलने के लिए तैयार!"
            }
        }
    }

    fun getStepDescription(step: TutorialStep, lang: TutorialLanguage): String {
        return when (lang) {
            TutorialLanguage.ENGLISH -> when (step) {
                TutorialStep.PAWN_MOVE ->
                    "Blue is your pawn (Player 1). Your goal is to reach any square in the TOP row (Row 0) before your opponent reaches the bottom.\nOn each turn, you can move 1 step UP, DOWN, LEFT, or RIGHT."
                TutorialStep.PLACE_WALL ->
                    "Each player gets 10 wooden walls. A wall is 2 tiles wide. You can place it Horizontally or Vertically to block your opponent's shortest path and force them to take a longer detour."
                TutorialStep.BFS_RULE ->
                    "Quoridor Golden Rule: You can create long detours, but you can NEVER completely trap a player (100% block). At least ONE valid open path must always exist to the goal line!"
                TutorialStep.JUMP_OPPONENT ->
                    "When facing your opponent head-to-head with no wall blocking behind them, you can JUMP over them! This lets you leap 2 squares forward in a single turn."
                TutorialStep.ONLINE_MULTIPLAYER ->
                    "Play in real-time with friends or random players worldwide! Use Quick Match or share a 4-letter Room Code. Talk live using Voice Chat and send instant emojis!"
                TutorialStep.SETTINGS_GUIDE ->
                    "Customize your game experience! Switch between simple Button Tap mode and tactile Drag & Drop wall placement. Toggle sounds and Dark/Light theme anytime."
                TutorialStep.COMPLETED ->
                    "Congratulations! You've mastered pawn movement, strategic wall placements, opponent jumps, online voice duels, and game settings. Choose a mode below and start playing!"
            }
            TutorialLanguage.HINDI -> when (step) {
                TutorialStep.PAWN_MOVE ->
                    "नीली गोटी (Blue) आपकी है! आपका लक्ष्य अपने प्रतिद्वंद्वी से पहले बोर्ड की सबसे ऊपरी पंक्ति (Row 0) तक पहुँचना है।\nअपनी हर बारी में आप 1 कदम ऊपर, नीचे, दाएँ या बाएँ चल सकते हैं।"
                TutorialStep.PLACE_WALL ->
                    "प्रत्येक खिलाड़ी को 10 दीवारें मिलती हैं। एक दीवार ठीक 2 खानों जितनी लंबी होती है। आप इसे आड़ा (Horizontal) या खड़ा (Vertical) लगाकर विरोधी का रास्ता रोक सकते हैं।"
                TutorialStep.BFS_RULE ->
                    "खेल का सबसे महत्वपूर्ण नियम: आप विरोधी का रास्ता लंबा कर सकते हैं, लेकिन किसी को 100% बंद नहीं कर सकते! लक्ष्य तक जाने के लिए कम से कम 1 रास्ता हमेशा खुला होना अनिवार्य है।"
                TutorialStep.JUMP_OPPONENT ->
                    "जब आपकी और विरोधी की गोटी आमने-सामने आ जाए और उसके पीछे कोई दीवार न हो, तो आप उसके ऊपर से छलांग (Jump) लगाकर 1 ही बारी में 2 कदम आगे जा सकते हैं!"
                TutorialStep.ONLINE_MULTIPLAYER ->
                    "दुनिया भर के खिलाड़ियों या दोस्तों के साथ ऑनलाइन खेलें! 'त्वरित मैच' (Quick Match) से तुरंत खेलें या 4-अक्षर का रूम कोड शेयर करें। लाइव माइक से बात करें और इमोजी भेजें!"
                TutorialStep.SETTINGS_GUIDE ->
                    "गेम को अपनी पसंद अनुसार सेट करें! बटन टैप मोड या ड्रैग-एंड-ड्रॉप चुनें, साउंड ऑन/ऑफ करें और डार्क या लाइट थीम सेट करें।"
                TutorialStep.COMPLETED ->
                    "बधाई हो! आप गोटी चलाना, दीवार की रणनीति, छलांग लगाना, ऑनलाइन वॉइस मैच और सेटिंग्स पूरी तरह सीख चुके हैं। नीचे से कोई भी मोड चुनकर खेल शुरू करें!"
            }
        }
    }

    fun getStepPrompt(step: TutorialStep, lang: TutorialLanguage): String {
        return when (lang) {
            TutorialLanguage.ENGLISH -> when (step) {
                TutorialStep.PAWN_MOVE -> "👉 Tap the highlighted green tile ahead to move forward!"
                TutorialStep.PLACE_WALL -> "👉 Choose Horizontal/Vertical, tap an intersection, then tap Confirm Wall!"
                TutorialStep.BFS_RULE -> "👉 Notice how walls that completely trap players are prohibited by the path rule."
                TutorialStep.JUMP_OPPONENT -> "👉 Tap the green tile directly behind Red to leap over him!"
                TutorialStep.ONLINE_MULTIPLAYER -> "👉 Explore the interactive Online features below: test Voice Mic & Emojis!"
                TutorialStep.SETTINGS_GUIDE -> "👉 Tap 'Open Settings' below to test and adjust your controls."
                TutorialStep.COMPLETED -> "🎉 You are now a certified BlockPath strategist!"
            }
            TutorialLanguage.HINDI -> when (step) {
                TutorialStep.PAWN_MOVE -> "👉 आगे बढ़ें! ऊपर हरे रंग के हाइलाइट किए गए बॉक्स पर टैप करें।"
                TutorialStep.PLACE_WALL -> "👉 Horizontal/Vertical चुनें, बोर्ड पर टैप करें और 'Confirm Wall' दबाएं।"
                TutorialStep.BFS_RULE -> "👉 ध्यान दें: पूरी तरह रास्ता रोकने वाली दीवार गेम में अवैध मानी जाती है।"
                TutorialStep.JUMP_OPPONENT -> "👉 लाल गोटी के ठीक पीछे वाले हरे बॉक्स पर टैप करें और छलांग लगाएं!"
                TutorialStep.ONLINE_MULTIPLAYER -> "👉 नीचे ऑनलाइन फीचर्स का अनुभव लें: माइक और इमोजी टेस्ट करके देखें!"
                TutorialStep.SETTINGS_GUIDE -> "👉 नीचे 'सेटिंग्स खोलें' पर टैप करके अपनी पसंद के कंट्रोल्स चेक करें।"
                TutorialStep.COMPLETED -> "🎉 आप ब्लॉकपाथ के माहिर खिलाड़ी बन चुके हैं!"
            }
        }
    }

    fun getOnlineHighlights(lang: TutorialLanguage): List<OnlineHighlight> {
        return when (lang) {
            TutorialLanguage.ENGLISH -> listOf(
                OnlineHighlight(
                    iconName = "Bolt",
                    title = "Quick Match (1-Tap)",
                    desc = "Instantly pairs you with another online player within seconds. No waiting or room setup needed."
                ),
                OnlineHighlight(
                    iconName = "Key",
                    title = "Private Room Code",
                    desc = "Create a custom 4-character code (e.g. 'ABCD') or join your friend's room from anywhere in the world."
                ),
                OnlineHighlight(
                    iconName = "Mic",
                    title = "Real-Time Voice Chat",
                    desc = "Talk directly with your opponent during the match! Tap the Mic button to mute/unmute anytime. 100% peer-to-peer."
                ),
                OnlineHighlight(
                    iconName = "Mood",
                    title = "Live Reaction Emojis",
                    desc = "Send instant animated emojis (😂, 🔥, 👏, 🧠, 🤯) during the game for friendly banter."
                ),
                OnlineHighlight(
                    iconName = "Timer",
                    title = "Turn Timer & Rematch",
                    desc = "30-second turn countdown ensures active games. When the game ends, request an instant 1-tap rematch!"
                )
            )
            TutorialLanguage.HINDI -> listOf(
                OnlineHighlight(
                    iconName = "Bolt",
                    title = "त्वरित मुकाबला (Quick Match)",
                    desc = "सिर्फ 1 टैप में कुछ ही सेकंड में किसी भी ऑनलाइन खिलाड़ी से जुड़ें। बिना किसी झंझट के तुरंत खेल शुरू।"
                ),
                OnlineHighlight(
                    iconName = "Key",
                    title = "प्राइवेट रूम कोड (Room Code)",
                    desc = "अपना 4-अक्षर का कोड (जैसे 'ABCD') बनाएं और अपने दोस्त को कोड भेजें ताकि आप दोनों साथ खेल सकें।"
                ),
                OnlineHighlight(
                    iconName = "Mic",
                    title = "लाइव वॉइस चैट (Voice Chat)",
                    desc = "खेलते-खेलते सीधे विरोधी से बात करें! जब चाहें माइक बटन दबाकर आवाज़ बंद या चालू करें। पूर्णतः सुरक्षित।"
                ),
                OnlineHighlight(
                    iconName = "Mood",
                    title = "लाइव इमोजी रिएक्शन्स",
                    desc = "मैच के दौरान तुरंत मजेदार इमोजी (😂, 🔥, 👏, 🧠, 🤯) भेजें जो बोर्ड पर तैरती हुई दिखती हैं।"
                ),
                OnlineHighlight(
                    iconName = "Timer",
                    title = "टर्न टाइमर और रीमैच",
                    desc = "30 सेकंड का टाइमर सुनिश्चित करता है कि मैच लटका न रहे। मैच खत्म होने पर 1 टैप में दोबारा मुकाबला करें!"
                )
            )
        }
    }
}

data class OnlineHighlight(
    val iconName: String,
    val title: String,
    val desc: String
)
