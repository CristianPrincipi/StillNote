# Notion database setup

StillNote reads one database shape: **one page per task.** This page describes what the app needs,
how it finds the right properties, what it reads and writes, and an example database, so you can
create your own or adapt one you already have.

## What the app needs

| Field | Type | Required | What StillNote does with it |
| --- | --- | --- | --- |
| Task name | **Title** | Yes | The words on the post-it. |
| Done | **Checkbox** | Yes | Ticked when you tick the task off. |
| Date | **Date** | Yes | The task's day, and its time if it has one. |
| Priority | **Select** | No | The post-it's colour and its `!` marks. |
| Project | **Select** | No | The project the task belongs to, one card per option on *Projects*. |
| Anything else | Any type | No | Ignored and left exactly as it is. |

Property **names are yours to choose**. During setup the app shows which property it picked for each
field, and you can change any of them. That choice is stored by Notion's property ID, so renaming a
column in Notion later does not break sync.

A database without *Priority* hides the priority flag in the note. One without *Project* hides the
project button, and *Projects* says "This database has no project field."

### How the app guesses

First by name, ignoring case, in English or Italian:

| Field | Names it recognises |
| --- | --- |
| Task name | `Name`, `Title`, `Task`, `Nome`, `Titolo`, `Attività` |
| Done | `Done`, `Complete`, `Completed`, `Fatto`, `Completato`, or a checkbox with a blank name |
| Date | `Due`, `Date`, `Due date`, `When`, `Data`, `Scadenza`, `Quando` |
| Priority | `Priority`, `Priorità`, `Priorita` |
| Project | `Project`, `Progetto` |

Then, for a field still missing, by type: the three required fields take a property of their type,
and *Priority* takes a select whose options look like priorities. *Project* is only guessed by name.
A property is never used for two fields. When nothing fits a required field, setup says
"Not found · choose" next to it.

In the list of databases, setup starts on one named `Tasks`, `Task`, `To-do`, `To do`, `Todo`,
`Attività` or `Compiti`, if there is one.

### Priority options

The priority select's options are matched by name, ignoring case:

| Priority | Options it recognises | Post-it |
| --- | --- | --- |
| High | `High`, `Alta` | apricot, `!!!` |
| Medium | `Medium`, `Media` | canary, `!!` |
| Low | `Low`, `Bassa` | sky, `!` |
| None | `None`, `Nessuna`, or empty | white card |

A task whose option the app doesn't recognise reads as *None*. Setting *None* in the app writes the
`None` option if the database has one, and clears the select if it doesn't.

### Projects

Each option of the project select is a project. **An option named `Task` means "no project"**: it is
never printed on a post-it, it has no card on *Projects*, and a task saved with no project gets it.
Without a `Task` option, a task with no project has the select left empty.

A project made in the app stays on the phone until its first task is saved; Notion then adds it to
the select's options. StillNote never changes the database's structure otherwise.

## What StillNote reads and writes

- **A new task** creates a page with the name, the date (and time), done, the priority and the
  project.
- **Ticking a task off**, or back on, sends only the checkbox, so it can't undo a change made in
  Notion meanwhile.
- **Editing a task** sends every field above.
- **Deleting a task** moves its page to Notion's trash.
- **Pulling** asks for the pages dated in the month on screen, and for every page that has a
  project. A page with no date and no project shows on no screen, so the app only lets a task lose
  its date when it has a project.

Times are written with the phone's UTC offset and shown in the phone's time zone. Only the start of
a date is read; an edit in the app writes the date without an end.

Reminders stay on the phone, since Notion has no field for them.

**Disconnecting** (saving an empty token in *Settings*) removes Notion's tasks from the phone; they
stay in Notion. Tasks written in the app before you first connect are copied into the database when
you do.

## The example database

This is the shape StillNote was developed against. Recreate it as shown, or use it as a starting
point.

| Property | Type | Role in the app |
| --- | --- | --- |
| `Name` | Title | Task name |
| ` ` (a single space) | Checkbox | Done. A blank name makes the column a plain checkbox. |
| `Date` | Date | The day, with a 24-hour time when the task has one |
| `Priority` | Select | Priority |
| `Project` | Select | Project |

`Priority` options: `High` · `Medium` · `Low` · `None`

`Project` options: `Task` (no project) · one per project, for example `Work` · `Personal` ·
`Errands`

## Creating it

1. In Notion, create a new database (a table view is the easiest to work with).
2. Keep the **Title** property for the task name.
3. Add a **Checkbox** property for done, and a **Date** property for the day.
4. Optionally add a **Select** property `Priority` with the options above, and a **Select**
   property `Project` with a `Task` option and one option per project.

Then share the database with your Notion connection and connect the app, as described in the
[README](../README.md#connect-notion).
