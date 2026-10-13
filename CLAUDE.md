# StillNote

## Project

StillNote is a minimalist Android to-do app whose tasks live in the user's Notion.
Stack: native Android, Kotlin + Jetpack Compose, single activity, MVVM. The app calls the Notion REST API directly; there is no backend.

**Design source of truth: `design/DESIGN.md`.** Read it before any UI work, then look at the PNGs in `design/screens/png/` (the visual target). `design/screens/html/` has the same screens with the exact values inline. Where they disagree, the screens win on visuals and DESIGN.md wins on behaviour.

### Rules

- Never hard-code, log or commit the Notion token. `.gitignore` must exist before the first commit.
- Don't add screens, buttons or features that aren't in the design. Ask first. The undecided items are listed in DESIGN.md §7.
- Match the PNGs closely. 1 px in the mockups is 1 dp; font px is sp.
- Touch targets are at least 48dp, even where the mockups draw 44.
- When a milestone's UI is done: build and run the app, screenshot each screen, compare it with its PNG, and list the differences.

### Decisions made after the design

These settle items DESIGN.md leaves open (§7), or say how something it doesn't draw was built.

Tasks:

- **New task:** priority is None, on a canary note until one is picked. The date is Today with no time. The clock opens at the next half hour, and a time can't be cleared once set. From Today the project is "No project".
- **Order on Today:** time ascending with untimed tasks last, then High to None.
- **Order within a Calendar day:** done tasks first, then the Today order. This applies to the day's squares and to the day panel.
- **No design yet, so no action:** tapping a post-it's body, or a project card's header.
- **Stroke weights:** use the widths in DESIGN.md and the HTML (1.6 dp circles, and so on). The PNGs draw them thinner only because their renderer rounded fractional borders down to 1 px.
- **App id** `app.stillnote`, **minSdk** 26.

Look:

- **Palettes:** default colours only for now. The swatches in setup and Settings are drawn but do nothing, and Canary stays selected.
- **Dark mode, when it is added:** only the page goes dark (`#26231F`, dots `#3D3832`, light text and icons on the page). Post-its, cards and sheets keep their colours with dark ink.

Notion:

- **Local tasks are copied into Notion** when someone who used the app without it connects later.
- **Settings** has no database list. "Connect" (when not connected) and "Change database or fields" reopen setup steps 1 and 2 on their own, as "Step 1 of 2" and "Step 2 of 2". Saving an empty token disconnects: Notion's tasks leave the phone and stay in Notion.
- **Project is a select**, a plain label that separates tasks. There is no Projects database and no relation support. The option "Task" means "no project" and is never printed on a post-it.
- **A new project** is not written to Notion's schema. It lives on the phone and becomes a select option when its first task is saved.
- **"Done" is a checkbox only**; a Status property is not supported.
- **Fields:** guessed by name (English and Italian), then by type for the three required ones; Priority is also recognised by its options. A Project is only guessed by name. The ▾ opens a sheet of card with RadioRows, plus "Don't use" for Priority and Project.
- **A database without Priority or Project:** the flag or project toggle is hidden in the note, and the Projects page says "This database has no project field."
- **Sync states:** the header summary gains "syncing", "offline" or "couldn't sync" only when needed. A change Notion refuses shows a paper strip, "Couldn't save." with Retry. Setup shows its errors under the field in `inkHigh`.

## Build and run

Gradle needs `JAVA_HOME` (JDK 21) and `ANDROID_HOME`; neither is set globally on the dev machine, so pass them on the command line.

```sh
./gradlew :app:installDebug              # build and install on the running emulator or phone
./gradlew :app:testDebugUnitTest         # Notion client and property mapping, on the JVM
./gradlew :app:connectedDebugAndroidTest # the sync engine against real SQLite, on the emulator
./gradlew :app:lintDebug
python3 tools/screens.py                 # screenshot the designed screens and compare with the mockups
python3 tools/notion_e2e.py              # drive the app against a fake Notion: connect, sync, offline, errors
```

Both scripts expect the emulator display described in `tools/screens.py` (390×844 dp at 2×) and write to `screenshots/`, which git ignores. `screens.py` covers mockups 05 to 13 in demo mode; `notion_e2e.py` wipes the app's data and covers 01 to 04 and 14 against a fake Notion.

Debug builds take two launch extras, used by those scripts:

- `--ez demo true` shows the mockups' sample tasks and projects with "today" fixed to Mon 12 Oct 2026, and skips the first-run flow.
- `--es notion_url <url>` sends Notion calls to `tools/fake_notion.py` instead; it is remembered until given an empty value or the app's data is cleared.

How the data flows: screens read and write `LocalTaskRepository` (Room: tasks and projects). Each change is queued for `PushWorker` (WorkManager), and `NotionSync` pushes and pulls under one lock: the visible month by date, and every task that has a project. The token is encrypted with a Keystore key (`KeystoreTokenVault`).
