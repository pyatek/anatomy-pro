package com.ptk.anatomypro.backend

import com.ptk.anatomypro.api.ApiErrorCode
import io.ktor.http.HttpStatusCode

/** A refusal with a reason the client can act on. Every one leaves as an `ApiError`. */
class ApiException(val status: HttpStatusCode, val code: ApiErrorCode, message: String = "") : Exception(message)
