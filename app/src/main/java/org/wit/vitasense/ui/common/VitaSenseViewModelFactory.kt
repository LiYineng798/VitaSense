package org.wit.vitasense.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.wit.vitasense.AppContainer
import org.wit.vitasense.ui.assessment.AssessmentViewModel
import org.wit.vitasense.ui.auth.AuthViewModel
import org.wit.vitasense.ui.dashboard.DashboardViewModel
import org.wit.vitasense.ui.mood.MoodViewModel
import org.wit.vitasense.ui.profile.ProfileViewModel
import org.wit.vitasense.ui.settings.SettingsViewModel
import org.wit.vitasense.ui.trends.TrendsViewModel

class VitaSenseViewModelFactory(
    private val appContainer: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(
                    healthRepository = appContainer.healthRepository,
                    authRepository = appContainer.authRepository,
                    settingsRepository = appContainer.settingsRepository,
                    aiAdviceRepository = appContainer.aiAdviceRepository,
                ) as T

            modelClass.isAssignableFrom(AssessmentViewModel::class.java) ->
                AssessmentViewModel(appContainer.healthRepository) as T

            modelClass.isAssignableFrom(TrendsViewModel::class.java) ->
                TrendsViewModel(appContainer.healthRepository) as T

            modelClass.isAssignableFrom(MoodViewModel::class.java) ->
                MoodViewModel(appContainer.moodRepository) as T

            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(appContainer.authRepository) as T

            modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                ProfileViewModel(
                    authRepository = appContainer.authRepository,
                    healthRepository = appContainer.healthRepository,
                    settingsRepository = appContainer.settingsRepository,
                ) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    healthRepository = appContainer.healthRepository,
                    settingsRepository = appContainer.settingsRepository,
                ) as T

            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
}
