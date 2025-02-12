package dev.omkartenkale.fliptopause

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AccelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lightweight, zero-dependency replacement for MutableStateFlow.
 * Thread-safe and dispatches observer notifications on the main UI thread.
 */
class StateValue<T>(initialValue: T) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(T) -> Unit>()

    @Volatile
    var value: T = initialValue
        set(newValue) {
            if (field == newValue) return
            field = newValue
            if (Looper.myLooper() == Looper.myLooper() && Looper.myLooper() == Looper.getMainLooper()) {
                notifyListeners(newValue)
            } else {
                mainHandler.post { notifyListeners(this.value) }
            }
        }

    private fun notifyListeners(current: T) {
        for (listener in listeners) {
            listener(current)
        }
    }

    fun observe(listener: (T) -> Unit): () -> Unit {
        listeners.add(listener)
        listener(value)
        return { listeners.remove(listener) }
    }
}

/**
 * Dynamic, zero-XML localization with friendly, everyday language.
 * Automatically picks strings based on the user's current device language.
 */
data class LocalizedCopy(
    val appTitle: String,
    val helpSubtitle: String,
    val tapToTurnOn: String,
    val putFaceUpToPause: String,
    val putFaceDownToPause: String,
    val putFaceUpToResume: String,
    val putFaceDownToResume: String,
    val notifFaceUpToPause: String,
    val notifFaceDownToPause: String,
    val notifFaceUpToResume: String,
    val notifFaceDownToResume: String,
    val notifTurnOff: String,
    val notifChannelName: String,
    val toastPaused: String,
    val toastResumed: String,
    val tileOn: String,
    val tileOff: String,
    val reverseTitle: String,
    val reverseSubDefault: String,
    val reverseSubInverted: String,
    val delayLabel: String,
    val angleLabel: String,
    val resetDefaults: String,
    val batteryHelp: String,
    val unsupportedBadge: String,
    val unsupportedTitle: String,
    val unsupportedMessage: String,
    val closeApp: String,
    val cdHelp: String,
    val cdSettings: String,
    val cdBack: String,
    val faqs: List<Pair<String, String>>
)

object AppStrings {
    val current: LocalizedCopy
        get() = when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "es" -> ES
            "hi" -> HI
            "fr" -> FR
            "de" -> DE
            "pt" -> PT
            "ja" -> JA
            "ru" -> RU
            else -> EN
        }

    private val EN = LocalizedCopy(
        appTitle = "FLIP TO PAUSE",
        helpSubtitle = "Control your music and videos hands-free. Just flip your phone to pause what's playing, and flip it back when you're ready to listen again.",
        tapToTurnOn = "tap to turn on",
        putFaceUpToPause = "put face up to pause",
        putFaceDownToPause = "put face down to pause",
        putFaceUpToResume = "put face up to resume",
        putFaceDownToResume = "put face down to resume",
        notifFaceUpToPause = "Place phone face up to pause",
        notifFaceDownToPause = "Place phone face down to pause",
        notifFaceUpToResume = "Flip phone face up to resume",
        notifFaceDownToResume = "Flip phone face down to resume",
        notifTurnOff = "Turn off",
        notifChannelName = "Flip to Pause Status",
        toastPaused = "Paused",
        toastResumed = "Playing",
        tileOn = "On",
        tileOff = "Off",
        reverseTitle = "FLIP DIRECTION",
        reverseSubDefault = "face up pauses · face down plays",
        reverseSubInverted = "face down pauses · face up plays",
        delayLabel = "PAUSE DELAY",
        angleLabel = "TILT SENSITIVITY",
        resetDefaults = "reset settings",
        batteryHelp = "stops working in background?",
        unsupportedBadge = "NOT SUPPORTED",
        unsupportedTitle = "Motion Sensor Missing",
        unsupportedMessage = "Your phone doesn't have the motion sensor needed to tell which way it's facing.\n\nUnfortunately, Flip to Pause won't work on this device.",
        closeApp = "close app",
        cdHelp = "Help",
        cdSettings = "Settings",
        cdBack = "Back",
        faqs = listOf(
            "Which apps does this work with?" to "It works with almost any music, podcast, audiobook, or video app—including Spotify, YouTube, Apple Music, Audible, and more.",
            "How does it pause and play?" to "When you lay your phone down, it gently tells your music or video app to pause—just like when a phone call comes in. When you flip it back, your audio picks up right where you left off.",
            "Can I swap face up and face down?" to "Yes! Open Settings and turn on 'Flip Direction'. You can choose whether laying your phone face up or face down pauses your audio.",
            "What if it pauses by accident when I'm holding it?" to "In Settings, you can add a short Pause Delay (like 1 or 2 seconds) so quick movements are ignored, or lower the Tilt Sensitivity so the phone has to be laid flatter.",
            "Will this drain my battery?" to "Not at all. Your phone already tracks its screen orientation using almost zero power. This app doesn't use the internet, GPS, or keep your screen awake.",
            "Why does it sometimes stop working after a while?" to "Some phones automatically close background apps to save battery. If that happens, tap 'stops working in background?' at the bottom of Settings to allow it to stay active."
        )
    )

    private val ES = LocalizedCopy(
        appTitle = "FLIP TO PAUSE",
        helpSubtitle = "Controla tu música y videos sin usar las manos. Solo voltea tu teléfono para pausar lo que estás escuchando y vuélvelo a girar para continuar.",
        tapToTurnOn = "toca para activar",
        putFaceUpToPause = "pon boca arriba para pausar",
        putFaceDownToPause = "pon boca abajo para pausar",
        putFaceUpToResume = "pon boca arriba para reanudar",
        putFaceDownToResume = "pon boca abajo para reanudar",
        notifFaceUpToPause = "Pon el teléfono boca arriba para pausar",
        notifFaceDownToPause = "Pon el teléfono boca abajo para pausar",
        notifFaceUpToResume = "Voltea boca arriba para continuar",
        notifFaceDownToResume = "Voltea boca abajo para continuar",
        notifTurnOff = "Apagar",
        notifChannelName = "Estado de Flip to Pause",
        toastPaused = "En pausa",
        toastResumed = "Reproduciendo",
        tileOn = "Activo",
        tileOff = "Inactivo",
        reverseTitle = "INVERTIR GIRO",
        reverseSubDefault = "boca arriba pausa · boca abajo reproduce",
        reverseSubInverted = "boca abajo pausa · boca arriba reproduce",
        delayLabel = "ESPERA AL PAUSAR",
        angleLabel = "SENSIBILIDAD",
        resetDefaults = "restablecer ajustes",
        batteryHelp = "¿se detiene en segundo plano?",
        unsupportedBadge = "NO COMPATIBLE",
        unsupportedTitle = "Falta sensor de movimiento",
        unsupportedMessage = "Tu teléfono no tiene el sensor necesario para detectar hacia qué lado está orientado.\n\nFlip to Pause no puede funcionar en este dispositivo.",
        closeApp = "cerrar app",
        cdHelp = "Ayuda",
        cdSettings = "Ajustes",
        cdBack = "Volver",
        faqs = listOf(
            "¿Con qué aplicaciones funciona?" to "Funciona con casi cualquier app de música, podcasts, audiolibros o videos, como Spotify, YouTube, Apple Music, Audible y más.",
            "¿Cómo pausa y reanuda el audio?" to "Al apoyar el teléfono, le indica a tu reproductor que haga una pausa (igual que cuando entra una llamada). Al voltearlo de nuevo, continúa justo donde quedó.",
            "¿Puedo cambiar entre boca arriba y boca abajo?" to "¡Sí! Abre los Ajustes y activa 'Invertir giro' para elegir qué posición pausa tu música.",
            "¿Qué hago si se pausa por accidente en mi mano?" to "En Ajustes puedes añadir un segundo de espera ('Espera al pausar') o reducir la sensibilidad para que solo funcione cuando el teléfono esté bien plano.",
            "¿Gasta mucha batería?" to "Para nada. Usa el sensor básico de inclinación que tu teléfono ya tiene activo sin usar internet, ubicación ni encender la pantalla.",
            "¿Por qué deja de funcionar después de un rato?" to "Algunos teléfonos cierran apps automáticamente para ahorrar batería. Toca '¿se detiene en segundo plano?' abajo en Ajustes para evitarlo."
        )
    )

    private val HI = LocalizedCopy(
        appTitle = "FLIP TO PAUSE",
        helpSubtitle = "बिना हाथ लगाए अपना म्यूज़िक और वीडियो कंट्रोल करें। बस फ़ोन को पलट कर रखें और ऑडियो रुक जाएगा, वापस पलटें तो वहीं से फिर शुरू हो जाएगा।",
        tapToTurnOn = "चालू करने के लिए टैप करें",
        putFaceUpToPause = "रोकने के लिए स्क्रीन ऊपर रखें",
        putFaceDownToPause = "रोकने के लिए स्क्रीन नीचे रखें",
        putFaceUpToResume = "चलाने के लिए स्क्रीन ऊपर करें",
        putFaceDownToResume = "चलाने के लिए स्क्रीन नीचे करें",
        notifFaceUpToPause = "रोकने के लिए फ़ोन सीधा (स्क्रीन ऊपर) रखें",
        notifFaceDownToPause = "रोकने के लिए फ़ोन उल्टा (स्क्रीन नीचे) रखें",
        notifFaceUpToResume = "फिर से चलाने के लिए स्क्रीन ऊपर करें",
        notifFaceDownToResume = "फिर से चलाने के लिए स्क्रीन नीचे करें",
        notifTurnOff = "बंद करें",
        notifChannelName = "Flip to Pause स्थिति",
        toastPaused = "रुक गया",
        toastResumed = "चल रहा है",
        tileOn = "चालू",
        tileOff = "बंद",
        reverseTitle = "उल्टा मोड़ (REVERSE)",
        reverseSubDefault = "स्क्रीन ऊपर: रुकेगा · स्क्रीन नीचे: चलेगा",
        reverseSubInverted = "स्क्रीन नीचे: रुकेगा · स्क्रीन ऊपर: चलेगा",
        delayLabel = "रुकने का समय (DELAY)",
        angleLabel = "झुकाव (ANGLE)",
        resetDefaults = "सेटिंग्स रीसेट करें",
        batteryHelp = "बैकग्राउंड में बंद हो जाता है?",
        unsupportedBadge = "सपोर्ट नहीं करता",
        unsupportedTitle = "मोशन सेंसर उपलब्ध नहीं है",
        unsupportedMessage = "आपके फ़ोन में वह सेंसर नहीं है जिससे पता चले कि फ़ोन सीधा रखा है या उल्टा।\n\nइसलिए यह ऐप इस डिवाइस पर काम नहीं कर सकता।",
        closeApp = "ऐप बंद करें",
        cdHelp = "मदद",
        cdSettings = "सेटिंग्स",
        cdBack = "पीछे",
        faqs = listOf(
            "यह किन ऐप्स के साथ काम करता है?" to "यह Spotify, YouTube, Apple Music, पॉडकास्ट और ऑडियोबुक जैसे लगभग सभी म्यूज़िक और वीडियो ऐप्स के साथ काम करता है।",
            "यह अपने आप कैसे रुकता और चलता है?" to "जब आप फ़ोन को रखते हैं, तो यह आपके म्यूज़िक ऐप को पॉज़ करने का सिग्नल देता है। जब आप फ़ोन वापस पलटते हैं, तो गाना वहीं से फिर शुरू हो जाता है।",
            "क्या मैं सीधा और उल्टा रखने का तरीका बदल सकता हूँ?" to "हाँ! सेटिंग्स में जाकर 'उल्टा मोड़ (Reverse)' चालू करें, जिससे स्क्रीन नीचे रखने पर ऑडियो रुकेगा और ऊपर करने पर चलेगा।",
            "अगर हाथ में पकड़े हुए गलती से पॉज़ हो जाए तो?" to "सेटिंग्स में जाकर 'रुकने का समय (Delay)' को 1 या 2 सेकंड पर सेट करें, ताकि तुरंत गलती से गाना न रुके।",
            "क्या इससे बैटरी जल्दी खत्म होती है?" to "बिल्कुल नहीं। यह इंटरनेट या GPS का इस्तेमाल नहीं करता और बहुत ही कम बैटरी लेता है।",
            "कुछ देर बाद यह अपने आप काम करना क्यों बंद कर देता है?" to "कई फ़ोन बैटरी बचाने के लिए बैकग्राउंड ऐप्स को बंद कर देते हैं। सेटिंग्स में नीचे 'बैकग्राउंड में बंद हो जाता है?' पर टैप करके इसे अनुमति दें।"
        )
    )

    private val FR = LocalizedCopy(
        appTitle = "FLIP TO PAUSE",
        helpSubtitle = "Contrôlez votre musique et vos vidéos sans les mains. Posez simplement votre téléphone pour mettre en pause, et retournez-le pour reprendre l'écoute.",
        tapToTurnOn = "appuyer pour activer",
        putFaceUpToPause = "écran vers le haut pour mettre en pause",
        putFaceDownToPause = "écran vers le bas pour mettre en pause",
        putFaceUpToResume = "écran vers le haut pour reprendre",
        putFaceDownToResume = "écran vers le bas pour reprendre",
        notifFaceUpToPause = "Posez l'écran vers le haut pour mettre en pause",
        notifFaceDownToPause = "Posez l'écran vers le bas pour mettre en pause",
        notifFaceUpToResume = "Retournez l'écran vers le haut pour reprendre",
        notifFaceDownToResume = "Retournez l'écran vers le bas pour reprendre",
        notifTurnOff = "Arrêter",
        notifChannelName = "État de Flip to Pause",
        toastPaused = "En pause",
        toastResumed = "Lecture",
        tileOn = "Activé",
        tileOff = "Désactivé",
        reverseTitle = "INVERSER LE SENS",
        reverseSubDefault = "face visible pause · face cachée reprend",
        reverseSubInverted = "face cachée pause · face visible reprend",
        delayLabel = "DÉLAI DE PAUSE",
        angleLabel = "SENSIBILITÉ",
        resetDefaults = "réinitialiser",
        batteryHelp = "l'app s'arrête toute seule ?",
        unsupportedBadge = "NON COMPATIBLE",
        unsupportedTitle = "Capteur manquant",
        unsupportedMessage = "Votre téléphone ne possède pas le capteur de mouvement nécessaire pour détecter son orientation.\n\nFlip to Pause ne peut pas fonctionner sur cet appareil.",
        closeApp = "fermer l'app",
        cdHelp = "Aide",
        cdSettings = "Paramètres",
        cdBack = "Retour",
        faqs = listOf(
            "Avec quelles applications cela fonctionne-t-il ?" to "Avec presque toutes les applications de musique, podcasts, livres audio et vidéos : Spotify, YouTube, Deezer, Apple Music, Audible, etc.",
            "Comment la pause et la reprise fonctionnent-elles ?" to "Lorsque vous posez votre téléphone, l'application demande simplement au lecteur de faire une pause (comme lors d'un appel). En le retournant, la lecture reprend immédiatement.",
            "Puis-je inverser le sens (écran vers le bas / haut) ?" to "Oui ! Dans les Paramètres, activez « Inverser le sens » pour mettre en pause lorsque l'écran est tourné vers le bas.",
            "Que faire si la pause s'active par erreur dans ma main ?" to "Dans les Paramètres, ajoutez un léger délai (1 ou 2 secondes) ou réduisez la sensibilité d'inclinaison.",
            "Est-ce que cela consomme de la batterie ?" to "Pas du tout. L'application utilise uniquement le capteur d'orientation déjà actif sur votre téléphone, sans internet ni GPS.",
            "Pourquoi l'application s'arrête-t-elle parfois en arrière-plan ?" to "Certains téléphones ferment les applications en arrière-plan pour économiser la batterie. Appuyez sur « l'app s'arrête toute seule ? » en bas des Paramètres pour l'empêcher."
        )
    )

    private val DE = LocalizedCopy(
        appTitle = "FLIP TO PAUSE",
        helpSubtitle = "Steuere Musik und Videos ganz ohne Hände. Lege dein Handy einfach ab, um zu pausieren, und drehe es um, um weiterzuhören.",
        tapToTurnOn = "zum einschalten tippen",
        putFaceUpToPause = "display nach oben zum pausieren",
        putFaceDownToPause = "display nach unten zum pausieren",
        putFaceUpToResume = "display nach oben zum fortsetzen",
        putFaceDownToResume = "display nach unten zum fortsetzen",
        notifFaceUpToPause = "Display nach oben legen zum Pausieren",
        notifFaceDownToPause = "Display nach unten legen zum Pausieren",
        notifFaceUpToResume = "Display nach oben drehen zum Fortsetzen",
        notifFaceDownToResume = "Display nach unten drehen zum Fortsetzen",
        notifTurnOff = "Ausschalten",
        notifChannelName = "Flip to Pause Status",
        toastPaused = "Pausiert",
        toastResumed = "Wiedergabe",
        tileOn = "An",
        tileOff = "Aus",
        reverseTitle = "RICHTUNG UMKEHREN",
        reverseSubDefault = "oben pausiert · unten spielt ab",
        reverseSubInverted = "unten pausiert · oben spielt ab",
        delayLabel = "VERZÖGERUNG",
        angleLabel = "NEIGUNG",
        resetDefaults = "zurücksetzen",
        batteryHelp = "stoppt im hintergrund?",
        unsupportedBadge = "NICHT UNTERSTÜTZT",
        unsupportedTitle = "Bewegungssensor fehlt",
        unsupportedMessage = "Dein Smartphone hat keinen Sensor, um zu erkennen, wie herum es liegt.\n\nFlip to Pause kann auf diesem Gerät leider nicht genutzt werden.",
        closeApp = "app schließen",
        cdHelp = "Hilfe",
        cdSettings = "Einstellungen",
        cdBack = "Zurück",
        faqs = listOf(
            "Mit welchen Apps funktioniert das?" to "Mit fast allen Musik-, Podcast-, Hörbuch- und Video-Apps wie Spotify, YouTube, Apple Music, Audible und vielen mehr.",
            "Wie funktioniert das Pausieren und Fortsetzen?" to "Wenn du dein Handy ablegst, gibt die App deinem Player das Signal zum Pausieren – genau wie bei einem eingehenden Anruf. Drehst du es um, läuft die Wiedergabe weiter.",
            "Kann ich Display-oben und Display-unten tauschen?" to "Ja! Schalte in den Einstellungen einfach „Richtung umkehren“ ein.",
            "Was, wenn es versehentlich in der Hand pausiert?" to "Stelle in den Einstellungen eine kurze Verzögerung (z. B. 1–2 Sekunden) ein oder verringere die Neigungsempfindlichkeit.",
            "Verbraucht die App viel Akku?" to "Überhaupt nicht. Sie nutzt nur den sparsamen Lagesensor deines Handys – ohne Internet, GPS oder Wachhalten des Bildschirms.",
            "Warum hört die App manchmal von selbst auf?" to "Manche Handys beenden Hintergrund-Apps zur Akku-Schonung. Tippe unten in den Einstellungen auf „stoppt im hintergrund?“, um das zu verhindern."
        )
    )

    private val PT = LocalizedCopy(
        appTitle = "FLIP TO PAUSE",
        helpSubtitle = "Controle suas músicas e vídeos sem usar as mãos. Basta virar o celular para pausar o que está tocando e desvirar para voltar a ouvir.",
        tapToTurnOn = "toque para ativar",
        putFaceUpToPause = "tela para cima para pausar",
        putFaceDownToPause = "tela para baixo para pausar",
        putFaceUpToResume = "tela para cima para retomar",
        putFaceDownToResume = "tela para baixo para retomar",
        notifFaceUpToPause = "Deixe a tela para cima para pausar",
        notifFaceDownToPause = "Deixe a tela para baixo para pausar",
        notifFaceUpToResume = "Vire a tela para cima para continuar",
        notifFaceDownToResume = "Vire a tela para baixo para continuar",
        notifTurnOff = "Desligar",
        notifChannelName = "Status do Flip to Pause",
        toastPaused = "Pausado",
        toastResumed = "Tocando",
        tileOn = "Ativo",
        tileOff = "Inativo",
        reverseTitle = "INVERTER LADO",
        reverseSubDefault = "tela p/ cima pausa · p/ baixo toca",
        reverseSubInverted = "tela p/ baixo pausa · p/ cima toca",
        delayLabel = "ATRASO DA PAUSA",
        angleLabel = "SENSIBILIDADE",
        resetDefaults = "restaurar padrão",
        batteryHelp = "parando em segundo plano?",
        unsupportedBadge = "NÃO COMPATÍVEL",
        unsupportedTitle = "Sensor Ausente",
        unsupportedMessage = "Seu aparelho não possui o sensor de movimento necessário para identificar a posição.\n\nO Flip to Pause não pode funcionar neste dispositivo.",
        closeApp = "fechar app",
        cdHelp = "Ajuda",
        cdSettings = "Configurações",
        cdBack = "Voltar",
        faqs = listOf(
            "Com quais aplicativos ele funciona?" to "Funciona com quase todos os apps de música, podcast, audiolivro e vídeo, incluindo Spotify, YouTube, Apple Music, Audible e outros.",
            "Como ele pausa e volta a tocar?" to "Quando você apoia o celular, ele avisa seu app de mídia para fazer uma pausa (como quando você recebe uma ligação). Ao virar de volta, o áudio continua de onde parou.",
            "Posso inverter entre tela para cima e para baixo?" to "Sim! Nas Configurações, ative 'Inverter lado' para pausar quando colocar a tela virada para baixo.",
            "E se pausar sem querer enquanto estou segurando?" to "Nas Configurações, aumente um pouco o 'Atraso da pausa' (1 ou 2 segundos) para evitar pausas acidentais.",
            "Isso gasta bateria?" to "Quase nada. O app usa apenas o sensor básico de inclinação do aparelho, sem usar internet, GPS ou manter a tela acesa.",
            "Por que às vezes ele para de funcionar sozinho?" to "Alguns celulares fecham apps em segundo plano para economizar bateria. Toque em 'parando em segundo plano?' no final das Configurações para resolver."
        )
    )

    private val JA = LocalizedCopy(
        appTitle = "FLIP TO PAUSE",
        helpSubtitle = "ハンズフリーで音楽や動画をコントロール。スマホを置くだけで一時停止し、裏返すと続きから再生を再開します。",
        tapToTurnOn = "タップしてオンにする",
        putFaceUpToPause = "画面を上にして一時停止",
        putFaceDownToPause = "画面を下にして一時停止",
        putFaceUpToResume = "画面を上にして再生再開",
        putFaceDownToResume = "画面を下にして再生再開",
        notifFaceUpToPause = "画面を上に向けて置くと一時停止します",
        notifFaceDownToPause = "画面を下に向けて置くと一時停止します",
        notifFaceUpToResume = "画面を上にすると再生を再開します",
        notifFaceDownToResume = "画面を下にすると再生を再開します",
        notifTurnOff = "オフにする",
        notifChannelName = "Flip to Pause ステータス",
        toastPaused = "一時停止しました",
        toastResumed = "再開しました",
        tileOn = "オン",
        tileOff = "オフ",
        reverseTitle = "向きを反転",
        reverseSubDefault = "表向きで停止 · 裏向きで再生",
        reverseSubInverted = "裏向きで停止 · 表向きで再生",
        delayLabel = "停止までの待ち時間",
        angleLabel = "傾きの感度",
        resetDefaults = "設定をリセット",
        batteryHelp = "バックグラウンドで止まりますか？",
        unsupportedBadge = "非対応デバイス",
        unsupportedTitle = "センサーが見つかりません",
        unsupportedMessage = "この端末には向きを検知するためのセンサーが搭載されていません。\n\n申し訳ありませんが、この端末ではご利用いただけません。",
        closeApp = "アプリを閉じる",
        cdHelp = "ヘルプ",
        cdSettings = "設定",
        cdBack = "戻る",
        faqs = listOf(
            "どのアプリで使えますか？" to "Spotify、YouTube、Apple Music、Audible、ポッドキャストなど、ほとんどの音楽・動画アプリでそのまま使えます。",
            "どうやって一時停止・再開しているのですか？" to "スマホを置くと、電話がかかってきた時と同じように再生アプリへ一時停止の合図を送ります。裏返すとすぐに続きから再生されます。",
            "表向きと裏向きの動作を逆にできますか？" to "はい！設定画面で「向きを反転」をオンにすると、画面を下にした時に一時停止するようになります。",
            "手に持っている時に誤って止まってしまう場合は？" to "設定で「停止までの待ち時間」を1〜2秒に増やすか、「傾きの感度」を下げると誤作動を防げます。",
            "バッテリーは消費しませんか？" to "ほとんど消費しません。通信やGPSは一切使わず、省電力な傾き検知のみを使用しています。",
            "しばらくすると勝手に止まってしまうのはなぜ？" to "省エネ機能によってアプリが強制終了されることがあります。設定の一番下にある「バックグラウンドで止まりますか？」をタップして制限を解除してください。"
        )
    )

    private val RU = LocalizedCopy(
        appTitle = "FLIP TO PAUSE",
        helpSubtitle = "Управляйте музыкой и видео без рук. Просто положите телефон, чтобы поставить на паузу, и переверните обратно, чтобы продолжить слушать.",
        tapToTurnOn = "нажмите, чтобы включить",
        putFaceUpToPause = "экраном вверх — пауза",
        putFaceDownToPause = "экраном вниз — пауза",
        putFaceUpToResume = "экраном вверх — продолжить",
        putFaceDownToResume = "экраном вниз — продолжить",
        notifFaceUpToPause = "Положите экраном вверх для паузы",
        notifFaceDownToPause = "Положите экраном вниз для паузы",
        notifFaceUpToResume = "Переверните экраном вверх для продолжения",
        notifFaceDownToResume = "Переверните экраном вниз для продолжения",
        notifTurnOff = "Выключить",
        notifChannelName = "Статус Flip to Pause",
        toastPaused = "Пауза",
        toastResumed = "Воспроизведение",
        tileOn = "Вкл",
        tileOff = "Выкл",
        reverseTitle = "ПОМЕНЯТЬ СТОРОНЫ",
        reverseSubDefault = "экраном вверх — пауза · вниз — играть",
        reverseSubInverted = "экраном вниз — пауза · вверх — играть",
        delayLabel = "ЗАДЕРЖКА ПАУЗЫ",
        angleLabel = "ЧУВСТВИТЕЛЬНОСТЬ",
        resetDefaults = "сбросить настройки",
        batteryHelp = "выключается в фоне?",
        unsupportedBadge = "НЕ ПОДДЕРЖИВАЕТСЯ",
        unsupportedTitle = "Нет датчика наклона",
        unsupportedMessage = "В вашем телефоне нет датчика ориентации, чтобы определить, какой стороной он лежит.\n\nК сожалению, приложение не сможет работать на этом устройстве.",
        closeApp = "закрыть",
        cdHelp = "Помощь",
        cdSettings = "Настройки",
        cdBack = "Назад",
        faqs = listOf(
            "С какими приложениями это работает?" to "Практически с любыми плеерами для музыки, подкастов, аудиокниг и видео: Spotify, YouTube, Яндекс Музыка, Apple Music и другими.",
            "Как именно ставится пауза?" to "Когда вы кладете телефон, приложение просит плеер приостановить звук — точно так же, как при входящем звонке. Когда вы переворачиваете телефон обратно, воспроизведение продолжается.",
            "Можно ли сделать паузу экраном вниз, а не вверх?" to "Да! Откройте Настройки и включите «Поменять стороны».",
            "Что делать, если пауза срабатывает случайно в руке?" to "В Настройках можно добавить небольшую задержку (1–2 секунды) или уменьшить чувствительность наклона.",
            "Сильно ли расходуется батарея?" to "Совсем нет. Приложение не использует интернет или GPS и не будит экран, полагаясь только на экономичный датчик наклона.",
            "Почему приложение иногда перестает работать само по себе?" to "Некоторые телефоны закрывают фоновые программы для экономии заряда. Нажмите «выключается в фоне?» внизу Настроек, чтобы разрешить работу без ограничений."
        )
    )
}

object FlipState {
    const val PREFS_NAME = "app_prefs"
    private const val KEY_DELAY = "min_pause_delay_seconds"
    private const val KEY_THRESHOLD = "z_threshold"
    private const val KEY_REVERSED = "is_reversed"

    const val DEFAULT_DELAY = 0f
    const val DEFAULT_THRESHOLD = 8.45f
    const val DEFAULT_REVERSED = false

    val isServiceRunning = StateValue(false)
    val isPausedByUs = StateValue(false)
    val minPauseDelaySeconds = StateValue(DEFAULT_DELAY)
    val zThreshold = StateValue(DEFAULT_THRESHOLD)
    val isReversed = StateValue(DEFAULT_REVERSED)

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        minPauseDelaySeconds.value = sp.getFloat(KEY_DELAY, DEFAULT_DELAY)
        zThreshold.value = sp.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        isReversed.value = sp.getBoolean(KEY_REVERSED, DEFAULT_REVERSED)
    }

    fun setDelay(seconds: Float) {
        minPauseDelaySeconds.value = seconds
        prefs?.edit()?.putFloat(KEY_DELAY, seconds)?.apply()
    }

    fun setThreshold(value: Float) {
        zThreshold.value = value
        prefs?.edit()?.putFloat(KEY_THRESHOLD, value)?.apply()
    }

    fun setReversed(reversed: Boolean) {
        isReversed.value = reversed
        prefs?.edit()?.putBoolean(KEY_REVERSED, reversed)?.apply()
    }

    fun resetToDefaults() {
        setDelay(DEFAULT_DELAY)
        setThreshold(DEFAULT_THRESHOLD)
        setReversed(DEFAULT_REVERSED)
    }
}

enum class AppView {
    UNSUPPORTED,
    TOGGLE,
    HELP,
    SETTINGS
}

class ViewActivity : Activity() {

    companion object {
        private const val REQ_NOTIFICATION = 101
        private const val KEY_ASKED_BEFORE = "asked_notif_permission"

        private const val COLOR_NEON_LIME = 0xFFB2FF05.toInt()
        private const val COLOR_NEON_GLOW_MID = 0xFF4C7A00.toInt()
        private const val COLOR_CRIMSON_PAUSE = 0xFFFF1744.toInt()
        private const val COLOR_CRIMSON_DIM = 0xFF7A1220.toInt()
        private const val COLOR_AMBER_TRANSITION = 0xFFFFD740.toInt()

        // Discrete steps for Delay (in seconds)
        private val DELAY_STEPS = floatArrayOf(0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f)

        // Discrete steps for Angle (Z-thresholds corresponding to 15°, 20°, 25°, 30°, 40°, 50°, 60°)
        private val ANGLE_THRESHOLD_STEPS = floatArrayOf(9.46f, 9.20f, 8.87f, 8.45f, 7.49f, 6.28f, 4.88f)
    }

    private val meditativeInterpolator = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)
    private val fastOutInterpolator = AccelerateInterpolator(1.4f)
    private val argbEvaluator = ArgbEvaluator()

    private lateinit var rootContainer: FrameLayout
    private var currentAppView: AppView = AppView.TOGGLE
    private var activeChildView: View? = null

    private var settingsSensorManager: SensorManager? = null
    private var settingsSensorListener: SensorEventListener? = null
    private var onBatteryStatusRefresh: (() -> Unit)? = null

    private val viewSubscriptions = mutableListOf<() -> Unit>()
    private var backInvokedCallback: Any? = null
    private var isBackCallbackRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { it.remove() }
        }

        FlipState.init(this)
        setupEdgeToEdgeWindow()

        currentAppView = if (isSensorSupported()) AppView.TOGGLE else AppView.UNSUPPORTED
        updateBackHandling(false)

        rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
            alpha = 0f
        }

        rootContainer.setOnApplyWindowInsetsListener { view, windowInsets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            } else {
                @Suppress("DEPRECATION")
                var left = windowInsets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                var top = windowInsets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                var right = windowInsets.systemWindowInsetRight
                @Suppress("DEPRECATION")
                var bottom = windowInsets.systemWindowInsetBottom

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    windowInsets.displayCutout?.let { cutout ->
                        left = max(left, cutout.safeInsetLeft)
                        top = max(top, cutout.safeInsetTop)
                        right = max(right, cutout.safeInsetRight)
                        bottom = max(bottom, cutout.safeInsetBottom)
                    }
                }
                view.setPadding(left, top, right, bottom)
                @Suppress("DEPRECATION")
                windowInsets.consumeSystemWindowInsets()
            }
        }

        setContentView(rootContainer)

        val initialView = createViewFor(currentAppView)
        activeChildView = initialView
        rootContainer.addView(initialView)

        rootContainer.post {
            rootContainer.animate()
                .alpha(1f)
                .setStartDelay(0L)
                .setDuration(1500L)
                .setInterpolator(meditativeInterpolator)
                .start()
        }
    }

    private fun updateBackHandling(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (backInvokedCallback == null) {
                backInvokedCallback = OnBackInvokedCallback {
                    if (currentAppView == AppView.HELP || currentAppView == AppView.SETTINGS) {
                        navigateTo(AppView.TOGGLE)
                    }
                }
            }
            val callback = backInvokedCallback as OnBackInvokedCallback
            if (enabled && !isBackCallbackRegistered) {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    callback
                )
                isBackCallbackRegistered = true
            } else if (!enabled && isBackCallbackRegistered) {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
                isBackCallbackRegistered = false
            }
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (currentAppView == AppView.HELP || currentAppView == AppView.SETTINGS) {
            navigateTo(AppView.TOGGLE)
        } else {
            super.onBackPressed()
        }
    }

    private fun isSensorSupported(): Boolean {
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return (sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)) != null
    }

    @Suppress("DEPRECATION")
    private fun setupEdgeToEdgeWindow() {
        val decorView = window.decorView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView.windowInsetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        }
    }

    override fun onResume() {
        super.onResume()
        onBatteryStatusRefresh?.invoke()
    }

    override fun onDestroy() {
        clearViewSubscriptions()
        cleanupSettingsResources()
        updateBackHandling(false)
        super.onDestroy()
    }

    private fun clearViewSubscriptions() {
        for (unsubscribe in viewSubscriptions) {
            unsubscribe()
        }
        viewSubscriptions.clear()
    }

    private fun cleanupSettingsResources() {
        settingsSensorListener?.let { settingsSensorManager?.unregisterListener(it) }
        settingsSensorListener = null
        settingsSensorManager = null
        onBatteryStatusRefresh = null
        window.decorView.keepScreenOn = false
    }

    private fun navigateTo(destination: AppView) {
        if (destination == currentAppView) return
        clearViewSubscriptions()
        if (currentAppView == AppView.SETTINGS) {
            cleanupSettingsResources()
        }

        val isBack = destination == AppView.TOGGLE &&
                (currentAppView == AppView.HELP || currentAppView == AppView.SETTINGS)

        currentAppView = destination
        updateBackHandling(destination == AppView.HELP || destination == AppView.SETTINGS)

        val outgoingView = activeChildView

        for (i in rootContainer.childCount - 1 downTo 0) {
            val child = rootContainer.getChildAt(i)
            if (child != outgoingView) {
                child.animate().cancel()
                rootContainer.removeViewAt(i)
            }
        }

        val slideDistance = dpF(16f)
        val incomingView = createViewFor(destination).apply {
            alpha = 0f
            translationX = if (isBack) -slideDistance else slideDistance
        }
        activeChildView = incomingView
        rootContainer.addView(incomingView)

        val outDuration = if (isBack) 140L else 170L
        val inDuration = if (isBack) 210L else 250L
        val inDelay = if (isBack) 30L else 45L

        outgoingView?.let { out ->
            out.isClickable = false
            out.animate().cancel()
            out.animate()
                .alpha(0f)
                .translationX(if (isBack) slideDistance else -slideDistance)
                .setStartDelay(0L)
                .setDuration(outDuration)
                .setInterpolator(fastOutInterpolator)
                .withEndAction {
                    rootContainer.removeView(out)
                }
                .start()
        }

        incomingView.animate().cancel()
        incomingView.animate()
            .alpha(1f)
            .translationX(0f)
            .setStartDelay(inDelay)
            .setDuration(inDuration)
            .setInterpolator(meditativeInterpolator)
            .withEndAction(null)
            .start()
    }

    private fun createViewFor(viewType: AppView): View = when (viewType) {
        AppView.UNSUPPORTED -> buildUnsupportedSensorView()
        AppView.TOGGLE -> buildToggleView()
        AppView.HELP -> buildHelpView()
        AppView.SETTINGS -> buildSettingsView()
    }

    // =========================================================================
    // 0. UNSUPPORTED SENSOR VIEW
    // =========================================================================
    private fun buildUnsupportedSensorView(): View {
        val strings = AppStrings.current
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setPadding(dp(36), dp(32), dp(36), dp(32))
        }

        val centerColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER)
        }

        val iconView = UnsupportedSensorIconView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(84)).apply {
                bottomMargin = dp(28)
            }
        }

        val badgeText = TextView(this).apply {
            text = strings.unsupportedBadge
            setTextColor(COLOR_CRIMSON_PAUSE)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 2.2f / 11f
            gravity = Gravity.CENTER
        }

        val titleText = TextView(this).apply {
            text = strings.unsupportedTitle
            setTextColor(0xFFF2F2F2.toInt())
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            letterSpacing = 1.2f / 24f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
        }

        val messageText = TextView(this).apply {
            text = strings.unsupportedMessage
            setTextColor(0xFF787878.toInt())
            typeface = Typeface.SANS_SERIF
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(spF(8f), 1f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(20)
            }
        }

        centerColumn.addView(iconView)
        centerColumn.addView(badgeText)
        centerColumn.addView(titleText)
        centerColumn.addView(messageText)

        val closeHitBox = FrameLayout(this).apply {
            minimumHeight = dp(48)
            setPadding(dp(24), dp(12), dp(24), dp(12))
            layoutParams = FrameLayout.LayoutParams(
                WRAP_CONTENT,
                WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dp(8)
            }
        }

        val closeText = TextView(this).apply {
            text = strings.closeApp
            setTextColor(0xFF666666.toInt())
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 2f / 12f
            gravity = Gravity.CENTER
        }
        closeHitBox.addView(closeText)
        attachPressTintEffect(closeHitBox, 0xFF666666.toInt(), COLOR_CRIMSON_PAUSE, { closeText.setTextColor(it) }) {
            finish()
        }

        container.addView(centerColumn)
        container.addView(closeHitBox)
        return container
    }

    // =========================================================================
    // 1. TOGGLE VIEW
    // =========================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun buildToggleView(): View {
        val strings = AppStrings.current
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        val upperArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            setOnClickListener { handleSwitchClick() }
        }

        val centerStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER)
            setPadding(dp(32), 0, dp(32), 0)
        }

        val powerButtonView = PowerButtonCanvasView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(240), dp(240))
        }

        val instructionTextView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 1.5f / 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                topMargin = dp(24)
            }
        }

        centerStack.addView(powerButtonView)
        centerStack.addView(instructionTextView)
        upperArea.addView(centerStack)

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(100))
        }

        val helpBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
            contentDescription = strings.cdHelp
        }
        val helpIcon = VectorIconView(this, isSettingsGear = false).apply {
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER)
        }
        helpBox.addView(helpIcon)
        attachPressTintEffect(helpBox, 0xFF757575.toInt(), COLOR_NEON_LIME, { helpIcon.tintColor = it }) {
            navigateTo(AppView.HELP)
        }

        val settingsBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
            contentDescription = strings.cdSettings
        }
        val settingsIcon = VectorIconView(this, isSettingsGear = true).apply {
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER)
        }
        settingsBox.addView(settingsIcon)
        attachPressTintEffect(settingsBox, 0xFF757575.toInt(), COLOR_NEON_LIME, { settingsIcon.tintColor = it }) {
            navigateTo(AppView.SETTINGS)
        }

        bottomBar.addView(helpBox)
        bottomBar.addView(settingsBox)

        root.addView(upperArea)
        root.addView(bottomBar)

        var currentDisplayedText = ""
        var colorAnimator: ValueAnimator? = null
        var currentInstructionColor = 0xFF525252.toInt()
        instructionTextView.setTextColor(currentInstructionColor)

        fun updateToggleUi() {
            val s = AppStrings.current
            val running = FlipState.isServiceRunning.value
            val paused = FlipState.isPausedByUs.value
            val reversed = FlipState.isReversed.value

            powerButtonView.setRunning(running)

            // Default (reversed = false): face up pauses, face down resumes.
            // Reversed (reversed = true): face down pauses, face up resumes.
            val newText = when {
                !running -> s.tapToTurnOn
                paused && reversed -> s.putFaceUpToResume
                paused -> s.putFaceDownToResume
                reversed -> s.putFaceDownToPause
                else -> s.putFaceUpToPause
            }

            if (currentDisplayedText.isEmpty()) {
                currentDisplayedText = newText
                instructionTextView.text = newText
            } else if (currentDisplayedText != newText) {
                currentDisplayedText = newText
                instructionTextView.animate().cancel()
                instructionTextView.animate()
                    .alpha(0f)
                    .setStartDelay(0L)
                    .setDuration(120L)
                    .withEndAction {
                        instructionTextView.text = newText
                        instructionTextView.animate()
                            .alpha(1f)
                            .setStartDelay(0L)
                            .setDuration(180L)
                            .start()
                    }
                    .start()
            }

            val targetColor = if (running) {
                setColorAlpha(COLOR_NEON_LIME, (255 * 0.65f).toInt())
            } else {
                0xFF525252.toInt()
            }

            if (targetColor != currentInstructionColor) {
                colorAnimator?.cancel()
                val startColor = currentInstructionColor
                colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 400L
                    interpolator = meditativeInterpolator
                    addUpdateListener { anim ->
                        val c = argbEvaluator.evaluate(anim.animatedFraction, startColor, targetColor) as Int
                        currentInstructionColor = c
                        instructionTextView.setTextColor(c)
                    }
                    start()
                }
            }
        }

        viewSubscriptions += FlipState.isServiceRunning.observe { updateToggleUi() }
        viewSubscriptions += FlipState.isPausedByUs.observe { updateToggleUi() }
        viewSubscriptions += FlipState.isReversed.observe { updateToggleUi() }

        return root
    }

    // =========================================================================
    // 2. HELP VIEW
    // =========================================================================
    private fun buildHelpView(): View {
        val strings = AppStrings.current
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        val topBackHitZone = FrameLayout(this).apply {
            minimumWidth = dp(96)
            minimumHeight = dp(56)
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            setPadding(dp(28), 0, dp(28), 0)
            contentDescription = strings.cdBack
        }

        val backArrow = TextView(this).apply {
            text = "←"
            setTextColor(0xFF6E6E6E.toInt())
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.START)
        }
        topBackHitZone.addView(backArrow)
        attachPressTintEffect(topBackHitZone, 0xFF6E6E6E.toInt(), COLOR_NEON_LIME, { backArrow.setTextColor(it) }) {
            navigateTo(AppView.TOGGLE)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isVerticalScrollBarEnabled = false
        }

        val contentCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPadding(dp(28), dp(8), dp(28), dp(44))
        }

        val title = TextView(this).apply {
            text = strings.appTitle
            setTextColor(COLOR_NEON_LIME)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            letterSpacing = 2.5f / 22f
        }

        val subtitle = TextView(this).apply {
            text = strings.helpSubtitle
            setTextColor(0xFF9E9E9E.toInt())
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setLineSpacing(spF(8f), 1f)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(12)
                bottomMargin = dp(34)
            }
        }

        contentCol.addView(title)
        contentCol.addView(subtitle)

        strings.faqs.forEachIndexed { index, (q, a) ->
            if (index > 0) {
                val divider = View(this).apply {
                    setBackgroundColor(0xFF181818.toInt())
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply {
                        topMargin = dp(24)
                        bottomMargin = dp(24)
                    }
                }
                contentCol.addView(divider)
            }

            val qText = TextView(this).apply {
                text = q
                setTextColor(0xFFEAEAEA.toInt())
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                letterSpacing = 0.3f / 16f
                setLineSpacing(spF(7f), 1f)
            }

            val aText = TextView(this).apply {
                text = a
                setTextColor(0xFF8C8C8C.toInt())
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                setLineSpacing(spF(7.5f), 1f)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    topMargin = dp(10)
                }
            }

            contentCol.addView(qText)
            contentCol.addView(aText)
        }

        scrollView.addView(contentCol)
        root.addView(topBackHitZone)
        root.addView(scrollView)
        return root
    }

    // =========================================================================
    // 3. SETTINGS VIEW
    // =========================================================================
    @SuppressLint("BatteryLife")
    private fun buildSettingsView(): View {
        val strings = AppStrings.current
        window.decorView.keepScreenOn = true

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        // 1. Top Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPadding(0, 0, dp(28), 0)
        }

        val backBox = FrameLayout(this).apply {
            minimumWidth = dp(96)
            minimumHeight = dp(56)
            setPadding(dp(28), 0, 0, 0)
            contentDescription = strings.cdBack
        }
        val backArrow = TextView(this).apply {
            text = "←"
            setTextColor(0xFF6E6E6E.toInt())
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.START)
        }
        backBox.addView(backArrow)
        attachPressTintEffect(backBox, 0xFF6E6E6E.toInt(), COLOR_NEON_LIME, { backArrow.setTextColor(it) }) {
            navigateTo(AppView.TOGGLE)
        }

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        val zReadoutText = TextView(this).apply {
            text = "+9.8 m/s²"
            setTextColor(COLOR_CRIMSON_PAUSE)
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 1f / 12f
        }

        topBar.addView(backBox)
        topBar.addView(spacer)
        topBar.addView(zReadoutText)

        // 2. 3D Orientation Visualizer
        val visualizerView = Device3DVisualizerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }

        // 3. Bottom Controls
        val bottomControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPadding(dp(32), 0, dp(32), dp(20))
        }

        // Reverse Row
        val reverseRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(20)
            }
        }
        val reverseTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val reverseTitle = TextView(this).apply {
            text = strings.reverseTitle
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 2f / 11f
        }
        val reverseSub = TextView(this).apply {
            setTextColor(0xFF525252.toInt())
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.5f / 11f
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                topMargin = dp(3)
            }
        }
        reverseTextCol.addView(reverseTitle)
        reverseTextCol.addView(reverseSub)

        val pillSwitch = HairlinePillSwitchView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(20)).apply {
                marginStart = dp(16)
            }
        }
        reverseRow.addView(reverseTextCol)
        reverseRow.addView(pillSwitch)
        reverseRow.setOnClickListener {
            FlipState.setReversed(!FlipState.isReversed.value)
            FlipMediaToggleService.triggerHaptic(this, 35L)
        }

        // Delay Stepper Row
        val delayValueText = TextView(this)
        val delayStepper = HairlineStepControlView(this, DELAY_STEPS) { newVal ->
            FlipState.setDelay(newVal)
            FlipMediaToggleService.triggerHaptic(this, 25L)
        }
        val delaySection = createStepperSection(strings.delayLabel, delayValueText, delayStepper)

        // Angle Stepper Row
        val angleValueText = TextView(this)
        val angleStepper = HairlineStepControlView(this, ANGLE_THRESHOLD_STEPS) { newVal ->
            FlipState.setThreshold(newVal)
            FlipMediaToggleService.triggerHaptic(this, 25L)
        }
        val angleSection = createStepperSection(strings.angleLabel, angleValueText, angleStepper)

        // Footer Links Row (Reset Defaults + Battery Optimization if needed)
        val footerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        val resetBox = FrameLayout(this).apply {
            minimumHeight = dp(38)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        val resetText = TextView(this).apply {
            text = strings.resetDefaults
            setTextColor(0xFF5E5E5E.toInt())
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 1.2f / 12f
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
        }
        resetBox.addView(resetText)
        attachPressTintEffect(resetBox, 0xFF5E5E5E.toInt(), COLOR_NEON_LIME, { resetText.setTextColor(it) }) {
            FlipState.resetToDefaults()
            FlipMediaToggleService.triggerHaptic(this, 35L)
        }

        val batteryBox = FrameLayout(this).apply {
            minimumHeight = dp(38)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        val batteryText = TextView(this).apply {
            text = strings.batteryHelp
            setTextColor(0xFF5E5E5E.toInt())
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            letterSpacing = 1.2f / 12f
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
        }
        batteryBox.addView(batteryText)
        attachPressTintEffect(batteryBox, 0xFF5E5E5E.toInt(), COLOR_NEON_LIME, { batteryText.setTextColor(it) }) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val refreshBatteryRow = {
            val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
            batteryBox.visibility = if (ignoring) View.GONE else View.VISIBLE
        }
        onBatteryStatusRefresh = refreshBatteryRow
        refreshBatteryRow()

        footerContainer.addView(resetBox)
        footerContainer.addView(batteryBox)

        bottomControls.addView(reverseRow)
        bottomControls.addView(delaySection)
        bottomControls.addView(angleSection)
        bottomControls.addView(footerContainer)

        root.addView(topBar)
        root.addView(visualizerView)
        root.addView(bottomControls)

        // Sensor registration for live 3D visualizer + boundary tick haptics
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        settingsSensorManager = sm

        val gravityFiltered = floatArrayOf(0f, 0f, 9.81f)
        var lastZone: Int? = null // 1 = TOP (face up), 0 = DEADZONE, -1 = BOTTOM (face down)

        fun refreshZReadout(currentZ: Float) {
            val threshold = FlipState.zThreshold.value
            val reversed = FlipState.isReversed.value
            val inPause = if (reversed) currentZ < -threshold else currentZ > threshold
            val inResume = if (reversed) currentZ > threshold else currentZ < -threshold

            val zColor = when {
                inPause -> COLOR_CRIMSON_PAUSE
                inResume -> setColorAlpha(COLOR_NEON_LIME, (255 * 0.85f).toInt())
                else -> 0xFF525252.toInt()
            }
            zReadoutText.setTextColor(zColor)
            zReadoutText.text = String.format(Locale.US, "%+.1f m/s²", currentZ)
        }

        if (sm != null && sensor != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event ?: return
                    val rawZ = event.values[2]
                    val currentThreshold = FlipState.zThreshold.value

                    val currentZone = when {
                        rawZ > currentThreshold -> 1
                        rawZ < -currentThreshold -> -1
                        else -> 0
                    }

                    if (lastZone != null && currentZone != lastZone) {
                        FlipMediaToggleService.triggerHaptic(this@ViewActivity, 35L)
                    }
                    lastZone = currentZone

                    gravityFiltered[0] = gravityFiltered[0] * 0.8f + event.values[0] * 0.2f
                    gravityFiltered[1] = gravityFiltered[1] * 0.8f + event.values[1] * 0.2f
                    gravityFiltered[2] = gravityFiltered[2] * 0.8f + rawZ * 0.2f

                    visualizerView.updateGravity(gravityFiltered[0], gravityFiltered[1], gravityFiltered[2])
                    refreshZReadout(gravityFiltered[2])
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            settingsSensorListener = listener
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        fun syncSettingsControls() {
            val s = AppStrings.current
            val rev = FlipState.isReversed.value
            val delaySec = FlipState.minPauseDelaySeconds.value
            val thresh = FlipState.zThreshold.value

            reverseTitle.setTextColor(if (rev) 0xFFDADADA.toInt() else 0xFF6E6E6E.toInt())
            reverseSub.text = if (rev) s.reverseSubInverted else s.reverseSubDefault
            pillSwitch.setChecked(rev)

            delayValueText.text = String.format(Locale.US, "%.1fs", delaySec)
            delayStepper.setCurrentValue(delaySec)

            val coneAngleDeg = Math.toDegrees(acos((thresh / 9.81f).coerceIn(0f, 1f)).toDouble()).toInt()
            angleValueText.text = "${coneAngleDeg}°"
            angleStepper.setCurrentValue(thresh)

            val isDefault = abs(delaySec - FlipState.DEFAULT_DELAY) < 0.05f &&
                    abs(thresh - FlipState.DEFAULT_THRESHOLD) < 0.05f &&
                    rev == FlipState.DEFAULT_REVERSED
            resetBox.visibility = if (isDefault) View.INVISIBLE else View.VISIBLE

            visualizerView.updateConfig(thresh, rev)
            refreshZReadout(gravityFiltered[2])
        }

        viewSubscriptions += FlipState.isReversed.observe { syncSettingsControls() }
        viewSubscriptions += FlipState.minPauseDelaySeconds.observe { syncSettingsControls() }
        viewSubscriptions += FlipState.zThreshold.observe { syncSettingsControls() }

        return root
    }

    private fun createStepperSection(
        labelText: String,
        valueTextView: TextView,
        stepperView: HairlineStepControlView
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(18)
            }

            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            }

            val label = TextView(context).apply {
                text = labelText
                setTextColor(0xFF6E6E6E.toInt())
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                letterSpacing = 2f / 11f
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }

            valueTextView.apply {
                setTextColor(0xFFB5B5B5.toInt())
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                letterSpacing = 1f / 12f
            }

            headerRow.addView(label)
            headerRow.addView(valueTextView)

            stepperView.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(32))

            addView(headerRow)
            addView(stepperView)
        }
    }

    // =========================================================================
    // CUSTOM CANVAS VIEWS
    // =========================================================================

    private inner class UnsupportedSensorIconView(context: Context) : View(context) {
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpF(1.5f)
            color = 0xFF262626.toInt()
        }
        private val phonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpF(2f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = 0xFF707070.toInt()
        }
        private val slashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpF(2.2f)
            strokeCap = Paint.Cap.ROUND
            color = COLOR_CRIMSON_PAUSE
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = setColorAlpha(COLOR_CRIMSON_PAUSE, (255 * 0.10f).toInt())
        }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val outerR = min(width, height) / 2f - dpF(3f)

            canvas.drawCircle(cx, cy, outerR, glowPaint)
            canvas.drawCircle(cx, cy, outerR, ringPaint)

            val pw = dpF(18f)
            val ph = dpF(30f)
            rect.set(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f)
            canvas.drawRoundRect(rect, dpF(3.5f), dpF(3.5f), phonePaint)

            val slashOffset = dpF(16f)
            canvas.drawLine(
                cx - slashOffset,
                cy - slashOffset,
                cx + slashOffset,
                cy + slashOffset,
                slashPaint
            )
        }
    }

    private inner class PowerButtonCanvasView(context: Context) : View(context) {
        private var glowProgress = 0f
        private var coreColor = 0xFF141414.toInt()
        private var iconColor = 0xFF424242.toInt()
        private var pulseScale = 1f
        private var isRunningState: Boolean? = null

        private var stateAnimator: ValueAnimator? = null
        private val pulseAnimator = ValueAnimator.ofFloat(0.96f, 1.05f).apply {
            duration = 3000L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                if (glowProgress > 0.01f) invalidate()
            }
        }

        private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpF(1f)
        }
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpF(4.2f)
            strokeCap = Paint.Cap.ROUND
        }
        private val arcRect = RectF()

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            pulseAnimator.start()
        }

        override fun onDetachedFromWindow() {
            pulseAnimator.cancel()
            stateAnimator?.cancel()
            super.onDetachedFromWindow()
        }

        fun setRunning(running: Boolean) {
            if (isRunningState == null) {
                isRunningState = running
                glowProgress = if (running) 1f else 0f
                coreColor = if (running) COLOR_NEON_LIME else 0xFF141414.toInt()
                iconColor = if (running) 0xFF0A0D02.toInt() else 0xFF424242.toInt()
                invalidate()
                return
            }
            if (isRunningState == running) return
            isRunningState = running

            stateAnimator?.cancel()
            val startGlow = glowProgress
            val targetGlow = if (running) 1f else 0f
            val startCore = coreColor
            val targetCore = if (running) COLOR_NEON_LIME else 0xFF141414.toInt()
            val startIcon = iconColor
            val targetIcon = if (running) 0xFF0A0D02.toInt() else 0xFF424242.toInt()

            stateAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 500L
                interpolator = meditativeInterpolator
                addUpdateListener { anim ->
                    val f = anim.animatedFraction
                    glowProgress = startGlow + (targetGlow - startGlow) * f
                    coreColor = argbEvaluator.evaluate(f, startCore, targetCore) as Int
                    iconColor = argbEvaluator.evaluate(f, startIcon, targetIcon) as Int
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val coreRadius = dpF(64f)
            val middleHaloRadius = dpF(82f) * (if (isRunningState == true) pulseScale else 1f)
            val outerOrbitRadius = dpF(106f) * 1.15f

            if (glowProgress > 0.01f && outerOrbitRadius > 0f) {
                auraPaint.shader = RadialGradient(
                    cx, cy, outerOrbitRadius,
                    intArrayOf(
                        setColorAlpha(COLOR_NEON_LIME, (255 * 0.26f * glowProgress).toInt()),
                        setColorAlpha(COLOR_NEON_GLOW_MID, (255 * 0.12f * glowProgress).toInt()),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, outerOrbitRadius, auraPaint)

                haloPaint.color = setColorAlpha(COLOR_NEON_LIME, (255 * 0.16f * glowProgress).toInt())
                canvas.drawCircle(cx, cy, middleHaloRadius, haloPaint)
            }

            corePaint.color = coreColor
            canvas.drawCircle(cx, cy, coreRadius, corePaint)

            if (glowProgress < 0.99f) {
                rimPaint.color = setColorAlpha(0xFF262626.toInt(), (255 * (1f - glowProgress)).toInt())
                canvas.drawCircle(cx, cy, coreRadius, rimPaint)
            }

            val iconRadius = dpF(15f)
            val yOffset = dpF(2f)
            iconPaint.color = iconColor
            arcRect.set(cx - iconRadius, cy - iconRadius + yOffset, cx + iconRadius, cy + iconRadius + yOffset)
            canvas.drawArc(arcRect, -56f, 292f, false, iconPaint)
            canvas.drawLine(cx, cy - iconRadius - dpF(4f), cx, cy - dpF(1f), iconPaint)
        }
    }

    private inner class VectorIconView(context: Context, private val isSettingsGear: Boolean) : View(context) {
        var tintColor: Int = 0xFF757575.toInt()
            set(value) {
                field = value
                invalidate()
            }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpF(2f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val path = Path()
        private val arcRect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            strokePaint.color = tintColor
            fillPaint.color = tintColor

            if (!isSettingsGear) {
                canvas.drawCircle(cx, cy, dpF(12f), strokePaint)
                val hookRadius = dpF(3.4f)
                val hookCenterY = cy - dpF(2.4f)
                path.reset()
                path.moveTo(cx - hookRadius, hookCenterY)
                arcRect.set(cx - hookRadius, hookCenterY - hookRadius, cx + hookRadius, hookCenterY + hookRadius)
                path.arcTo(arcRect, 180f, 225f, false)
                path.lineTo(cx, cy + dpF(1.8f))
                canvas.drawPath(path, strokePaint)
                canvas.drawCircle(cx, cy + dpF(5.2f), dpF(1.3f), fillPaint)
            } else {
                canvas.drawCircle(cx, cy, dpF(4.2f), strokePaint)
                val teeth = 8
                val innerR = dpF(9.2f)
                val outerR = dpF(12.2f)
                val step = (2.0 * Math.PI) / teeth
                path.reset()
                for (i in 0 until teeth) {
                    val base = i * step
                    val a1 = base - step * 0.24
                    val a2 = base - step * 0.14
                    val a3 = base + step * 0.14
                    val a4 = base + step * 0.24

                    val x1 = cx + innerR * cos(a1).toFloat()
                    val y1 = cy + innerR * sin(a1).toFloat()
                    val x2 = cx + outerR * cos(a2).toFloat()
                    val y2 = cy + outerR * sin(a2).toFloat()
                    val x3 = cx + outerR * cos(a3).toFloat()
                    val y3 = cy + outerR * sin(a3).toFloat()
                    val x4 = cx + innerR * cos(a4).toFloat()
                    val y4 = cy + innerR * sin(a4).toFloat()

                    if (i == 0) path.moveTo(x1, y1) else path.lineTo(x1, y1)
                    path.lineTo(x2, y2)
                    path.lineTo(x3, y3)
                    path.lineTo(x4, y4)
                }
                path.close()
                canvas.drawPath(path, strokePaint)
            }
        }
    }

    private inner class HairlinePillSwitchView(context: Context) : View(context) {
        private var progress = 0f
        private var checkedState: Boolean? = null
        private var anim: ValueAnimator? = null

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpF(1.5f)
        }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val rect = RectF()

        fun setChecked(checked: Boolean) {
            if (checkedState == null) {
                checkedState = checked
                progress = if (checked) 1f else 0f
                invalidate()
                return
            }
            if (checkedState == checked) return
            checkedState = checked
            anim?.cancel()
            anim = ValueAnimator.ofFloat(progress, if (checked) 1f else 0f).apply {
                duration = 200L
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val stroke = trackPaint.strokeWidth
            val halfStroke = stroke / 2f
            rect.set(halfStroke, halfStroke, width - halfStroke, height - halfStroke)
            val radius = rect.height() / 2f

            val activeTrack = setColorAlpha(COLOR_NEON_LIME, (255 * 0.25f).toInt())
            trackPaint.color = argbEvaluator.evaluate(progress, 0xFF1F1F1F.toInt(), activeTrack) as Int
            canvas.drawRoundRect(rect, radius, radius, trackPaint)

            val minX = height / 2f
            val maxX = width - height / 2f
            val cx = minX + (maxX - minX) * progress
            thumbPaint.color = argbEvaluator.evaluate(progress, 0xFF6E6E6E.toInt(), COLOR_NEON_LIME) as Int
            canvas.drawCircle(cx, height / 2f, dpF(4.5f), thumbPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private inner class HairlineStepControlView(
        context: Context,
        private val steps: FloatArray,
        private val onStepSelected: (Float) -> Unit
    ) : View(context) {

        private var currentIndex = 0

        private val segmentFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val segmentStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpF(1f)
        }
        private val rect = RectF()

        fun setCurrentValue(value: Float) {
            var bestIdx = 0
            var minDiff = Float.MAX_VALUE
            for (i in steps.indices) {
                val diff = abs(steps[i] - value)
                if (diff < minDiff) {
                    minDiff = diff
                    bestIdx = i
                }
            }
            if (currentIndex != bestIdx) {
                currentIndex = bestIdx
                invalidate()
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    selectSegmentAt(event.x)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    selectSegmentAt(event.x)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun selectSegmentAt(x: Float) {
            if (steps.isEmpty()) return
            val trackStart = paddingLeft + dpF(1f)
            val trackEnd = (width - paddingRight - dpF(1f)).coerceAtLeast(trackStart + 1f)
            val fraction = ((x - trackStart) / (trackEnd - trackStart)).coerceIn(0f, 0.9999f)
            val targetIdx = (fraction * steps.size).toInt().coerceIn(0, steps.lastIndex)
            if (targetIdx != currentIndex) {
                currentIndex = targetIdx
                onStepSelected(steps[currentIndex])
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (steps.isEmpty()) return

            val cy = height / 2f
            val segGap = dpF(5f)
            val trackStart = paddingLeft + dpF(1f)
            val trackEnd = (width - paddingRight - dpF(1f)).coerceAtLeast(trackStart + 1f)
            val totalWidth = trackEnd - trackStart
            val count = steps.size
            val segWidth = ((totalWidth - segGap * (count - 1)) / count).coerceAtLeast(dpF(4f))

            for (i in 0 until count) {
                val left = trackStart + i * (segWidth + segGap)
                val right = left + segWidth
                val isSelected = i == currentIndex
                val isPassed = i < currentIndex

                val segH = if (isSelected) dpF(12f) else dpF(6f)
                val top = cy - segH / 2f
                val bot = cy + segH / 2f
                val r = segH / 2f
                rect.set(left, top, right, bot)

                when {
                    isSelected -> {
                        segmentFillPaint.color = COLOR_NEON_LIME
                        canvas.drawRoundRect(rect, r, r, segmentFillPaint)
                    }
                    isPassed -> {
                        segmentFillPaint.color = setColorAlpha(COLOR_NEON_LIME, (255 * 0.35f).toInt())
                        canvas.drawRoundRect(rect, r, r, segmentFillPaint)
                    }
                    else -> {
                        segmentFillPaint.color = 0xFF161616.toInt()
                        canvas.drawRoundRect(rect, r, r, segmentFillPaint)
                        segmentStrokePaint.color = 0xFF262626.toInt()
                        canvas.drawRoundRect(rect, r, r, segmentStrokePaint)
                    }
                }
            }
        }
    }

    private inner class Device3DVisualizerView(context: Context) : View(context) {
        private var gx = 0f
        private var gy = 0f
        private var gz = 9.81f
        private var zThreshold = FlipState.DEFAULT_THRESHOLD
        private var isReversed = FlipState.DEFAULT_REVERSED

        private val topPath = Path()
        private val bottomPath = Path()
        private val screenPath = Path()

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        fun updateGravity(x: Float, y: Float, z: Float) {
            gx = x
            gy = y
            gz = z
            invalidate()
        }

        fun updateConfig(threshold: Float, reversed: Boolean) {
            zThreshold = threshold
            isReversed = reversed
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val scale = min(width, height) * 0.38f
            val cameraDist = 4.2f

            val camPitch = Math.toRadians(22.0).toFloat()
            val cosCam = cos(camPitch)
            val sinCam = sin(camPitch)

            fun projectX(x: Float, y: Float, z: Float): Float {
                val rz = y * sinCam + z * cosCam
                val perspective = cameraDist / (cameraDist - rz).coerceAtLeast(0.5f)
                return cx + x * scale * perspective
            }

            fun projectY(y: Float, z: Float): Float {
                val ry = y * cosCam - z * sinCam
                val rz = y * sinCam + z * cosCam
                val perspective = cameraDist / (cameraDist - rz).coerceAtLeast(0.5f)
                return cy - ry * scale * perspective
            }

            val normG = 9.81f
            val cosTheta = (zThreshold / normG).coerceIn(0.1f, 0.99f)
            val sinTheta = sqrt(1f - cosTheta * cosTheta)

            val coneLen = 1.25f
            val ringY = coneLen * cosTheta
            val ringR = coneLen * sinTheta
            val segments = 32

            val inTopCone = gz > zThreshold
            val inBottomCone = gz < -zThreshold

            val topActiveColor = if (isReversed) COLOR_NEON_LIME else COLOR_CRIMSON_PAUSE
            val topIdleColor = setColorAlpha(
                if (isReversed) COLOR_NEON_GLOW_MID else COLOR_CRIMSON_DIM,
                (255 * 0.45f).toInt()
            )
            val bottomActiveColor = if (isReversed) COLOR_CRIMSON_PAUSE else COLOR_NEON_LIME
            val bottomIdleColor = setColorAlpha(
                if (isReversed) COLOR_CRIMSON_DIM else COLOR_NEON_GLOW_MID,
                (255 * 0.45f).toInt()
            )

            val topConeColor = if (inTopCone) topActiveColor else topIdleColor
            val bottomConeColor = if (inBottomCone) bottomActiveColor else bottomIdleColor

            topPath.reset()
            bottomPath.reset()
            val originX = projectX(0f, 0f, 0f)
            val originY = projectY(0f, 0f)

            for (i in 0..segments) {
                val angle = (i.toFloat() / segments) * (2f * PI.toFloat())
                val rx = ringR * cos(angle)
                val rz = ringR * sin(angle)

                val pTopX = projectX(rx, ringY, rz)
                val pTopY = projectY(ringY, rz)
                val pBotX = projectX(rx, -ringY, rz)
                val pBotY = projectY(-ringY, rz)

                if (i == 0) {
                    topPath.moveTo(pTopX, pTopY)
                    bottomPath.moveTo(pBotX, pBotY)
                } else {
                    topPath.lineTo(pTopX, pTopY)
                    bottomPath.lineTo(pBotX, pBotY)
                }

                if (i % 4 == 0 && i < segments) {
                    linePaint.strokeWidth = 1.2f
                    linePaint.color = setColorAlpha(topConeColor, (255 * 0.2f).toInt())
                    canvas.drawLine(originX, originY, pTopX, pTopY, linePaint)

                    linePaint.color = setColorAlpha(bottomConeColor, (255 * 0.2f).toInt())
                    canvas.drawLine(originX, originY, pBotX, pBotY, linePaint)
                }
            }

            linePaint.color = topConeColor
            linePaint.strokeWidth = if (inTopCone) 4f else 1.5f
            canvas.drawPath(topPath, linePaint)

            linePaint.color = bottomConeColor
            linePaint.strokeWidth = if (inBottomCone) 4f else 1.5f
            canvas.drawPath(bottomPath, linePaint)

            val gMag = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(0.1f)
            val nx = gx / gMag
            val ny = gy / gMag
            val nz = gz / gMag

            val pitch = atan2(ny, nz)
            val roll = atan2(-nx, sqrt(ny * ny + nz * nz))

            fun rotateDeviceToWorld(px: Float, py: Float, pz: Float): FloatArray {
                val x1 = px * cos(roll) + pz * sin(roll)
                val y1 = py
                val z1 = -px * sin(roll) + pz * cos(roll)

                val x2 = x1
                val y2 = y1 * cos(pitch) - z1 * sin(pitch)
                val z2 = y1 * sin(pitch) + z1 * cos(pitch)

                return floatArrayOf(x2, z2, -y2)
            }

            val w = 0.42f
            val h = 0.82f
            val d = 0.06f

            val rawVertices = arrayOf(
                floatArrayOf(-w, -h, d), floatArrayOf(w, -h, d),
                floatArrayOf(w, h, d), floatArrayOf(-w, h, d),
                floatArrayOf(-w, -h, -d), floatArrayOf(w, -h, -d),
                floatArrayOf(w, h, -d), floatArrayOf(-w, h, -d)
            )

            val projX = FloatArray(8)
            val projY = FloatArray(8)
            for (i in 0 until 8) {
                val v = rawVertices[i]
                val world = rotateDeviceToWorld(v[0], v[1], v[2])
                projX[i] = projectX(world[0], world[1], world[2])
                projY[i] = projectY(world[1], world[2])
            }

            screenPath.reset()
            screenPath.moveTo(projX[0], projY[0])
            screenPath.lineTo(projX[1], projY[1])
            screenPath.lineTo(projX[2], projY[2])
            screenPath.lineTo(projX[3], projY[3])
            screenPath.close()

            val isScreenFacingPauseSide = if (isReversed) gz < 0 else gz > 0
            fillPaint.color = if (isScreenFacingPauseSide) {
                setColorAlpha(COLOR_CRIMSON_PAUSE, (255 * 0.12f).toInt())
            } else {
                setColorAlpha(COLOR_NEON_LIME, (255 * 0.10f).toInt())
            }
            canvas.drawPath(screenPath, fillPaint)

            val edges = arrayOf(
                0 to 1, 1 to 2, 2 to 3, 3 to 0,
                4 to 5, 5 to 6, 6 to 7, 7 to 4,
                0 to 4, 1 to 5, 2 to 6, 3 to 7
            )
            for ((s, e) in edges) {
                val isScreenEdge = s < 4 && e < 4
                linePaint.color = if (isScreenEdge) {
                    setColorAlpha(COLOR_NEON_LIME, (255 * 0.8f).toInt())
                } else {
                    0xFF3A3A3A.toInt()
                }
                linePaint.strokeWidth = if (isScreenEdge) 3f else 1.5f
                canvas.drawLine(projX[s], projY[s], projX[e], projY[e], linePaint)
            }

            val vecTipWorld = rotateDeviceToWorld(0f, 0f, coneLen)
            val vecTipX = projectX(vecTipWorld[0], vecTipWorld[1], vecTipWorld[2])
            val vecTipY = projectY(vecTipWorld[1], vecTipWorld[2])

            val vectorColor = when {
                inTopCone -> topActiveColor
                inBottomCone -> bottomActiveColor
                else -> COLOR_AMBER_TRANSITION
            }

            linePaint.color = vectorColor
            linePaint.strokeWidth = 5.5f
            linePaint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(originX, originY, vecTipX, vecTipY, linePaint)

            fillPaint.color = vectorColor
            canvas.drawCircle(vecTipX, vecTipY, 9f, fillPaint)
        }
    }

    // =========================================================================
    // PERMISSIONS, SERVICE COMMANDS & UI HELPERS
    // =========================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun attachPressTintEffect(
        target: View,
        idleColor: Int,
        pressedColor: Int,
        applyColor: (Int) -> Unit,
        onClick: () -> Unit
    ) {
        var anim: ValueAnimator? = null
        var currentColor = idleColor
        applyColor(idleColor)

        fun animateTo(dest: Int) {
            anim?.cancel()
            val start = currentColor
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 140L
                addUpdateListener {
                    currentColor = argbEvaluator.evaluate(it.animatedFraction, start, dest) as Int
                    applyColor(currentColor)
                }
                start()
            }
        }

        target.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    animateTo(pressedColor)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    animateTo(idleColor)
                    if (event.x in 0f..v.width.toFloat() && event.y in 0f..v.height.toFloat()) {
                        onClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    animateTo(idleColor)
                    true
                }
                else -> false
            }
        }
    }

    private fun handleSwitchClick() {
        if (!isSensorSupported()) {
            navigateTo(AppView.UNSUPPORTED)
            return
        }
        if (FlipState.isServiceRunning.value) {
            sendServiceCommand(start = false)
            return
        }
        if (!hasNotificationPermission()) {
            requestOrOpenNotificationSettings()
            return
        }
        sendServiceCommand(start = true)
    }

    private fun hasNotificationPermission(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !nm.areNotificationsEnabled()) {
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestOrOpenNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val prefs = getSharedPreferences(FlipState.PREFS_NAME, Context.MODE_PRIVATE)
            val askedBefore = prefs.getBoolean(KEY_ASKED_BEFORE, false)
            val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)

            if (!askedBefore || shouldShowRationale) {
                prefs.edit().putBoolean(KEY_ASKED_BEFORE, true).apply()
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIFICATION
                )
            } else {
                openAppNotificationSettings()
            }
        } else {
            openAppNotificationSettings()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendServiceCommand(start = true)
            }
        }
    }

    private fun openAppNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }
        startActivity(intent)
    }

    private fun sendServiceCommand(start: Boolean) {
        val intent = Intent(this, FlipMediaToggleService::class.java).apply {
            putExtra(
                if (start) FlipMediaToggleService.START_ACTION else FlipMediaToggleService.STOP_ACTION,
                true
            )
        }
        if (start && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun setColorAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun dpF(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun spF(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}