package com.ptk.anatomypro

import com.ptk.anatomypro.core.data.model.SignInProvider
import com.ptk.anatomypro.core.data.repository.ProviderSignIn
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASAuthorizationErrorDomain
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sign in with Apple, through AuthenticationServices (backend plan 6.3).
 *
 * The token's audience is the app's bundle id, which is what the server lists in
 * `APPLE_CLIENT_IDS`. Only the stable subject is used (§5.3), so no scope is asked for: the
 * sheet asks for neither a name nor an email.
 *
 * [enabled] stands for the app having the Sign in with Apple capability; without it the
 * sheet fails, so nothing is offered. Google on iOS is a web flow, and is not built.
 */
class AppleProviderSignIn(enabled: Boolean) : ProviderSignIn {

    override val providers: List<SignInProvider> = if (enabled) listOf(SignInProvider.APPLE) else emptyList()

    override suspend fun idToken(provider: SignInProvider): String? {
        require(provider in providers) { "$provider is not offered on this device" }
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val request = ASAuthorizationAppleIDProvider().createRequest()
                val controller = ASAuthorizationController(authorizationRequests = listOf(request))
                // The controller holds its delegate weakly; this keeps it until it answers.
                val delegate = SheetDelegate { result ->
                    if (continuation.isActive) {
                        result.fold(continuation::resume, continuation::resumeWithException)
                    }
                }
                controller.delegate = delegate
                controller.presentationContextProvider = delegate
                continuation.invokeOnCancellation { controller.cancel() }
                controller.performRequests()
            }
        }
    }
}

/** Answers once: with the token, with null where the sheet was closed, or with what went wrong. */
private class SheetDelegate(
    private val answer: (Result<String?>) -> Unit,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    @OptIn(BetaInteropApi::class)
    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization: ASAuthorization) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        val token = credential?.identityToken?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
        answer(if (token != null) Result.success(token) else Result.failure(IllegalStateException("the sheet gave no identity token")))
    }

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        val closed = didCompleteWithError.domain == ASAuthorizationErrorDomain && didCompleteWithError.code == ASAuthorizationErrorCanceled
        // Closed: the user knows. Anything else is said as a failure.
        answer(if (closed) Result.success(null) else Result.failure(IllegalStateException("Sign in with Apple failed: ${didCompleteWithError.code}")))
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .let { windows -> windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() }
            ?: UIWindow()
}
