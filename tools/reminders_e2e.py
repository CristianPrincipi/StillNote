#!/usr/bin/env python3
"""Checks the debug app's reminders on the emulator, moving its clock instead of waiting.

    python3 tools/reminders_e2e.py

It wipes the app's data and uses the app without Notion. While it runs, the emulator's automatic
time is off and its clock jumps around today and tomorrow; both are put back at the end. It
covers the permission prompt after the first task that will remind, a reminder at the default 10
minutes, Later bringing it back at the task's time, Done from the notification, a task's own
reminder and Edit, and the morning list, and which alarm brings each: an alarm clock within 70
minutes, otherwise a wake-up 70 minutes ahead. Screenshots go to screenshots/ as m4-*.png.
"""
import os
import re
import sys
import time
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from screens import APP, adb, back, launch, nodes, shot, tap, type_text  # noqa: E402

failures = []


def labels():
    return [label for label, _ in nodes() if label]


def expect(condition, what):
    print(("  ok    " if condition else "  FAIL  ") + what)
    if not condition:
        failures.append(what)
        print("        on screen: " + " | ".join(labels()))
        print("        notifications: " + repr(notifications()))


def wait_for(condition, seconds=20):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if condition():
            return True
        time.sleep(0.5)
    return False


def on_screen(text, seconds=10):
    return wait_for(lambda: any(text in label for label in labels()), seconds)


ZONE = ZoneInfo(adb("shell", "getprop", "persist.sys.timezone").strip() or "UTC")
TODAY = datetime.now(ZONE).date()


def set_clock(day, hhmm, seconds=0):
    """Moves the emulator's clock, which sends the TIME_SET broadcast an app hears after a clock change."""
    when = datetime(day.year, day.month, day.day, int(hhmm[:2]), int(hhmm[3:]), seconds, tzinfo=ZONE)
    adb("shell", "cmd", "alarm", "set-time", str(int(when.timestamp() * 1000)))
    time.sleep(1)


def notifications():
    """(title, text) of each of the app's notifications on show."""
    found = []
    for record in adb("shell", "dumpsys", "notification", "--noredact").split("NotificationRecord(")[1:]:
        if f"pkg={APP} " not in record.split("\n", 1)[0]:
            continue
        title = re.search(r"android\.title=\w+ \((.*)\)", record)
        text = re.search(r"android\.text=\w+ \((.*)\)", record)
        found.append((title.group(1) if title else "", text.group(1) if text else ""))
    return found


def alarm():
    """("clock" or "wake-up", "HH:MM") for the app's reminder alarm, or None if it has none."""
    for entry in re.split(r"\n    (?=\w+ #\d+: Alarm\{)", adb("shell", "dumpsys", "alarm")):
        if f" {APP}}}" in entry.split("\n", 1)[0] and "REMINDER_ALARM" in entry:
            when = re.search(r"origWhen=\d{4}-\d\d-\d\d (\d\d:\d\d)", entry)
            return "clock" if "Alarm clock:" in entry else "wake-up", when.group(1) if when else ""
    return None


def notification(title, seconds=40):
    """The notification whose title is [title], once it shows, or None."""
    shown = []
    wait_for(lambda: shown.extend(n for n in notifications() if n[0] == title) or shown, seconds)
    return shown[0] if shown else None


def in_shade(label, name=None):
    """Pulls the notification shade down and taps [label] there; [name] saves a screenshot first."""
    adb("shell", "cmd", "statusbar", "expand-notifications")
    time.sleep(2)
    if name:
        shot(name)
    found = [bounds for text, bounds in nodes() if text == label]
    if found:
        x0, y0, x1, y1 = found[0]
        adb("shell", "input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2))
        time.sleep(2)
    else:
        print(f"        nothing in the shade is labelled {label!r}")
    adb("shell", "cmd", "statusbar", "collapse")
    time.sleep(1)
    return bool(found)


def add_task(title, *steps):
    tap("Add a task")
    type_text(title)
    for step in steps:
        tap(step)


try:
    adb("shell", "settings", "put", "global", "auto_time", "0")
    adb("shell", "pm", "clear", APP)
    set_clock(TODAY, "09:40")
    launch()
    tap("Use without Notion")
    tap("Start writing")

    print("A reminder at the default time")
    add_task("Call the plumber", "Set date and time", "30 minutes later")
    expect("10 min before" in labels() and "DEFAULT REMINDER" in labels(), "a task with a time shows the default reminder in its clock")
    shot("m4-note-reminder")
    tap("Save task")
    expect(on_screen("Allow"), "saving the first task that will remind asks to allow notifications")
    tap("Allow")
    expect(alarm() == ("clock", "09:50"), f"10 minutes off, the reminder gets an alarm clock ({alarm()})")
    set_clock(TODAY, "09:49", 50)
    shown = notification("Almost time: Call the plumber")
    expect(shown is not None and shown[1] == "10:00 · in 10 minutes", f"10 minutes before, it reminds ({shown})")
    expect(in_shade("Later", name="m4-reminder"), "the reminder has Later")
    expect(wait_for(lambda: not notifications()), "which takes it down")
    set_clock(TODAY, "09:59", 50)
    shown = notification("It's time: Call the plumber")
    expect(shown is not None and shown[1] == "10:00", f"and brings it back at the task's time ({shown})")
    expect(in_shade("Done"), "the reminder has Done")
    expect(wait_for(lambda: not notifications()), "which takes it down")
    launch()
    expect(on_screen("Mark Call the plumber as not done"), "and ticks the task off")

    print("A task's own reminder, and Edit")
    add_task("Water the basil", "Set date and time", "30 minutes later")
    tap("Next reminder choice", times=2)  # the default, no reminder, then 5 minutes
    expect("5 min before" in labels() and "REMINDER" in labels(), "a task can have its own reminder")
    expect(any(label.endswith("10:30 · 5 min before") for label in labels()), "and its chip says so")
    tap("Save task")
    set_clock(TODAY, "10:24", 50)
    shown = notification("Almost time: Water the basil")
    expect(shown is not None and shown[1] == "10:30 · in 5 minutes", f"5 minutes before, it reminds ({shown})")
    expect(in_shade("Edit"), "the reminder has Edit")
    expect(on_screen("EDIT TASK") and on_screen("Water the basil"), "which opens the task in the note")
    expect(wait_for(lambda: not notifications()), "and takes the reminder down")
    back()

    print("The morning list")
    add_task("Buy stamps", "Set date and time", "Next day", "Save task")
    expect(alarm() == ("wake-up", "06:50"), f"tomorrow's, far off, gets a wake-up 70 minutes ahead ({alarm()})")
    set_clock(TODAY + timedelta(days=1), "06:49", 30)
    expect(alarm() == ("wake-up", "06:50"), f"still a wake-up 70 minutes and 30 seconds before ({alarm()})")
    expect(wait_for(lambda: alarm() == ("clock", "08:00"), 60), f"which sets the alarm clock when it goes off ({alarm()})")
    set_clock(TODAY + timedelta(days=1), "07:59", 50)
    shown = notification("Today: 1 task at any time")
    expect(shown is not None and shown[1] == "Buy stamps", f"at 08:00 the next day it lists the untimed task ({shown})")
    expect(in_shade("Today: 1 task at any time", name="m4-morning-list"), "it can be tapped")
    expect(on_screen("Mark Buy stamps as done"), "which opens Today")

    print("Settings")
    tap("Settings")
    adb("shell", "input", "swipe", "390", "1500", "390", "300", "400")
    time.sleep(1)
    expect(on_screen("Morning list") and on_screen("10 min"), "Settings has the reminder rules")
    shot("m4-settings-reminders")
    back()
finally:
    adb("shell", "cmd", "statusbar", "collapse")
    adb("shell", "cmd", "alarm", "set-time", str(int(time.time() * 1000)))
    adb("shell", "settings", "put", "global", "auto_time", "1")

print("\n%d checks failed" % len(failures) if failures else "\nAll checks passed")
sys.exit(1 if failures else 0)
