package com.tyust.course.scvtc

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The API key and account-scoped conversations never enter Android backups or school storage. */
internal class AssistantStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "campus-assistant").apply { mkdirs() }
    companion object { private val keyLock = Any(); private const val ALIAS = "scvtc.assistant.v1" }
    private fun key(): SecretKey = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.getKey(ALIAS, null) as? SecretKey ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    private fun file(scope: String) = AtomicFile(File(directory, MessageDigest.getInstance("SHA-256")
        .digest(scope.toByteArray()).joinToString("") { "%02x".format(it) } + ".sealed"))
    fun read(scope: String): JSONObject {
        val source = file(scope)
        if (!source.baseFile.exists()) return JSONObject()
        val bytes = source.readFully()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(scope.toByteArray())
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }
    fun write(scope: String, value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(scope.toByteArray())
        val target = file(scope); val output = target.startWrite()
        try { output.write(cipher.iv + cipher.doFinal(value.toString().toByteArray())); target.finishWrite(output) }
        catch (error: Exception) { target.failWrite(output); throw error }
    }
    fun clearHistory(account: String) { file("history:$account").delete() }
}
