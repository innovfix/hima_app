package com.gmwapp.hima.callbacks

/**
 * Implemented by fragments that should reload their main content when the user taps Retry
 * on the app-wide offline snackbar (after connectivity is restored).
 */
interface NetworkRetryable {
    fun onNetworkRetry()
}
