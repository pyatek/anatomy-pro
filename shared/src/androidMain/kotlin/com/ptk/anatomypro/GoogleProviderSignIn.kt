package com.ptk.anatomypro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ptk.anatomypro.core.data.model.SignInProvider
import com.ptk.anatomypro.core.data.repository.ProviderSignIn
import java.lang.ref.WeakReference

/**
 * Google's sign-in sheet, through Credential Manager (backend plan 6.3).
 *
 * [serverClientId] is the *web* client id from the Google Cloud console, not the Android one:
 * it is the audience of the token the sheet hands back, so it is the one the server must
 * list in `GOOGLE_CLIENT_IDS`. Without it nothing is offered.
 *
 * Sign in with Apple is not offered here: on Android it is a web flow, and is not built.
 */
class GoogleProviderSignIn(
    private val serverClientId: String,
    private val activity: () -> Activity?,
) : ProviderSignIn {

    override val providers: List<SignInProvider> =
        if (serverClientId.isBlank()) emptyList() else listOf(SignInProvider.GOOGLE)

    override suspend fun idToken(provider: SignInProvider): String? {
        require(provider in providers) { "$provider is not offered on this device" }
        // The sheet is shown over an activity. The model asking outlives any one of them.
        val host = checkNotNull(activity()) { "no activity to show the sign-in sheet over" }
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        val credential = try {
            CredentialManager.create(host).getCredential(host, request).credential
        } catch (_: GetCredentialCancellationException) {
            return null
        }
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "the sheet answered with a ${credential.type}"
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}

/**
 * The activity the app is showing, for a sheet to be shown over. Held weakly and for the
 * process, because the models that ask for a sheet survive the activity being recreated.
 */
private object ShownActivity {
    private var current: WeakReference<Activity>? = null

    fun get(): Activity? = current?.get()

    fun set(activity: Activity) {
        current = WeakReference(activity)
    }

    fun clear(activity: Activity) {
        if (current?.get() === activity) current = null
    }
}

/** Google sign-in for a build given [serverClientId]; a blank one offers nothing. */
@Composable
fun rememberGoogleSignIn(serverClientId: String): ProviderSignIn {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        if (activity != null) ShownActivity.set(activity)
        onDispose { if (activity != null) ShownActivity.clear(activity) }
    }
    return remember(serverClientId) { GoogleProviderSignIn(serverClientId, ShownActivity::get) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
