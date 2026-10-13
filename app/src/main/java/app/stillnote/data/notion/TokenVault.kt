package app.stillnote.data.notion

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where the Notion token is kept. */
interface TokenVault {
    fun load(): String?
    fun save(token: String)
    fun clear()
}

/**
 * Keeps the token in [file], encrypted with an AES key that lives in the Android Keystore and
 * never leaves it. The token is not logged, and the file is useless on another device.
 */
class KeystoreTokenVault(private val file: File) : TokenVault {
    private var cached: String? = null

    @Synchronized
    override fun load(): String? {
        cached?.let { return it }
        if (!file.exists()) return null
        // A key lost to a restore or a device reset just means "not connected".
        return runCatching {
            val bytes = file.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull().also { cached = it }
    }

    @Synchronized
    override fun save(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(sealed)
        check(temp.renameTo(file)) { "could not store the token" }
        cached = token
    }

    @Synchronized
    override fun clear() {
        file.delete()
        cached = null
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "notion_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
