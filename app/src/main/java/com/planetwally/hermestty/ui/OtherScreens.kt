package com.planetwally.hermestty.ui

import com.planetwally.hermestty.ChatViewModel
import com.planetwally.hermestty.Screen
import com.planetwally.hermestty.SessionInfo
import com.planetwally.hermestty.Tone
import com.planetwally.hermestty.BuildConfig
import com.planetwally.hermestty.PairRequest
import com.planetwally.hermestty.transportWarning
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private fun ago(epochSeconds: Double): String {
    if (epochSeconds <= 0) return "—"
    val s = (System.currentTimeMillis() / 1000.0 - epochSeconds).toLong().coerceAtLeast(0)
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m ago"
        s < 86400 -> "${s / 3600}h ago"
        else -> "${s / 86400}d ago"
    }
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(Hx.panel).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(title, Modifier.weight(1f).padding(vertical = 8.dp), color = Hx.title, bold = true)
        if (onBack != null) BracketButton("✕") { onBack() }
    }
}

@Composable
fun SessionsScreen(vm: ChatViewModel) {
    var apiOnly by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Hx.bg)) {
        Header("≡ SESSIONS") { vm.screen = Screen.TERMINAL }
        Row(Modifier.padding(horizontal = 4.dp)) {
            BracketButton("+ new") { vm.newSession() }
            BracketButton("refresh", enabled = !vm.sessionsLoading) { vm.refreshSessions() }
            BracketButton(if (apiOnly) "api only" else "all sources", Hx.label) { apiOnly = !apiOnly }
        }
        Rule(Hx.border)
        when {
            vm.sessionsLoading && vm.sessions.isEmpty() -> T("  loading…", Modifier.padding(10.dp), color = Hx.muted)
            vm.sessionsError != null -> T("  ✗ ${vm.sessionsError}", Modifier.padding(10.dp), color = Hx.error)
        }
        val shown = vm.sessions.filter { !apiOnly || it.source == "api_server" }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { s -> SessionRow(s, s.id == vm.session?.id) { vm.resume(s) } }
        }
    }
}

@Composable
private fun SessionRow(s: SessionInfo, current: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (current) Hx.selection else Hx.bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row {
            T(if (current) "▶ " else "▸ ", color = Hx.accent)
            T(s.label, color = if (current) Hx.title else Hx.text, maxLines = 1)
        }
        T(
            "  ${s.source ?: "?"} · ${s.messageCount} msgs · ${ago(s.lastActive)}",
            color = Hx.muted, scale = 0.85f, maxLines = 1,
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Uri,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        T(label, color = Hx.label, scale = 0.85f)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = termStyle(Hx.text),
            cursorBrush = SolidColor(Hx.title),
            visualTransformation = if (secret) PasswordVisualTransformation('•') else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            decorationBox = { inner ->
                Row {
                    T("❯ ", color = Hx.accent)
                    Column(Modifier.weight(1f)) {
                        if (value.isEmpty()) T(placeholder, color = Hx.muted, maxLines = 1)
                        inner()
                    }
                }
            },
        )
        Rule(Hx.border)
    }
}

@Composable
fun SetupScreen(vm: ChatViewModel) {
    var url by rememberSaveable { mutableStateOf(vm.prefs.baseUrl) }
    var key by rememberSaveable { mutableStateOf(vm.prefs.apiKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<Pair<String, Tone>>() }
    val scope = rememberCoroutineScope()
    val canSave = url.isNotBlank() && key.isNotBlank()
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val code = result.contents ?: return@rememberLauncherForActivityResult // cancelled
        if (!vm.pair(code)) {
            log.clear(); log += "✗ not a Hermes TTY pairing code" to Tone.ERROR
        }
    }

    fun scan() {
        scanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("scan the code from pair.py")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    Column(Modifier.fillMaxSize().background(Hx.bg)) {
        Header("⚙ SETTINGS", if (vm.prefs.configured) ({ vm.screen = Screen.TERMINAL }) else null)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            T("── gateway ──", color = Hx.dim)
            BracketButton("▣ scan QR", Hx.title) { scan() }
            T("run ./pair.py on the Hermes machine and scan the code — or enter details by hand:", color = Hx.muted, scale = 0.85f)
            Field("server url", url, { url = it }, "http://192.168.1.52:8642")
            if (url.isNotBlank()) transportWarning(url)?.let { (msg, tone) -> T(msg, color = tone.color(), scale = 0.85f) }
            Field(
                "api key (API_SERVER_KEY)", key, { key = it.trim() }, "paste key",
                secret = !showKey, keyboard = KeyboardType.Password,
            )
            Row {
                BracketButton(if (showKey) "hide key" else "show key", Hx.label) { showKey = !showKey }
                BracketButton("test", enabled = canSave && !busy) {
                    busy = true
                    log.clear()
                    log += "… testing" to Tone.DIM
                    scope.launch {
                        val result = vm.probe(url, key)
                        log.clear(); log += result
                        busy = false
                    }
                }
                BracketButton("save", Hx.title, enabled = canSave) { vm.saveSettings(url, key) }
            }
            log.forEach { (line, tone) -> T(line, color = tone.color(), scale = 0.9f) }

            Spacer(Modifier.height(16.dp))
            T("── security ──", color = Hx.dim)
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("fingerprint lock ", color = Hx.label)
                BracketButton(if (vm.lockEnabled) "on" else "off", if (vm.lockEnabled) Hx.ok else Hx.muted) {
                    vm.setLock(!vm.lockEnabled)
                }
            }
            T("asks on launch and after 5 min in the background; falls back to your PIN/pattern", color = Hx.muted, scale = 0.85f)

            Spacer(Modifier.height(16.dp))
            T("── display ──", color = Hx.dim)
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("text size ", color = Hx.label)
                BracketButton("-") { vm.setFont(vm.fontSize - 1) }
                T("${vm.fontSize}sp", color = Hx.text)
                BracketButton("+") { vm.setFont(vm.fontSize + 1) }
            }

            Spacer(Modifier.height(16.dp))
            T("── host setup ──", color = Hx.dim)
            T(
                "On the Hermes machine, in ~/.hermes/.env:\n\n" +
                    "  API_SERVER_ENABLED=true\n" +
                    "  API_SERVER_KEY=<long random secret>\n" +
                    "  API_SERVER_HOST=0.0.0.0\n\n" +
                    "then restart the gateway (hermes gateway restart). " +
                    "Use the machine's LAN IP at home, or its Tailscale IP from anywhere. " +
                    "Port 8642 must be open in the host firewall. " +
                    "Don't expose it to the open internet — it runs tools with your permissions.",
                color = Hx.muted, scale = 0.85f,
            )

            Spacer(Modifier.height(16.dp))
            BracketButton("about & licenses", Hx.label) { vm.screen = Screen.ABOUT }
        }
    }
}

/** Shown over everything when a pairing link arrives, so a stray link can't silently swap the server. */
@Composable
fun PairConfirm(req: PairRequest, current: String?, onConfirm: () -> Unit, onReject: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Hx.bg).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Header("▣ PAIR WITH SERVER", null)
        Spacer(Modifier.height(12.dp))
        T("connect to", color = Hx.label)
        T(req.host, color = Hx.title, bold = true)
        transportWarning(req.url)?.let { (msg, tone) -> T(msg, color = tone.color(), scale = 0.85f) }
        if (!current.isNullOrBlank() && current != req.host) {
            Spacer(Modifier.height(8.dp))
            T("this replaces your current server:", color = Hx.label, scale = 0.9f)
            T(current, color = Hx.text, scale = 0.9f)
        }
        Spacer(Modifier.height(12.dp))
        T(
            "That server will receive everything you type and can ask you to approve commands. " +
                "Only continue if you just ran pair.py on your own Hermes machine.",
            color = Hx.muted, scale = 0.85f,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            BracketButton("connect", Hx.title, onClick = onConfirm)
            BracketButton("cancel", Hx.label, onClick = onReject)
        }
    }
}

private val NOTICES = listOf(
    "Hermes TTY" to "MIT · © 2026 Wally (planetwally.com)",
    "hermes-agent colour skin" to "MIT · © 2025 Nous Research",
    "JetBrains Mono" to "SIL Open Font License 1.1 · © 2020 The JetBrains Mono Project Authors",
    "OkHttp · Okio" to "Apache 2.0 · © Square, Inc.",
    "ZXing · zxing-android-embedded" to "Apache 2.0 · © ZXing authors, JourneyApps",
    "AndroidX · Jetpack Compose" to "Apache 2.0 · © The Android Open Source Project",
    "Kotlin · kotlinx.coroutines" to "Apache 2.0 · © JetBrains s.r.o.",
)

@Composable
fun AboutScreen(vm: ChatViewModel) {
    val context = LocalContext.current
    val fullText = remember {
        runCatching { context.assets.open("licenses.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    var showFull by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Hx.bg)) {
        Header("ⓘ ABOUT") { vm.screen = if (vm.prefs.configured) Screen.TERMINAL else Screen.SETUP }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            T("⚕ Hermes TTY ${BuildConfig.VERSION_NAME}", color = Hx.title, bold = true)
            T("github.com/planetwally/hermes-tty", color = Hx.muted, scale = 0.85f)
            Spacer(Modifier.height(12.dp))
            T(
                "An independent, unofficial client for Hermes Agent. Not affiliated with or endorsed by " +
                    "Nous Research. \"Hermes\" and \"Hermes Agent\" are used only to say what it works with.",
                color = Hx.text, scale = 0.9f,
            )
            Spacer(Modifier.height(16.dp))
            T("── licenses ──", color = Hx.dim)
            NOTICES.forEach { (what, license) ->
                T(what, color = Hx.label, scale = 0.9f)
                T("  $license", color = Hx.muted, scale = 0.85f)
            }
            Spacer(Modifier.height(8.dp))
            BracketButton(if (showFull) "hide full texts" else "show full texts", Hx.label) { showFull = !showFull }
            if (showFull) T(fullText, color = Hx.muted, scale = 0.75f)
        }
    }
}
