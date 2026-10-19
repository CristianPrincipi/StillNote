# Notion To-Do: design handoff

A minimalist Android to-do app. Tasks live in the user's Notion, or only on the phone if they skip Notion. It should feel **focused, calm, clear and analog**: like post-it notes on a dotted notebook page, not like a typical modern app. There are few buttons and lots of empty space.

This folder is the design source of truth. Build the UI to match it. Where this file and the screens disagree, the screens win on visuals and this file wins on behaviour.

## What's in this folder

| Path | What it is | How to use it |
|---|---|---|
| `DESIGN.md` | This spec: tokens, components, screens, behaviour, Notion mapping | Read first |
| `screens/png/*.png` | A rendered screenshot of every screen (2× scale) | The visual target. Look at it before building each screen. |
| `screens/html/*.html` | Static HTML renders of the same screens (default state) | Open in a browser. Inline styles hold the exact values. |
| `screens/source/*.dc.html` | Original mockup sources from the design tool | Reference only, not app code. `{{holes}}`, `<sc-for>` and `<sc-if>` are template syntax. The `renderVals()` class at the bottom shows the interactive behaviour and sample data. |

Screens, in flow order:

| # | Screen | PNG | Source |
|---|---|---|---|
| 1 | First run: welcome | `01-welcome.png` | `Welcome.dc.html` |
| 2 | First run: connect Notion | `02-setup-connect-notion.png` | `SetupToken.dc.html` |
| 3 | First run: pick database | `03-setup-pick-database.png` | `SetupDatabase.dc.html` |
| 4 | First run: choose a look | `04-setup-choose-look.png` | `SetupStyle.dc.html` |
| 5 | Today (home) | `05-today.png` | `Main.dc.html` |
| 6 | New task: empty note | `06-new-task.png` | `AddTask.dc.html` |
| 7 | New task: date & time open | `07-new-task-date-time.png` | `AddTaskWhen.dc.html` |
| 7a | New task: calendar picker | `07a-calendar-picker.png` | `AddTaskCalendar.dc.html` |
| 7b | New task: clock picker, hours | `07b-clock-hours.png` | `AddTaskClock.dc.html` |
| 7c | New task: clock picker, minutes | `07c-clock-minutes.png` | `AddTaskClockMinutes.dc.html` (mounts `AddTaskClock` with `mode="minutes"`) |
| 8 | New task: priority open | `08-new-task-priority.png` | `AddTaskPriority.dc.html` |
| 9 | Calendar | `09-calendar.png` | `Calendar.dc.html` |
| 10 | Projects | `10-projects.png` | `Projects.dc.html` |
| 11 | Projects: "+" menu open | `11-projects-add-menu.png` | `ProjectsMenu.dc.html` (mounts `Projects` with `menuOpen`) |
| 12 | New task: project open | `12-new-task-project.png` | `ProjectNewTask.dc.html` |
| 13 | New project | `13-new-project.png` | `NewProject.dc.html` |
| 14 | Settings | `14-settings.png` | `Settings.dc.html` |
| 15 | Home-screen widget, 2×2 | `15-widget-2x2.png` | `Widget2x2.dc.html` |
| 16 | Home-screen widget, 4×2 | `16-widget-4x2.png` | `Widget4x2.dc.html` |

Units:
- The app screens are drawn on a 390×844 px phone. Treat **1 px = 1 dp** and font px as **sp**.
- The widgets are drawn at real size (172×172 and 356×184) on a dark stand-in for the wallpaper.

---

## 1. Design principles

1. **Paper, not glass.** Everything is a sheet of paper: post-its for tasks, index cards for projects, a dotted notebook page as the background. Don't use gradients, glossy elevation, Material ripples on everything, or FABs with circular shadows.
2. **Colour means priority.** A task's post-it colour is its priority, so don't use colour decoratively. On full-size task rows, priority is also shown with `!` marks, so it never relies on colour alone.
3. **Handwriting for the user's words, typewriter for the system.** What the user writes (task names, project names, big titles) is in a handwritten face. Dates, counts and labels are in a small mono face.
4. **Slightly imperfect.** Post-its tilt by under 1°, circles are hand-drawn, and selected things sit on a small strip of "tape". Keep it subtle: calm comes first.
5. **Few controls.** Each screen has one obvious action. Don't add toolbars, overflow menus or extra buttons without asking.

---

## 2. Design tokens

### Colours

| Token | Hex | Used for |
|---|---|---|
| `paper` | `#F3EEE4` | Screen background, bottom nav background |
| `dot` | `#D8CEBD` | Background dot grid: 1 dp dots every 22 dp, offset 11 dp |
| `ink` | `#2A2621` | Primary text, icons, outlines, primary buttons, filled checks |
| `ink2` | `#4F463D` | Secondary text: dates, meta lines, overline labels, inactive nav, trash icon |
| `ink3` | `#6A6056` | Completed (struck-through) task text, placeholder text |
| `inkDisabled` | `#A39888` | Days outside the current month |
| `rule` | `#D9CFBD` | Bottom-nav top border |
| `ruleDashed` | `#CDC2AF` | Dashed separators in lists |
| `outlineDashed` | `#8A7F72` | Dashed outlines (the fields box in setup) |
| `card` | `#FCFAF5` | Index cards, text fields, the "None" priority post-it |
| `canary` | `#F7E08C` | **Medium** priority. Also the app accent: title highlighter, active-nav tape, selected calendar day, the "+" pad, the default new-task note, the text on primary buttons |
| `apricot` | `#F4B795` | **High** priority |
| `sky` | `#CFE1EA` | **Low** priority |
| `inkHigh` | `#A63D22` | High-priority text in the widget; "Not found" in setup |
| `inkMed` | `#7A5A00` | Medium-priority text in the widget |
| `inkLow` | `#2C5F80` | Low-priority text in the widget |
| `indexRed` | `#E39C86` | Header rule on index cards |
| `indexBlue` | `#D3E2EA` | Ruled lines on index cards (every 24 dp) |
| `scrim` | `rgba(42,38,33,0.42)` | Overlay behind sheets (the note, new project), plus a 1.5 dp blur of the screen behind |
| `scrimLight` | `rgba(42,38,33,0.32)` | Overlay behind the Projects "+" menu (no blur) |
| `scrimPicker` | `rgba(42,38,33,0.34)` | Overlay between the note and the calendar or clock card (no extra blur, so the note stays readable) |
| `tape` | `rgba(252,250,245,0.58)` | The tape strip on top of notes and cards |

Priority map (shared everywhere):

| Priority | Post-it colour | Ink colour (text on canary) | Marks | Notion select value |
|---|---|---|---|---|
| High | `apricot` | `inkHigh` | `!!!` | `High` |
| Medium | `canary` | `inkMed` | `!!` | `Medium` |
| Low | `sky` | `inkLow` | `!` | `Low` |
| None | `card` | `ink` | (none) | empty |

### Palettes (Settings → Color palette, and setup step "Choose a look")

The default is **Canary** (the tokens above). Each palette swaps the background, the dot colour and the two most visible note colours:

| Palette | Background | Dots | Note A | Note B |
|---|---|---|---|---|
| Canary (default) | `#F3EEE4` | `#D8CEBD` | `#F7E08C` | `#F4B795` |
| Sage | `#EEF0E6` | `#D2D7C6` | `#CFE3C4` | `#F1D9A0` |
| Blush | `#F6EEEB` | `#E0D2CD` | `#F4C8C1` | `#E3D5EC` |
| Night (dark) | `#26231F` | `#3D3832` | `#F7E08C` | `#8FB3C9` |

The full colour mapping for Sage, Blush and Night hasn't been designed yet. See *Not designed yet*.

### Typography

All four fonts are on Google Fonts under the OFL licence. Bundle them as resources in `res/font`.

| Role | Font | Size / weight | Notes |
|---|---|---|---|
| Screen title ("Today", "October", "Projects") | Kalam | 46 / 700, line height 1.15, letter spacing −0.01em | "Today" sits on a canary highlighter band covering 58–90% of its height |
| Setup / Settings title | Kalam | 40 / 700 | |
| Welcome "Hello." | Kalam | 66 / 700 | |
| Task title (post-it) | Kalam | 21 / 400, line height 1.2 | |
| Mini post-it title (Projects) | Kalam | 17 / 400 | One line, cut off with "…" |
| Done task title | Kalam | 19 / 400, `ink3`, struck through (1.5 dp) | |
| Note / sheet text input | Kalam | 27 / 400, line height 1.3 | Placeholder in `ink3` |
| New project name input | Kalam | 32 / 700 | |
| Day stepper value | Kalam | 28 / 700 | |
| Section heading (calendar day, card names) | Kalam | 25–26 / 700 | |
| Small handwritten labels | Kalam | 17–20 / 400 | Priority names, palette names, field labels, project pills, menu slips |
| Overline label ("MONDAY · 12 OCTOBER", "STEP 1 OF 3") | DM Mono | 11–12 / 400–500, UPPERCASE, letter spacing 0.16em, `ink2` | |
| Body / help text | DM Mono | 11–12.5, line height 1.6, `ink2` | |
| Meta line ("10:30 · Work · !!!") | DM Mono | 11.5 / 400, letter spacing 0.03em, `ink2` | |
| Button label | DM Mono | 13–14 / 500, letter spacing 0.08em | |
| Nav label | DM Mono | 10.5, UPPERCASE, letter spacing 0.14em | 500 when active |
| Time stepper value | DM Mono | 30 / 500 | |
| Calendar picker month | Kalam | 32 / 700 | Year overline above it |
| Calendar picker day number | DM Mono | 15 | 500 for today and the selected day |
| Clock picker time display | DM Mono | 46 / 500 | The part being edited sits on canary tape |
| Clock face numbers | Kalam 20–21 (outer ring), DM Mono 13 (inner ring) | | |
| Calendar day number | DM Mono | 14 | 500 for today |

The Typography setting swaps the **Kalam** role for: Handwritten = Kalam (default), Typewriter = Courier Prime, Bookish = Newsreader. DM Mono labels stay as they are.

### Shape, elevation and tilt

- **Post-it corner curl:** asymmetric radius with a larger bottom-left corner.
  - Task post-it: `2 2 3 18 / 2 2 3 7`
  - Mini post-it: `2 2 2 12 / 2 2 2 5`
  - Note sheet: `2 2 3 26 / 2 2 3 9`
  - "+" pad: `3 3 3 13 / 3 3 3 6`

  In Compose, approximate this with a custom `Shape`: a rounded rect with the bottom-left corner as an ellipse (e.g. rx 18, ry 7).
- **Index card:** `card` fill, `indexBlue` lines every 24 dp, and a header band whose bottom edge is a 1.2 dp `indexRed` line. Radius 2.
- **Hand-drawn circle** (check buttons, today ring, radio buttons): `border-radius: 54% 46% 52% 48% / 47% 55% 45% 53%` with a 1.6 dp `ink` stroke, 22 dp in lists and 18 dp on mini post-its. A slightly irregular `Path` is fine.
- **Post-it shadow:** a tight contact shadow `0 1 1 rgba(42,38,33,.07)` plus a soft lift under the bottom edge `0 12 14 −12 rgba(42,38,33,.55)`. The shadow belongs *below* the paper, not around it.
- **Sheet shadow:** `0 2 2 rgba(42,38,33,.10)` plus `0 34 40 −26 rgba(20,16,12,.75)`.
- **Tilt:**

  | Element | Tilt |
  |---|---|
  | Task post-its (cycle by index) | −0.7°, 0.5°, −0.3°, 0.8°, −0.5° |
  | Mini post-its (cycle by index) | −0.6°, 0.5°, −0.3°, 0.7°, −0.5°, 0.4° |
  | Project cards | about ±0.6° |
  | Note | −1.2° |
  | Calendar picker card | 0.8° |
  | Clock picker card | −0.8° |
  | Selected day in the calendar picker | −3° |
  | Active part of the clock display | −2° |
  | New-project card | −1° |
  | "+" pad | −4° |
  | Menu slips | −2° and 1.5° |
  | Selected calendar day | −2° |
  | Tape | −3° |
  | Active nav label tape | −2° |

  Add a setting later to turn tilt off; the Today mockup has a `tilt` toggle.

### Buttons

- **Primary:** an `ink` pill with `canary` text in mono 14/500 and a trailing arrow. At least 52 dp tall, padding 0 24–26. Disabled: 35% opacity.
- **Secondary:** an outline pill with a 1.4 dp `ink` border and `ink` text, same size.
- **Send / create:** a 52 dp `ink` circle with a `canary` arrow, bottom-right of notes and cards.
- **Text link:** mono 12–13 in `ink2`, or `ink` and underlined for an inline escape hatch.
- **Back:** an arrow plus "Back", mono 13 `ink2`, at least 44 tall.

### Icons

These are line icons drawn like an ink pen: 1.6–1.8 dp stroke, round caps and joins, 22–23 dp, in `ink` or `ink2`. Exact SVG paths are in the source files. Port them as `ImageVector`s.

| Icon | Shape |
|---|---|
| settings | gear |
| trash | bin |
| add | plus, 2 dp stroke; rotates 45° to become "×" |
| clock | circle with hands |
| priority | flag |
| project | two stacked cards (also the Projects tab icon) |
| send | arrow right |
| check | tick |
| calendar | calendar |
| tasks | note with a folded corner (also the Tasks tab icon) |
| chevrons | left, right, down |
| back | arrow left |
| show secret | eye |
| refresh | two curved arrows |
| external link | arrow up-right |

### Spacing and touch targets

- Header padding: 30 top and 24 sides. List padding: 20–22 sides. Gap between task post-its: 14. Gap between mini post-its: 9.
- The mockups use 44 px hit targets. **Implement them as at least 48 dp**, which is Android's minimum.
- Every icon-only button needs a `contentDescription`. The mockup `aria-label`s give the wording, e.g. "Mark Reply to Marco about Friday as done".

---

## 3. Components

| Component | Spec |
|---|---|
| **TaskPostIt** (Today) | Post-it filled with its priority colour and tilted. Left: a 48 dp check button with a hand-drawn 22 dp empty circle. Middle: the title in Kalam 21, with the meta line `time · project · marks` underneath; empty parts are skipped. Right: a 48 dp trash button in `ink2`. |
| **DoneRow** | No post-it, just a row of at least 52 with a dashed bottom border. The check is a filled `ink` hand-drawn circle with a `paper`-coloured tick. Title in Kalam 19, `ink3`, struck through. Trash on the right. Tapping the check moves the task back to open. |
| **MiniPostIt** (Projects) | A horizontal post-it at least 44 tall in the priority colour (None = `card` with a 1 dp 14%-ink border), tilted by index, with a small shadow. It holds **only** an 18 dp hand-drawn check and the title (Kalam 17, one line, "…"). There's no meta and no trash. |
| **AddPad** ("+") | 60×60, `canary`, 1.6 dp `ink` border, tilted −4°. A second sheet sits behind it, offset (4, 4), with an `ink` outline, so it reads as a pad of post-its. Plus icon 26 dp. Position: 26 from the right, 104 from the bottom (just above the nav). On **Today** it opens the NoteSheet; on **Projects** it opens the AddMenu, and its plus rotates to "×" while the menu is open. |
| **AddMenu** (Projects) | Two paper slips stacked above the pad, right-aligned, 16 apart, each at least 52 tall with an icon and a Kalam 20 label. **New task** (`canary` post-it, −2°) sits nearest the pad; **New project** (white index card with ruled lines, 1.5°) sits above it. Behind them is `scrimLight`, and tapping it closes the menu. Use proper menu semantics. |
| **BottomNav** | Three equal tabs: **Calendar · Tasks · Projects**, with Tasks in the centre and Tasks as home. Background is `paper` with no dots and a 1 dp `rule` top border. Each tab is an icon over an uppercase mono label. The active tab is `ink` and its label sits on a `canary` tape tilted −2°. Inactive tabs are `ink2`. Don't use a pill indicator or ripple blob. |
| **NoteSheet** (new task) | A post-it 342 wide (24 margins), tilted −1.2°, with a tape strip at the top centre (112×28, −3°). It floats over the scrim plus the blurred screen behind; tapping outside closes it without saving. It contains, in order: overline "NEW TASK"; a borderless multi-line text field (Kalam 27); a row of chips for the chosen date, priority and project; an expandable picker area below a dashed "tear line" that runs the full width of the note; and a bottom row with **three toggles** on the left (clock, flag, project) and the send button on the right. The active toggle gets an `rgba(42,38,33,.12)` rounded background. Only one picker is open at a time. The note is `canary` by default and takes the priority colour once a priority is picked. |
| **Chip** | Pill with a 1.2 dp `ink` border, a translucent white fill (45%), mono 12, and a 14 dp icon. For example "Tomorrow, 18:00", "!!! High" or "Work". |
| **Stepper** | Three columns: `[‹] value [›]`. Day: Kalam 28/700 value ("Today", "Tomorrow", then the weekday name) with a mono date underneath ("TUE 13 OCT"); you can't go before today. Time: mono 30/500 value in 30-minute steps, with a part-of-day label underneath (Morning before 12, Afternoon before 17, Evening before 21, otherwise Night). **The value itself is tappable.** It has a dashed fill-in-the-blank underline (1.5 dp, 50% `ink`): tapping the day opens the CalendarPicker, and tapping the time opens the ClockPicker. The arrows stay for quick ±1 day and ±30 min. |
| **CalendarPicker** | A `card` sheet, 350 wide, tilted 0.8°, with tape. It lies over the note with `scrimPicker` between them; tapping the scrim closes it without changes. **Header** (index-card style, `indexRed` rule under it): year overline, month in Kalam 32/700, and ‹ › month arrows (‹ disabled on the current month; no limit going forward). **Grid:** weekday row `M T W T F S S` (Monday first); days of other months are left blank. Each day is a 46-tall cell: a mono 15 number inside a 30 dp hand-drawn ring (ring only on today), and under it up to three 5 dp outline squares showing how many open tasks already fall on that day, so the user can spot a free day. **Past days** are `inkDisabled` and can't be selected. The **selected day** becomes a small `canary` post-it (−3°, shadow). **Footer:** "NO DATE" text button on the left (clears the date); a primary pill on the right that names the choice: "Set Tue 13 Oct ✓". |
| **ClockPicker** | A `card` sheet, 350 wide, tilted −0.8°, with tape; same scrim behaviour. Uses **24-hour** time. **Header:** overline "TIME · PICK THE HOUR" (or "… THE MINUTES"), with "NO TIME" on the right (clears the time). **Display:** "18 : 00" in mono 46/500. The hour and minute parts are separate buttons, and the one being edited sits on `canary` tape (−2°). A part-of-day label goes underneath. **Clock face:** a 272 dp hand-drawn circle (1.6 dp `ink`, `paper` fill), with a 2 dp `ink` hand from the centre and a 14 dp `canary` pin with an `ink` border in the middle. **Hours mode:** outer ring 12, 1–11 (Kalam 21, radius 106) and inner ring 00, 13–23 (mono 13, `ink2`, radius 68). The selected number gets a 40 dp `canary` hand-drawn circle with an `ink` border, and the hand points to it. Tapping an hour selects it and **switches to minutes mode automatically**. **Minutes mode:** outer ring 00–55 in steps of 5 (Kalam 20). Dragging the hand should also work, snapping to 1 minute while dragging and to 5 on release, so any minute can be set. **Footer:** "CANCEL" text button and a primary pill "Set 18:00 ✓". |
| **PriorityPicker** | Four 54×54 mini post-its: High, Medium, Low, None. Each shows its handwritten name and marks below. The selected one gets a 2 dp `ink` border, a tick and a small tilt; the others get a 1.2 dp border at 40% `ink`. **Picking one recolours the whole note** (crossfade, 260 ms). |
| **ProjectPicker** | A wrapping row of pills, at least 44 tall, with a 1.4 dp `ink` border and Kalam 18 text: each project plus "No project". The selected pill is `ink` with `canary` text; the others use a translucent white fill. |
| **CalendarDayCell** | 54 tall. Mono day number inside a 30 dp hand-drawn ring; the ring is only drawn for today. Under it, up to four 7 dp squares, one per task: filled `ink` means done, an outline means not done. The selected day becomes a small `canary` post-it, tilted −2°, with a shadow. Days outside the month are `inkDisabled` and can't be selected. |
| **ProjectCard** | A full-width index card. Header (54 tall): the name in Kalam 25/700 on the left, and "6 open · 4 done" in mono `ink2` on the right, above the `indexRed` rule. Body: a vertical list of MiniPostIts with padding 12/14/14 and a 9 dp gap. **Max height 258 dp; it scrolls inside the card**, so about four items show with the fifth peeking out as a scroll hint. A card with no open tasks shows "Nothing left here." in Kalam 18 `ink3`. |
| **NewProjectSheet** | An index card, 342 wide, tilted −1°, with tape, over the scrim plus blur. Header band: overline "NEW PROJECT" and a name input (Kalam 32/700, placeholder "Name it…"). On the ruled body: a short mono helper line and the send button at the bottom right. |
| **TokenField** | `card` fill, 1.4 dp `ink` border, radius 3, 50–52 tall. Mono 14 password text. During setup it has a **Paste** text button and a show/hide eye; in Settings it has only the eye. |
| **RadioRow** (setup database list, typography) | Full width, at least 52–54 tall: content on the left and a hand-drawn radio on the right. The selected row becomes a `card` sheet with the post-it shadow and a −0.5° tilt; the others are transparent with a dashed bottom border. |
| **PaletteSwatch** | A 62–74-tall tile in the palette's background colour, with two overlapping mini post-its in its two note colours. The selected tile gets a 2 dp `ink` border and its name sits on canary tape. |
| **StepHeader** (setup) | Three 9 dp squares (filled = current or done, outline = to do) plus a mono overline "STEP 1 OF 3". The final step shows only "LAST STEP", because it's reached from both setup paths. Then a Kalam 40/700 title and a mono intro line. |

---

## 4. Screens and behaviour

### 4.1 First run

Shown once, before Today, until setup is finished. Every step has a **Back** link at the bottom left and a **primary pill** at the bottom right.

**Welcome**
- A pile of three post-its (sky, apricot, and a canary front note with tape and "Hello.").
- Title "Your tasks, on little notes." and the line "Keep them in sync with your Notion workspace, or keep everything on this phone."
- Two buttons:
  - **Connect Notion** (primary) goes to *Connect Notion*.
  - **Use without Notion** (secondary outline) goes straight to *Choose a look* and puts the app in **local mode** (§5.2).
- A small note underneath: "Without Notion, tasks stay on this phone. You can connect later in Settings."
- The app name is deliberately not shown.

**Connect Notion (step 1 of 3)**
- An index card titled "Get your secret", with an **Open Notion ↗** link to `https://www.notion.so/my-integrations` and four handwritten steps:
  1. Go to notion.so/my-integrations
  2. Create a new internal integration
  3. Copy its Internal Integration Secret
  4. Give it access to your tasks database ("In Notion: ••• › Connections › your integration")
- Then the field labelled "Paste your secret": a TokenField with a **Paste** button (from the clipboard) and the eye.
- Help line: "Starts with ntn_ or secret_. Stored encrypted on this phone and never sent anywhere except Notion."
- Escape link: "No Notion? Use the app without it" goes to *Choose a look* in local mode.
- **Connect** stays disabled until the field isn't empty. On tap, validate the secret with a cheap API call. Show a short checking state on the button, and show errors inline under the field in `inkHigh` (wording to be decided). On success, go to step 2.

**Pick your tasks (step 2 of 3)**
- RadioRows listing **every database the integration can access**: the name in Kalam 20, with the parent page in mono ("in Home").
- "Missing one? Connect it in Notion, then refresh." plus a **Refresh** button that searches again.
- **Fields box:** a dashed box titled `FIELDS IN "<DB>"` with "N of 5 found" on the right. It has five rows: Task name, Done, Date, Priority, Project.
  - Each row shows the matched Notion property (Kalam 17) with a ▾. Tapping it opens a picker listing that database's properties of a compatible type.
  - An unmatched row shows "Not found · choose" in `inkHigh` mono.
  - Matching is automatic first: by name (case-insensitive, including Italian synonyms if cheap), then by type.
  - Task name, Done and Date are **required** for Next. Priority and Project are optional; without them, those features are hidden.
- **Next** goes to *Choose a look*.

**Choose a look (last step)**
- A live preview box: two task post-its on the chosen palette's background, in the chosen font.
- Then the palette swatches and three typography RadioRows ("Handwritten · Kalam", "Typewriter · Courier Prime", "Bookish · Newsreader").
- **Start writing** saves everything and opens Today.

### 4.2 Today (home)

- **Header:**
  - Overline with today's date ("MONDAY · 12 OCTOBER").
  - Title "Today" on the highlighter band.
  - Summary "4 to do · 2 done".
  - Settings gear at the top right, which opens Settings.
- **To-do list:** today's open tasks as TaskPostIts.
  - Order: by time (untimed last), then by priority. *(The mock happens to be in that order; confirm with the user.)*
  - Tapping the check marks the task done and moves it to the Done section.
  - Tapping the trash deletes the task. In Notion this moves the page to trash; show an "Undo" snackbar styled like a paper strip.
- **Done · N:** today's completed tasks as DoneRows.
- **Empty state:** when no open tasks are left, show "All clear for today." in Kalam 30 with "Nothing left on the desk." in mono underneath.
- **AddPad** opens the NoteSheet. The bottom nav has Tasks active.

### 4.3 New task (NoteSheet)

1. **Empty:** the placeholder "What needs doing?" with the keyboard open. There are no chips yet.
2. **Clock:** opens the date & time Stepper area below the tear line. The chip shows the result live ("Tomorrow, 18:00"). Tapping the clock again collapses it.
   - For dates further away, tap the **day value** to open the **CalendarPicker** (screen 7a).
   - For an exact time, tap the **time value** to open the **ClockPicker** (screens 7b–7c).
   - When either picker closes, the Stepper and the chip update to the chosen values. "No date" and "No time" clear them, and the chip then shows only what's left (or disappears).
3. **Flag:** opens the PriorityPicker. The note recolours to the chosen priority and a chip shows "!!! High".
4. **Project:** opens the ProjectPicker and the chip shows the project name. When the note is opened from Projects → **New task**, this picker is **already open** and preselects the project the user was looking at most recently (it's Work in the mock).
5. **Send:** creates the task (§5) and closes the note. The new post-it appears where it belongs (Today if it's due today, and in its project card). Don't allow saving with an empty title.

The defaults for a new task haven't been decided; there's an open question on this in §7.

### 4.4 Calendar

- **Header:**
  - Year overline.
  - Month name as the title.
  - ‹ › month arrows: **‹ goes back to past months; › is disabled on the current month.**
  - Summary line: current month "16 done · 2 missed · 14 to come"; past months "X done · Y missed".
- **Grid:** weekday header `M T W T F S S`, with the week starting on Monday. Shows 5–6 weeks of CalendarDayCells.
- **Legend:** "■ done □ not done".
- **Selected day panel:**
  - Heading "Monday 12" with the count ("6 TASKS") on the right, above a 1.5 dp ink rule.
  - Rows show the status circle, then the title (struck through if done), then the time on the right (or "missed" for past undone tasks, or "any time"), then a 12 dp priority swatch.
  - If the day has no tasks, show "Nothing written down."
- Today is selected by default. The bottom nav has Calendar active.

### 4.5 Projects

- **Header:**
  - Overline "GROUPS OF NOTES".
  - Title "Projects".
  - Summary "4 projects · 12 open notes".
- **Cards:** a single column of ProjectCards, 26 apart, with the page scrolling. Each card lists that project's **next open tasks** as MiniPostIts.
  - Order: by due date (soonest first; undated last), then by priority.
  - Tapping a check completes the task: it leaves the list and the card's done count goes up.
  - Tapping the card header has no action yet (see §7).
- **AddPad** (bottom right) toggles the **AddMenu**:
  - **New task** opens the NoteSheet with the ProjectPicker open (§4.3).
  - **New project** opens the NewProjectSheet.
- **NewProjectSheet:**
  - The name is required.
  - Send creates the project (§5) and adds an empty card at the end of the list ("Nothing left here."). Ask before building reordering.
- The bottom nav has Projects active.

### 4.6 Settings

Settings is a sub-page: a back arrow and the "Settings" title, with no bottom nav.

**Notion**
- Field label "Integration token", then the TokenField.
- Help text: "Create an internal integration at notion.so/my-integrations, then share your tasks database with it."
- A status row with a filled check and "Connected to "Tasks"", then a **Save** pill.
- Save should validate the token and then show the connected database name.
- *In local mode this section needs a "Not connected · Connect Notion" state. It isn't designed yet (§7).*

**Color palette**
- Four PaletteSwatches: Canary, Sage, Blush, Night.
- Applies app-wide and is remembered.

**Typography**
- Three options: Handwritten, Typewriter, Bookish.
- Applies app-wide and is remembered.

### 4.7 Home-screen widget (later milestone)

A small post-it on the home screen showing **today's open tasks**, in two sizes.

**Note**
- A `canary` post-it with the curled corner, a strip of tape at the top, a soft drop shadow, and a slight tilt: −1.5° on the 2×2 and −0.8° on the 4×2.

**Header**
- "Today" in Kalam 700 (22 on the 2×2, 24 on the 4×2).
- Next to it, the open-task count in mono: "4" on the 2×2, "MON 12 OCT · 4 TO DO" on the 4×2.
- A "+" on the right opens the app straight into the new-task note.
- Tapping the header opens Today.

**Order**
- **High → Medium → Low → None.** Within a priority, earliest time first.

**Rows**
- Each row has a hand-drawn check circle and the title on one line, cut off with "…".
- The title and the circle outline are drawn in the priority's **ink** colour (`inkHigh` / `inkMed` / `inkLow` / `ink`). All of these are at least 4.5:1 on canary.
- The 4×2 also shows the time (mono, `ink2`) and the `!` marks in the ink colour on the right, with dashed separators between rows.

**Behaviour**
- Tapping a row's circle completes the task, and the task leaves the widget.
- The **whole row** should be the tap target for completing, because rows are only about 30 dp tall and can't fit a 48 dp check button.

**Limits and states**

| | 2×2 | 4×2 |
|---|---|---|
| Rows shown | 3 | 4 |
| Overflow | "+N more" in mono, which opens Today | same |
| Empty state | "All clear." | "All clear for today." |

**Implementation notes**
- Build it with Jetpack Glance.
- Widgets (RemoteViews) have limited support for custom fonts, rotation, custom shapes and shadows. Check what the current Glance version allows before designing around it.
- Likely approach:
  - Draw the post-it background (curl, tape, shadow, tilt) as a drawable or bitmap.
  - Keep the content itself un-rotated.
  - If Kalam can't be used for widget text, ask the user before choosing a fallback.
- Refresh after every change made in the app, on a periodic background sync, and at midnight, when "today" changes.

---

## 5. Data and sync

### 5.1 Notion mode

- **Auth:** the user pastes an **internal integration token**. Newer tokens start with `ntn_`; older ones with `secret_`. Store it encrypted on the device (Android Keystore). Never log it and never commit it. There's no OAuth for v1.
- **API version:** the API changed in **2025-09-03**: databases now contain *data sources*. Queries go to `/v1/data_sources/{data_source_id}/query`, and new pages use `parent: { type: "data_source_id", … }`. You get the `data_source_id` from `GET /v1/databases/{database_id}`. A newer version (`2026-03-11`) also exists. **Read the current upgrade guides at developers.notion.com before writing the client, and pin the latest `Notion-Version` header.**
- **Database list (setup step 2):** use the search endpoint, filtered to databases / data sources the integration can access.
- **Field mapping:** the user confirms it in setup step 2 and it's stored locally. The default names the app looks for:

  | App field | Notion property | Type |
  |---|---|---|
  | title | Name | title |
  | done | Done | checkbox (or a Status property whose "Done" group counts as done) |
  | due date and time | Due | date (with or without time) |
  | priority | Priority | select: High / Medium / Low |
  | project | Project | relation to a Projects database (preferred) or a select |

- **Projects:**
  - If Project is a **relation**, projects are the pages of the related database. "New project" creates a page there.
  - If Project is a **select**, projects are its options. "New project" adds an option, which the API may only allow through a data source update; check this.
- **Operations:**

  | Action | API call |
  |---|---|
  | Load Today | Query tasks where Due is on today |
  | Load Calendar | Query tasks with Due in the visible month |
  | Load Projects | Query open tasks grouped by project; count done tasks |
  | Create task | Create a page |
  | Complete / undo | Update the done property |
  | Delete | Move the page to trash (`in_trash`) |
  | Create project | See above |

- **Feel:** the app must feel instant. Update the UI optimistically, keep a local cache for offline use, sync in the background, and retry on failure. Notion's average rate limit is about 3 requests per second, so batch and back off.

### 5.2 Local mode (no Notion)

- Chosen from Welcome or from the escape link on Connect Notion.
- Tasks and projects live **only in the local database** (Room), using the same model as the Notion cache, so every screen works the same.
- Sync code is skipped; the widget and everything else still work.
- The user can connect Notion later from Settings. What happens to the local tasks then is an open question (§7).

---

## 6. Sample data (used in the mockups and for previews)

**Today (Mon 12 Oct 2026)**

| Task | Time | Project | Priority | State |
|---|---|---|---|---|
| Reply to Marco about Friday | 10:30 | Work | High | Open |
| Weekly review in Notion | 13:00 | Personal | Medium | Open |
| Pick up the parcel | 18:00 | Errands | Low | Open |
| Read 20 pages | none | Reading | None | Open |
| Water the plants | 08:00 | Personal | Low | Done |
| Back up the laptop | none | Personal | None | Done |

**Projects (open tasks shown on each card)**

| Project | Open tasks (priority) | Done |
|---|---|---|
| Work | Reply to Marco about Friday (High), Draft the Q4 plan (High), Send the weekly report (Medium), Review the access requests (Medium), Book the meeting room (Low), Tidy the shared folder (None) | 4 |
| Personal | Buy a birthday card for Giulia (High), Weekly review in Notion (Medium), Call grandma (Medium) | 7 |
| Errands | Pick up the parcel (Low), Book the car service (Medium) | 1 |
| Reading | Read 20 pages (None) | 5 |

**Databases in setup step 2**

| Database | Parent | Fields matched |
|---|---|---|
| Tasks | in Home | all 5 |
| Projects | in Home | some fields missing |
| Reading list | in Books | some fields missing |
| Habits | in Home | some fields missing |
| Meeting notes | in Work | some fields missing |

The exact field matches for each database are in `SetupDatabase.dc.html`. The calendar month data is in `Calendar.dc.html` (`tasksFor()`).

---

## 7. Not designed yet / open questions

Ask the user before inventing any of these:

- The Settings Notion section in **local mode** ("Not connected · Connect Notion"), and the re-connect flow from Settings: does it reuse setup steps 1–2?
- When a local-mode user connects Notion later: copy the local tasks into Notion, keep them separate, or ask?
- Error, offline and syncing states, including invalid secret, no databases found, and rate limited.
- Editing an existing task (tap on a post-it?).
- Tapping a project card header: a project detail page? Renaming or deleting a project? Reordering cards?
- The full colour mapping for the Sage, Blush and Night palettes (Night is a dark theme).
- Defaults for new tasks: today or no date? priority None?
- Calendar and clock pickers: should a single tap close them (picking a day, or picking the minutes), instead of needing the "Set" button? The mockups use "Set".
- Task order on Today.
- Notifications or reminders: out of scope for now. The home-screen widget is designed (§4.7) but comes after the main app works.
