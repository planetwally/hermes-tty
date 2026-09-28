package com.planetwally.hermestty

import com.planetwally.hermestty.ui.AboutScreen
import com.planetwally.hermestty.ui.BracketButton
import com.planetwally.hermestty.ui.HermesTheme
import com.planetwally.hermestty.ui.Hx
import com.planetwally.hermestty.ui.PairConfirm
import com.planetwally.hermestty.ui.SessionsScreen
import com.planetwally.hermestty.ui.SetupScreen
import com.planetwally.hermestty.ui.T
import com.planetwally.hermestty.ui.TerminalScreen
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private const val RELOCK_AFTER_MS = 5 * 60 * 1000L
private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

// FragmentActivity (a ComponentActivity) because BiometricPrompt hosts itself in a fragment.
class MainActivity : FragmentActivity() {
    private val vm: ChatViewModel by viewModels()
    private lateinit var prompt: BiometricPrompt
    private var backgroundedAt = 0L
    private var authenticating = false
    /** A pairing link that arrived while locked; applied once unlocked. */
    private var pendingLink: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authenticating = false
                unlock()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Cancelled or locked out: stay on the lock screen; [unlock] retries.
                authenticating = false
            }
        })
        if (savedInstanceState == null) intent?.dataString?.let { handleLink(it) }
        setContent { App(vm, ::authenticate) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { handleLink(it) }
    }

    override fun onStart() {
        super.onStart()
        if (vm.lockEnabled && backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt > RELOCK_AFTER_MS) {
            vm.locked = true
        }
        backgroundedAt = 0
        if (vm.locked) authenticate()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) backgroundedAt = SystemClock.elapsedRealtime()
    }

    private fun handleLink(link: String) {
        if (vm.locked) pendingLink = link else vm.pair(link)
    }

    private fun unlock() {
        vm.locked = false
        pendingLink?.let { vm.pair(it) }
        pendingLink = null
    }

    private fun authenticate() {
        if (!vm.lockEnabled) return unlock()
        // No fingerprint and no screen lock set up on the phone: nothing to check against.
        if (BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) return unlock()
        if (authenticating) return
        authenticating = true
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Hermes TTY")
                .setSubtitle("Your agent can run commands on your machine")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        )
    }
}

@Composable
private fun App(vm: ChatViewModel, onUnlock: () -> Unit) {
    HermesTheme(vm.fontSize) {
        LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onForeground() }
        BackHandler(enabled = !vm.locked && vm.screen == Screen.ABOUT && !vm.prefs.configured) {
            vm.screen = Screen.SETUP
        }
        BackHandler(enabled = !vm.locked && vm.screen != Screen.TERMINAL && vm.prefs.configured) {
            vm.screen = Screen.TERMINAL
        }
        // Composed last so it wins over the handlers above while a pairing prompt is up.
        BackHandler(enabled = !vm.locked && vm.pendingPair != null) { vm.rejectPair() }
        Box(Modifier.fillMaxSize().background(Hx.bg).safeDrawingPadding()) {
            val pending = vm.pendingPair
            when {
                vm.locked -> LockScreen(onUnlock)
                pending != null -> PairConfirm(pending, vm.prefs.baseUrl.ifBlank { null }, vm::confirmPair, vm::rejectPair)
                vm.screen == Screen.TERMINAL -> TerminalScreen(vm)
                vm.screen == Screen.SESSIONS -> SessionsScreen(vm)
                vm.screen == Screen.ABOUT -> AboutScreen(vm)
                else -> SetupScreen(vm)
            }
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        T("⚕", color = Hx.title, scale = 3f)
        T("HERMES TTY", color = Hx.title, bold = true, scale = 1.3f)
        T("locked", color = Hx.muted)
        BracketButton("▣ unlock", Hx.accent, onClick = onUnlock)
    }
}
