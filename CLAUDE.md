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

These settle items DESIGN.md leaves open (§7) or change what it shows.

- **New task:** priority is None, on a canary note until one is picked. The date is Today with no time. The clock opens at the next half hour, and a time can't be cleared once set.
- **Order on Today:** time ascending with untimed tasks last, then High to None.
- **Order within a Calendar day:** done tasks first, then the Today order. This applies to the day's squares and to the day panel.
- **Stroke weights:** use the widths in DESIGN.md and the HTML (1.6 dp circles, and so on). The PNGs draw them thinner only because their renderer rounded fractional borders down to 1 px.
- **Palettes:** on hold. Settings draws the four swatches, but they do nothing and Canary stays selected.
- **Projects tab:** a blank page with only the title, until it is redesigned.
- **Project "Task":** in the Notion database this is the default value of the `Project` select and means "no project". Don't print it on a post-it.
- **No design yet, so no action:** tapping a post-it's body.
- **App id** `app.stillnote`, **minSdk** 26.

Notion (milestone 2):

- **The app works without Notion.** Tasks live in a database on the phone, and Notion sync is optional. A Welcome screen offering "add a token" or "use without Notion" is still to be designed; until then the app opens on Today with no Notion, and it is connected from Settings.
- **Connecting uploads.** Tasks made without Notion are created in the chosen database when it is connected. Saving an empty token disconnects: Notion's tasks leave the phone and stay in Notion.
- **Database:** after Save, Settings always lists the databases the token can see, and the user picks one. It needs a date and a checkbox property.
- **Property roles** are found by type, then by name, and stored by property id. There is no mapping screen. "Done" is a checkbox only; a Status property is not supported yet.
- **Sync states:** the header summary gains "syncing", "offline" or "couldn't sync" only when needed. A change Notion refuses shows a paper strip, "Couldn't save." with Retry.
- **New tasks** are written with Project "Task" and, for no priority, the database's "None" option.

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

Both scripts expect the emulator display described in `tools/screens.py` (390×844 dp at 2×) and write to `screenshots/`, which git ignores. `notion_e2e.py` wipes the app's data first.

Debug builds take two launch extras, used by those scripts:

- `--ez demo true` shows the mockups' sample tasks with "today" fixed to Mon 12 Oct 2026.
- `--es notion_url <url>` sends Notion calls to `tools/fake_notion.py` instead; it is remembered until given an empty value or the app's data is cleared.

How the data flows: screens read and write `LocalTaskRepository` (Room). Each change is queued for `PushWorker` (WorkManager), and `NotionSync` pushes and pulls under one lock. The token is encrypted with a Keystore key (`KeystoreTokenVault`).
