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
    /**
     * Treats every account as a subscriber. Off unless a deployment turns it on by name:
     * the daily and the boards are paid (design §29), and until purchases are verified
     * (plan stage 7) the only safe default is that nobody has paid.
     */
    val everyoneIsSubscribed: Boolean = false,
    /** The pack the daily draws from: the one onboarding installs for everyone (§4.2). */
    val dailyPack: String = "skeletal-body",
    /**
     * The languages the daily is offered in, the first being the one it falls back to. A
     * name is asked about only if it is reviewed in all of them.
     */
    val dailyLocales: List<String> = listOf("la"),
    val dailyQuestionCount: Int = 10,
    /** How many days a topic rests before it may be the daily again, where there are enough. */
    val topicRestDays: Int = 6,
    /** An attempt faster than this for each question is kept and not ranked (§6.3). */
    val speedFloorMillisPerQuestion: Long = 1_000,
    /** How long past midnight UTC a submit for the day before is still ranked (§6.3). */
    val lateSubmitGraceMinutes: Long = 15,
    /** Rows a board lists; the caller's own row comes as well, wherever it is. */
    val boardSize: Int = 50,
)
