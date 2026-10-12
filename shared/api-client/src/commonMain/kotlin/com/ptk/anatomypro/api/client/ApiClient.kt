package com.ptk.anatomypro.api.client

import com.ptk.anatomypro.api.AnonymousAccountResponse
import com.ptk.anatomypro.api.ApiError
import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.ApiJson
import com.ptk.anatomypro.api.ApiPaths
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer

/**
 * A few small secrets kept on the device: the Keychain on iOS, a Keystore-encrypted file on
 * Android (backend spec §5.1). Never the settings database.
 */
interface ClientStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
    suspend fun remove(key: String)
}

/** The server answered, and the answer was a refusal. Not thrown for a server that cannot be reached. */
class ApiFailure(val code: ApiErrorCode, val status: Int) : Exception("the server refused: $code ($status)")

/**
 * Every call to the server, as the account this device speaks for.
 *
 * The account is made the first time the server is needed, without asking the user anything
 * (§5.1), and its token is kept in [store]. A token the server no longer knows — the account
 * was deleted, or the server's data was — is replaced by a new account's, once, and the call
 * is made again.
 */
class ApiClient(private val http: HttpClient, private val baseUrl: String, private val store: ClientStore) {

    private val account = Mutex()

    suspend fun <T> get(path: String, response: KSerializer<T>): T =
        ApiJson.decodeFromString(response, send(HttpMethod.Get, path, body = null))

    suspend fun <B, T> post(path: String, request: KSerializer<B>, body: B, response: KSerializer<T>): T =
        ApiJson.decodeFromString(response, send(HttpMethod.Post, path, ApiJson.encodeToString(request, body)))

    private suspend fun send(method: HttpMethod, path: String, body: String?): String {
        val token = token()
        var answer = call(method, path, body, token)
        if (answer.status == HttpStatusCode.Unauthorized) {
            answer = call(method, path, body, token(replacing = token))
        }
        return answer.bodyOrThrow()
    }

    private suspend fun call(method: HttpMethod, path: String, body: String?, token: String?): HttpResponse =
        http.request(baseUrl.trimEnd('/') + path) {
            this.method = method
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    private suspend fun HttpResponse.bodyOrThrow(): String {
        val text = bodyAsText()
        if (status.isSuccess()) return text
        val code = runCatching { ApiJson.decodeFromString(ApiError.serializer(), text).code }.getOrDefault(ApiErrorCode.UNKNOWN)
        throw ApiFailure(code, status.value)
    }

    /**
     * This device's token, making the account if there is none. With [replacing], the token
     * that was just refused: it is dropped and a new account made — unless another call got
     * there first, in which case its token is the one to use.
     */
    private suspend fun token(replacing: String? = null): String = account.withLock {
        val held = store.read(TOKEN_KEY)
        if (held != null && held != replacing) return@withLock held
        val created = ApiJson.decodeFromString(
            AnonymousAccountResponse.serializer(),
            call(HttpMethod.Post, ApiPaths.ANONYMOUS, body = null, token = null).bodyOrThrow(),
        )
        store.write(TOKEN_KEY, created.deviceToken)
        created.deviceToken
    }

    companion object {
        const val TOKEN_KEY = "device-token"
    }
}
