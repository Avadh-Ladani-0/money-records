# MoneyRecords

An offline, India-first personal finance app for Android. Track income, expenses and transfers across
your accounts, set monthly budgets, and keep everything on your phone — no account, no server.

- **₹ by default**, with Indian digit grouping (`₹1,00,000`); money is stored as whole paise, never floating point.
- **Private**: all data stays in a local database on the device.
- **Design**: minimalist monochrome with a single red accent and dot-matrix numbers, in light and dark themes.

> Status: early development (v0.1.0). The app shell, design system and data layer are done; the
> screens for adding and browsing records come next.

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 1 | App scaffold, design system (colours, type, dot-matrix components), navigation | ✅ Done |
| 2 | Data layer: Room database, repositories, default categories, tests | ✅ Done |
| 3 | Core screens: add/edit record, records list & search, accounts, categories, live Home | ⏳ Next |
| 4 | Budgets: monthly per-category and overall limits with progress | Planned |
| 5 | Recurring records and reminders | Planned |
| 6 | App lock: PIN and fingerprint | Planned |
| 7 | Home-screen widget | Planned |
| 8 | Backup & restore to a file (free) | Planned |
| 9 | Polish: motion, haptics, accessibility | Planned |

After v1: analytics dashboard, CSV/Excel/PDF export and more features.

## Tech stack

- Kotlin, Jetpack Compose with Material 3, Navigation Compose (type-safe routes)
- Room (SQLite) with exported schemas, Hilt for dependency injection
- Android Gradle Plugin 9.5, Gradle 9.9 (wrapper included), Kotlin 2.4
- `minSdk` 26 (Android 8.0), `targetSdk` 36, `compileSdk` 37.1

## Getting started

**Requirements**
- JDK 17
- Android SDK with **Platform 37.1** (Gradle installs it automatically if SDK licences are accepted)
- An Android device or emulator on Android 8.0+

**Setup** — point Gradle at your SDK in `local.properties` (not committed):

```properties
sdk.dir=/path/to/Android/Sdk
```

**Build, test, install**

```bash
./gradlew assembleDebug                 # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest             # JVM unit tests
./gradlew connectedDebugAndroidTest     # database tests on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build installs as `com.moneyrecords.app.debug`, so it can sit next to a release build.

## Project structure

```
app/src/main/java/com/moneyrecords/app/
├── domain/         # Plain Kotlin: models, repository interfaces, Money (₹ format/parse), MonthRange
├── data/
│   ├── db/         # Room entities, DAOs, database, default-category seed
│   ├── mapper/     # Entity <-> domain mapping
│   └── repository/ # Room-backed repositories (validation, archive-or-delete)
├── di/             # Hilt modules
└── ui/
    ├── theme/      # Colours, typography, shapes, spacing
    ├── components/ # Reusable UI: amounts, dot-matrix progress, cards, rows, bottom bar
    ├── icons/      # Stored icon keys -> icons
    ├── navigation/ # Scaffold, bottom bar, routes
    └── feature/    # Screens: home, records, accounts, budgets, settings, design system
app/schemas/        # Exported Room schemas (used to write and test migrations)
```

## Key decisions

- **Money as `Long` paise.** All arithmetic is exact; formatting happens only at the edge (`domain/money/Money.kt`).
- **Balances are computed**, not stored: opening balance + income − expenses ± transfers.
- **Transfers are one record** (from → to account) and are excluded from income/expense totals.
- **Archive instead of delete.** Deleting an account or category that records use archives it, so history is never lost; unused ones are deleted.
- **First launch** seeds default income/expense categories only; you create your own accounts.
- **Schema changes** need a Room migration; schemas are exported to `app/schemas/`.

## Design system

Open **Settings → Design system** in the app to see every colour, type style and component.

## Credits

Fonts are bundled under the SIL Open Font License 1.1 (licence texts in `app/src/main/assets/licenses/`):
- [Doto](https://github.com/oliverlalan/Doto) — dot-matrix display face for amounts
- [Inter](https://github.com/rsms/inter) — text face
