package com.gmwapp.hima.utils

import com.gmwapp.hima.BuildConfig

object Config {
    val BASE_URL: String
        get() = BuildConfig.BASE_URL.removeSuffix("/")

    val API_ROOT: String
        get() = BuildConfig.BASE_URL.removeSuffix("auth/")

    val APP_ROOT: String
        get() = BuildConfig.BASE_URL.removeSuffix("api/auth/")

    // Socket.IO Server URL - use only the app host root; the path is set in options.
    val SOCKET_URL: String
        get() = APP_ROOT.removeSuffix("/")

    const val SOCKET_PATH = "/socket.io"  // Removed trailing slash - some servers don't accept it
}

