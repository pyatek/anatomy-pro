package com.ptk.anatomypro.backend

import com.ptk.anatomypro.api.IdentityProvider
import com.ptk.anatomypro.api.Plan
import com.ptk.anatomypro.backend.accounts.ProviderConfig
import com.ptk.anatomypro.backend.subscriptions.AppStoreConfig
import com.ptk.anatomypro.backend.subscriptions.GooglePlayConfig
import java.time.Duration

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
     * The providers people may sign in with, each with the client ids a token must be made
     * out to (§5.3). A provider left out accepts nothing.
     */
    val identityProviders: Map<IdentityProvider, ProviderConfig> = emptyMap(),
    /**
     * Treats every account as a subscriber, whatever it has bought. Off unless a deployment
     * turns it on by name: the daily and the boards are paid (design §29), and who has paid
     * is answered from verified purchases (§8.1). This is an override for trying things
     * out, and a production server that has it on is giving the product away.
     */
    val everyoneIsSubscribed: Boolean = false,
    /**
     * What is on sale: the product ids, the same in both stores, and how long each runs
     * (§8.3). A purchase of any other product is not honoured. None: nothing can be bought.
     */
    val plans: List<Plan> = emptyList(),
    /** Google Play, or null where it is not set up: its purchases and its webhook then answer nothing. */
    val google: GooglePlayConfig? = null,
    /** The App Store, likewise. */
    val apple: AppStoreConfig? = null,
    /**
     * How often subscriptions near their expiry are asked about again (§8.1), or null for
     * never: a test, or a laptop with no store to ask.
     */
    val recheckEvery: Duration? = null,
    /** A subscription running out within this long is asked about again. */
    val recheckWithin: Duration = Duration.ofDays(1),
    /**
     * And one that ran out within this long, because a renewal whose notification was lost
     * looks like an expiry — and that is someone who paid being kept out.
     */
    val recheckLapsedFor: Duration = Duration.ofDays(7),
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
