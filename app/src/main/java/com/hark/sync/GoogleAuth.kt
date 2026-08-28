package com.hark.sync

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google authorization for the Drive appData scope, via Play Services' Identity
 * Authorization API. No backend, no client-side secret.
 *
 * First sign-in needs UI: [authorize] returns a result whose [AuthorizationResult.hasResolution]
 * is true; the caller launches [AuthorizationResult.getPendingIntent] and completes with
 * [resultFromIntent]. After that, [silentToken] returns tokens without any UI.
 */
object GoogleAuth {
    private const val APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(APPDATA_SCOPE)))
            .build()

    suspend fun authorize(context: Context): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(context)
                .authorize(request())
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    fun resultFromIntent(context: Context, data: Intent): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    /** Token without any UI. Returns null when consent is required (sync should abort and
     *  let the user sign in again from Settings). */
    suspend fun silentToken(context: Context): String? {
        val res = authorize(context)
        return if (res.hasResolution()) null else res.accessToken
    }
}
