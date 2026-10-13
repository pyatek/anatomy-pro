package com.ptk.anatomypro.backend.subscriptions

import com.ptk.anatomypro.api.ApiErrorCode
import com.ptk.anatomypro.api.PurchaseVerdict
import com.ptk.anatomypro.backend.ApiException
import com.ptk.anatomypro.backend.transaction
import io.ktor.http.HttpStatusCode
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

enum class Store { GOOGLE, APPLE }

/** What a store last said of a subscription. Only the first three let anyone in. */
enum class SubscriptionState(val entitles: Boolean) {
    ACTIVE(true),

    /** Will not renew, and is paid for until it expires. */
    CANCELLED(true),

    /** A renewal's payment failed, and the store says to keep the subscriber in while it tries again. */
    GRACE(true),

    /** Bought with a payment that has not arrived yet. */
    PENDING(false),
    ON_HOLD(false),
    PAUSED(false),
    EXPIRED(false),

    /** Refunded, or taken back by the store. */
    REVOKED(false),

    /** Given up for another purchase (an upgrade, or subscribing again), which is the one that counts now. */
    REPLACED(false),
}

/**
 * What a store says of one subscription, already checked to be the store's own word: read
 * from the Play Developer API, or from a transaction Apple signed.
 */
data class StoreStatement(
    val store: Store,
    val storeId: String,
    val productId: String,
    val state: SubscriptionState,
    val expiresAt: Instant?,
    /** When the store said it. */
    val statedAt: Instant,
    /** The store id of the purchase this one replaces, where the store names one. */
    val supersedes: String? = null,
)

data class Subscription(
    val store: Store,
    val storeId: String,
    val productId: String,
    val state: SubscriptionState,
    val expiresAt: Instant?,
    val statedAt: Instant,
    /** Null when the account that owned it was deleted. */
    val account: UUID?,
) {
    fun entitles(now: Instant): Boolean = state.entitles && expiresAt?.isAfter(now) == true

    /** Worth giving an owner: it lets them in, or will as soon as the payment arrives. */
    fun claimable(now: Instant): Boolean = entitles(now) || state == SubscriptionState.PENDING

    /** What [caller], who presented this purchase, is told about it (§8.1). */
    fun verdictFor(caller: UUID, now: Instant): PurchaseVerdict = when {
        !claimable(now) -> PurchaseVerdict.INVALID
        account != caller -> PurchaseVerdict.OWNED_BY_ANOTHER_ACCOUNT
        entitles(now) -> PurchaseVerdict.VERIFIED
        else -> PurchaseVerdict.PENDING
    }
}

/**
 * Who has paid (backend spec §8.1). Everything here is the database's side of it: nothing
 * in this class talks to a store, so nothing in it can hold a connection across a call to one.
 */
class Subscriptions(private val database: DataSource) {

    /** Until when [account] is a subscriber, or null when it is not one at [now]. */
    fun entitledUntil(account: UUID, now: Instant): Instant? = database.connection.use { connection ->
        connection.prepareStatement(
            "SELECT max(expires_at) FROM subscription WHERE account_id = ? AND state IN ($ENTITLING) AND expires_at > ?",
        ).use { query ->
            query.setObject(1, account)
            query.setTimestamp(2, Timestamp.from(now))
            query.executeQuery().use { rows -> rows.next(); rows.getTimestamp(1)?.toInstant() }
        }
    }

    fun find(store: Store, storeId: String): Subscription? = database.connection.use { read(it, store, storeId, forUpdate = false) }

    /**
     * Writes down what a store said, and settles whose the subscription is. One transaction.
     *
     * [caller] is the account presenting the purchase, or null when nobody is — a
     * notification, or the re-check — in which case nothing is given an owner that had none.
     *
     * The rules (§8.1):
     * - a purchase nobody holds becomes [caller]'s, if it is worth holding ([Subscription.claimable]);
     * - a purchase somebody holds stays theirs, whoever presents it;
     * - a purchase that replaces another belongs to whoever held the one it replaces, and
     *   that one is finished for good;
     * - what the store said earlier than what is already written changes nothing.
     *
     * @return the row as it now stands, or null when there is none: the purchase is worth
     *   nothing and was never anyone's.
     * @throws ApiException 503 when [caller] no longer exists.
     */
    fun record(statement: StoreStatement, caller: UUID?, now: Instant): Subscription? = database.transaction { connection ->
        // The caller's account is held still first, in the order a merge takes its locks
        // (the account, then its subscriptions). A caller that a merge or a deletion removed
        // in the meantime is told to ask again — its device may by now speak for the account
        // that survived — rather than that a real purchase is worth nothing.
        if (caller != null) {
            val stillThere = connection.prepareStatement("SELECT 1 FROM account WHERE id = ? FOR KEY SHARE").use { query ->
                query.setObject(1, caller)
                query.executeQuery().use { it.next() }
            }
            if (!stillThere) throw ApiException(HttpStatusCode.ServiceUnavailable, ApiErrorCode.UNKNOWN, "the account changed; ask again")
        }
        val inherited = statement.supersedes?.let { replaced ->
            connection.prepareStatement("UPDATE subscription SET state = 'REPLACED' WHERE store = ? AND store_id = ? RETURNING account_id").use { update ->
                update.setString(1, statement.store.name)
                update.setString(2, replaced)
                update.executeQuery().use { rows -> if (rows.next()) rows.getObject(1, UUID::class.java) else null }
            }
        }
        val owner = inherited ?: caller
        val stated = Subscription(statement.store, statement.storeId, statement.productId, statement.state, statement.expiresAt, statement.statedAt, owner)

        // Claimed if nobody has it. Two accounts presenting it at once both get here; one
        // insert wins, and both then read whose it is.
        if (owner != null && stated.claimable(now)) {
            connection.prepareStatement(
                """
                INSERT INTO subscription (store, store_id, product_id, state, expires_at, stated_at, account_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """.trimIndent(),
            ).use {
                it.setString(1, stated.store.name)
                it.setString(2, stated.storeId)
                it.setString(3, stated.productId)
                it.setString(4, stated.state.name)
                it.setTimestamp(5, stated.expiresAt?.let(Timestamp::from))
                it.setTimestamp(6, Timestamp.from(stated.statedAt))
                it.setObject(7, owner)
                it.setTimestamp(8, Timestamp.from(now))
                it.executeUpdate()
            }
        }
        var held = read(connection, statement.store, statement.storeId, forUpdate = true) ?: return@transaction null
        // Replaced is final. Google may go on calling the old token active for a while, and
        // it must not be worth a second subscription to anyone.
        if (held.state == SubscriptionState.REPLACED) return@transaction held

        if (!statement.statedAt.isBefore(held.statedAt)) {
            connection.prepareStatement(
                "UPDATE subscription SET product_id = ?, state = ?, expires_at = ?, stated_at = ? WHERE store = ? AND store_id = ?",
            ).use {
                it.setString(1, statement.productId)
                it.setString(2, statement.state.name)
                it.setTimestamp(3, statement.expiresAt?.let(Timestamp::from))
                it.setTimestamp(4, Timestamp.from(statement.statedAt))
                it.setString(5, statement.store.name)
                it.setString(6, statement.storeId)
                it.executeUpdate()
            }
            held = held.copy(productId = statement.productId, state = statement.state, expiresAt = statement.expiresAt, statedAt = statement.statedAt)
        }
        // Left without an owner by a deleted account: whoever presents it now, the store
        // having just vouched for it, has it. Decided on the row as it stands, so a stale
        // statement cannot claim what a later one ended.
        if (held.account == null && owner != null && held.claimable(now)) {
            connection.prepareStatement("UPDATE subscription SET account_id = ? WHERE store = ? AND store_id = ?").use {
                it.setObject(1, owner)
                it.setString(2, statement.store.name)
                it.setString(3, statement.storeId)
                it.executeUpdate()
            }
            held = held.copy(account = owner)
        }
        held
    }

    /**
     * The subscriptions worth asking the store about again (§8.1): those that run out
     * within [within]; those that ran out within the last [lapsedFor], because a renewal
     * whose notification never came looks exactly like an expiry until someone asks; and
     * those bought within [lapsedFor] and still waiting to be paid for, which have no
     * expiry to go by.
     */
    fun dueForRecheck(now: Instant, within: Duration, lapsedFor: Duration): List<Pair<Store, String>> = database.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT store, store_id FROM subscription
            WHERE (expires_at > ? AND expires_at <= ? AND state NOT IN ('REVOKED', 'REPLACED'))
               OR (state = 'PENDING' AND created_at > ?)
            ORDER BY expires_at, created_at
            """.trimIndent(),
        ).use { query ->
            query.setTimestamp(1, Timestamp.from(now.minus(lapsedFor)))
            query.setTimestamp(2, Timestamp.from(now.plus(within)))
            query.setTimestamp(3, Timestamp.from(now.minus(lapsedFor)))
            query.executeQuery().use { rows -> buildList { while (rows.next()) add(Store.valueOf(rows.getString(1)) to rows.getString(2)) } }
        }
    }

    /** Whether the notification a store calls [id] has been acted on already. */
    fun alreadyHeard(store: Store, id: String): Boolean = database.connection.use { connection ->
        connection.prepareStatement("SELECT 1 FROM store_notification WHERE store = ? AND notification_id = ?").use { query ->
            query.setString(1, store.name)
            query.setString(2, id)
            query.executeQuery().use { it.next() }
        }
    }

    /** Written only once a notification has been acted on, so one that failed half way is acted on when it comes again. */
    fun heard(store: Store, id: String, now: Instant) {
        database.connection.use { connection ->
            connection.prepareStatement("INSERT INTO store_notification (store, notification_id, received_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING").use {
                it.setString(1, store.name)
                it.setString(2, id)
                it.setTimestamp(3, Timestamp.from(now))
                it.executeUpdate()
            }
        }
    }

    private fun read(connection: Connection, store: Store, storeId: String, forUpdate: Boolean): Subscription? =
        connection.prepareStatement(
            "SELECT store, store_id, product_id, state, expires_at, stated_at, account_id FROM subscription WHERE store = ? AND store_id = ?" +
                if (forUpdate) " FOR UPDATE" else "",
        ).use { query ->
            query.setString(1, store.name)
            query.setString(2, storeId)
            query.executeQuery().use { rows -> if (rows.next()) rows.subscription() else null }
        }

    private fun ResultSet.subscription() = Subscription(
        store = Store.valueOf(getString(1)),
        storeId = getString(2),
        productId = getString(3),
        state = SubscriptionState.valueOf(getString(4)),
        expiresAt = getTimestamp(5)?.toInstant(),
        statedAt = getTimestamp(6).toInstant(),
        account = getObject(7, UUID::class.java),
    )

    private companion object {
        /** The states that let someone in, for the query that asks. */
        val ENTITLING = SubscriptionState.entries.filter { it.entitles }.joinToString { "'${it.name}'" }
    }
}
