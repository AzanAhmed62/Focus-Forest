package com.example.core.di

import android.content.Context
import com.example.core.monetization.EntitlementGateway
import com.example.core.monetization.LocalFreeEntitlementGateway
import com.example.core.time.ClockProvider
import com.example.core.time.SystemClockProvider
import com.example.data.local.datastore.UserPreferencesDataStore
import com.example.data.local.db.FocusForestDatabase
import com.example.data.repository.ChallengeRepository
import com.example.data.repository.DataManagementRepository
import com.example.data.repository.FocusRepository
import com.example.data.repository.RewardAuthority
import com.example.data.repository.ShopAndForestRepository
import com.example.data.repository.UsageRepository
import com.example.platform.audio.AmbientAudioController
import com.example.platform.audio.AndroidAmbientAudioController
import com.example.platform.usage.AndroidUsageStatsGateway
import com.example.platform.usage.UsageStatsGateway

class AppContainer(
    context: Context,
    val clockProvider: ClockProvider = SystemClockProvider(context.applicationContext),
    val database: FocusForestDatabase = FocusForestDatabase.getInstance(context.applicationContext),
    val preferencesDataStore: UserPreferencesDataStore = UserPreferencesDataStore(context.applicationContext),
    val usageStatsGateway: UsageStatsGateway = AndroidUsageStatsGateway(context.applicationContext, clockProvider),
    val entitlementGateway: EntitlementGateway = LocalFreeEntitlementGateway(),
    val ambientAudioController: AmbientAudioController = AndroidAmbientAudioController()
) {
    private val appContext: Context = context.applicationContext

    val rewardAuthority: RewardAuthority by lazy {
        RewardAuthority(database, clockProvider)
    }

    val focusRepository: FocusRepository by lazy {
        FocusRepository(database, preferencesDataStore, clockProvider, rewardAuthority)
    }

    val challengeRepository: ChallengeRepository by lazy {
        ChallengeRepository(database, clockProvider, rewardAuthority)
    }

    val usageRepository: UsageRepository by lazy {
        UsageRepository(database, preferencesDataStore, usageStatsGateway, clockProvider)
    }

    val shopAndForestRepository: ShopAndForestRepository by lazy {
        ShopAndForestRepository(database, clockProvider)
    }

    val dataManagementRepository: DataManagementRepository by lazy {
        DataManagementRepository(
            db = database,
            prefs = preferencesDataStore,
            clockProvider = clockProvider,
            appContext = appContext,
            usageRepository = usageRepository,
            ambientAudioController = ambientAudioController
        )
    }
}
