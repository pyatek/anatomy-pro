package com.ptk.anatomypro.navigation

import kotlinx.serialization.Serializable

/**
 * Throwaway. Task 14 replaces these with the real destination hierarchy; they exist only
 * so the version pair in Task 1 is proven against something that compiles.
 */
@Serializable
data object SpikeHome

@Serializable
data class SpikeDetail(val structureId: String)
