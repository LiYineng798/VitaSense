# Family Card Information Hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize Family member cards into clear identity, status, support, and control zones so the page is easier to scan.

**Architecture:** Keep the existing backend, repository, ViewModel, and behavior unchanged. Add a few display labels to the Family UI model, map them in `FamilyUiMapper`, then update `item_family_member.xml` and `FamilyMemberAdapter` so the same data is presented in clearer visual groups.

**Tech Stack:** Kotlin, XML Views, ViewBinding, Material Components, RecyclerView, JUnit mapper tests, Gradle Android build gates.

---

## Scope Check

This plan only changes Family page presentation. It does not add new Family data, backend fields, APIs, health metrics, collapsible cards, or family-level aggregate summaries.

---

## File Map

- Modify `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiModels.kt`
  - Add section title fields used by the adapter.
- Modify `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiMapper.kt`
  - Populate section labels: `Mood`, `Health`, and `Support`.
- Modify `app/src/test/java/org/wit/vitasense/ui/family/FamilyUiMapperTest.kt`
  - Add mapper tests that prove the card exposes the section labels and keeps privacy switch rules.
- Modify `app/src/main/res/layout/item_family_member.xml`
  - Restructure the card into identity, today status, support, and controls zones.
- Modify `app/src/main/java/org/wit/vitasense/ui/family/FamilyMemberAdapter.kt`
  - Bind the new section title views and move existing data into the new zones.

---

### Task 1: Add Section Labels To UI Model

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiModels.kt`
- Modify: `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiMapper.kt`
- Modify: `app/src/test/java/org/wit/vitasense/ui/family/FamilyUiMapperTest.kt`

- [ ] **Step 1: Add failing mapper test for section labels**

In `app/src/test/java/org/wit/vitasense/ui/family/FamilyUiMapperTest.kt`, add this test after `current_user_card_exposes_health_score_share_switch()`:

```kotlin
@Test
fun member_card_exposes_section_labels_for_clear_grouping() {
    val state =
        FamilyUiMapper.build(
            currentUserId = 1,
            isSignedIn = true,
            family =
                family(
                    currentUserRole = FamilyRole.MEMBER,
                    members =
                        listOf(
                            member(
                                userId = 1,
                                fullName = "Ava Stone",
                                username = "ava",
                                role = FamilyRole.MEMBER,
                                moodType = "CALM",
                                shareHealthScore = true,
                                healthScore = 82,
                                healthScoreLabel = "Stable",
                                healthScoreUpdatedAt = 1770000000000,
                            ),
                        ),
                ),
            isLoading = false,
            errorMessage = null,
        )

    val member = state.members.single()
    assertEquals("Mood", member.moodSectionTitle)
    assertEquals("Health", member.healthSectionTitle)
    assertEquals("Support", member.supportSectionTitle)
    assertEquals("Calm", member.moodLabel)
    assertEquals("Health Score 82", member.healthScoreText)
    assertEquals("Stable", member.healthScoreDetailText)
}
```

- [ ] **Step 2: Run compile and confirm failure**

Run:

```powershell
.\gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: fails with unresolved references for `moodSectionTitle`, `healthSectionTitle`, and `supportSectionTitle`.

- [ ] **Step 3: Extend `FamilyMemberUiModel`**

In `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiModels.kt`, update `FamilyMemberUiModel` to include section labels after `roleLabel`:

```kotlin
data class FamilyMemberUiModel(
    val userId: Long,
    val avatarInitial: String,
    val displayName: String,
    val roleLabel: String,
    val moodSectionTitle: String,
    val healthSectionTitle: String,
    val supportSectionTitle: String,
    val moodLabel: String,
    val moodNote: String?,
    val statusLabel: String,
    val supportSummary: String,
    val latestSupportText: String,
    val shareHealthScore: Boolean,
    val healthScoreText: String,
    val healthScoreDetailText: String,
    val showShareHealthScoreSwitch: Boolean,
    val canSendSupport: Boolean,
    val canRemove: Boolean,
    val supportTypes: List<FamilySupportType> = FamilySupportType.entries,
)
```

- [ ] **Step 4: Populate section labels in mapper**

In `app/src/main/java/org/wit/vitasense/ui/family/FamilyUiMapper.kt`, update the `FamilyMemberUiModel(...)` call:

```kotlin
return FamilyMemberUiModel(
    userId = userId,
    avatarInitial = displayName().firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
    displayName = displayName(),
    roleLabel = role.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) },
    moodSectionTitle = "Mood",
    healthSectionTitle = "Health",
    supportSectionTitle = "Support",
    moodLabel = moodType?.formatLabel() ?: "No check-in yet",
    moodNote = moodNote,
    statusLabel = statusLabel,
    supportSummary = supportCountToday.supportSummary(),
    latestSupportText = latestSupportType?.displayName.orEmpty(),
    shareHealthScore = shareHealthScore,
    healthScoreText = healthScoreText,
    healthScoreDetailText = healthScoreDetailText,
    showShareHealthScoreSwitch = isSelf,
    canSendSupport = !isSelf,
    canRemove = ownerCanManage && !isSelf && role != FamilyRole.OWNER,
)
```

- [ ] **Step 5: Run compile and verify pass**

Run:

```powershell
.\gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

Run:

```powershell
git add app/src/main/java/org/wit/vitasense/ui/family/FamilyUiModels.kt app/src/main/java/org/wit/vitasense/ui/family/FamilyUiMapper.kt app/src/test/java/org/wit/vitasense/ui/family/FamilyUiMapperTest.kt
git commit -m "feat: add family card section labels"
```

---

### Task 2: Restructure Member Card Layout

**Files:**
- Modify: `app/src/main/res/layout/item_family_member.xml`
- Modify: `app/src/main/java/org/wit/vitasense/ui/family/FamilyMemberAdapter.kt`

- [ ] **Step 1: Replace the member card inner layout**

In `app/src/main/res/layout/item_family_member.xml`, keep the outer `MaterialCardView` and replace the inner content with this structure:

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <LinearLayout
        android:id="@+id/memberIdentityRow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <com.google.android.material.card.MaterialCardView
            android:layout_width="44dp"
            android:layout_height="44dp"
            app:cardBackgroundColor="?attr/vsColorPrimarySoft"
            app:cardCornerRadius="22dp">

            <TextView
                android:id="@+id/memberAvatarText"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:gravity="center"
                android:textColor="?android:attr/textColorPrimary"
                android:textSize="18sp"
                android:textStyle="bold" />
        </com.google.android.material.card.MaterialCardView>

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/memberNameText"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textColor="?android:attr/textColorPrimary"
                android:textSize="16sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/memberRoleText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textColor="?android:attr/textColorSecondary"
                android:textSize="13sp" />
        </LinearLayout>
    </LinearLayout>

    <LinearLayout
        android:id="@+id/todayStatusRow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="14dp"
        android:orientation="horizontal">

        <LinearLayout
            android:id="@+id/moodStatusBlock"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginEnd="6dp"
            android:layout_weight="1"
            android:background="?attr/vsColorPrimarySoft"
            android:orientation="vertical"
            android:padding="12dp">

            <TextView
                android:id="@+id/moodSectionTitleText"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textColor="?android:attr/textColorSecondary"
                android:textSize="12sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/memberMoodText"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:textColor="?android:attr/textColorPrimary"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/memberStatusText"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textColor="?android:attr/textColorSecondary"
                android:textSize="13sp" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/healthStatusBlock"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="6dp"
            android:layout_weight="1"
            android:background="?attr/vsColorPrimarySoft"
            android:orientation="vertical"
            android:padding="12dp">

            <TextView
                android:id="@+id/healthSectionTitleText"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textColor="?android:attr/textColorSecondary"
                android:textSize="12sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/healthScoreText"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:textColor="?android:attr/textColorPrimary"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/healthScoreDetailText"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textColor="?android:attr/textColorSecondary"
                android:textSize="13sp" />
        </LinearLayout>
    </LinearLayout>

    <TextView
        android:id="@+id/memberNoteText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp"
        android:textColor="?android:attr/textColorSecondary" />

    <LinearLayout
        android:id="@+id/supportSection"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="14dp"
        android:orientation="vertical">

        <TextView
            android:id="@+id/supportSectionTitleText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="?android:attr/textColorSecondary"
            android:textSize="12sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/memberSupportSummaryText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:textColor="?attr/vsColorPrimaryStrong" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/supportButtonGroup"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp"
        android:orientation="vertical">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/supportThinkingButton"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/family_support_thinking" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/supportNeedAnythingButton"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/family_support_need_anything" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/supportTakePauseButton"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/family_support_take_pause" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/supportProudButton"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/family_support_proud" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/memberControlsSection"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:orientation="vertical">

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/shareHealthScoreSwitch"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/family_share_health_score" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/removeMemberButton"
            style="?attr/materialButtonOutlinedStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/family_remove" />
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: Bind new section title views**

In `app/src/main/java/org/wit/vitasense/ui/family/FamilyMemberAdapter.kt`, add these lines in `bind(item)` after role binding:

```kotlin
binding.moodSectionTitleText.text = item.moodSectionTitle
binding.healthSectionTitleText.text = item.healthSectionTitle
binding.supportSectionTitleText.text = item.supportSectionTitle
```

- [ ] **Step 3: Control visibility for controls section**

In `FamilyMemberAdapter.bind(item)`, after binding `removeMemberButton.isVisible`, add:

```kotlin
binding.memberControlsSection.isVisible = item.showShareHealthScoreSwitch || item.canRemove
```

Keep the existing switch listener order:

```kotlin
binding.shareHealthScoreSwitch.isVisible = item.showShareHealthScoreSwitch
binding.shareHealthScoreSwitch.setOnCheckedChangeListener(null)
binding.shareHealthScoreSwitch.isChecked = item.shareHealthScore
binding.shareHealthScoreSwitch.setOnCheckedChangeListener { _, isChecked ->
    onShareHealthScoreChanged(isChecked)
}
```

- [ ] **Step 4: Run Android compile**

Run:

```powershell
.\gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run Android assemble**

Run:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

Run:

```powershell
git add app/src/main/res/layout/item_family_member.xml app/src/main/java/org/wit/vitasense/ui/family/FamilyMemberAdapter.kt
git commit -m "feat: reorganize family member cards"
```

---

### Task 3: Final Verification

**Files:**
- Modify only files already touched if verification exposes issues.

- [ ] **Step 1: Run Android compile gate**

Run:

```powershell
.\gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run Android build gate**

Run:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Attempt targeted mapper test**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "org.wit.vitasense.ui.family.FamilyUiMapperTest"
```

Expected if the local Gradle worker issue is fixed: tests pass.

If it fails with:

```text
ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain
```

Record it as the known local Gradle Test Executor issue. Do not change application code for that environment failure.

- [ ] **Step 4: Manual UI inspection**

Install or run the debug build and inspect the Family page:

- Current user's member card shows identity at the top.
- Mood and Health appear as paired status blocks.
- `Share health score with family` appears only on the current user's card.
- Other members do not show the sharing switch.
- Support summary and support buttons are visually below the status blocks.
- Remove button appears only when allowed and is separated from daily status.
- Long text does not overlap inside the card.

- [ ] **Step 5: Commit verification fixes if needed**

If fixes were required:

```powershell
git add app/src/main/java/org/wit/vitasense/ui/family app/src/main/res/layout/item_family_member.xml app/src/test/java/org/wit/vitasense/ui/family/FamilyUiMapperTest.kt
git commit -m "fix: polish family member card hierarchy"
```

If no fixes were required, skip this commit.

---

## Self-Review

Spec coverage:

- Clear identity zone: Task 2 keeps the identity row at the top.
- Today status zone: Task 2 creates paired Mood and Health blocks.
- Support zone: Task 2 adds a support section title and places support actions below it.
- Controls zone: Task 2 places sharing and remove controls in `memberControlsSection`.
- No backend changes: no backend files are in the file map.
- Privacy behavior unchanged: switch visibility and binding remain based on existing `showShareHealthScoreSwitch`.

Completeness scan:

- No incomplete deferred steps remain.
- All code changes use concrete file paths and snippets.

Type consistency:

- New UI model fields are `moodSectionTitle`, `healthSectionTitle`, and `supportSectionTitle`.
- Adapter binding uses the same property names and generated binding IDs:
  - `moodSectionTitleText`
  - `healthSectionTitleText`
  - `supportSectionTitleText`
