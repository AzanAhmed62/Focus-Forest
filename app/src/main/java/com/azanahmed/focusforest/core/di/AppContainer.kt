package com.azanahmed.focusforest.core.di

import android.content.Context
import com.azanahmed.focusforest.core.monetization.EntitlementGateway
import com.azanahmed.focusforest.core.monetization.LocalFreeEntitlementGateway
import com.azanahmed.focusforest.core.time.ClockProvider
import com.azanahmed.focusforest.core.time.SystemClockProvider
import com.azanahmed.focusforest.data.local.datastore.UserPreferencesDataStore
import com.azanahmed.focusforest.data.local.db.FocusForestDatabase
import com.azanahmed.focusforest.data.repository.ChallengeRepository
import com.azanahmed.focusforest.data.repository.DataManagementRepository
import com.azanahmed.focusforest.data.repository.FocusContextRepository
import com.azanahmed.focusforest.data.repository.FocusRepository
import com.azanahmed.focusforest.data.repository.RewardAuthority
import com.azanahmed.focusforest.data.repository.ShopAndForestRepository
import com.azanahmed.focusforest.data.repository.UsageRepository
import com.azanahmed.focusforest.platform.audio.AmbientAudioController
import com.azanahmed.focusforest.platform.audio.AndroidAmbientAudioController
import com.azanahmed.focusforest.platform.usage.AndroidUsageStatsGateway
import com.azanahmed.focusforest.platform.usage.UsageStatsGateway

class AppContainer(
    context: Context,
    val clockProvider: ClockProvider = SystemClockProvider(context.applicationContext),
    val database: FocusForestDatabase = FocusForestDatabase.getInstance(context.applicationContext),
    val preferencesDataStore: UserPreferencesDataStore = UserPreferencesDataStore(context.applicationContext),
    val usageStatsGateway: UsageStatsGateway = AndroidUsageStatsGateway(context.applicationContext, clockProvider),
    val entitlementGateway: EntitlementGateway = LocalFreeEntitlementGateway(),
    val ambientAudioController: AmbientAudioController = AndroidAmbientAudioController(context.applicationContext)
) {
    private val appContext: Context = context.applicationContext

    val rewardAuthority: RewardAuthority by lazy {
        RewardAuthority(database, clockProvider)
    }

    val focusRepository: FocusRepository by lazy {
        FocusRepository(database, preferencesDataStore, clockProvider, rewardAuthority)
    }

    val focusContextRepository: FocusContextRepository by lazy {
        FocusContextRepository(database, clockProvider)
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
