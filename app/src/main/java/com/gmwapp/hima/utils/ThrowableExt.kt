package com.gmwapp.hima.utils

import java.io.IOException

/**
 * Maps a [Throwable] to a safe, user-facing error message.
 *
 * W024: never surface the raw [Throwable.message] to users. For connectivity
 * failures the message embeds the internal hostname (e.g.
 * "Unable to resolve host demolivedb.himaapp.in" from UnknownHostException),
 * which is an information-disclosure leak. Connectivity/IO failures collapse to
 * a generic "No internet connection"; anything else returns [fallback].
 *
 * UnknownHostException, ConnectException, SocketTimeoutException and SSLException
 * are all IOException subclasses, so the single `is IOException` check covers
 * every network transport error a Retrofit `onFailure` can hand back.
 */
fun Throwable.toUserMessage(fallback: String = "Something went wrong. Please try again."): String =
    if (this is IOException) "No internet connection" else fallback
