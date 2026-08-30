package com.hark.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google authorization for Drive appData scope via GoogleSignIn + GoogleAuthUtil.
 * - Handles interactive sign-in with account picker & consent
 * - Retrieves and silently auto-refreshes OAuth Bearer tokens on Dispatchers.IO
 */
object GoogleAuth {
    private const val TAG = "HarkGoogleAuth"
    private const val APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val OAUTH_SCOPE = "oauth2:$APPDATA_SCOPE"

    private fun getOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(APPDATA_SCOPE))
            .build()

    fun getClient(context: Context): GoogleSignInClient =
        GoogleSignIn.getClient(context, getOptions())

    fun getSignInIntent(context: Context): Intent =
        getClient(context).signInIntent

    fun getLastSignedInAccount(context: Context): GoogleSignInAccount? {
        val acc = GoogleSignIn.getLastSignedInAccount(context)
        return if (acc != null && GoogleSignIn.hasPermissions(acc, Scope(APPDATA_SCOPE))) acc else null
    }

    /** Last sign-in / token failure, surfaced to the UI so failures aren't silent. */
    @Volatile
    var lastError: String? = null

    fun resultFromIntent(data: Intent?): GoogleSignInAccount? {
        if (data == null) {
            lastError = "No sign-in data returned"
            return null
        }
        return try {
            val acc = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            lastError = null
            acc
        } catch (e: ApiException) {
            // 10 = DEVELOPER_ERROR (Android OAuth client SHA-1/package mismatch in the console),
            // 12501 = cancelled, 12500 = failed. The code IS the diagnosis.
            lastError = "Sign-in failed: code ${e.statusCode}"
            Log.e(TAG, "Sign-in failed: statusCode=${e.statusCode}", e)
            null
        }
    }

    /**
     * Get a valid OAuth Bearer access token for Drive appData.
     * Runs on Dispatchers.IO. GoogleAuthUtil handles token caching and automatic refresh.
     */
    suspend fun getAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val acc = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
            val androidAccount = acc.account ?: Account(acc.email ?: "", "com.google")
            GoogleAuthUtil.getToken(context, androidAccount, OAUTH_SCOPE)
        } catch (e: Exception) {
            lastError = "Token error: ${e.message}"
            Log.e(TAG, "Failed to get access token", e)
            null
        }
    }

    suspend fun silentToken(context: Context): String? = getAccessToken(context)

    /** Invalidate a cached token that Drive rejected (401), so the next fetch returns a fresh one. */
    suspend fun clearToken(context: Context, token: String): Unit = withContext(Dispatchers.IO) {
        runCatching { GoogleAuthUtil.clearToken(context, token) }
        Unit
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        try {
            getClient(context).signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
        }
    }
}
