package com.ptk.anatomypro

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform