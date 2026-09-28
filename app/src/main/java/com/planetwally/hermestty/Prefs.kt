package com.planetwally.hermestty

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals secrets before they touch SharedPreferences. */
interface Vault {
    fun seal(plain: String): String
    /** Null when the sealed value can't be opened (e.g. the key was wiped); the user re-enters it. */
    fun open(sealed: String): String?
}

/** AES-GCM with a non-exportable key held in the Android Keystore. */
object KeystoreVault : Vault {
    private const val ALIAS = "hermestty_api_key"
    private const val GCM_TAG_BITS = 128

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }
            .generateKey()
    }

    override fun seal(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    override fun open(sealed: String): String? = runCatching {
        val raw = Base64.decode(sealed, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, raw, 0, 12))
        String(c.doFinal(raw, 12, raw.size - 12))
    }.getOrNull()
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("hermestty", Context.MODE_PRIVATE)

    private fun put(key: String, value: String?) = sp.edit().putString(key, value).apply()

    var baseUrl: String
        get() = sp.getString("base_url", "").orEmpty()
        set(v) = put("base_url", v)

    // Cached so Compose reads (e.g. [configured]) don't hit the Keystore on every recomposition.
    private var keyCache: String? = null

    var apiKey: String
        get() = keyCache ?: sp.getString("api_key_sealed", null)?.let { vault.open(it) }.orEmpty().also { keyCache = it }
        set(v) {
            keyCache = v
            put("api_key_sealed", if (v.isEmpty()) null else vault.seal(v))
        }

    /** Session the terminal is attached to; restored on launch. */
    var sessionId: String?
        get() = sp.getString("session_id", null)
        set(v) = put("session_id", v)

    /** Run in flight when the app was last alive, so it can be picked up again after process death. */
    var activeRunId: String?
        get() = sp.getString("active_run_id", null)
        set(v) = put("active_run_id", v)

    var fontSize: Int
        get() = sp.getInt("font_size", 13)
        set(v) = sp.edit().putInt("font_size", v).apply()

    /** Require fingerprint / screen lock on launch and after a while in the background. */
    var lockEnabled: Boolean
        get() = sp.getBoolean("lock_enabled", true)
        set(v) = sp.edit().putBoolean("lock_enabled", v).apply()

    val configured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    companion object {
        /** Swapped out in Robolectric tests, which have no AndroidKeyStore provider. */
        var vault: Vault = KeystoreVault
    }
}
