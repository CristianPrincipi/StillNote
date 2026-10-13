# Notion To-Do: design handoff

A minimalist Android to-do app whose tasks live in the user's Notion. It should feel **focused, calm, clear and analog**: like post-it notes on a dotted notebook page, not like a typical modern app. There are few buttons and lots of empty space.

This folder is the design source of truth. Build the UI to match it. Where this file and the screens disagree, the screens win on visuals and this file wins on behaviour.

## What's in this folder

| Path | What it is | How to use it |
|---|---|---|
| `DESIGN.md` | This spec: tokens, components, screens, behaviour, Notion mapping | Read first |
| `screens/png/*.png` | Rendered screenshots of every screen at 390×844 (2× scale) | The visual target. Look at them before building each screen. |
| `screens/html/*.html` | Static HTML renders of the same screens (default state) | Open in a browser. Inline styles hold the exact values. |
| `screens/source/*.dc.html` | Original mockup sources from the design tool | Reference only, not app code. `{{holes}}`, `<sc-for>` and `<sc-if>` are template syntax. The `renderVals()` class at the bottom shows the interactive behaviour and sample data. |

Screens, in flow order:

| # | Screen | PNG | Source |
|---|---|---|---|
| 1 | Today (home) | `01-today.png` | `Main.dc.html` |
| 2 | New task: empty note | `02-new-task.png` | `AddTask.dc.html` |
| 3 | New task: date & time open | `03-new-task-date-time.png` | `AddTaskWhen.dc.html` |
| 4 | New task: priority open | `04-new-task-priority.png` | `AddTaskPriority.dc.html` |
| 5 | Calendar | `05-calendar.png` | `Calendar.dc.html` |
| 6 | Projects | `06-projects.png` | `Projects.dc.html` |
| 7 | Settings | `07-settings.png` | `Settings.dc.html` |
| 8 | Home-screen widget, 2×2 | `08-widget-2x2.png` | `Widget2x2.dc.html` |
| 9 | Home-screen widget, 4×2 | `09-widget-4x2.png` | `Widget4x2.dc.html` |

Units: the app screens are drawn on a 390×844 px phone. The widgets are drawn at real size (172×172 and 356×184) on a dark stand-in for the wallpaper. Treat **1 px = 1 dp** and font px as **sp**.

---

## 1. Design principles

1. **Paper, not glass.** Everything is a sheet of paper: post-its for tasks, index cards for projects, a dotted notebook page as the background. Don't use gradients, glossy elevation, Material ripples on everything, or FABs with circular shadows.
2. **Colour means priority.** A task's post-it colour is its priority, so don't use colour decoratively. Priority is also shown with `!` marks, so it never relies on colour alone.
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
| `ink` | `#2A2621` | Primary text, icons, outlines, the send button, filled checks |
| `ink2` | `#4F463D` | Secondary text: dates, meta lines, overline labels, inactive nav, trash icon |
| `ink3` | `#6A6056` | Completed (struck-through) task text, placeholder text |
| `inkDisabled` | `#A39888` | Days outside the current month |
| `rule` | `#D9CFBD` | Bottom-nav top border |
| `ruleDashed` | `#CDC2AF` | Dashed separators in lists |
| `outlineDashed` | `#8A7F72` | Dashed "New project" card |
| `card` | `#FCFAF5` | Index cards, text fields, "None" priority post-it |
| `canary` | `#F7E08C` | **Medium** priority. Also the app accent: title highlighter, active-nav tape, selected calendar day, the "+" pad, default new note |
| `apricot` | `#F4B795` | **High** priority |
| `sky` | `#CFE1EA` | **Low** priority |
| `indexRed` | `#E39C86` | Header rule on project index cards |
| `indexBlue` | `#D3E2EA` | Ruled lines on index cards (every 24 dp) |
| `scrim` | `rgba(42,38,33,0.42)` | Overlay behind the new-task note, plus a 1.5 dp blur of the screen behind |
| `tape` | `rgba(252,250,245,0.58)` | The tape strip on top of the new-task note |

Priority map (shared everywhere):

| Priority | Colour | Marks | Notion select value |
|---|---|---|---|
| High | `apricot` | `!!!` | `High` |
| Medium | `canary` | `!!` | `Medium` |
| Low | `sky` | `!` | `Low` |
| None | `card` | (none) | empty |

### Palettes (Settings → Color palette)

The default is **Canary** (the tokens above). Each palette swaps the background and the two most visible note colours:

| Palette | Background | Note A | Note B |
|---|---|---|---|
| Canary (default) | `#F3EEE4` | `#F7E08C` | `#F4B795` |
| Sage | `#EEF0E6` | `#CFE3C4` | `#F1D9A0` |
| Blush | `#F6EEEB` | `#F4C8C1` | `#E3D5EC` |
| Night (dark) | `#26231F` | `#F7E08C` | `#8FB3C9` |

The full colour mapping for Sage, Blush and Night hasn't been designed yet. See *Not designed yet*.

### Typography

All three fonts are on Google Fonts under the OFL licence. Bundle them as resources in `res/font`.

| Role | Font | Size / weight | Notes |
|---|---|---|---|
| Screen title ("Today", "October", "Projects") | Kalam | 46 / 700, line height 1.15, letter spacing −0.01em | "Today" sits on a canary highlighter band covering 58–90% of its height |
| Settings title | Kalam | 40 / 700 | |
| Task title (post-it) | Kalam | 21 / 400, line height 1.2 | |
| Done task title | Kalam | 19 / 400, `ink3`, struck through (1.5 dp) | |
| New-task text input | Kalam | 27 / 400, line height 1.3 | Placeholder "What needs doing?" in `ink3` |
| Day stepper value | Kalam | 28 / 700 | |
| Section heading (calendar day) | Kalam | 26 / 700 | |
| Project card name | Kalam | 24 / 700 | |
| Small handwritten labels | Kalam | 17–19 / 400 | Priority names, palette names, field label |
| Overline label ("MONDAY · 12 OCTOBER", "DONE · 2", "WHEN") | DM Mono | 11–12 / 400–500, UPPERCASE, letter spacing 0.16em, `ink2` | |
| Meta line ("10:30 · Work · !!!") | DM Mono | 11.5 / 400, letter spacing 0.03em, `ink2` | |
| Nav label | DM Mono | 10.5, UPPERCASE, letter spacing 0.14em | 500 when active |
| Time stepper value | DM Mono | 30 / 500 | |
| Calendar day number | DM Mono | 14 | 500 for today |

The Settings → Typography choices swap the **Kalam** role for: Handwritten = Kalam (default), Typewriter = Courier Prime, Bookish = Newsreader. DM Mono labels stay as they are.

### Shape, elevation and tilt

- **Post-it corner curl:** asymmetric radius with a larger bottom-left corner.
  - Task post-it: `2 2 3 18 / 2 2 3 7`
  - New-task note: `2 2 3 26 / 2 2 3 9`
  - "+" pad: `3 3 3 13 / 3 3 3 6`

  In Compose, approximate this with a custom `Shape`: a rounded rect with the bottom-left corner as an ellipse of rx 18, ry 7.
- **Hand-drawn circle** (check buttons, today ring, radio buttons): `border-radius: 54% 46% 52% 48% / 47% 55% 45% 53%` with a 1.6 dp `ink` stroke, 22 dp in lists. A slightly irregular `Path` is fine.
- **Post-it shadow:** a tight contact shadow `0 1 1 rgba(42,38,33,.07)` plus a soft lift under the bottom edge `0 12 14 −12 rgba(42,38,33,.55)`. The shadow belongs *below* the paper, not around it.
- **Note shadow:** `0 2 2 rgba(42,38,33,.10)` plus `0 34 40 −26 rgba(20,16,12,.75)`.
- **Tilt:** task post-its cycle through −0.7°, 0.5°, −0.3°, 0.8°, −0.5° by index. The new-task note is −1.2°, the "+" pad −4°, the selected calendar day −2°, the tape −3°, and the active nav label tape −2°. Add a setting later to turn tilt off; the mockup has a `tilt` toggle.

### Icons

These are line icons drawn like an ink pen: 1.6–1.8 dp stroke, round caps and joins, 22–23 dp, in `ink` or `ink2`. Exact SVG paths are in the source files. Port them as `ImageVector`s.

| Icon | Shape |
|---|---|
| settings | gear |
| trash | bin |
| add | plus, 2 dp stroke |
| clock | circle with hands |
| priority | flag |
| send | arrow right |
| check | tick |
| calendar | calendar |
| tasks | note with a folded corner (also the Tasks tab icon) |
| projects | two stacked cards |
| chevron left / right | chevrons |
| back | arrow left |
| show token | eye |

### Spacing and touch targets

- Header padding: 30 top and 24 sides. List padding: 20 sides. Gap between task post-its: 14.
- Task post-it: min height 70; padding 10 top/bottom, 6 left, 4 right.
- The mockups use 44 px hit targets. **Implement them as at least 48 dp**, which is Android's minimum.
- Every icon-only button needs a `contentDescription`. The mockup `aria-label`s give the wording, e.g. "Mark Reply to Marco about Friday as done".

---

## 3. Components

| Component | Spec |
|---|---|
| **TaskPostIt** | Post-it filled with its priority colour and tilted. Left: a 48 dp check button with a hand-drawn 22 dp empty circle. Middle: the title in Kalam 21, with the meta line `time · project · marks` underneath; empty parts are skipped. Right: a 48 dp trash button in `ink2`. |
| **DoneRow** | No post-it, just a row of at least 52 with a dashed bottom border. The check is a filled `ink` hand-drawn circle with a `paper`-coloured tick. Title in Kalam 19, `ink3`, struck through. Trash on the right. Tapping the check moves the task back to open. |
| **AddPad** ("+") | 60×60, `canary`, 1.6 dp `ink` border, tilted −4°. A second sheet sits behind it, offset (4, 4), with an `ink` outline, so it reads as a pad of post-its. Plus icon 26 dp. Position: 26 from the right, 104 from the bottom (just above the nav). Only on Today. |
| **BottomNav** | Three equal tabs: **Calendar · Tasks · Projects**, with Tasks in the centre and Tasks as home. Background is `paper` with no dots and a 1 dp `rule` top border. Each tab is an icon over an uppercase mono label. The active tab is `ink` and its label sits on a `canary` tape tilted −2°. Inactive tabs are `ink2`. Don't use a pill indicator or ripple blob. |
| **NoteSheet** (new task) | A `canary` square post-it, 342 wide (24 margins), at least 300 tall, tilted −1.2°, with a strip of tape at the top centre (112×28, −3°). It floats over the scrim plus blurred Today screen; tapping outside closes it without saving. It contains, in order: overline "NEW TASK"; a borderless multi-line text field (Kalam 27); a row of chips for the chosen date and priority; an expandable picker area below a dashed "tear line" that runs the full width of the note; and a bottom row with the clock and flag toggle buttons on the left and the send button on the right (52 dp `ink` circle with an arrow in the note colour). The active toggle gets an `rgba(42,38,33,.12)` rounded background. Only one picker is open at a time. |
| **Chip** | Pill with a 1.2 dp `ink` border, a translucent white fill (45%), mono 12, and a 14 dp icon. For example "Tomorrow, 18:00" or "!!! High". |
| **Stepper** | Three columns: `[‹] value [›]`. Day: Kalam 28/700 value ("Today", "Tomorrow", then the weekday name) with a mono date underneath ("TUE 13 OCT"); you can't go before today. Time: mono 30/500 value in 30-minute steps, with a part-of-day label underneath (Morning before 12, Afternoon before 17, Evening before 21, otherwise Night). |
| **PriorityPicker** | Four 54×54 mini post-its: High, Medium, Low, None. Each shows its handwritten name and marks below. The selected one gets a 2 dp `ink` border, a tick and a small tilt; the others get a 1.2 dp border at 40% `ink`. **Picking one recolours the whole note** (crossfade, 260 ms). |
| **CalendarDayCell** | 54 tall. Mono day number inside a 30 dp hand-drawn ring; the ring is only drawn for today. Under it, up to four 7 dp squares, one per task: filled `ink` means done, an outline means not done. The selected day becomes a small `canary` post-it, tilted −2°, with a shadow. Days outside the month are `inkDisabled` and can't be selected. |
| **ProjectIndexCard** | 158 tall, `card` background with `indexBlue` rules every 24 dp. Header: 50 tall, with the name in Kalam 24/700 above a 1.2 dp `indexRed` line. Body: "6 open · 4 done" in mono, then one 9 dp square per task (filled = done, outline = open). Small tilt and the post-it shadow. |
| **NewProjectCard** | Same size, transparent, with a 1.5 dp dashed `outlineDashed` border, a plus icon and "New project" in Kalam 20. |
| **TokenField** | `card` fill, 1.4 dp `ink` border, radius 3, 50 tall. Mono 14 password text with a show/hide eye button. |
| **PaletteSwatch** | A 74-tall tile in the palette's background colour, with two overlapping mini post-its in its two note colours. The selected tile gets a 2 dp `ink` border and its name sits on canary tape. |
| **FontOption** | A full-width row showing the sample "Water the plants" in that font at 21, with "Handwritten · Kalam" in mono underneath and a hand-drawn radio button on the right. The selected row becomes a `card` sheet with the post-it shadow. |

---

## 4. Screens and behaviour

### 4.1 Today (home)

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

### 4.2 New task (NoteSheet)

1. **Empty:** the placeholder "What needs doing?" with the keyboard open. There are no chips yet.
2. **Clock:** opens the date & time stepper area below the tear line. The chip shows the result live ("Tomorrow, 18:00"). Tapping the clock again collapses it.
3. **Flag:** opens the PriorityPicker. The note recolours to the chosen priority and a chip shows "!!! High".
4. **Send:** creates the task (in Notion, see §5) and closes the note. The new post-it appears on Today if its date is today. Don't allow saving with an empty title.

The defaults for a new task haven't been decided; there's an open question on this in §7.

### 4.3 Calendar

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

### 4.4 Projects

- **Header:**
  - Overline "GROUPS OF NOTES".
  - Title "Projects".
  - Summary "4 projects · 12 open notes".
- **Grid:** a two-column grid of ProjectIndexCards plus a NewProjectCard. Projects are index cards on purpose, so they never look like tasks.
- Tapping a project opens its detail page (not designed yet). The bottom nav has Projects active. There's no AddPad here.

### 4.5 Settings

Settings is a sub-page: a back arrow and the "Settings" title, with no bottom nav.

**Notion**
- Field label "Integration token", then the TokenField.
- Help text: "Create an internal integration at notion.so/my-integrations, then share your tasks database with it."
- A status row with a filled check and "Connected to "Tasks"", then a **Save** pill (`ink` with canary text).
- Save should validate the token with a cheap API call and then show the connected database name.

**Color palette**
- Four PaletteSwatches: Canary, Sage, Blush, Night.
- Applies app-wide and is remembered.

**Typography**
- Three FontOptions: Handwritten, Typewriter, Bookish.
- Applies app-wide and is remembered.

### 4.6 Home-screen widget (later milestone)

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
- The title and the circle outline are drawn in the priority's **ink** colour. These are darker versions of the post-it colours, readable on canary (all at least 4.5:1):

  | Priority | Ink | Hex |
  |---|---|---|
  | High | terracotta | `#A63D22` |
  | Medium | ochre | `#7A5A00` |
  | Low | blue | `#2C5F80` |
  | None | `ink` | `#2A2621` |

  Add these to the colour tokens as `inkHigh`, `inkMed`, `inkLow`.
- The 4×2 also shows the time (mono, `ink2`) and the `!` marks in the ink colour on the right, with dashed separators between rows.

**Behaviour**
- Tapping a row's circle completes the task in Notion, and the task leaves the widget.
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

## 5. Notion integration (behaviour, not visuals)

- **Auth:** the user pastes an **internal integration token**. Newer tokens start with `ntn_`; older ones with `secret_`. Store it encrypted on the device (Android Keystore). Never log it and never commit it. There's no OAuth for v1.
- **API version:** the API changed in **2025-09-03**: databases now contain *data sources*. Queries go to `/v1/data_sources/{data_source_id}/query`, and new pages use `parent: { type: "data_source_id", … }`. You get the `data_source_id` from `GET /v1/databases/{database_id}`. A newer version (`2026-03-11`) also exists. **Read the current upgrade guides at developers.notion.com before writing the client, and pin the latest `Notion-Version` header.**
- **Suggested Tasks database schema.** The property names should be configurable, because the user may point the app at an existing database.

  | App field | Notion property | Type |
  |---|---|---|
  | title | Name | title |
  | done | Done | checkbox (or a Status property whose "Done" group counts as done) |
  | due date and time | Due | date (with or without time) |
  | priority | Priority | select: High / Medium / Low |
  | project | Project | relation to a Projects database (or a select) |

- **Operations:**

  | Action | API call |
  |---|---|
  | Load Today | Query tasks where Due is on today |
  | Load Calendar | Query tasks with Due in the visible month |
  | Load Projects | Query the projects; count open and done tasks per project |
  | Create | Create a page |
  | Complete / undo | Update the Done property |
  | Delete | Move the page to trash (`in_trash`) |

- **Feel:** the app must feel instant. Update the UI optimistically, keep a local cache for offline use, sync in the background, and retry on failure. Notion's average rate limit is about 3 requests per second, so batch and back off.

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

**Projects**

| Project | Open | Done |
|---|---|---|
| Work | 6 | 4 |
| Personal | 3 | 7 |
| Errands | 2 | 1 |
| Reading | 1 | 5 |

The calendar month data is in `screens/source/Calendar.dc.html` (`tasksFor()`).

---

## 7. Not designed yet / open questions

Ask the user before inventing any of these:

- Project detail page (the tasks inside one project, and adding a task to it).
- Editing an existing task (tap on a post-it?).
- Choosing a project when creating a task.
- First-run / onboarding when there's no token yet, and how the user picks a database.
- Error, offline and syncing states.
- The full colour mapping for the Sage, Blush and Night palettes (Night is a dark theme).
- Defaults for new tasks: today or no date? priority None?
- Task order on Today.
- Notifications or reminders: out of scope for now. The home-screen widget is designed (§4.6) but comes after the main app works.
