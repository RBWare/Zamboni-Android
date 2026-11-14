# Zamboni

Material 3 Android app (`io.rbware.zamboni`) built with Jetpack Compose, with adaptive layouts for phones, foldables and tablets.

## Stack
- Kotlin 2.3, AGP 8.13, Gradle 8.13, JDK 17 target
- Jetpack Compose (BOM 2026.06.01), Material 3 with dynamic color
- `material3-adaptive` (list/detail pane scaffold) and `material3-adaptive-navigation-suite`
- minSdk 26, targetSdk / compileSdk 36

## Layout
```
app/src/main/java/io/rbware/zamboni/
|-- ZamboniApplication.kt        # creates the AppContainer
|-- MainActivity.kt              # splash, then onboarding (first run) or main UI
|-- di/AppContainer.kt           # manual DI; swap placeholder repositories here
|-- data/
|   |-- model/                   # Team, NewsSource, NotificationType
|   |-- preferences/             # DataStore-backed user choices + onboarding flag
|   |-- teams/                   # TeamRepository (placeholder until hockeynerd.com API)
|   `-- news/                    # NewsSourceRepository (placeholder until RSS feeds)
`-- ui/
    |-- ZamboniApp.kt            # NavigationSuiteScaffold: bottom bar, rail or drawer by window size
    |-- onboarding/              # first-run flow: welcome, teams, notifications, news
    |-- preferences/             # pickers shared by onboarding and Settings
    |-- scores/                  # Scores tab (sample list/detail for now)
    |-- settings/                # Settings tab: list/detail editor for the onboarding choices
    `-- theme/                   # color scheme, typography, ZamboniTheme
```

## Still to do
- Scores: implement `TeamRepository` (and score fetching) against the hockeynerd.com API.
- News: real RSS source list and feed parsing.
- Notifications: preferences are stored, but nothing is delivered yet.

## Build
```sh
./gradlew assembleDebug        # debug build installs as io.rbware.zamboni.debug
./gradlew testDebugUnitTest
```
