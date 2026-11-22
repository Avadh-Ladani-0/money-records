# MoneyRecords

An offline, India-first personal finance app for Android. Track income, expenses and transfers across
your accounts, set monthly budgets, and keep everything on your phone — no account, no server.

- **₹ by default**, with Indian digit grouping (`₹1,00,000`); money is stored as whole paise, never floating point.
- **Private**: all data stays in a local database on the device.
- **Design**: minimalist monochrome with a single red accent and dot-matrix numbers, in light and dark themes.

> Status: early development (v0.1.0). Accounts, records, the Records tab with filters and search,
> categories, monthly budgets, recurring records, reminders, app lock and a floating calculator work.

## Features so far

- **Fast record entry**: Expense · Income · Transfer, built-in ₹ keypad that also calculates (`250+180`),
  category icon grid with subcategories, account, date, time and note. Remembers your last account and
  category. Tap a record to edit it; long-press to duplicate it; delete with undo.
- **Records tab**: month-by-month with In / Out / Net, grouped by day; custom date range, multi-select
  account and category filters, type filter and search.
- **Accounts**: Cash, Bank, Card, Wallet with opening balances and live balances; archive instead of delete.
- **Categories**: add, rename, nest one level deep, pick an icon, archive. Create one on the spot from the
  record's category picker. **Combine** two main categories (long-press one, tap another) into e.g.
  "Home-Food" with a split icon; it lives inside the first, so it counts toward Home but not Food.
- **Budgets**: monthly limits for **Overall**, individual main categories, and **Everything else**
  (all categories without their own budget). Set once, they carry forward to later months; edits apply
  from that month on, and a budget can be stopped without touching past months. Dot-matrix progress,
  "80% used" / "Over" warnings, "₹X a day left", suggestions for unbudgeted spending, and an overall
  budget bar on Home. **Account budgets** too: a monthly limit per account (expenses + transfers
  out) or for **Other accounts**. When editing any budget you can tick income records (and, for an
  account, transfers into it) to **count back** — e.g. a refund or a card bill payment. On the 1st of each month a notification asks whether to keep last month's
  budgets — ✓ Keep, or ✗ Edit to open the Budgets tab.
- **Recurring records**: tap **Repeat** when adding a record (daily, weekly, monthly, yearly, every N,
  optional end date). When one is due you get a notification — **✓ Add**, **Skip** or **Edit** — and it
  also waits on Home under **Due**; nothing is recorded until you confirm. Manage them in
  Settings → Recurring (edit, pause, delete).
- **Reminders**: a daily "log today's spending" nudge (default 9 PM, skipped if you've already logged),
  "due tomorrow" heads-ups for recurring bills, and 80% / over-budget alerts for every budget type.
  All can be switched off in Settings → Notifications.
- **App lock**: lock MoneyRecords with your phone's own screen lock (fingerprint, face, PIN or pattern).
  Locks on a fresh start and after time away (Immediately · 30 s · 1 min · 5 min, default 1 min).
  Optionally hides the app's content in recent apps (also blocks screenshots). Notifications never
  show amounts on the phone's lock screen. Settings → App lock.
- **Floating calculator**: draggable bubble on every screen with a Copy button; can be turned off in Settings.

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 1 | App scaffold, design system (colours, type, dot-matrix components), navigation | ✅ Done |
| 2 | Data layer: Room database, repositories, default categories, tests | ✅ Done |
| 3 | Core screens: add/edit record, records list & search, accounts, categories, live Home, floating calculator | ✅ Done |
| 4 | Budgets: overall, per-category and "everything else" monthly limits, carry-forward, progress | ✅ Done |
| 5 | Recurring records and reminders (+ budget alerts) | ✅ Done |
| 6 | App lock with the phone's screen lock, privacy in recents and on the lock screen | ✅ Done |
| 7 | Home-screen widget | ⏳ Next |
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
├── domain/         # Plain Kotlin: models, repository interfaces, Money (₹ format/parse), MonthRange,
│                   # calc/Expression (keypad & calculator maths)
├── data/
│   ├── db/         # Room entities, DAOs, database, default-category seed
│   ├── mapper/     # Entity <-> domain mapping
│   ├── prefs/      # DataStore: last-used account/category, calculator settings
│   └── repository/ # Room-backed repositories (validation, archive-or-delete)
├── di/             # Hilt modules
└── ui/
    ├── theme/      # Colours, typography, shapes, spacing
    ├── components/ # Reusable UI: amounts, dot-matrix progress, cards, rows, bottom bar
    ├── icons/      # Stored icon keys -> icons
    ├── navigation/ # Scaffold, bottom bar, routes
    └── feature/    # Screens: home, record editor, records, accounts, categories, budgets, settings
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
