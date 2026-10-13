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

## Build and run

Milestone 1 runs on fake data (`FakeTaskRepository`, with "today" fixed to Mon 12 Oct 2026 so it matches the mockups). There is no Notion code yet.

Gradle needs `JAVA_HOME` (JDK 21) and `ANDROID_HOME`; neither is set globally on the dev machine, so pass them on the command line.

```sh
./gradlew :app:installDebug      # build and install on the running emulator or phone
./gradlew :app:lintDebug
python3 tools/screens.py         # screenshot every screen and compare it with its mockup
```

`tools/screens.py` explains the emulator display it expects (390×844 dp at 2×). Its output goes to `screenshots/`, which git ignores.
