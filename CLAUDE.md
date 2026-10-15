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

- **New task:** priority is None, on a canary note until one is picked. The date is Today with no time. From Today the project is "No project".
- **The clock** opens on "Any time", for a new task and for one with no time, so a task can go on another day without a time. From "Any time", › picks the next half hour and ‹ the one before it; after that each step is 30 minutes. A time can't be cleared once set. Once there is a time, a third stepper sets the task's reminder (see Reminders).
- **Editing a task:** tapping a post-it (on Today or a project card), a Done row or a Calendar day row opens the new-task note filled in, headed "EDIT TASK", in the task's colour and without the keyboard. Its chips show what the task has: a day other than today, a time, a priority other than None, a project, or "No date" for an undated one. The day can't go back before today, or before the task's own day if that is earlier. Send saves the changes, and an unchanged note saves nothing. Ticking and deleting stay on the post-it.
- **Order on Today:** time ascending with untimed tasks last, then High to None.
- **Order within a Calendar day:** done tasks first, then the Today order. This applies to the day's squares and to the day panel.
- **No design yet, so no action:** tapping a project card's header.
- **Stroke weights:** use the widths in DESIGN.md and the HTML (1.6 dp circles, and so on). The PNGs draw them thinner only because their renderer rounded fractional borders down to 1 px.
- **App id** `app.stillnote`, **minSdk** 26. Debug builds are `app.stillnote.debug`, labelled "StillNote debug", so they install next to the release build; their classes stay in `app.stillnote`.

Reminders (asked for by the user; not in the design):

- **Every open task with a time** reminds before it: Settings → Reminders offers Off, 5, 10, 15, 30 or 60 minutes as pills, 10 by default. A task can have its own in the clock picker's third stepper: the default, "No reminder", then 5 to 60 minutes before. The time chip names it when it isn't the default ("Today, 18:00 · 30 min before"). It stays on the phone, since Notion has no field for it.
- **The notification** is Android's own: "Almost time: <task>", or "It's time: <task>" from the task's time on, over "10:30 · in 15 minutes · Work". Done ticks the task off; Later brings it back at the task's time, or 10 minutes on once that has passed; Edit, or a tap on it, opens the task in the note.
- **The morning list:** one notification with today's open tasks that have no time, "Today: 3 tasks at any time", at a time set in Settings (Off, or 05:00 to 12:00 in half hours; 08:00 by default). Nothing on a day with none. A tap opens Today.
- **Permission:** Android is asked once, right after the first saved task that will remind, or when a reminder is chosen in Settings. After that Settings says "Notifications are off for StillNote" with a link to Android's settings page.
- **Timing:** one exact alarm on the next reminder, through `USE_EXACT_ALARM`, which Android grants without asking; fine while the app is installed by hand, but Play Store would want `SCHEDULE_EXACT_ALARM` instead. A task written after its reminder's time doesn't remind; a late reminder (the phone was off) still shows until 5 minutes after its task's time. A change made in Notion only reaches the reminders at the next sync. On the user's OnePlus 12, OxygenOS turns the exact alarm into an inexact one, even with battery use unrestricted: it can be late by up to three quarters of the time between setting it and its due time, an hour at most. The user chose to leave it for now; the ways out are stepping the alarm closer, or `setAlarmClock` with its status-bar icon.

Widget:

- **One resizable widget**, 2×2 when placed. When it is at least half again as wide as it is tall it draws the 4×2 note.
- **Small cells:** the note shrinks evenly to fit, but not below 75% of the mockup; shorter than that, it holds fewer rows. Some launchers also shrink the whole widget view to fit small grid cells (OnePlus lays a 2×2 out at 148 dp and shows it at about 110), so on those a 3×3 or 4×3 shows the full note at a readable size.
- **Drawn as one image** with the app's fonts, shapes and shadows (RemoteViews can't show bundled fonts or tilt), so it follows the Typography setting and tilts the text with the note, as the PNGs do. Invisible tap areas with labels sit over it.
- **Taps:** a row's circle completes its task, and the rest of the row opens the task in the note to edit it. Rows are only about 30 tall, so the circle's tap area is a strip at least 48 wide. "Today" and "+N more" open Today; "+" opens the new-task note. A row or "+" leaves alone a note that's already being written. The rest of the note does nothing.
- **More than four tasks on the 4×2:** it shows three and "+N more", since four rows leave no room for the line.
- **No open tasks:** the header keeps its count ("Today 0", "· 0 to do") and the note says "All clear." or "All clear for today." in the hand font at 20, centred below the header.
- **Redraws** after a change in the app or on the widget, a background sync, a new font, and the midnight sync.

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
- **Pull to sync** (asked for by the user; not in the mockups): pulling Today, Calendar or Projects down sends the waiting changes, then pulls that screen's month and the projects. A paper disc with the refresh icon comes out under the header and spins until it's done. Without Notion, pulling does nothing.

## Build and run

Gradle needs `JAVA_HOME` (JDK 21) and `ANDROID_HOME`; neither is set globally on the dev machine, so pass them on the command line.

```sh
./gradlew :app:installDebug              # build and install on the running emulator or phone
./gradlew :app:assembleRelease           # the signed release APK, shrunk by R8
./gradlew :app:testDebugUnitTest         # Notion client, property mapping and reminder rules, on the JVM
./gradlew :app:connectedDebugAndroidTest # the sync engine against real SQLite, on the emulator
./gradlew :app:lintDebug
python3 tools/screens.py                 # screenshot the designed screens and the widget, and compare with the mockups
python3 tools/notion_e2e.py              # drive the app against a fake Notion: connect, sync, offline, errors
python3 tools/reminders_e2e.py           # reminders and the morning list, moving the emulator's clock
```

The scripts expect the emulator display described in `tools/screens.py` (390×844 dp at 2×) and write to `screenshots/`, which git ignores. `screens.py` covers mockups 05 to 13 in demo mode, compares the widget as the app draws it at the size of mockups 15 and 16, and puts the real widget on the home screen; `notion_e2e.py` wipes the app's data and covers 01 to 04 and 14 against a fake Notion. `reminders_e2e.py` also wipes the app's data, and turns the emulator's automatic time off while it moves the clock; it turns it back on at the end. Mockup 07 now differs by the reminder stepper, which it doesn't draw.

Release builds are signed from `keystore.properties` at the repo root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), which git ignores; without it the APK is built unsigned. On the dev machine the key is `~/.android/stillnote-release.jks`. Keep both backed up: a release build signed with another key can't update the installed app. The launch extras below don't exist in release builds, so check a release build by hand, and look at logcat for what R8 removed: anything made by reflection (Glance callbacks, workers) needs a rule in `app/proguard-rules.pro` unless its library ships one.

Debug builds take these launch extras, used by those scripts. Name the activity in full, since the package and the classes differ: `adb shell am start -n app.stillnote.debug/app.stillnote.MainActivity --ez demo true`.

- `--ez demo true` shows the mockups' sample tasks and projects with "today" fixed to Mon 12 Oct 2026, and skips the first-run flow.
- `--es notion_url <url>` sends Notion calls to `tools/fake_notion.py` instead; it is remembered until given an empty value or the app's data is cleared.
- `--ez render_widgets true` draws the widget's two mockup frames and the picker preview from the sample tasks into the app's `files/widgets/`. The preview is copied by hand to `res/drawable-nodpi/widget_preview.png`.
- `--ez pin_widget true` asks the launcher to place the widget.

How the data flows: screens read and write `LocalTaskRepository` (Room: tasks and projects). Each change is queued for `PushWorker` (WorkManager), and `NotionSync` pushes and pulls under one lock. A push sends only the checkbox for a tick and every field after an edit (`TaskRow.edited`), so a tick can't undo something changed in Notion meanwhile. It pulls the visible month by date, and every task that has a project. While Notion is connected, `PullWorker` does the same with the app closed, every 15 minutes (Android's minimum) and just after midnight, so the future widget doesn't wait for the app to be opened. The token is encrypted with a Keystore key (`KeystoreTokenVault`). Each version of the database schema is saved in `app/schemas`: a change to `TaskRow` or `ProjectRow` needs a new version and a migration (an `AutoMigration` where Room can work it out), or the installed app can't open its data. `Reminders` keeps one exact alarm on the next reminder or morning list: after every change to the tasks or Settings while the app runs, when the alarm goes off, and after a restart, an app update or a change of clock or time zone, it shows whatever has come due (remembering what it showed) and sets the alarm again. The rules are in `ReminderPlan`, which the JVM tests cover.
