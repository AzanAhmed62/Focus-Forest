package com.azanahmed.focusforest.core.monetization

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FeatureEntitlement {
    ADVANCED_ANALYTICS,
    LONG_TERM_COMPARISONS,
    EXTENDED_FOREST_ENVIRONMENTS,
    CLOUD_SYNC,
    SOCIAL_ROOMS
}

enum class PlanTier {
    FREE,
    FOCUS_FOREST_PLUS
}

data class EntitlementState(
    val tier: PlanTier = PlanTier.FREE,
    val isServerVerified: Boolean = false
)

/**
 * Isolated monetization boundary prepared for future native Google Play Billing integration
 * with server-verified purchase receipts. Version 1 strictly exposes the local Free tier without
 * any mock unlocks, local premium flags, or ads.
 */
interface EntitlementGateway {
    val entitlementState: StateFlow<EntitlementState>
    fun hasEntitlement(feature: FeatureEntitlement): Boolean
}

class LocalFreeEntitlementGateway : EntitlementGateway {
    private val _entitlementState = MutableStateFlow(EntitlementState())
    override val entitlementState: StateFlow<EntitlementState> = _entitlementState.asStateFlow()

    override fun hasEntitlement(feature: FeatureEntitlement): Boolean = false
}
