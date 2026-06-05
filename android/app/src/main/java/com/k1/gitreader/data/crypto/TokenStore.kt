package com.k1.gitreader.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * リポジトリ毎の認証トークンを AndroidKeyStore の AES/GCM 鍵で暗号化し、
 * 暗号文を SharedPreferences に保存する。平文トークンは永続化しない。
 *
 * 保存形式: Base64(iv) + ":" + Base64(ciphertext)
 */
class TokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("repo_tokens", Context.MODE_PRIVATE)

    // --- 手入力トークン (key prefix "repo_") ---
    fun setToken(repoId: Long, token: String) = put(tokenKey(repoId), token)
    fun getToken(repoId: Long): String? = get(tokenKey(repoId))
    fun removeToken(repoId: Long) = remove(tokenKey(repoId))

    // --- OAuth 資格情報 JSON (key prefix "oauth_") ---
    fun setOAuth(repoId: Long, json: String) = put(oauthKey(repoId), json)
    fun getOAuth(repoId: Long): String? = get(oauthKey(repoId))
    fun removeOAuth(repoId: Long) = remove(oauthKey(repoId))

    // --- provider 別の「記憶したログイン」(key prefix "oauth_session_") ---
    // リポ追加時の再ログインを省くため、最後に成功したログインを provider 単位で保持する。
    fun setOAuthSession(provider: String, json: String) = put(sessionKey(provider), json)
    fun getOAuthSession(provider: String): String? = get(sessionKey(provider))
    fun removeOAuthSession(provider: String) = remove(sessionKey(provider))

    private fun put(key: String, plain: String) {
        prefs.edit().putString(key, encrypt(plain)).apply()
    }

    private fun get(key: String): String? =
        prefs.getString(key, null)?.let { decrypt(it) }

    private fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(cipher.iv) + ":" + b64(ct)
    }

    private fun decrypt(packed: String): String {
        val (ivB64, ctB64) = packed.split(":", limit = 2).let { it[0] to it[1] }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, unb64(ivB64)))
        return String(cipher.doFinal(unb64(ctB64)), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    private fun tokenKey(repoId: Long) = "repo_$repoId"
    private fun oauthKey(repoId: Long) = "oauth_$repoId"
    private fun sessionKey(provider: String) = "oauth_session_$provider"
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "gitreader_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
