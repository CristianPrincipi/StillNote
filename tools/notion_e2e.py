#!/usr/bin/env python3
"""Drives the debug app against tools/fake_notion.py and checks what reaches "Notion".

    python3 tools/notion_e2e.py

It wipes the app's data first, then walks through: using the app with no Notion, a rejected
token, picking a database, syncing both ways, working offline, a refused change and its Retry,
and disconnecting. Screenshots of each state go to screenshots/ as m2-*.png. The emulator
needs the display described in tools/screens.py.
"""
import os
import sys
import time
from datetime import date, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fake_notion  # noqa: E402
from screens import APP, adb, back, compare, launch, nodes, shot, tap, type_text  # noqa: E402

PORT = 8787
TOKEN = "fake-token-for-stillnote"  # 24 characters, like the dots in the Settings mockup
notion = fake_notion.FakeNotion()
today = date.today()


def labels():
    return [label for label, _ in nodes() if label]


def expect(condition, what):
    print(("  ok    " if condition else "  FAIL  ") + what)
    if not condition:
        failures.append(what)


def wait_for(condition, seconds=20):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if condition():
            return True
        time.sleep(0.5)
    return False


def on_screen(text, seconds=10):
    return wait_for(lambda: any(text in label for label in labels()), seconds)


def task(title):
    return next((t for t in notion.tasks() if t["title"] == title), None)


def clear_field():
    adb("shell", "input", "keyevent", "123")  # move to the end
    adb("shell", "input", "keyevent", *["67"] * 60)  # then delete backwards
    time.sleep(0.5)


def enter_token(value):
    tap("Integration token", nth=1)
    clear_field()
    if value:
        type_text(value)
    back()  # hide the keyboard
    tap("Save")


def airplane(on):
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable" if on else "disable")
    time.sleep(3)


failures = []
server = fake_notion.serve(notion, PORT, quiet=True)
notion.add_task("Reply to Marco about Friday", today, "10:30", "High")
notion.add_task("Weekly review in Notion", today, "13:00", "Medium", project="David")
notion.add_task("Read 20 pages", today)
notion.add_task("Water the plants", today, "08:00", "Low", done=True)
notion.add_task("Last month's task", today.replace(day=1) - timedelta(days=3), done=True)

try:
    airplane(False)
    adb("reverse", f"tcp:{PORT}", f"tcp:{PORT}")  # the phone's 127.0.0.1:PORT is this machine's
    adb("shell", "pm", "clear", APP)
    launch("--es", "notion_url", f"http://127.0.0.1:{PORT}")

    print("Without Notion")
    expect(on_screen("All clear for today."), "a fresh install opens on an empty Today")
    shot("m2-01-today-without-notion")
    tap("Add a task")
    type_text("Bought offline")
    tap("Save task")
    expect(on_screen("Bought offline"), "a task can be added with no Notion connected")
    expect(not notion.requests, "and nothing is sent anywhere")

    print("Connecting")
    tap("Settings")
    expect(on_screen("Not connected"), "Settings says it is not connected")
    shot("m2-02-settings-not-connected")
    enter_token("bad-token")
    expect(on_screen("Notion rejected this token"), "a bad token is rejected")
    shot("m2-03-settings-token-rejected")
    enter_token(TOKEN)
    expect(on_screen("Pick a database"), "a good token lists the databases to pick from")
    expect({"Tasks", "Expenses", "Books"} <= set(labels()), "all three databases are listed")
    shot("m2-04-settings-pick-a-database")
    tap("Expenses")
    expect(on_screen("“Expenses” needs a checkbox"), "a database with no checkbox is refused, with the reason")
    shot("m2-05-settings-unusable-database")
    tap("Tasks")
    expect(on_screen("Connected to “Tasks”"), "choosing Tasks connects")
    shot("07-settings")
    print(f"  Settings against its mockup: {compare('07-settings'):.1%} of pixels differ")

    print("Syncing")
    back()
    expect(on_screen("Reply to Marco about Friday"), "Notion's tasks appear on Today")
    expect(on_screen(" · David · !!"), "with the time, a real project and the priority marks")
    expect(not any("Task ·" in label or label.endswith("· Task") for label in labels()), 'and the "Task" project is not printed')
    expect(wait_for(lambda: task("Bought offline") is not None), "the task made without Notion is uploaded")
    uploaded = task("Bought offline") or {}
    expect(uploaded.get("start") == today.isoformat() and uploaded.get("project") == "Task" and uploaded.get("priority") == "None",
           "as a page dated today, with Project Task and Priority None")
    shot("m2-06-today-synced")

    tap("Mark Read 20 pages as done")
    expect(wait_for(lambda: task("Read 20 pages")["done"]), "ticking a task ticks its checkbox in Notion")
    tap("Delete Weekly review in Notion")
    expect(wait_for(lambda: task("Weekly review in Notion")["in_trash"]), "deleting moves the page to the trash")
    tap("Undo")
    expect(wait_for(lambda: not task("Weekly review in Notion")["in_trash"]), "and Undo brings it back")

    tap("Add a task")
    type_text("Dentist")
    tap("Set date and time")
    tap("Next day")
    shown = next(label for label in labels() if len(label) == 5 and label[2] == ":")
    tap("Set priority")
    tap("High")
    tap("Save task")
    expect(wait_for(lambda: task("Dentist") is not None), "a new task becomes a page")
    made = task("Dentist") or {}
    tomorrow = (today + timedelta(days=1)).isoformat()
    expect(str(made.get("start", "")).startswith(f"{tomorrow}T{shown}:00") and made.get("priority") == "High",
           f"dated tomorrow at {shown} with a UTC offset, priority High ({made.get('start')})")

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
    shot("m2-07-today-offline")
    expect(not task("Reply to Marco about Friday")["done"], "the change waits on the phone")
    airplane(False)
    expect(wait_for(lambda: task("Reply to Marco about Friday")["done"], 60), "and reaches Notion once back online")
    expect(wait_for(lambda: not any("· offline" in label for label in labels()), 30), 'then "offline" goes away')

    print("A refused change")
    notion.fail, notion.fail_status = "writes", 400
    tap("Mark Added in Notion as done")
    expect(on_screen("Couldn't save.", 20), 'a change Notion refuses shows "Couldn\'t save." with Retry')
    shot("m2-08-today-could-not-save")
    notion.fail = None
    tap("Retry")
    expect(wait_for(lambda: task("Added in Notion")["done"]), "Retry sends it again")
    expect(wait_for(lambda: not any("Couldn't save" in label for label in labels())), "and the strip goes away")

    print("Notion failing")
    notion.fail, notion.fail_status = "all", 500
    launch()
    expect(on_screen("· couldn't sync", 30), 'when Notion keeps failing the header says "couldn\'t sync"')
    shot("m2-09-today-could-not-sync")
    notion.fail = None

    print("Disconnecting")
    tap("Settings")
    enter_token("")
    expect(on_screen("Not connected"), "saving an empty token disconnects")
    back()
    expect(on_screen("All clear for today."), "and Notion's tasks leave the phone")
    expect(task("Dentist") is not None and not task("Dentist")["in_trash"], "while staying in Notion")
finally:
    airplane(False)
    server.shutdown()

print("\n%d checks failed" % len(failures) if failures else "\nAll checks passed")
sys.exit(1 if failures else 0)
