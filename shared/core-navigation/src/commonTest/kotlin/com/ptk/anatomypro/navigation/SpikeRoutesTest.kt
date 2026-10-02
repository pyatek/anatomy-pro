package com.ptk.anatomypro.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SpikeRoutesTest {

    @Test
    fun a_route_with_an_argument_round_trips() {
        val encoded = Json.encodeToString(SpikeDetail.serializer(), SpikeDetail("costa-vii"))
        val decoded = Json.decodeFromString(SpikeDetail.serializer(), encoded)

        assertEquals(SpikeDetail("costa-vii"), decoded)
    }
}
