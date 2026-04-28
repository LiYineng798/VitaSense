# Auth, Profile, And Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `Assessment` tab with `Profile`, add local registration/login/logout, merge Settings into Profile, add Home auth status UI, and ship a standalone Python auth API scaffold for future server deployment.

**Architecture:** Add a dedicated `AuthRepository` backed by a new Room user table plus session/config values in `app_settings`; expose auth state to Home and Profile through new view models; keep Android auth local for now while persisting a future `auth_base_url`; package a standalone `FastAPI + sqlite3` auth service whose request/response contracts match the Android-side models.

**Tech Stack:** Android XML + Fragments, Room, Kotlin Flow, ViewModel, Navigation Component, Material 3 widgets already in use, Python FastAPI, sqlite3, Pydantic.

---

### Task 1: Add auth data models, DAO, repository contract, and Room wiring

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/db/entity/LocalUserEntity.kt`
- Create: `app/src/main/java/org/wit/vitasense/db/dao/LocalUserDao.kt`
- Create: `app/src/main/java/org/wit/vitasense/model/AuthModels.kt`
- Create: `app/src/main/java/org/wit/vitasense/repository/AuthRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/db/AppDatabase.kt`
- Modify: `app/src/main/java/org/wit/vitasense/AppContainer.kt`
- Modify: `app/src/main/java/org/wit/vitasense/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt`
- Test: `app/src/test/java/org/wit/vitasense/data/repository/DefaultSettingsRepositoryTest.kt`

- [ ] **Step 1: Write the failing settings and auth contract tests**

```kotlin
package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.db.dao.AppSettingDao
import org.wit.vitasense.db.entity.AppSettingEntity

class DefaultSettingsRepositoryAuthConfigTest {
    @Test
    fun persists_and_reads_auth_base_url() = runBlocking {
        val dao = InMemoryAppSettingDao()
        val repository = DefaultSettingsRepository(dao)

        repository.setAuthBaseUrl("https://example.com/api")

        assertEquals("https://example.com/api", repository.getAuthBaseUrl())
        assertEquals("https://example.com/api", repository.observeAuthBaseUrl().first())
    }
}
```

- [ ] **Step 2: Run the targeted test and verify it fails because auth-base-url methods do not exist yet**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.data.repository.DefaultSettingsRepositoryAuthConfigTest`

Expected: FAIL with unresolved `setAuthBaseUrl`, `getAuthBaseUrl`, or `observeAuthBaseUrl` methods.

- [ ] **Step 3: Add the auth models and repository contract**

```kotlin
package org.wit.vitasense.model

data class AuthUser(
    val id: Long,
    val fullName: String,
    val email: String,
    val username: String,
    val birthDate: String,
)

sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult
    data class Error(val message: String) : AuthResult
}
```

```kotlin
package org.wit.vitasense.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.model.AuthResult
import org.wit.vitasense.model.AuthUser

interface AuthRepository {
    fun observeCurrentUser(): Flow<AuthUser?>
    suspend fun getCurrentUser(): AuthUser?
    suspend fun register(
        fullName: String,
        email: String,
        username: String,
        password: String,
        birthDate: String,
    ): AuthResult
    suspend fun login(
        identifier: String,
        password: String,
    ): AuthResult
    suspend fun logout()
}
```

- [ ] **Step 4: Add the user entity, DAO, and database wiring**

```kotlin
package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_users",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["username"], unique = true),
    ],
)
data class LocalUserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val email: String,
    val username: String,
    val passwordHash: String,
    val birthDate: String,
    val createdAt: Long = System.currentTimeMillis(),
)
```

```kotlin
@Dao
interface LocalUserDao {
    @Insert
    suspend fun insert(user: LocalUserEntity): Long

    @Query("SELECT * FROM local_users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): LocalUserEntity?

    @Query("SELECT * FROM local_users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): LocalUserEntity?

    @Query("SELECT * FROM local_users WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<LocalUserEntity?>
}
```

```kotlin
@Database(
    entities = [
        HeartRateRawSampleEntity::class,
        SleepRecordEntity::class,
        DailyPhysiologySummaryEntity::class,
        RiskAssessmentRecordEntity::class,
        MoodRecordEntity::class,
        AppSettingEntity::class,
        ImportLogEntity::class,
        LocalUserEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localUserDao(): LocalUserDao
}
```

```kotlin
private val database: AppDatabase by lazy {
    Room.databaseBuilder(context, AppDatabase::class.java, "vitasense.db")
        .fallbackToDestructiveMigration()
        .build()
}
```

- [ ] **Step 5: Expand settings repository for auth session/config keys**

```kotlin
interface SettingsRepository {
    fun observeThemeMode(): Flow<ThemeMode>
    fun observeThemeFamily(): Flow<ThemeFamily>
    fun observeAuthBaseUrl(): Flow<String>
    fun observeCurrentUserId(): Flow<Long?>
    suspend fun getThemeMode(): ThemeMode
    suspend fun getThemeFamily(): ThemeFamily
    suspend fun getAuthBaseUrl(): String
    suspend fun getCurrentUserId(): Long?
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setThemeFamily(family: ThemeFamily)
    suspend fun setAuthBaseUrl(baseUrl: String)
    suspend fun setCurrentUserId(userId: Long?)
}
```

```kotlin
private companion object {
    const val THEME_KEY = "theme_mode"
    const val THEME_FAMILY_KEY = "theme_family"
    const val AUTH_BASE_URL_KEY = "auth_base_url"
    const val CURRENT_USER_ID_KEY = "current_user_id"
}
```

- [ ] **Step 6: Run the settings repository tests and verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.data.repository.DefaultSettingsRepositoryTest`

Expected: PASS, including the new auth-base-url coverage.


### Task 2: Implement local auth repository and its unit tests

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/data/repository/DefaultAuthRepository.kt`
- Create: `app/src/main/java/org/wit/vitasense/util/PasswordHashing.kt`
- Create: `app/src/test/java/org/wit/vitasense/data/repository/DefaultAuthRepositoryTest.kt`
- Modify: `app/src/main/java/org/wit/vitasense/AppContainer.kt`

- [ ] **Step 1: Write the failing auth repository tests**

```kotlin
package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.AuthResult

class DefaultAuthRepositoryTest {
    @Test
    fun registers_user_and_sets_session() = runBlocking {
        val repository = buildRepository()

        val result = repository.register(
            fullName = "Ava Stone",
            email = "ava@example.com",
            username = "ava",
            password = "password123",
            birthDate = "2000-01-02",
        )

        assertTrue(result is AuthResult.Success)
        assertEquals("ava@example.com", repository.getCurrentUser()?.email)
    }
}
```

- [ ] **Step 2: Run the auth repository test and verify it fails because `DefaultAuthRepository` does not exist**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.data.repository.DefaultAuthRepositoryTest`

Expected: FAIL with missing repository or method implementations.

- [ ] **Step 3: Implement password hashing helper and repository behavior**

```kotlin
package org.wit.vitasense.util

import java.security.MessageDigest

object PasswordHashing {
    fun sha256(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
```

```kotlin
class DefaultAuthRepository(
    private val localUserDao: LocalUserDao,
    private val settingsRepository: SettingsRepository,
) : AuthRepository {
    override suspend fun register(
        fullName: String,
        email: String,
        username: String,
        password: String,
        birthDate: String,
    ): AuthResult {
        if (localUserDao.findByEmail(email.lowercase()) != null) {
            return AuthResult.Error("Email is already registered.")
        }
        if (localUserDao.findByUsername(username.lowercase()) != null) {
            return AuthResult.Error("Username is already taken.")
        }
        val id =
            localUserDao.insert(
                LocalUserEntity(
                    fullName = fullName.trim(),
                    email = email.trim().lowercase(),
                    username = username.trim().lowercase(),
                    passwordHash = PasswordHashing.sha256(password),
                    birthDate = birthDate,
                ),
            )
        settingsRepository.setCurrentUserId(id)
        return AuthResult.Success(requireNotNull(getCurrentUser()))
    }
}
```

- [ ] **Step 4: Add login, observeCurrentUser, and logout coverage**

```kotlin
@Test
fun logs_in_with_username_and_rejects_wrong_password() = runBlocking {
    val repository = buildRepositoryWithUser(
        email = "ava@example.com",
        username = "ava",
        password = "password123",
    )

    val success = repository.login("ava", "password123")
    val failure = repository.login("ava", "wrong-password")

    assertTrue(success is AuthResult.Success)
    assertTrue(failure is AuthResult.Error)
    assertEquals("Invalid credentials.", (failure as AuthResult.Error).message)
}
```

- [ ] **Step 5: Wire the repository through `AppContainer`**

```kotlin
val authRepository: AuthRepository by lazy {
    DefaultAuthRepository(
        localUserDao = database.localUserDao(),
        settingsRepository = settingsRepository,
    )
}
```

- [ ] **Step 6: Run the auth repository tests and verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.data.repository.DefaultAuthRepositoryTest`

Expected: PASS for register, duplicate validation, login, current-user observation, and logout scenarios.


### Task 3: Add auth and profile view models plus factory wiring

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/ui/auth/AuthViewModel.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/profile/ProfileViewModel.kt`
- Create: `app/src/test/java/org/wit/vitasense/ui/auth/AuthViewModelTest.kt`
- Create: `app/src/test/java/org/wit/vitasense/ui/profile/ProfileViewModelTest.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt`

- [ ] **Step 1: Write the failing auth and profile view model tests**

```kotlin
class AuthViewModelTest {
    @Test
    fun validates_register_form_before_repository_call() = runBlocking {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository, CoroutineScope(Job() + Dispatchers.Unconfined))

        viewModel.submitRegister(
            fullName = "",
            email = "not-an-email",
            username = "",
            password = "123",
            confirmPassword = "456",
            birthDate = "",
        )

        assertEquals("Passwords do not match.", viewModel.state.value.errorMessage)
    }
}
```

```kotlin
class ProfileViewModelTest {
    @Test
    fun combines_signed_in_user_with_theme_and_demo_data() = runBlocking {
        val viewModel = buildProfileViewModel()

        assertEquals("Ava Stone", viewModel.state.value.user?.fullName)
        assertEquals(ThemeFamily.DEFAULT, viewModel.state.value.themeFamily)
    }
}
```

- [ ] **Step 2: Run the targeted tests and verify they fail because the new view models do not exist yet**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.auth.AuthViewModelTest --tests org.wit.vitasense.ui.profile.ProfileViewModelTest`

Expected: FAIL with missing classes or state properties.

- [ ] **Step 3: Implement `AuthViewModel` with explicit login/register state**

```kotlin
data class AuthScreenState(
    val mode: AuthMode = AuthMode.LOGIN,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val signedInUser: AuthUser? = null,
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val modelScope = scope ?: viewModelScope
    private val _state = MutableStateFlow(AuthScreenState())
    val state: StateFlow<AuthScreenState> = _state.asStateFlow()
}
```

- [ ] **Step 4: Implement `ProfileViewModel` by moving reusable settings behavior here**

```kotlin
data class ProfileScreenState(
    val user: AuthUser? = null,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val themeFamily: ThemeFamily = ThemeFamily.DEFAULT,
    val demoBundles: List<DemoBundleInfo> = emptyList(),
    val isSignedIn: Boolean = false,
)
```

```kotlin
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val healthRepository: HealthRepository,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope? = null,
) : ViewModel()
```

- [ ] **Step 5: Register the new view models in `VitaSenseViewModelFactory`**

```kotlin
modelClass.isAssignableFrom(AuthViewModel::class.java) ->
    AuthViewModel(appContainer.authRepository) as T

modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
    ProfileViewModel(
        authRepository = appContainer.authRepository,
        healthRepository = appContainer.healthRepository,
        settingsRepository = appContainer.settingsRepository,
    ) as T
```

- [ ] **Step 6: Run the auth/profile/settings view model tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.auth.AuthViewModelTest --tests org.wit.vitasense.ui.profile.ProfileViewModelTest --tests org.wit.vitasense.ui.settings.SettingsViewModelTest`

Expected: PASS with validation, state composition, and theme behavior intact.


### Task 4: Replace bottom-tab destinations and nav graph wiring

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/ui/profile/ProfileFragment.kt`
- Create: `app/src/main/java/org/wit/vitasense/ui/auth/AuthFragment.kt`
- Modify: `app/src/main/res/navigation/main_nav_graph.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/navigation/BottomTabDestination.kt`
- Modify: `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- Modify: `app/src/main/res/layout/view_floating_bottom_tabs.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/org/wit/vitasense/ui/navigation/BottomTabDestinationTest.kt`

- [ ] **Step 1: Write the failing bottom-tab mapping test**

```kotlin
@Test
fun maps_profile_to_fourth_bottom_tab_and_ignores_assessment() {
    assertEquals(BottomTabDestination.HOME, BottomTabDestination.fromDestinationId(R.id.dashboardFragment))
    assertEquals(BottomTabDestination.TRENDS, BottomTabDestination.fromDestinationId(R.id.trendsFragment))
    assertEquals(BottomTabDestination.MOOD, BottomTabDestination.fromDestinationId(R.id.moodFragment))
    assertEquals(BottomTabDestination.PROFILE, BottomTabDestination.fromDestinationId(R.id.profileFragment))
    assertNull(BottomTabDestination.fromDestinationId(R.id.assessmentFragment))
}
```

- [ ] **Step 2: Run the bottom-tab test and verify it fails because `PROFILE` and `profileFragment` do not exist yet**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.navigation.BottomTabDestinationTest`

Expected: FAIL with missing destination IDs or enum members.

- [ ] **Step 3: Update the enum, nav graph, and floating tab layout together**

```kotlin
enum class BottomTabDestination(
    @IdRes val navDestinationId: Int,
    @IdRes val tabViewId: Int,
    @IdRes val iconViewId: Int,
    @IdRes val labelViewId: Int,
) {
    HOME(R.id.dashboardFragment, R.id.tabHome, R.id.tabHomeIcon, R.id.tabHomeLabel),
    TRENDS(R.id.trendsFragment, R.id.tabTrends, R.id.tabTrendsIcon, R.id.tabTrendsLabel),
    MOOD(R.id.moodFragment, R.id.tabMood, R.id.tabMoodIcon, R.id.tabMoodLabel),
    PROFILE(R.id.profileFragment, R.id.tabProfile, R.id.tabProfileIcon, R.id.tabProfileLabel),
}
```

```xml
<fragment
    android:id="@+id/profileFragment"
    android:name="org.wit.vitasense.ui.profile.ProfileFragment"
    android:label="@string/nav_profile" />

<fragment
    android:id="@+id/authFragment"
    android:name="org.wit.vitasense.ui.auth.AuthFragment"
    android:label="@string/auth_title" />
```

- [ ] **Step 4: Update `MainActivity` click bindings to `Home / Trends / Mood / Profile`**

```kotlin
findViewById<View>(R.id.tabMood).setOnClickListener {
    navigateToBottomDestination(BottomTabDestination.MOOD)
}
findViewById<View>(R.id.tabProfile).setOnClickListener {
    navigateToBottomDestination(BottomTabDestination.PROFILE)
}
```

- [ ] **Step 5: Run the bottom-tab test again**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.navigation.BottomTabDestinationTest`

Expected: PASS with the new four-tab mapping.


### Task 5: Add Home auth-status state and remove the Home settings action

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardHomeModels.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`
- Modify: `app/src/main/res/layout/fragment_dashboard.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/org/wit/vitasense/ui/dashboard/DashboardViewModelTest.kt`

- [ ] **Step 1: Write the failing Home auth-state view model test**

```kotlin
@Test
fun exposes_signed_in_home_header_copy() = runBlocking {
    val authRepository = FakeAuthRepository(currentUser = AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02"))
    val viewModel = DashboardViewModel(FakeHealthRepository(), authRepository, CoroutineScope(Job() + Dispatchers.Unconfined))

    assertEquals("Welcome, Ava Stone!", viewModel.state.value.authPrompt)
    assertTrue(viewModel.state.value.isSignedIn)
}
```

- [ ] **Step 2: Run the targeted dashboard test and verify it fails because the auth fields are missing**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.dashboard.DashboardViewModelTest`

Expected: FAIL with missing constructor args or `authPrompt` state fields.

- [ ] **Step 3: Extend the dashboard state and view model with auth-aware header fields**

```kotlin
data class DashboardScreenState(
    val totalScore: String = "--",
    val trendPages: List<DashboardTrendPageModel> = listOf(
        DashboardTrendPageModel("7-Day Trend", TrendChartModel.Empty),
    ),
    val showTrendDots: Boolean = false,
    val isSignedIn: Boolean = false,
    val authPrompt: String = "Tap to sign in",
    val authInitial: String = "?",
)
```

```kotlin
class DashboardViewModel(
    private val repository: HealthRepository,
    private val authRepository: AuthRepository,
    scope: CoroutineScope? = null,
) : ViewModel()
```

- [ ] **Step 4: Replace the toolbar menu in the Home layout with an inline auth-status row**

```xml
<LinearLayout
    android:id="@+id/homeAuthEntry"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal">

    <com.google.android.material.card.MaterialCardView
        android:layout_width="40dp"
        android:layout_height="40dp"
        app:cardCornerRadius="20dp">

        <TextView
            android:id="@+id/homeAuthAvatarText"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:text="?" />
    </com.google.android.material.card.MaterialCardView>

    <TextView
        android:id="@+id/homeAuthStatusText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="10dp"
        android:text="@string/home_auth_signed_out" />
</LinearLayout>
```

- [ ] **Step 5: Navigate the Home auth entry to `authFragment` when signed out, otherwise to `profileFragment`**

```kotlin
binding.homeAuthEntry.setOnClickListener {
    val destination = if (state.isSignedIn) R.id.profileFragment else R.id.authFragment
    findNavController().navigate(destination)
}
```

- [ ] **Step 6: Run the dashboard test again**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.dashboard.DashboardViewModelTest`

Expected: PASS with the new signed-in/signed-out Home header state.


### Task 6: Build the Profile UI and migrate settings behavior into it

**Files:**
- Create: `app/src/main/res/layout/fragment_profile.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/profile/ProfileFragment.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/profile/ProfileViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/org/wit/vitasense/ui/profile/ProfileViewModelTest.kt`

- [ ] **Step 1: Write the failing profile signed-out vs signed-in test**

```kotlin
@Test
fun exposes_signed_out_profile_cta_and_signed_in_identity() = runBlocking {
    val signedOut = buildProfileViewModel(user = null)
    val signedIn = buildProfileViewModel(user = AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02"))

    assertEquals(false, signedOut.state.value.isSignedIn)
    assertEquals(true, signedIn.state.value.isSignedIn)
    assertEquals("ava@example.com", signedIn.state.value.user?.email)
}
```

- [ ] **Step 2: Run the profile test and verify it fails before UI state is implemented**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.profile.ProfileViewModelTest`

Expected: FAIL because the state does not yet expose account/profile fields.

- [ ] **Step 3: Build `fragment_profile.xml` by adapting the existing settings sections**

```xml
<androidx.core.widget.NestedScrollView ...>
    <LinearLayout ...>
        <com.google.android.material.card.MaterialCardView ...>
            <LinearLayout ...>
                <TextView
                    android:id="@+id/profileAvatarText"
                    ... />
                <TextView
                    android:id="@+id/profileNameText"
                    ... />
                <TextView
                    android:id="@+id/profileEmailText"
                    ... />
                <com.google.android.material.button.MaterialButton
                    android:id="@+id/profileSignInButton"
                    ... />
                <com.google.android.material.button.MaterialButton
                    android:id="@+id/profileLogoutButton"
                    ... />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
```

- [ ] **Step 4: Move theme and import actions into `ProfileFragment`**

```kotlin
binding.themeDefaultCard.setOnClickListener { viewModel.setThemeFamily(ThemeFamily.DEFAULT) }
binding.lightThemeButton.setOnClickListener { viewModel.setThemeMode(ThemeMode.LIGHT) }
binding.profileSignInButton.setOnClickListener { findNavController().navigate(R.id.authFragment) }
binding.profileLogoutButton.setOnClickListener { viewModel.logout() }
```

- [ ] **Step 5: Keep `SettingsViewModel` only as a thin compatibility wrapper or stop referencing it from UI**

```kotlin
// Preferred end state: ProfileViewModel owns theme/import behavior and SettingsFragment is unused.
```

- [ ] **Step 6: Run the profile and settings tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.profile.ProfileViewModelTest --tests org.wit.vitasense.ui.settings.SettingsViewModelTest`

Expected: PASS with theme/import behavior preserved under Profile state.


### Task 7: Build the login/register fragment and wire form interactions

**Files:**
- Create: `app/src/main/res/layout/fragment_auth.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/auth/AuthFragment.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/auth/AuthViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/org/wit/vitasense/ui/auth/AuthViewModelTest.kt`

- [ ] **Step 1: Expand the failing auth view model tests to cover login, register, and mode switching**

```kotlin
@Test
fun successful_register_switches_state_to_signed_in() = runBlocking {
    val repository = FakeAuthRepository(registerResult = AuthResult.Success(AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")))
    val viewModel = AuthViewModel(repository, CoroutineScope(Job() + Dispatchers.Unconfined))

    viewModel.submitRegister(
        fullName = "Ava Stone",
        email = "ava@example.com",
        username = "ava",
        password = "password123",
        confirmPassword = "password123",
        birthDate = "2000-01-02",
    )

    assertEquals("Ava Stone", viewModel.state.value.signedInUser?.fullName)
}
```

- [ ] **Step 2: Run the auth view model test and verify it fails before submit logic exists**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.auth.AuthViewModelTest`

Expected: FAIL with missing submit methods or state mutation.

- [ ] **Step 3: Implement register/login validation and submit methods**

```kotlin
fun submitLogin(
    identifier: String,
    password: String,
) {
    if (identifier.isBlank() || password.isBlank()) {
        _state.value = _state.value.copy(errorMessage = "Identifier and password are required.")
        return
    }
    modelScope.launch {
        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)
        when (val result = authRepository.login(identifier, password)) {
            is AuthResult.Success -> _state.value = _state.value.copy(isSubmitting = false, signedInUser = result.user)
            is AuthResult.Error -> _state.value = _state.value.copy(isSubmitting = false, errorMessage = result.message)
        }
    }
}
```

- [ ] **Step 4: Build the XML with login/register mode switching and a date picker trigger**

```xml
<com.google.android.material.button.MaterialButtonToggleGroup
    android:id="@+id/authModeToggle"
    ...>
    <com.google.android.material.button.MaterialButton
        android:id="@+id/loginModeButton"
        android:text="@string/auth_login_tab" />
    <com.google.android.material.button.MaterialButton
        android:id="@+id/registerModeButton"
        android:text="@string/auth_register_tab" />
</com.google.android.material.button.MaterialButtonToggleGroup>
```

```kotlin
DatePickerDialog(
    requireContext(),
    { _, year, month, dayOfMonth ->
        binding.registerBirthDateInput.setText("%04d-%02d-%02d".format(year, month + 1, dayOfMonth))
    },
    2000,
    0,
    1,
).show()
```

- [ ] **Step 5: Navigate back when a signed-in user appears in state**

```kotlin
if (state.signedInUser != null) {
    findNavController().popBackStack()
}
```

- [ ] **Step 6: Run the auth view model tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.ui.auth.AuthViewModelTest`

Expected: PASS for login, register, validation, and mode-switch coverage.


### Task 8: Add the standalone Python auth API program

**Files:**
- Create: `python_auth_api/main.py`
- Create: `python_auth_api/requirements.txt`
- Create: `python_auth_api/README.md`
- Create: `python_auth_api/smoke_test.py`

- [ ] **Step 1: Write the smoke-test script first**

```python
import requests

BASE_URL = "http://127.0.0.1:8000"

def main():
    register = requests.post(
        f"{BASE_URL}/api/v1/auth/register",
        json={
            "full_name": "Ava Stone",
            "email": "ava@example.com",
            "username": "ava",
            "password": "password123",
            "birth_date": "2000-01-02",
        },
        timeout=10,
    )
    assert register.status_code == 200, register.text
```

- [ ] **Step 2: Run the smoke test and verify it fails because the API is not implemented yet**

Run: `python python_auth_api/smoke_test.py`

Expected: FAIL with connection error or missing server.

- [ ] **Step 3: Implement the FastAPI service with sqlite-backed user and token tables**

```python
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, EmailStr
import hashlib
import secrets
import sqlite3

app = FastAPI(title="VitaSense Auth API")

class RegisterRequest(BaseModel):
    full_name: str
    email: EmailStr
    username: str
    password: str
    birth_date: str
```

```python
@app.post("/api/v1/auth/register")
def register(payload: RegisterRequest):
    ...

@app.post("/api/v1/auth/login")
def login(payload: LoginRequest):
    ...

@app.get("/api/v1/auth/me")
def me(authorization: str | None = Header(default=None)):
    ...
```

- [ ] **Step 4: Document run and deployment commands in the README**

```md
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
python smoke_test.py
```

- [ ] **Step 5: Run the smoke test against a local server**

Run: `python python_auth_api/smoke_test.py`

Expected: PASS for register, duplicate rejection, login, invalid password rejection, and `/me`.


### Task 9: Final Android verification and cleanup

**Files:**
- Modify: any touched files from Tasks 1-8
- Test: `app/src/test/java/org/wit/vitasense/data/repository/DefaultAuthRepositoryTest.kt`
- Test: `app/src/test/java/org/wit/vitasense/ui/auth/AuthViewModelTest.kt`
- Test: `app/src/test/java/org/wit/vitasense/ui/profile/ProfileViewModelTest.kt`
- Test: `app/src/test/java/org/wit/vitasense/ui/navigation/BottomTabDestinationTest.kt`
- Test: `app/src/test/java/org/wit/vitasense/ui/dashboard/DashboardViewModelTest.kt`

- [ ] **Step 1: Run the focused Android auth/profile/navigation test suite**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.data.repository.DefaultAuthRepositoryTest --tests org.wit.vitasense.ui.auth.AuthViewModelTest --tests org.wit.vitasense.ui.profile.ProfileViewModelTest --tests org.wit.vitasense.ui.navigation.BottomTabDestinationTest --tests org.wit.vitasense.ui.dashboard.DashboardViewModelTest`

Expected: PASS

- [ ] **Step 2: Run the existing theme/settings regression suite**

Run: `./gradlew.bat :app:testDebugUnitTest --tests org.wit.vitasense.data.repository.DefaultSettingsRepositoryTest --tests org.wit.vitasense.ui.settings.SettingsViewModelTest --tests org.wit.vitasense.ui.theme.ThemeFamilyStyleResolverTest --tests org.wit.vitasense.ui.theme.ThemeSemanticColorRegressionTest`

Expected: PASS

- [ ] **Step 3: Assemble debug artifacts**

Run: `./gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Assemble Android test APK**

Run: `./gradlew.bat :app:assembleDebugAndroidTest`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Re-scan strings and navigation labels for removed `Settings` and top-level `Assessment` references**

Run: `rg -n "Settings|Assessment" app/src/main/res app/src/main/java`

Expected: only intentional secondary references remain.

- [ ] **Step 6: Do not create a git commit unless the user explicitly requests one**

```text
User preference override: leave the worktree uncommitted until asked.
```
