package com.planetwally.hermestty

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

enum class Screen { TERMINAL, SESSIONS, SETUP, ABOUT }
enum class Conn { UNCONFIGURED, CONNECTING, ONLINE, RECONNECTING, OFFLINE }
enum class Tone { DIM, INFO, OK, WARN, ERROR }

private val lineIds = AtomicLong()

/** One row of the terminal transcript. Streaming rows expose Compose state so deltas don't rebuild the list. */
sealed class Line {
    val id: Long = lineIds.incrementAndGet()
}

class BannerLine : Line()
class UserLine(val text: String) : Line()
class InfoLine(val text: String, val tone: Tone = Tone.INFO) : Line()

class AssistantLine(text: String = "", streaming: Boolean = false, interim: Boolean = false) : Line() {
    var text by mutableStateOf(text)
    var streaming by mutableStateOf(streaming)
    /** Text written between tool calls — rendered as commentary rather than a boxed response. */
    var interim by mutableStateOf(interim)
}

class ToolLine(val name: String, val preview: String, done: Boolean = false) : Line() {
    var done by mutableStateOf(done)
    var error by mutableStateOf(false)
    var duration by mutableStateOf<Double?>(null)
    var result by mutableStateOf<String?>(null)
}

class ApprovalLine(
    val runId: String,
    val command: String,
    val description: String?,
    val choices: List<String>,
) : Line() {
    var resolved by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
}

val COMMANDS = listOf(
    "/new" to "start a fresh session",
    "/sessions" to "browse & resume sessions",
    "/stop" to "interrupt the running turn",
    "/title" to "rename this session: /title <name>",
    "/reload" to "re-fetch this session's transcript",
    "/clear" to "clear the screen (local only)",
    "/settings" to "connection & display settings",
    "/about" to "version, disclaimer & licenses",
    "/help" to "show commands",
)

private val TERMINAL_STATUSES = setOf("completed", "failed", "cancelled", "interrupted")
private val IMAGE_DATA_URL = Regex("""data:image/[\w.+-]+;base64,[A-Za-z0-9+/=]+""")

fun cleanText(s: String) = s.replace(IMAGE_DATA_URL, "[image]")

fun Throwable.brief(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private var api: HermesApi? = null

    val lines = mutableStateListOf<Line>()
    var screen by mutableStateOf(if (prefs.configured) Screen.TERMINAL else Screen.SETUP)
    var conn by mutableStateOf(Conn.UNCONFIGURED)
    var session by mutableStateOf<SessionInfo?>(null)
    var model by mutableStateOf<String?>(null)
    var lastTokens by mutableStateOf(0L)
    var running by mutableStateOf(false)
    var runStartedAt by mutableStateOf(0L)
    /** What the agent is doing right now, shown next to the spinner (null = thinking). */
    var activity by mutableStateOf<String?>(null)
    var fontSize by mutableStateOf(prefs.fontSize)
    var lockEnabled by mutableStateOf(prefs.lockEnabled)
    /** Starts locked on a fresh process; MainActivity unlocks it via BiometricPrompt. */
    var locked by mutableStateOf(prefs.lockEnabled)
    /** Bumped when the transcript should snap to the newest line. */
    var scrollSignal by mutableStateOf(0)

    /** A pairing link waiting for the user to confirm the server it points at. */
    var pendingPair by mutableStateOf<PairRequest?>(null)

    val sessions = mutableStateListOf<SessionInfo>()
    var sessionsLoading by mutableStateOf(false)
    var sessionsError by mutableStateOf<String?>(null)

    val host: String get() = api?.base ?: normalizeBaseUrl(prefs.baseUrl)

    private var runId: String? = null
    private var runJob: Job? = null
    private var finished = true
    private var current: AssistantLine? = null
    private var streamedSinceTool = false
    private val shownApprovals = mutableSetOf<String>()

    init {
        lines += BannerLine()
        if (prefs.configured) connect()
    }

    // ── connection ──────────────────────────────────────────────────────────

    fun connect() {
        runJob?.cancel()
        val a = HermesApi(prefs.baseUrl, prefs.apiKey).also { api = it }
        lines.clear(); lines += BannerLine()
        conn = Conn.CONNECTING
        viewModelScope.launch {
            try {
                a.capabilities()
                conn = Conn.ONLINE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                conn = Conn.OFFLINE
                say("✗ can't reach ${a.base} — ${e.brief()}", Tone.ERROR)
                say("check /settings, and that the gateway API server listens on an address this phone can reach", Tone.DIM)
                return@launch
            }
            prefs.sessionId?.let { sid ->
                try {
                    session = a.session(sid)
                    model = session?.model
                    loadTranscript(sid)
                } catch (e: ApiError) {
                    if (e.status == 404) prefs.sessionId = null else say("✗ ${e.brief()}", Tone.ERROR)
                } catch (e: Exception) {
                    say("✗ ${e.brief()}", Tone.ERROR)
                }
            }
            prefs.activeRunId?.let { rid ->
                beginRun(rid)
                say("↻ picking up the turn that was running…", Tone.DIM)
                followRun(rid)
            }
        }
    }

    /** Called whenever the app comes back to the foreground. */
    fun onForeground() {
        val a = api ?: return
        val rid = runId
        if (running && rid != null && runJob?.isActive != true) {
            followRun(rid)
        } else if (!running && conn == Conn.OFFLINE) {
            viewModelScope.launch {
                conn = try { a.health(); Conn.ONLINE } catch (e: Exception) { Conn.OFFLINE }
            }
        }
    }

    suspend fun probe(url: String, key: String): List<Pair<String, Tone>> {
        val a = HermesApi(url, key)
        val out = mutableListOf("→ ${a.base}" to Tone.DIM)
        try {
            a.health(); out += "✓ gateway reachable" to Tone.OK
        } catch (e: Exception) {
            out += "✗ unreachable — ${e.brief()}" to Tone.ERROR
            return out
        }
        try {
            val caps = a.capabilities()
            out += "✓ authenticated · agent '${caps.optString("model")}'" to Tone.OK
            val f = caps.optJSONObject("features")
            if (f != null && !f.optBoolean("run_events_sse")) out += "! this gateway lacks run streaming — update hermes" to Tone.WARN
        } catch (e: Exception) {
            out += "✗ ${e.brief()}" to Tone.ERROR
        }
        return out
    }

    fun saveSettings(url: String, key: String) {
        prefs.baseUrl = normalizeBaseUrl(url)
        prefs.apiKey = key.trim()
        screen = Screen.TERMINAL
        connect()
    }

    /**
     * Stages a pairing link from a QR code or a `hermestty://` intent. Any app or web page can fire
     * that intent, so nothing is saved until the user confirms the server in [confirmPair].
     */
    fun pair(link: String?): Boolean {
        pendingPair = parsePairingLink(link) ?: return false
        return true
    }

    fun confirmPair() {
        val p = pendingPair ?: return
        pendingPair = null
        saveSettings(p.url, p.key)
    }

    fun rejectPair() {
        pendingPair = null
    }

    fun setLock(enabled: Boolean) {
        lockEnabled = enabled
        prefs.lockEnabled = enabled
    }

    fun setFont(size: Int) {
        fontSize = size.coerceIn(9, 22)
        prefs.fontSize = fontSize
    }

    // ── input ───────────────────────────────────────────────────────────────

    /** Returns true when the input was consumed and the field should be cleared. */
    fun submit(raw: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty()) return false
        if (text.startsWith("/") && command(text)) return true
        val a = api ?: run { screen = Screen.SETUP; return false }
        if (running) {
            steer(text)
            return true
        }
        lines += UserLine(text)
        scrollSignal++
        running = true
        finished = false
        runStartedAt = System.currentTimeMillis()
        activity = "sending"
        current = null
        streamedSinceTool = false
        runJob?.cancel()
        runJob = viewModelScope.launch {
            try {
                val sid = session?.id ?: a.createSession().also {
                    session = it
                    prefs.sessionId = it.id
                }.id
                val rid = a.startRun(sid, text)
                beginRun(rid)
                conn = Conn.ONLINE
                track(rid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("✗ couldn't send — ${e.brief()}", Tone.ERROR)
                if (e !is ApiError) conn = Conn.OFFLINE
                endRun()
            }
        }
        return true
    }

    private fun command(text: String): Boolean {
        val parts = text.split(Regex("\\s+"), limit = 2)
        val arg = parts.getOrNull(1)?.trim().orEmpty()
        when (parts[0].lowercase()) {
            "/new", "/reset" -> newSession()
            "/sessions", "/resume" -> openSessions()
            "/stop" -> if (running) stop() else say("nothing is running", Tone.DIM)
            "/clear" -> { lines.clear(); lines += BannerLine() }
            "/title" -> rename(arg)
            "/reload" -> session?.let { s -> viewModelScope.launch { loadTranscript(s.id) } }
                ?: say("no session yet", Tone.DIM)
            "/settings", "/connect" -> screen = Screen.SETUP
            "/about" -> screen = Screen.ABOUT
            "/help" -> {
                say("commands:", Tone.INFO)
                COMMANDS.forEach { (c, d) -> say("  ${c.padEnd(10)} $d", Tone.DIM) }
                say("anything else is sent to the agent. typing while a turn runs steers it.", Tone.DIM)
            }
            else -> return false
        }
        return true
    }

    private fun steer(text: String) {
        val rid = runId ?: return
        val a = api ?: return
        say("↳ steer: $text", Tone.DIM)
        viewModelScope.launch {
            try { a.steer(rid, text) } catch (e: Exception) { say("✗ steer not accepted — ${e.brief()}", Tone.WARN) }
        }
    }

    fun stop() {
        val rid = runId ?: return
        val a = api ?: return
        activity = "stopping"
        viewModelScope.launch {
            try { a.stop(rid) } catch (e: Exception) { say("✗ stop failed — ${e.brief()}", Tone.ERROR) }
        }
    }

    fun approve(line: ApprovalLine, choice: String) {
        val a = api ?: return
        line.busy = true
        viewModelScope.launch {
            try {
                a.approve(line.runId, choice)
                line.resolved = choice
                activity = null
            } catch (e: Exception) {
                say("✗ approval failed — ${e.brief()}", Tone.ERROR)
            } finally {
                line.busy = false
            }
        }
    }

    // ── sessions ────────────────────────────────────────────────────────────

    fun newSession() {
        if (running) { say("a turn is still running — /stop it first", Tone.WARN); return }
        session = null
        prefs.sessionId = null
        model = null
        lastTokens = 0
        lines.clear(); lines += BannerLine()
        say("new session — it's created when you send your first message", Tone.DIM)
        screen = Screen.TERMINAL
    }

    fun openSessions() {
        screen = Screen.SESSIONS
        refreshSessions()
    }

    fun refreshSessions() {
        val a = api ?: return
        sessionsLoading = true
        sessionsError = null
        viewModelScope.launch {
            try {
                val list = a.sessions()
                sessions.clear(); sessions += list
            } catch (e: Exception) {
                sessionsError = e.brief()
            } finally {
                sessionsLoading = false
            }
        }
    }

    fun resume(s: SessionInfo) {
        if (running) { say("a turn is still running — /stop it first", Tone.WARN); screen = Screen.TERMINAL; return }
        session = s
        prefs.sessionId = s.id
        model = s.model
        lastTokens = 0
        screen = Screen.TERMINAL
        viewModelScope.launch { loadTranscript(s.id) }
    }

    private fun rename(title: String) {
        val s = session ?: return say("no session yet", Tone.DIM)
        if (title.isBlank()) return say("usage: /title <name>", Tone.DIM)
        val a = api ?: return
        viewModelScope.launch {
            try {
                a.renameSession(s.id, title)
                session = s.copy(title = title)
                say("✓ renamed to “$title”", Tone.OK)
            } catch (e: Exception) {
                say("✗ rename failed — ${e.brief()}", Tone.ERROR)
            }
        }
    }

    private suspend fun loadTranscript(sid: String) {
        val a = api ?: return
        try {
            val msgs = a.messages(sid)
            lines.clear(); lines += BannerLine()
            renderHistory(msgs)
            say("── ${session?.label ?: sid} · ${msgs.size} messages ──", Tone.DIM)
            scrollSignal++
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            say("✗ couldn't load transcript — ${e.brief()}", Tone.ERROR)
        }
    }

    private fun renderHistory(msgs: List<JSONObject>) {
        for (m in msgs) {
            val content = cleanText(contentText(m.opt("content")))
            when (m.optString("role")) {
                "user" -> when {
                    m.str("display_kind") != null || content.startsWith("[System:") ->
                        say("· " + content.removePrefix("[System:").trim().lineSequence().first().take(140), Tone.DIM)
                    content.isNotBlank() -> lines += UserLine(content)
                }
                "assistant" -> {
                    val calls = toolCalls(m.opt("tool_calls"))
                    if (content.isNotBlank()) lines += AssistantLine(content, interim = calls.isNotEmpty())
                    calls.forEach { (name, preview) -> lines += ToolLine(name, preview, done = true) }
                }
            }
        }
    }

    // ── run tracking ────────────────────────────────────────────────────────

    private fun beginRun(rid: String) {
        runId = rid
        prefs.activeRunId = rid
        running = true
        finished = false
        if (runStartedAt == 0L) runStartedAt = System.currentTimeMillis()
        activity = null
    }

    private fun followRun(rid: String) {
        runJob?.cancel()
        runJob = viewModelScope.launch { track(rid) }
    }

    /** Live-stream the run; if the stream breaks (sleep, network switch), fall back to polling it to the end. */
    private suspend fun track(rid: String) {
        try {
            api!!.events(rid).collect { ev ->
                conn = Conn.ONLINE
                onEvent(ev)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Stream lost or already detached; the run keeps going on the server.
        }
        if (!finished) poll(rid)
    }

    private suspend fun poll(rid: String) {
        val a = api ?: return
        activity = "reattaching"
        var failures = 0
        while (!finished) {
            try {
                val st = a.run(rid)
                conn = Conn.ONLINE
                failures = 0
                val status = st.optString("status")
                when {
                    status in TERMINAL_STATUSES -> {
                        // Deltas were missed while detached — the server transcript is authoritative.
                        session?.let { loadTranscript(it.id) }
                        when (status) {
                            "failed" -> say("✗ run failed — ${st.str("error") ?: "unknown error"}", Tone.ERROR)
                            "cancelled", "interrupted" -> say("■ interrupted", Tone.WARN)
                        }
                        applyUsage(st)
                        endRun()
                    }
                    status == "waiting_for_approval" -> st.optJSONObject("approval")?.let { showApproval(it) }
                    else -> activity = "working (detached)"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                if (e.status == 404) {
                    say("· the gateway no longer tracks that run — reloading transcript", Tone.DIM)
                    session?.let { loadTranscript(it.id) }
                    endRun()
                } else {
                    failures++
                }
            } catch (e: Exception) {
                failures++
                conn = if (failures > 3) Conn.OFFLINE else Conn.RECONNECTING
            }
            if (!finished) delay(if (failures == 0) 1500L else 3000L)
        }
    }

    private fun onEvent(ev: JSONObject) {
        when (ev.optString("event")) {
            "message.delta" -> {
                val d = ev.optString("delta")
                if (d.isEmpty()) return
                val line = current ?: AssistantLine(streaming = true).also { current = it; lines += it }
                line.text += d
                streamedSinceTool = true
                activity = null
            }
            "message.interim" -> if (!ev.optBoolean("already_streamed")) {
                closeSegment()
                lines += AssistantLine(cleanText(ev.optString("text")), interim = true)
            }
            "tool.started" -> {
                closeSegment()
                val tool = ev.optString("tool")
                lines += ToolLine(tool, ev.optString("preview"))
                activity = tool
            }
            "tool.completed" -> {
                val tool = ev.optString("tool")
                val t = lines.lastOrNull { it is ToolLine && it.name == tool && !it.done } as? ToolLine
                t?.apply {
                    done = true
                    error = ev.optBoolean("error")
                    duration = ev.num("duration")
                    result = ev.str("preview")
                }
                activity = null
            }
            "approval.request" -> showApproval(ev)
            "approval.responded" -> activity = null
            "subagent.start" -> say("⇢ subagent: ${ev.str("goal")?.take(140) ?: "started"}", Tone.DIM)
            "subagent.complete" ->
                say("⇠ subagent ${ev.optString("status")}: ${ev.str("summary")?.take(160).orEmpty()}", Tone.DIM)
            "run.completed" -> {
                val out = cleanText(ev.optString("output"))
                if (!streamedSinceTool && out.isNotBlank()) {
                    closeSegment()
                    lines += AssistantLine(out)
                }
                applyUsage(ev)
                endRun()
            }
            "run.failed" -> {
                say("✗ run failed — ${ev.str("error") ?: "unknown error"}", Tone.ERROR)
                endRun()
            }
            "run.cancelled", "run.interrupted" -> {
                say("■ interrupted", Tone.WARN)
                endRun()
            }
        }
    }

    private fun showApproval(ev: JSONObject) {
        val rid = runId ?: return
        val key = ev.str("request_id") ?: ev.optString("command")
        if (!shownApprovals.add("$rid:$key")) return
        closeSegment()
        val choices = ev.optJSONArray("choices")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            ?: listOf("once", "session", "deny")
        lines += ApprovalLine(rid, ev.optString("command"), ev.str("description"), choices)
        activity = "awaiting approval"
    }

    /** Text before a tool call becomes commentary; the next delta starts a fresh segment. */
    private fun closeSegment() {
        current?.let {
            it.streaming = false
            if (it.text.isBlank()) lines.remove(it) else it.interim = true
        }
        current = null
        streamedSinceTool = false
    }

    private fun applyUsage(ev: JSONObject) {
        ev.optJSONObject("usage")?.let { u -> lastTokens = u.optLong("total_tokens", lastTokens) }
        ev.optJSONObject("runtime")?.str("model")?.let { model = it }
    }

    private fun endRun() {
        current?.let { it.streaming = false; it.text = cleanText(it.text) }
        current = null
        finished = true
        running = false
        activity = null
        runId = null
        runStartedAt = 0L
        prefs.activeRunId = null
        val a = api ?: return
        val sid = session?.id ?: return
        viewModelScope.launch {
            // Titles are generated server-side after the first turn.
            runCatching { a.session(sid) }.getOrNull()?.let { session = it; if (model == null) model = it.model }
        }
    }

    private fun say(text: String, tone: Tone = Tone.INFO) {
        lines += InfoLine(text, tone)
    }

    // ── history parsing ─────────────────────────────────────────────────────

    private fun contentText(c: Any?): String = when (c) {
        null, JSONObject.NULL -> ""
        is String -> c
        is JSONArray -> (0 until c.length()).mapNotNull { i ->
            val p = c.optJSONObject(i) ?: return@mapNotNull c.optString(i)
            when (p.optString("type")) {
                "text", "input_text", "output_text" -> p.optString("text")
                "image_url", "input_image" -> "[image]"
                else -> null
            }
        }.joinToString("\n")
        else -> c.toString()
    }

    private fun toolCalls(raw: Any?): List<Pair<String, String>> {
        val arr = when (raw) {
            is JSONArray -> raw
            is String -> runCatching { JSONArray(raw) }.getOrNull()
            else -> null
        } ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val call = arr.optJSONObject(i) ?: return@mapNotNull null
            val fn = call.optJSONObject("function") ?: call
            val name = fn.str("name") ?: return@mapNotNull null
            name to argPreview(fn.opt("arguments"))
        }
    }

    private fun argPreview(args: Any?): String {
        val o = when (args) {
            is JSONObject -> args
            is String -> runCatching { JSONObject(args) }.getOrNull() ?: return args.take(160)
            else -> return ""
        }
        for (k in listOf("command", "path", "file_path", "query", "url", "pattern", "goal", "name", "code")) {
            o.str(k)?.let { return it.lineSequence().first().take(160) }
        }
        return o.toString().take(160)
    }
}
