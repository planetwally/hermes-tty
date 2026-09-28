package com.planetwally.hermestty

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Renders the real UI to PNGs under app/build/screens/ (no emulator needed).
 * `liveTurn` drives an actual turn against the local gateway when ~/.hermes/.env has a key.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val out = File("build/screens").apply { mkdirs() }

    @Before
    fun reset() {
        Prefs.vault = PlainVault
        // Lock off: there's no BiometricPrompt to get past, and every shot would be the lock screen.
        ctx.getSharedPreferences("hermestty", Context.MODE_PRIVATE).edit().clear().putBoolean("lock_enabled", false).commit()
    }

    private fun pump(ms: Long = 16) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun waitFor(timeoutMs: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            pump(50)
            if (cond()) return true
            Thread.sleep(40)
        }
        return false
    }

    private fun snap(activity: MainActivity, name: String) {
        repeat(10) { pump() }
        val v = activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun launch(): Pair<MainActivity, ChatViewModel> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val vm = ViewModelProvider(activity)[ChatViewModel::class.java]
        return activity to vm
    }

    @Test
    fun setupScreen() {
        val (activity, _) = launch()
        snap(activity, "01-setup")
    }

    @Test
    fun pairingAndAbout() {
        val (activity, vm) = launch()
        check(vm.pair("hermestty://connect?url=http%3A%2F%2F192.168.1.52%3A8642&key=k"))
        check(!vm.prefs.configured) { "pairing must wait for confirmation" }
        snap(activity, "04-pair-confirm")
        vm.rejectPair()
        check(!vm.prefs.configured && vm.pendingPair == null)

        vm.screen = Screen.ABOUT
        snap(activity, "05-about")
    }

    @Test
    fun richTranscript() {
        val (activity, vm) = launch()
        vm.screen = Screen.TERMINAL
        vm.conn = Conn.ONLINE
        vm.model = "claude-opus-5-5"
        vm.lastTokens = 19_360
        vm.session = SessionInfo("api_1", "Fix flaky deploy script", "api_server", null, 12, 0.0, null)
        vm.lines += UserLine("why is the deploy script failing on the staging box?")
        vm.lines += AssistantLine("Let me look at the script and the last run's log.", interim = true)
        vm.lines += ToolLine("read_file", "~/ops/deploy.sh", done = true).apply { duration = 0.1 }
        vm.lines += ToolLine("terminal", "tail -n 40 /var/log/deploy.log", done = true).apply { duration = 1.4 }
        vm.lines += ToolLine("terminal", "systemctl status nginx", done = true).apply { error = true; duration = 0.3 }
        vm.lines += AssistantLine(
            """
            ## Cause
            The script runs `rsync` **before** the build finishes, so it ships a half-written `dist/`.

            - line 42 backgrounds the build with `&`
            - the `wait` on line 51 comes after the sync

            ```bash
            npm run build        # drop the trailing &
            rsync -a dist/ web:/srv/app/
            ```
            Docs: https://example.com/deploy
            """.trimIndent()
        )
        vm.lines += UserLine("ok fix it and restart nginx")
        vm.lines += ToolLine("patch", "~/ops/deploy.sh", done = true).apply { duration = 0.2 }
        vm.lines += ApprovalLine("run_x", "sudo systemctl restart nginx", "restart a system service", listOf("once", "session", "always", "deny"))
        vm.running = true
        vm.runStartedAt = System.currentTimeMillis() - 23_000
        vm.activity = "awaiting approval"
        snap(activity, "02-transcript")

        vm.screen = Screen.SESSIONS
        vm.sessions += listOf(
            SessionInfo("api_1", "Fix flaky deploy script", "api_server", null, 12, System.currentTimeMillis() / 1000.0 - 90, null),
            SessionInfo("tui_1", "Identify current AI model", "tui", null, 8, System.currentTimeMillis() / 1000.0 - 7200, null),
            SessionInfo("wa_1", "Check system time", "whatsapp", null, 179, System.currentTimeMillis() / 1000.0 - 90000, null),
        )
        snap(activity, "03-sessions")
    }

    @Test
    fun liveTurn() {
        val env = File(System.getProperty("user.home"), ".hermes/.env")
        val key = env.takeIf { it.exists() }?.readLines()
            ?.firstOrNull { it.startsWith("API_SERVER_KEY=") }
            ?.substringAfter("=")?.trim('"', '\'', ' ')
        assumeTrue("no local gateway key", !key.isNullOrBlank())
        val (activity, vm) = launch()
        vm.saveSettings("127.0.0.1:8642", key!!) // same path as the settings screen's [save]
        check(waitFor(15_000) { vm.conn == Conn.ONLINE }) { "never came online: ${vm.conn}" }
        vm.submit("Use the terminal tool to run `uname -sr`, then answer in one short sentence that quotes the output.")
        waitFor(90_000) { vm.lines.any { it is ToolLine } }
        snap(activity, "06-live-running")
        check(waitFor(180_000) { !vm.running }) { "turn did not finish" }
        waitFor(3_000) { vm.session?.title != null }
        snap(activity, "07-live-done")

        val transcript = vm.lines.joinToString("\n") { line ->
            when (line) {
                is UserLine -> "USER: ${line.text}"
                is AssistantLine -> "ASSISTANT${if (line.interim) "(interim)" else ""}: ${line.text}"
                is ToolLine -> "TOOL: ${line.name} ${line.preview} done=${line.done} err=${line.error} ${line.duration}"
                is InfoLine -> "INFO(${line.tone}): ${line.text}"
                is ApprovalLine -> "APPROVAL: ${line.command}"
                is BannerLine -> "BANNER"
            }
        }
        File(out, "live-transcript.txt").writeText(transcript + "\nSESSION: ${vm.session}\nMODEL: ${vm.model} TOKENS: ${vm.lastTokens}\n")

        // Clean up the throwaway session on the gateway.
        vm.session?.id?.let { sid ->
            OkHttpClient().newCall(
                Request.Builder().url("http://127.0.0.1:8642/api/sessions/$sid")
                    .header("Authorization", "Bearer $key").delete().build()
            ).execute().close()
        }
    }
}
