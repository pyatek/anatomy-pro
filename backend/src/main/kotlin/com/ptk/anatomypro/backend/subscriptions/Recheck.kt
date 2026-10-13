package com.ptk.anatomypro.backend.subscriptions

import com.ptk.anatomypro.backend.Settings
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.sql.DataSource

/** What a re-check did: how many subscriptions a store was asked about, and how many of those it could not be. */
data class Rechecked(val asked: Int, val failed: Int)

/**
 * Everything about subscriptions that the server is made of (backend spec §8), put together
 * once: the routes are given these, and so is the schedule.
 *
 * A store is here only when it is both configured and can be asked — Google needs its API
 * for everything; Apple's signed transactions are checked without one.
 */
class StoreServices(
    database: DataSource,
    private val settings: Settings,
    private val now: () -> Instant,
    fetchKeys: (url: String) -> String,
    play: PlayDeveloperApi?,
    appStoreApi: AppStoreServerApi?,
) {
    val subscriptions = Subscriptions(database)
    val entitlements = Entitlements(subscriptions, settings, now)
    private val products = settings.plans.map { it.productId }.toSet()
    val google: GooglePlay? = settings.google?.let { config -> play?.let { GooglePlay(config, it, subscriptions, products, fetchKeys, now) } }
    val apple: AppStore? = settings.apple?.let { AppStore(it, appStoreApi, subscriptions, products, now) }

    /**
     * Asks the stores again about every subscription that is about to run out or lately
     * did (§8.1), so that a notification that never came cannot keep someone who paid out,
     * or someone refunded in, for longer than the time between two of these.
     *
     * The list is read and the connection given back before any store is asked. A store
     * that cannot be asked about one subscription is counted and the rest are still asked;
     * not being able to ask changes nothing about anyone.
     */
    fun recheck(): Rechecked {
        val due = subscriptions.dueForRecheck(now(), settings.recheckWithin, settings.recheckLapsedFor)
        var asked = 0
        var failed = 0
        for ((store, storeId) in due) {
            val ask: (() -> Unit) = when (store) {
                Store.GOOGLE -> google?.let { { it.refresh(storeId) } }
                Store.APPLE -> apple?.takeIf { it.canAsk }?.let { { it.refresh(storeId) } }
            } ?: continue
            asked++
            try {
                ask()
            } catch (failure: Exception) {
                // The type and no more: a store client's message may name what it was asked about.
                failed++
                log.warn("a {} subscription could not be asked about again ({})", store, failure.javaClass.simpleName)
            }
        }
        if (asked > 0) log.info("asked the stores again about {} subscriptions near their expiry; {} could not be asked", asked, failed)
        return Rechecked(asked, failed)
    }

    private companion object {
        val log = LoggerFactory.getLogger(StoreServices::class.java)!!
    }
}
