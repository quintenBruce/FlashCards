package com.qcbru.flashcards

import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

// Use codes in logs. Never log exception bodies, messages, prompts, or credentials.
fun failureCode(error: Throwable): String = when (error) {
    is FirebaseFunctionsException -> error.code.name
    is TimeoutCancellationException -> "DEADLINE_EXCEEDED"
    is IOException -> "NETWORK"
    else -> error.javaClass.simpleName
}

fun operationError(error: Throwable): String {
    if (error is TimeoutCancellationException) return "The request timed out. Check its status before sending again."
    if (error is IOException) return "The connection failed. Check your internet connection and the request status."
    if (error !is FirebaseFunctionsException) return when (error) {
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "The request could not be completed."
        else -> "The operation could not be completed. Please try again."
    }
    val message = when (error.code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Sign in again to continue."
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> "This account cannot complete that action."
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> "The server took too long to reply. Check request status before sending again."
        FirebaseFunctionsException.Code.UNAVAILABLE -> "The server is temporarily unreachable. Check request status before sending again."
        FirebaseFunctionsException.Code.INTERNAL -> "The request could not be completed. Please try again."
        else -> error.message ?: "The request failed (${error.code.name})."
    }
    val reference = (error.details as? Map<*, *>)?.get("operationId") as? String
    return if (reference != null) "$message Reference: $reference" else message
}
