package com.example.logistics

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey


class UserPrefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "user_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveUser(email: String, password: String) {
        prefs.edit().apply {
            putString("email", email)
            putString("password", password)
            apply()
        }
    }

    fun getEmail(): String? = prefs.getString("email", null)
    fun getPassword(): String? = prefs.getString("password", null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
