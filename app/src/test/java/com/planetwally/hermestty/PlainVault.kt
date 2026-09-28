package com.planetwally.hermestty

/** Robolectric has no AndroidKeyStore provider, so tests store the key as-is. */
object PlainVault : Vault {
    override fun seal(plain: String) = plain
    override fun open(sealed: String) = sealed
}
