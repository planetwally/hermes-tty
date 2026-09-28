package com.planetwally.hermestty.ui

import com.planetwally.hermestty.ApprovalLine
import com.planetwally.hermestty.AssistantLine
import com.planetwally.hermestty.BannerLine
import com.planetwally.hermestty.COMMANDS
import com.planetwally.hermestty.ChatViewModel
import com.planetwally.hermestty.Conn
import com.planetwally.hermestty.InfoLine
import com.planetwally.hermestty.Line
import com.planetwally.hermestty.ToolLine
import com.planetwally.hermestty.UserLine
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LOGO = listOf(
    "██╗  ██╗███████╗██████╗ ███╗   ███╗███████╗███████╗   ████████╗████████╗██╗   ██╗",
    "██║  ██║██╔════╝██╔══██╗████╗ ████║██╔════╝██╔════╝   ╚══██╔══╝╚══██╔══╝╚██╗ ██╔╝",
    "███████║█████╗  ██████╔╝██╔████╔██║█████╗  ███████╗      ██║      ██║    ╚████╔╝",
    "██╔══██║██╔══╝  ██╔══██╗██║╚██╔╝██║██╔══╝  ╚════██║      ██║      ██║     ╚██╔╝",
    "██║  ██║███████╗██║  ██║██║ ╚═╝ ██║███████╗███████║      ██║      ██║      ██║",
    "╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝╚═╝     ╚═╝╚══════╝╚══════╝      ╚═╝      ╚═╝      ╚═╝",
)
private val LOGO_COLORS = listOf(Hx.title, Hx.title, Hx.accent, Hx.accent, Hx.border, Hx.border)

private val WAITING_FACES = listOf("(｡◕‿◕｡)", "(◕‿◕✿)", "٩(◕‿◕｡)۶", "(✿◠‿◠)", "( ˘▽˘)っ", "♪(´ε` )", "(◕ᴗ◕✿)", "ヾ(＾∇＾)", "(≧◡≦)", "(★ω★)")
private val THINKING_FACES = listOf("(｡•́︿•̀｡)", "(◔_◔)", "(¬‿¬)", "( •_•)>⌐■-■", "(⌐■_■)", "(´･_･`)", "◉_◉", "(°ロ°)", "( ˘⌣˘)♡", "ヽ(>∀<☆)☆", "٩(๑❛ᴗ❛๑)۶", "(⊙_⊙)", "(¬_¬)", "( ͡° ͜ʖ ͡°)", "ಠ_ಠ")
private val VERBS = listOf("pondering", "contemplating", "musing", "cogitating", "ruminating", "deliberating", "mulling", "reflecting", "processing", "reasoning", "analyzing", "computing", "synthesizing", "formulating", "brainstorming")
private const val SPINNER = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"

private val TOOL_ICONS = listOf(
    "terminal" to "💻", "process" to "⚙", "read_file" to "📖", "write_file" to "✍", "patch" to "🔧",
    "search" to "🔎", "web" to "🌐", "browser" to "🌐", "memory" to "🧠", "skill" to "📚",
    "delegate" to "🔀", "execute_code" to "🐍", "todo" to "📋", "image" to "🎨", "vision" to "👁",
    "cron" to "⏰", "send_message" to "✉", "clarify" to "❓", "tts" to "🔊",
)

private fun toolIcon(name: String) = TOOL_ICONS.firstOrNull { name.startsWith(it.first) || name.contains(it.first) }?.second ?: "⚡"

fun fmtTokens(n: Long) = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1e6)
    n >= 1_000 -> "%.1fk".format(n / 1e3)
    else -> n.toString()
}

private fun fmtElapsed(s: Long) = if (s < 60) "${s}s" else "${s / 60}m${"%02d".format(s % 60)}s"

@Composable
fun Rule(color: Color = Hx.border, prefix: String = "", label: String = "") {
    // Box-drawing glyphs rather than a drawn line, so rules match the terminal's stroke weight.
    T(
        prefix + label + "─".repeat(240),
        color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
    )
}

@Composable
fun BracketButton(
    label: String,
    color: Color = Hx.accent,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        T("[$label]", color = if (enabled) color else Hx.muted, bold = true, maxLines = 1)
    }
}

@Composable
fun TerminalScreen(vm: ChatViewModel) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(vm.scrollSignal) { listState.scrollToItem(0) }

    fun send(text: String = input.text) {
        if (vm.submit(text)) input = TextFieldValue("")
    }

    Column(Modifier.fillMaxSize().background(Hx.bg)) {
        TopBar(vm)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val lines = vm.lines
            val n = lines.size
            // Reversed so the newest output is anchored to the bottom while it streams.
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (vm.running) item(key = "spinner") { SpinnerLine(vm) }
                items(count = n, key = { lines[n - 1 - it].id }) { i -> LineView(lines[n - 1 - i], vm) }
            }
            if (listState.firstVisibleItemIndex > 1) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .background(Hx.panel)
                        .border(1.dp, Hx.border)
                        .clickable { scope.launch { listState.animateScrollToItem(0) } }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) { T("↓ latest", color = Hx.accent, scale = 0.9f) }
            }
        }
        val text = input.text
        if (text.startsWith("/") && !text.contains(' ')) {
            val matches = COMMANDS.filter { it.first.startsWith(text.lowercase()) }
            if (matches.isNotEmpty()) Suggestions(matches) { cmd ->
                if (cmd == "/title") input = TextFieldValue("/title ", TextRange(7)) else send(cmd)
            }
        }
        StatusBar(vm)
        InputBar(vm, input, { input = it }, { send() })
    }
}

@Composable
private fun TopBar(vm: ChatViewModel) {
    Row(
        Modifier.fillMaxWidth().background(Hx.panel).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("⚕ ", color = Hx.title, bold = true)
        T(
            vm.session?.let { it.title ?: "untitled session" } ?: "new session",
            Modifier.weight(1f), color = Hx.label, maxLines = 1,
        )
        BracketButton("≡") { vm.openSessions() }
        BracketButton("+") { vm.newSession() }
        BracketButton("⚙") { vm.screen = com.planetwally.hermestty.Screen.SETUP }
    }
}

@Composable
private fun StatusBar(vm: ChatViewModel) {
    val (dot, dotColor, state) = when (vm.conn) {
        Conn.ONLINE -> Triple("●", Hx.good, "online")
        Conn.CONNECTING -> Triple("◌", Hx.warn, "connecting")
        Conn.RECONNECTING -> Triple("◌", Hx.warn, "reconnecting")
        Conn.OFFLINE -> Triple("●", Hx.error, "offline")
        Conn.UNCONFIGURED -> Triple("○", Hx.statusDim, "not set up")
    }
    Row(
        Modifier.fillMaxWidth().background(Hx.panel).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("⚕ ", color = Hx.title, scale = 0.85f)
        T(vm.model ?: "hermes", Modifier.weight(1f, fill = false), color = Hx.title, scale = 0.85f, maxLines = 1)
        if (vm.lastTokens > 0) {
            T(" │ ", color = Hx.statusDim, scale = 0.85f)
            T("${fmtTokens(vm.lastTokens)} tok", color = Hx.statusText, scale = 0.85f)
        }
        Spacer(Modifier.weight(1f))
        T("$dot ", color = dotColor, scale = 0.85f)
        T(state, color = Hx.statusText, scale = 0.85f)
    }
}

@Composable
private fun Suggestions(items: List<Pair<String, String>>, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Hx.panel).border(1.dp, Hx.panelHi)) {
        items.forEach { (cmd, desc) ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(cmd) }.padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                T(cmd.padEnd(10), color = Hx.accent, bold = true)
                T(desc, color = Hx.statusText, maxLines = 1)
            }
        }
    }
}

@Composable
private fun InputBar(vm: ChatViewModel, value: TextFieldValue, onChange: (TextFieldValue) -> Unit, onSend: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Hx.bg)) {
        Rule(Hx.border)
        Row(Modifier.padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            T("❯ ", color = Hx.accent, bold = true)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                textStyle = termStyle(Hx.text),
                cursorBrush = SolidColor(Hx.title),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { inner ->
                    Box {
                        if (value.text.isEmpty()) {
                            T(if (vm.running) "steer the agent…" else "message hermes…", color = Hx.muted)
                        }
                        inner()
                    }
                },
            )
            if (vm.running && value.text.isBlank()) {
                BracketButton("■ stop", color = Hx.error) { vm.stop() }
            } else {
                BracketButton(if (vm.running) "steer" else "send", enabled = value.text.isNotBlank()) { onSend() }
            }
        }
        Rule(Hx.border)
    }
}

@Composable
private fun LineView(line: Line, vm: ChatViewModel) {
    when (line) {
        is BannerLine -> Banner(vm)
        is UserLine -> Row(Modifier.padding(top = 6.dp)) {
            T("❯ ", color = Hx.accent, bold = true)
            SelectionContainer { T(line.text, color = Hx.text, bold = true) }
        }
        is AssistantLine -> AssistantView(line)
        is ToolLine -> ToolView(line)
        is InfoLine -> T(line.text, color = line.tone.color(), scale = 0.9f)
        is ApprovalLine -> ApprovalView(line, vm)
    }
}

@Composable
private fun Banner(vm: ChatViewModel) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = LOGO.maxOf { it.length }
            // JetBrains Mono advances 0.6em per cell; size the block logo to the screen width.
            val px = constraints.maxWidth / (cols * 0.6f) * 0.98f
            val size = with(LocalDensity.current) { px.toSp() }
            Column {
                LOGO.forEachIndexed { i, row ->
                    Text(
                        row,
                        style = TextStyle(
                            fontFamily = Mono, fontSize = size, lineHeight = size, color = LOGO_COLORS[i],
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                        ),
                        maxLines = 1, softWrap = false,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            T("⚕ Hermes TTY", color = Hx.title, bold = true)
            T(" · unofficial Hermes Agent client", color = Hx.dim)
        }
        T(vm.host.ifEmpty { "not connected" }, color = Hx.muted, scale = 0.85f)
        T("type a message · /help for commands", color = Hx.muted, scale = 0.85f)
    }
}

@Composable
private fun AssistantView(line: AssistantLine) {
    val text = if (line.streaming) line.text.trimStart() + "▌" else line.text.trim()
    if (line.interim) {
        MarkdownText(text, color = Hx.text.copy(alpha = 0.72f), modifier = Modifier.padding(start = 2.dp))
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Rule(Hx.title, prefix = "╭─ ", label = "⚕ Hermes ")
        MarkdownText(text, modifier = Modifier.padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp))
        Rule(Hx.title, prefix = "╰")
    }
}

@Composable
private fun ToolView(line: ToolLine) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.Top) {
            T("┊ ", color = Hx.dim, scale = 0.9f)
            T("${toolIcon(line.name)} ${line.name} ", color = Hx.label, scale = 0.9f, maxLines = 1)
            T(
                line.preview.ifBlank { "" },
                Modifier.weight(1f), color = Hx.muted, scale = 0.9f,
                maxLines = if (expanded) 30 else 1,
            )
            val status = when {
                !line.done -> "…"
                line.error -> " ✗"
                else -> line.duration?.let { " %.1fs".format(it) } ?: " ✓"
            }
            T(status, color = if (line.error) Hx.error else Hx.statusDim, scale = 0.9f)
        }
        if (expanded) line.result?.let {
            SelectionContainer {
                T(
                    it, Modifier.padding(start = 16.dp, top = 2.dp).background(Hx.panel).padding(6.dp),
                    color = if (line.error) Hx.error else Hx.statusText, scale = 0.85f,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApprovalView(line: ApprovalLine, vm: ChatViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Hx.warn)
            .background(Hx.warn.copy(alpha = 0.06f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        T("⚠ approval required", color = Hx.warn, bold = true)
        line.description?.let { T(it, color = Hx.text, scale = 0.9f) }
        if (line.command.isNotBlank()) {
            T(
                line.command,
                Modifier.fillMaxWidth().background(Hx.panel).horizontalScroll(rememberScrollState()).padding(6.dp),
                color = Hx.accent, scale = 0.9f, softWrap = false,
            )
        }
        val resolved = line.resolved
        if (resolved == null) {
            FlowRow {
                line.choices.forEach { c ->
                    val (label, color) = when (c) {
                        "once" -> "allow once" to Hx.ok
                        "session" -> "this session" to Hx.accent
                        "always" -> "always" to Hx.accent
                        "deny" -> "deny" to Hx.error
                        else -> c to Hx.accent
                    }
                    BracketButton(label, color, enabled = !line.busy) { vm.approve(line, c) }
                }
            }
        } else {
            T(
                if (resolved == "deny") "✗ denied" else "✓ approved ($resolved)",
                color = if (resolved == "deny") Hx.error else Hx.ok,
            )
        }
    }
}

@Composable
private fun SpinnerLine(vm: ChatViewModel) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(100); tick++ } }
    val activity = vm.activity
    val face = remember(tick / 20, activity) { (if (activity == null) THINKING_FACES else WAITING_FACES).random() }
    val verb = remember(tick / 30) { VERBS.random() }
    val elapsed = if (vm.runStartedAt > 0) (System.currentTimeMillis() - vm.runStartedAt) / 1000 else 0
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        T(SPINNER[tick % SPINNER.length].toString() + " ", color = Hx.accent)
        T("$face ", color = Hx.label, maxLines = 1)
        T(
            when (activity) {
                null -> "$verb…"
                "sending", "reattaching", "stopping", "awaiting approval", "working (detached)" -> "$activity…"
                else -> "${toolIcon(activity)} $activity"
            },
            Modifier.weight(1f), color = Hx.dim, maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        T(fmtElapsed(elapsed), color = Hx.statusDim, scale = 0.85f)
    }
}
