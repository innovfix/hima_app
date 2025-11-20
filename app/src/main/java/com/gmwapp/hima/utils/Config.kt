package com.gmwapp.hima.utils

object Config {
    const val BASE_URL = "https://himaapp.in/api/auth"
    // ⭐ Socket.IO Server URL - Use base URL only, path is set in options
    const val SOCKET_URL = "https://test.himaapp.in"
    const val SOCKET_PATH = "/socket.io"  // Removed trailing slash - some servers don't accept it
}

