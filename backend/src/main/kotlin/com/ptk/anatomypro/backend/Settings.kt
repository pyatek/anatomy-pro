package com.ptk.anatomypro.backend

/** What can differ between a test, a laptop and the box, apart from the database. */
data class Settings(
    val nicknameBlocklist: Set<String> = emptySet(),
    /** New anonymous accounts one address may make in a minute. */
    val accountsPerAddressPerMinute: Int = 10,
    /** Requests one device token may make in a minute. */
    val requestsPerTokenPerMinute: Int = 120,
    /**
     * Whether `X-Forwarded-For` says who is calling. True only behind our own proxy, which
     * is the one thing listening publicly (spec §11.1); anywhere else a caller could name
     * any address it liked and walk round the limit above.
     */
    val trustForwardedHeaders: Boolean = false,
)
