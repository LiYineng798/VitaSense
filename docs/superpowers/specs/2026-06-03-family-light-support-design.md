# Family Light Support Design

## Goal

Add a lightweight Family feature to VitaSense so signed-in users can create one family, invite other users by code, see each other's daily lightweight status, and send fixed supportive signals.

The feature is intentionally companionship-focused. It must not expose detailed health metrics such as HRV, heart rate, sleep duration, raw samples, or trend charts to family members.

## Product Scope

### Included

- A signed-in user can create a family by entering a family name.
- The creator becomes the family `Owner`.
- A family has a short server-generated invite code.
- Another signed-in user can join the family by entering the invite code.
- A user can belong to at most one family in the first version.
- Family members can see member cards with:
  - avatar initial
  - display name
  - role
  - today's mood type
  - optional mood note
  - lightweight status label
  - last updated time
  - recent support summary
- Members can send fixed support signals to other members:
  - `Thinking of you`
  - `Need anything?`
  - `Take a pause`
  - `Proud of you`
- Family members can see recently received support counts or the latest support signal.
- Owner can:
  - rename the family
  - regenerate the invite code
  - remove members
- Member can leave the family.
- Profile gets the main Family entry.
- Home gets a lightweight Family summary card when the user has joined a family.

### Excluded

- Family chat.
- Custom support text.
- Push notifications.
- Multiple families per user.
- Owner transfer.
- Family deletion.
- Detailed health metrics sharing.
- Member health trend comparison.
- AI family advice.
- Deep-link invite links.
- Email invitations.

## Information Architecture

Family should not become a fifth bottom tab. The existing top-level tabs are personal health workflow destinations:

- Home
- Trends
- Mood
- Profile

Family is account-related and secondary, so the main entry belongs in Profile.

### Entry Points

1. Profile
   - Add a `Family` entry card near `Appearance` and `Settings & Import`.
   - This is the primary entry for family creation, joining, viewing, and management.

2. Home
   - Add a compact Family summary card only when the signed-in user has a family.
   - Example content:
     - `Family`
     - `2 updates today`
     - `1 support received`
   - Tapping the card opens the Family page.

3. Mood
   - No new visible entry.
   - Saving today's mood updates the user's family status snapshot indirectly.

## Android Architecture

Follow the existing app architecture:

- XML layouts
- Fragments
- Navigation Component
- ViewBinding
- ViewModels with StateFlow
- Repository interfaces plus default implementations
- Manual dependency assembly in `AppContainer`

### New Android Components

- `FamilyFragment`
  - Renders the Family screen.
  - Handles sign-in CTA, create form, join form, member list, support buttons, and management actions.

- `FamilyViewModel`
  - Combines auth state, local mood state, and remote family state.
  - Exposes a single screen state.
  - Handles create, join, rename, regenerate invite code, remove member, leave, and send support.

- `FamilyRepository`
  - Interface for remote family operations.

- `DefaultFamilyRepository`
  - Uses `HttpURLConnection`, matching the current auth, sync, and AI repositories.
  - Reads auth token and base URL from `SettingsRepository`.
  - Parses JSON responses into family models.

- `FamilyUiMapper`
  - Converts models into UI-ready member cards and Home summary text.

- Family model classes
  - `Family`
  - `FamilyMember`
  - `FamilyRole`
  - `FamilySupportType`
  - `FamilySupportSummary`
  - `FamilyStatusSnapshot`
  - `FamilyResult`

### Existing Components To Update

- `AppContainer`
  - Add `familyRepository`.

- `VitaSenseViewModelFactory`
  - Add `FamilyViewModel`.
  - Inject `FamilyRepository`, `AuthRepository`, `MoodRepository`, and any required settings dependency.

- `main_nav_graph.xml`
  - Add `familyFragment`.

- `FloatingTabShellDestinationPolicy`
  - Hide floating tabs on `familyFragment`.

- `ProfileFragment`
  - Add Family entry card.

- `DashboardFragment` / `DashboardViewModel`
  - Add Home Family summary state and navigation entry.

## Service API Design

Family should use separate API endpoints instead of being folded into auth.

### Endpoints

- `POST /api/v1/families`
  - Create a family.
  - Body: `{ "name": "My Family" }`

- `GET /api/v1/families/me`
  - Return the signed-in user's family, members, support summaries, and permissions.

- `POST /api/v1/families/join`
  - Join by invite code.
  - Body: `{ "invite_code": "ABC123" }`

- `PATCH /api/v1/families/{id}`
  - Rename a family.
  - Owner only.
  - Body: `{ "name": "New Name" }`

- `POST /api/v1/families/{id}/invite-code/regenerate`
  - Regenerate invite code.
  - Owner only.

- `DELETE /api/v1/families/{id}/members/{user_id}`
  - Remove a member.
  - Owner only.

- `DELETE /api/v1/families/{id}/members/me`
  - Leave family.
  - Member only in the first version.

- `POST /api/v1/families/{id}/status`
  - Upsert the current user's lightweight status snapshot.
  - Body includes mood and lightweight status only.

- `POST /api/v1/families/{id}/supports`
  - Send a fixed support signal.
  - Body:
    ```json
    {
      "receiver_user_id": 123,
      "support_type": "thinking_of_you"
    }
    ```

## Shared Status Snapshot

The Family service stores only lightweight status:

- user id
- display name
- mood type
- optional mood note
- status label
- updated at

It must not store or return:

- HRV
- heart rate
- sleep duration
- raw samples
- trend chart points
- total health score
- risk assessment score

The status label can be generated client-side from existing local state, for example:

- `Checked in today`
- `No check-in yet`
- `Could use support`

Today's mood uses the latest Mood record for the current date. If there is no mood record, the member card shows `No check-in yet`.

## UI Design

The UI should match VitaSense's existing style:

- English copy.
- Large page title.
- Vertical scroll.
- Material cards.
- 20-24dp rounded cards.
- Theme attributes such as `?attr/colorSurface`, `?attr/vsColorPrimarySoft`, `?attr/vsColorPrimaryStrong`, and `?attr/colorOutline`.
- No hard-coded palette for new feature surfaces.

### Family Screen States

#### Signed Out

Show:

- Title: `Family`
- Card explaining that Family requires sign-in.
- Button: `Sign In / Register`

The button opens `AuthFragment`.

#### Signed In Without Family

Show two cards:

1. Create Family
   - Text input: family name.
   - Button: `Create Family`.

2. Join Family
   - Text input: invite code.
   - Button: `Join`.

#### Signed In With Family

Top family card:

- Family name.
- Current user's role.
- Invite code.
- Owner-only action: `Regenerate Code`.
- Owner-only rename control.

Member list:

- One card per member.
- Avatar initial.
- Display name.
- Role badge.
- Today's mood.
- Optional mood note.
- Lightweight status label.
- Last updated time.
- Support summary.
- For other members:
  - `Thinking of you`
  - `Need anything?`
  - `Take a pause`
  - `Proud of you`
- Owner-only for other members:
  - `Remove`

Management section:

- Member action: `Leave Family`.
- Owner cannot leave in the first version.

### Home Family Summary

Only visible when the user has a family.

Show:

- `Family`
- update count for today
- received support count for today

Tap opens `FamilyFragment`.

## Business Rules

- A user must be signed in to use Family.
- A user can belong to only one family.
- Invite code is generated by the server.
- Invite code must be unique.
- Regenerating an invite code invalidates the previous code.
- Creator is `Owner`.
- Invite-code joiners are `Member`.
- Owner can rename the family.
- Owner can regenerate invite code.
- Owner can remove members.
- Member can leave.
- Owner cannot leave in the first version.
- Users cannot send support to themselves.
- Support type must be one of the fixed enum values.
- A sender can send the same support type to the same receiver at most once per day.
- Family API must not accept arbitrary support text.
- Family API must not expose detailed health metrics.

## Error Handling

Use concise user-facing messages through the existing event/snackbar pattern where applicable.

Expected errors:

- Not signed in.
- Family name is empty.
- Invite code is empty.
- Invite code is invalid.
- User already belongs to a family.
- User does not belong to a family.
- Permission denied for Owner-only actions.
- Cannot send support to self.
- Duplicate support for same receiver/type/day.
- Network unavailable.
- Unexpected server response.

Remote errors should be mapped to stable local messages, matching the style used by auth, cloud sync, and AI advice.

## Sync Timing

The app should upsert the local user's family status snapshot:

- when opening the Family page
- after saving a Mood entry
- when Home refreshes and the user has a family

The first version can refresh Family data only when the page opens or after an action completes. No real-time updates or push notifications are required.

## Testing Plan

### Android Unit Tests

- `FamilyViewModelTest`
  - signed out state
  - signed in without family
  - create family
  - join family
  - send support
  - Owner management visibility
  - Member leave action

- `DefaultFamilyRepositoryTest`
  - create request payload
  - join request payload
  - status snapshot payload excludes detailed metrics
  - support payload uses enum value
  - error mapping

- `FamilyUiMapperTest`
  - member cards
  - support summary text
  - role labels
  - Owner vs Member actions
  - Home summary text

### Android Instrumentation Tests

- Profile opens Family.
- Family page hides floating tabs.
- Signed-out Family page opens Auth.
- Home Family summary opens Family.

### Server Tests

- create family
- invite code uniqueness
- join by invite code
- user cannot join two families
- Owner permission checks
- Member cannot remove another member
- support enum validation
- duplicate support prevention
- family response excludes detailed health metrics

## Implementation Notes

The first implementation should keep the Family feature independent from cloud sync. Cloud sync currently handles personal health source data and theme. Family is a server-backed account feature with its own endpoints.

If later versions add notifications, family AI advice, or invite links, they should extend this feature through separate models and endpoints instead of expanding the first version's support signal model into chat.
