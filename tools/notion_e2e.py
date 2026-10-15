#!/usr/bin/env python3
"""Drives the debug app against tools/fake_notion.py and checks what reaches "Notion".

    python3 tools/notion_e2e.py

It wipes the app's data first, then walks through: the first-run flow against the mockups'
five databases, then a fresh install used without Notion, connected from Settings, synced both
ways (tasks and projects, by pulling down, and in the background with the app closed), tasks
changed from the app and from the widget, used offline, a refused change and its Retry, and
disconnected.
Screenshots go to screenshots/: the designed screens under their mockup's name, the rest as
m2-*.png. The emulator needs the display described in tools/screens.py.
"""
import os
import re
import sys
import time
from datetime import date, datetime, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fake_notion  # noqa: E402
from screens import APP, OUT, adb, back, close_sheet, compare, home, launch, nodes, place_widget, shot, tap, tap_task, type_text  # noqa: E402

PORT = 8787
TOKEN = "fake-token-for-stillnote"  # 24 characters, like the dots in the Settings mockup
URL = ("--es", "notion_url", f"http://127.0.0.1:{PORT}")
today = date.today()
failures = []


def labels():
    return [label for label, _ in nodes() if label]


def expect(condition, what):
    print(("  ok    " if condition else "  FAIL  ") + what)
    if not condition:
        failures.append(what)
        print("        on screen: " + " | ".join(labels()))


def wait_for(condition, seconds=20):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if condition():
            return True
        time.sleep(0.5)
    return False


def on_screen(text, seconds=10):
    return wait_for(lambda: any(text in label for label in labels()), seconds)


def clear_field():
    adb("shell", "input", "keyevent", "123")  # move to the end
    adb("shell", "input", "keyevent", *["67"] * 60)  # then delete backwards
    time.sleep(0.5)


def pull_down():
    """Drags the page down from below its header, slowly enough not to fling."""
    adb("shell", "input", "swipe", "390", "700", "390", "1300", "500")


def run_scheduled_work():
    """Runs the app's WorkManager jobs now, as if their time had come. Returns how many there were."""
    dump = adb("shell", "dumpsys", "jobscheduler")
    # Lines such as "androidx.work.systemjobscheduler:u0a190/3 #...#@androidx.work.systemjobscheduler@app.stillnote.debug/androidx.work..."
    jobs = set(re.findall(r"([\w.]+):u\d+a\d+/(\d+) #\S*@" + re.escape(APP) + r"/androidx\.work", dump))
    for namespace, job in sorted(jobs):
        adb("shell", "cmd", "jobscheduler", "run", "-f", "-n", namespace, APP, job)
    return len(jobs)


def airplane(on):
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable" if on else "disable")
    time.sleep(3)


def fresh_install():
    adb("shell", "pm", "clear", APP)
    launch(*URL)


def compared(name):
    shot(name)
    print(f"  {name} against its mockup: {compare(name):.1%} of pixels differ")


def first_run_against_the_mockups():
    """Screens 01 to 04 and Settings, with the databases the design shows."""
    notion = fake_notion.FakeNotion(mockup=True)
    server = fake_notion.serve(notion, PORT, quiet=True)
    try:
        print("First run")
        fresh_install()
        expect(on_screen("Your tasks, on little notes."), "a fresh install opens on Welcome")
        compared("01-welcome")
        tap("Connect Notion")
        expect(on_screen("Get your secret"), "Connect Notion leads to step 1")
        compared("02-setup-connect-notion")
        tap("Paste your secret", nth=1)
        type_text("bad-secret")
        back()
        tap("Connect")
        expect(on_screen("Notion didn't accept this secret"), "a bad secret is refused under the field")
        shot("m2-setup-secret-refused")
        tap("Paste your secret", nth=1)
        clear_field()
        type_text(TOKEN)
        back()
        tap("Connect")
        expect(on_screen("Pick your tasks"), "a good secret leads to step 2")
        expect(on_screen("5 of 5 found"), "Tasks is preselected with all five fields matched")
        expect({"in Home", "in Books", "in Work"} <= set(labels()), "each database shows the page it sits in")
        compared("03-setup-pick-database")
        tap("Meeting notes")
        expect(on_screen("Not found · choose"), "a database without a checkbox shows the field as not found")
        expect(on_screen("3 of 5 found"), "and counts what it did find")
        shot("m2-setup-fields-missing")
        tap("Tasks")
        on_screen("5 of 5 found")
        tap("Priority: Priority, change")
        expect(on_screen("Don't use"), "tapping a field lists the properties it could use")
        shot("m2-setup-field-picker")
        tap("Don't use")
        expect(on_screen("4 of 5 found"), "an optional field can be left unused")
        tap("Priority: not found, choose a property")
        tap("Priority")
        on_screen("5 of 5 found")
        tap("Next")
        expect(on_screen("Choose a look"), "Next leads to the last step")
        compared("04-setup-choose-look")
        tap("Typewriter")
        shot("m2-setup-look-typewriter")
        tap("Handwritten")
        tap("Start writing")
        expect(on_screen("All clear for today."), "Start writing opens Today")
        launch()
        expect(on_screen("All clear for today."), "and setup is not shown again")
        tap("Settings")
        expect(on_screen("Connected to “Tasks”"), "Settings shows the connection")
        compared("14-settings")
    finally:
        server.shutdown()
        server.server_close()


def without_notion_then_connected():
    notion = fake_notion.FakeNotion()
    server = fake_notion.serve(notion, PORT, quiet=True)
    notion.add_task("Reply to Marco about Friday", today, "10:30", "High")
    notion.add_task("Weekly review in Notion", today, "13:00", "Medium", project="David")
    notion.add_task("Read 20 pages", today)
    notion.add_task("Water the plants", today, "08:00", "Low", done=True)
    notion.add_task("Sketch the logo", None, None, "High", project="David")
    notion.add_task("Old David task", today - timedelta(days=200), done=True, project="David")
    notion.add_task("Last month's task", today.replace(day=1) - timedelta(days=3), done=True)

    def task(title):
        return next((t for t in notion.tasks() if t["title"] == title), None)

    try:
        print("Without Notion")
        fresh_install()
        tap("Use without Notion")
        expect(on_screen("Choose a look"), "Use without Notion skips to the last step")
        tap("Start writing")
        expect(on_screen("All clear for today."), "and then to an empty Today")
        tap("Add a task")
        type_text("Bought offline")
        tap("Save task")
        expect(on_screen("Bought offline"), "a task can be added with no Notion connected")
        tap("PROJECTS")
        expect(on_screen("No projects yet."), "Projects starts empty")
        tap("Add a task or project")
        tap("New project")
        type_text("Balcony")
        expect(on_screen("It gets its own card on this page."), "the new-project card doesn't mention Notion")
        tap("Create project")
        expect(on_screen("Nothing left here."), "a new project gets an empty card")
        tap("Add a task or project")
        tap("New task")
        expect("Hide project" in labels(), "New task from Projects opens the note on the project picker")
        tap("What needs doing?")
        type_text("Water the tomatoes")
        tap("Save task")
        expect(on_screen("Mark Water the tomatoes as done"), "and the task lands on the Balcony card")
        expect(on_screen("1 open · 0 done"), "which counts it")
        pull_down()
        time.sleep(1)
        expect(not notion.requests, "nothing was sent anywhere, even after pulling down")
        tap("TASKS")

        print("Connecting from Settings")
        tap("Settings")
        expect(on_screen("Not connected"), "Settings says it is not connected")
        shot("m2-settings-not-connected")
        tap("Connect")
        expect(on_screen("STEP 1 OF 2"), "Connect opens the two Notion steps")
        tap("Paste your secret", nth=1)
        type_text(TOKEN)
        back()
        tap("Connect")
        expect(on_screen("5 of 5 found"), "the user's Tasks database matches all five fields, blank-named checkbox included")
        expect(on_screen("(no name)"), "the blank-named checkbox is shown as (no name)")
        tap("Expenses")
        expect(on_screen("Not found · choose"), "a database with no checkbox can't be used")
        tap("Tasks")
        on_screen("5 of 5 found")
        tap("Save")
        expect(on_screen("Connected to “Tasks”"), "Save returns to Settings, connected")
        back()

        print("Syncing")
        expect(on_screen("Reply to Marco about Friday"), "Notion's tasks appear on Today")
        expect(on_screen(" · David · !!"), "with the time, a real project and the priority marks")
        expect(not any("Task ·" in label or label.endswith("· Task") for label in labels()), 'and the "Task" project is not printed')
        expect(wait_for(lambda: task("Bought offline") is not None), "the task made without Notion is uploaded")
        uploaded = task("Bought offline") or {}
        expect(uploaded.get("start") == today.isoformat() and uploaded.get("project") == "Task" and uploaded.get("priority") == "None",
               "as a page dated today, with Project Task and Priority None")
        expect(wait_for(lambda: (task("Water the tomatoes") or {}).get("project") == "Balcony"),
               "the task in the phone-made project is uploaded with Project Balcony")
        shot("m2-today-synced")

        tap("Mark Read 20 pages as done")
        expect(wait_for(lambda: task("Read 20 pages")["done"]), "ticking a task ticks its checkbox in Notion")
        tap("Delete Weekly review in Notion")
        expect(wait_for(lambda: task("Weekly review in Notion")["in_trash"]), "deleting moves the page to the trash")
        tap("Undo")
        expect(wait_for(lambda: not task("Weekly review in Notion")["in_trash"]), "and Undo brings it back")
        notion.add_task("Pulled from Notion", today, "20:00")
        pull_down()
        expect(on_screen("Pulled from Notion"), "pulling Today down brings in a task just added in Notion")

        print("Changing a task")
        tap_task("Pulled from Notion")
        expect(on_screen("EDIT TASK"), "tapping a post-it opens it in the note")
        tap("Pulled from Notion")
        clear_field()
        type_text("Pulled and renamed")
        back()  # hides the keyboard
        tap("Set priority")
        tap("High")
        tap("Set project")
        tap("Steady")  # a project whose card nothing below counts
        shot("m3-edit-task-synced")
        tap("Save changes")
        # The first saved task with a time asks to allow notifications. Refused, so no reminder
        # pops up over the screens for the rest of the run; tools/reminders_e2e.py allows them.
        expect(on_screen("send you notifications"), "saving the first task with a time asks to allow notifications")
        tap("Don’t allow")
        expect(on_screen("Pulled and renamed"), "the post-it shows the new title")
        expect(wait_for(lambda: (task("Pulled and renamed") or {}).get("priority") == "High"), "the change reaches Notion")
        changed = task("Pulled and renamed") or {}
        # The app writes the time in the phone's zone, so compare moments rather than text.
        start = changed.get("start") or ""
        kept = "T" in start and datetime.fromisoformat(start) == datetime.fromisoformat(f"{today.isoformat()}T20:00:00+02:00")
        expect(changed.get("project") == "Steady" and kept and task("Pulled from Notion") is None,
               f"on the same page, with Project Steady and the time kept ({changed})")

        print("With the app closed")
        place_widget()
        adb("shell", "am", "kill", APP)
        # High and early, so it is on the widget's first row.
        notion.add_task("Added while away", today, "07:00", "High")
        asked = len(notion.requests)
        jobs = run_scheduled_work()
        expect(wait_for(lambda: any(path.endswith("/query") for _, path, _ in notion.requests[asked:]), 30),
               f"the background sync asks Notion while the app is closed ({jobs} jobs run)")
        expect(wait_for(lambda: "Mark Added while away as done" in labels(), 20), "and the widget on the home screen shows the task")
        time.sleep(2)
        # With Notion refusing everything, only the background sync can have brought the task in.
        notion.fail, notion.fail_status = "all", 400
        launch()
        expect(on_screen("Added while away"), "and the task it brought in is on Today when the app opens")
        notion.fail = None
        home()
        tap("Edit Added while away")
        expect(on_screen("EDIT TASK") and on_screen("Added while away"), "tapping a row on the widget opens its task in the note")
        back()
        with notion.lock:
            notion.pages[task("Added while away")["id"]]["in_trash"] = True
        pull_down()
        expect(wait_for(lambda: not any("Added while away" in label for label in labels()), 20),
               "a page trashed in Notion leaves the phone on the next pull")
        expect(wait_for(lambda: not any("couldn't sync" in label for label in labels()), 20), "and the header is clear again")

        print("Projects")
        tap("PROJECTS")
        expect(on_screen("Mark Sketch the logo as done"), "a project's undated task from Notion shows on its card")
        expect(on_screen("2 open · 1 done"), "David counts its open tasks and its done one from months ago")
        expect({"Arch", "Steady", "Balcony"} <= set(labels()), "empty Notion projects and the phone-made one have cards too")
        shot("m2-projects-synced")
        tap("Mark Sketch the logo as done")
        expect(wait_for(lambda: task("Sketch the logo")["done"]), "ticking on a card ticks the page in Notion")
        expect(on_screen("1 open · 2 done"), "and moves it to the card's done count")
        tap("Add a task or project")
        tap("New project")
        type_text("Garden")
        back()
        # With Notion the card has the mockup's helper line, but the page behind it holds other
        # projects. Saved under its own name: 13-new-project.png is the demo one, from screens.py.
        shot("m2-new-project-synced")
        print(f"  the new project card against its mockup: {compare('m2-new-project-synced', mockup='13-new-project'):.1%} of pixels differ")
        tap("Create project")
        tap("Add a task or project")
        tap("New task")
        tap("Arch")
        tap("What needs doing?")
        type_text("Update the mirrors")
        tap("Set priority")
        tap("High")
        tap("Save task")
        expect(wait_for(lambda: task("Update the mirrors") is not None), "a task made from Projects becomes a page")
        made = task("Update the mirrors") or {}
        expect(made.get("project") == "Arch" and made.get("priority") == "High" and made.get("start") == today.isoformat(),
               f"dated today with Project Arch and priority High ({made})")
        tap("TASKS")

        tomorrow = (today + timedelta(days=1)).isoformat()
        tap("Add a task")
        type_text("Dentist")
        tap("Set date and time")
        tap("Next day")
        tap("30 minutes later")  # from "Any time" to the next half hour
        shown = next(label for label in labels() if len(label) == 5 and label[2] == ":")
        tap("Save task")
        expect(wait_for(lambda: task("Dentist") is not None), "a new task becomes a page")
        made = task("Dentist") or {}
        expect(str(made.get("start", "")).startswith(f"{tomorrow}T{shown}:00") and made.get("project") == "Task",
               f"dated tomorrow at {shown} with a UTC offset, Project Task ({made.get('start')})")
        tap("Add a task")
        type_text("Haircut")
        tap("Set date and time")
        tap("Next day")
        tap("Save task")
        expect(wait_for(lambda: task("Haircut") is not None), "a task for tomorrow at any time becomes a page")
        expect((task("Haircut") or {}).get("start") == tomorrow, f"with a plain date ({(task('Haircut') or {}).get('start')})")

        notion.add_task("Added in Notion", today, "21:00", "Low", project="Arch")
        launch()
        expect(on_screen("Added in Notion"), "a task added in Notion shows up when the app is opened")
        tap("CALENDAR")
        tap("Previous month")
        earlier = today.replace(day=1) - timedelta(days=3)
        expect(wait_for(lambda: any(start <= earlier.isoformat() <= end for start, end in notion.queried if start and end)),
               "paging back asks Notion for that month")
        tap(f"{earlier.day} {earlier.strftime('%B')}, 1 task")
        expect(on_screen("Last month's task"), "and shows last month's task")
        tap("TASKS")

        print("Offline")
        airplane(True)
        expect(on_screen("· offline", 15), 'the header says "offline"')
        tap("Mark Reply to Marco about Friday as done")
        expect(on_screen("Mark Reply to Marco about Friday as not done"), "a task can still be ticked")
        shot("m2-today-offline")
        expect(not task("Reply to Marco about Friday")["done"], "the change waits on the phone")
        airplane(False)
        expect(wait_for(lambda: task("Reply to Marco about Friday")["done"], 60), "and reaches Notion once back online")
        expect(wait_for(lambda: not any("· offline" in label for label in labels()), 30), 'then "offline" goes away')

        print("A refused change")
        notion.fail, notion.fail_status = "writes", 400
        tap("Mark Added in Notion as done")
        expect(on_screen("Couldn't save.", 20), 'a change Notion refuses shows "Couldn\'t save." with Retry')
        shot("m2-today-could-not-save")
        notion.fail = None
        tap("Retry")
        expect(wait_for(lambda: task("Added in Notion")["done"]), "Retry sends it again")
        expect(wait_for(lambda: not any("Couldn't save" in label for label in labels())), "and the strip goes away")

        print("Notion failing")
        notion.fail, notion.fail_status = "all", 500
        launch()
        expect(on_screen("· couldn't sync", 30), 'when Notion keeps failing the header says "couldn\'t sync"')
        shot("m2-today-could-not-sync")
        notion.fail = None

        print("Disconnecting")
        tap("Settings")
        tap("Integration token", nth=1)
        clear_field()
        back()
        tap("Save")
        expect(on_screen("Not connected"), "saving an empty token disconnects")
        back()
        expect(on_screen("All clear for today."), "and Notion's tasks leave the phone")
        expect(task("Dentist") is not None and not task("Dentist")["in_trash"], "while staying in Notion")
    finally:
        server.shutdown()
        server.server_close()


try:
    airplane(False)
    adb("reverse", f"tcp:{PORT}", f"tcp:{PORT}")  # the phone's 127.0.0.1:PORT is this machine's
    os.makedirs(os.path.join(OUT, "compare"), exist_ok=True)
    first_run_against_the_mockups()
    without_notion_then_connected()
finally:
    airplane(False)

print("\n%d checks failed" % len(failures) if failures else "\nAll checks passed")
sys.exit(1 if failures else 0)
