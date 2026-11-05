package org.wit.vitasense.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.wit.vitasense.AppContainer
import org.wit.vitasense.ui.assessment.AssessmentViewModel
import org.wit.vitasense.ui.auth.AuthViewModel
import org.wit.vitasense.ui.aichat.AiChatViewModel
import org.wit.vitasense.ui.dashboard.DashboardViewModel
import org.wit.vitasense.ui.family.FamilyViewModel
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
            modelClass.isAssignableFrom(org.wit.vitasense.ui.reminders.RemindersViewModel::class.java) ->
                org.wit.vitasense.ui.reminders.RemindersViewModel(appContainer.remindersRepository, appContainer.reminderScheduler) as T

            modelClass.isAssignableFrom(org.wit.vitasense.ui.reports.WeeklyReportsViewModel::class.java) ->
                org.wit.vitasense.ui.reports.WeeklyReportsViewModel(appContainer.weeklyReportsRepository) as T

            modelClass.isAssignableFrom(org.wit.vitasense.ui.family.FamilySafetyViewModel::class.java) ->
                org.wit.vitasense.ui.family.FamilySafetyViewModel(appContainer.familySafetyRepository) as T

            modelClass.isAssignableFrom(org.wit.vitasense.ui.insights.HealthInsightsViewModel::class.java) ->
                org.wit.vitasense.ui.insights.HealthInsightsViewModel(appContainer.healthInsightsRepository) as T

            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(
                    healthRepository = appContainer.healthRepository,
                    authRepository = appContainer.authRepository,
                    settingsRepository = appContainer.settingsRepository,
                    aiAdviceRepository = appContainer.aiAdviceRepository,
                    familyRepository = appContainer.familyRepository,
                ) as T

            modelClass.isAssignableFrom(AssessmentViewModel::class.java) ->
                AssessmentViewModel(appContainer.healthRepository) as T

            modelClass.isAssignableFrom(TrendsViewModel::class.java) ->
                TrendsViewModel(appContainer.healthRepository) as T

            modelClass.isAssignableFrom(MoodViewModel::class.java) ->
                MoodViewModel(appContainer.moodRepository) as T

            modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                AuthViewModel(appContainer.authRepository) as T

            modelClass.isAssignableFrom(FamilyViewModel::class.java) ->
                FamilyViewModel(
                    authRepository = appContainer.authRepository,
                    familyRepository = appContainer.familyRepository,
                    moodRepository = appContainer.moodRepository,
                    healthRepository = appContainer.healthRepository,
                ) as T

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
                    cloudSyncRepository = appContainer.cloudSyncRepository,
                ) as T

            modelClass.isAssignableFrom(AiChatViewModel::class.java) ->
                AiChatViewModel(
                    aiChatRepository = appContainer.aiChatRepository,
                    settingsRepository = appContainer.settingsRepository,
                ) as T

            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
}
