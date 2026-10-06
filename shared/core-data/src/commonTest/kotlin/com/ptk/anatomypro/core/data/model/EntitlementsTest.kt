package com.ptk.anatomypro.core.data.model

import com.ptk.anatomypro.core.model.SystemId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitlementsTest {

    // The ids packs and AtlasRepository.systems() actually carry. A free tier named after an
    // id nothing has is no free tier.
    private val skeletal = SystemId("skeletal-system")
    private val muscular = SystemId("muscular-system")

    @Test
    fun skeletal_is_free_forever_even_with_no_subscription_and_nothing_owned() {
        val none = Entitlements(subscribed = false, ownedSystems = emptySet())

        assertTrue(none.allows(skeletal))
    }

    @Test
    fun another_system_is_not_free() {
        val none = Entitlements(subscribed = false, ownedSystems = emptySet())

        assertFalse(none.allows(muscular))
    }

    @Test
    fun the_old_short_id_is_not_what_is_free() {
        val none = Entitlements(subscribed = false, ownedSystems = emptySet())

        assertFalse(none.allows(SystemId("skeletal")))
    }

    @Test
    fun a_subscription_allows_everything() {
        val subscribed = Entitlements(subscribed = true, ownedSystems = emptySet())

        assertTrue(subscribed.allows(muscular))
    }

    @Test
    fun an_owned_system_is_allowed_without_a_subscription() {
        val owned = Entitlements(subscribed = false, ownedSystems = setOf(muscular))

        assertTrue(owned.allows(muscular))
    }
}
