#!/usr/bin/env python3
"""Screenshots the designed screens on a running emulator and compares each with its mockup.

    python3 tools/screens.py

Settings and the Notion states are covered by tools/notion_e2e.py.

Needs adb, Pillow, and the debug build installed on an emulator set to the mockup's page:

    adb shell wm density 320                # 2x, like the PNGs
    adb shell wm size 780x<1688 + bars>     # 390x844 dp between the system bars

The status and navigation bar heights are read from the device, so each screenshot is cropped to
exactly the 780x1688 page. Output goes to screenshots/ (ignored by git): one PNG per screen and,
in screenshots/compare/, mockup | app | difference side by side.
"""
import os
import re
import subprocess
import sys
import time

from PIL import Image, ImageChops

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOCKUPS = os.path.join(ROOT, "design/screens/png")
OUT = os.path.join(ROOT, "screenshots")
ADB = os.path.join(os.environ.get("ANDROID_HOME", os.path.expanduser("~/Android/Sdk")), "platform-tools/adb")
APP = "app.stillnote"
PAGE = (780, 1688)


def adb(*args, binary=False):
    out = subprocess.run([ADB, *args], capture_output=True, check=True).stdout
    return out if binary else out.decode(errors="replace")


def status_bar():
    frame = re.search(r"type=statusBars frame=\[0,0\]\[\d+,(\d+)\]", adb("shell", "dumpsys", "window"))
    return int(frame.group(1)) if frame else 0


def nodes():
    """(label, bounds) for everything on screen, the label being its content description or text."""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    found = []
    for node in re.findall(r"<node [^>]*>", adb("shell", "cat", "/sdcard/ui.xml")):
        attr = lambda name: (re.search(name + r'="([^"]*)"', node) or [None, ""])[1]
        bounds = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attr("bounds"))
        if bounds:
            found.append((attr("content-desc") or attr("text"), [int(v) for v in bounds.groups()]))
    return found


def tap(label, times=1, nth=0):
    """Taps the middle of the thing labelled [label]; [nth] picks among several with the same label."""
    found = [bounds for name, bounds in nodes() if name == label]
    if len(found) <= nth:
        sys.exit(f"nothing on screen is labelled {label!r}")
    x0, y0, x1, y1 = found[nth]
    for _ in range(times):
        adb("shell", "input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2))
        time.sleep(0.3)
    time.sleep(0.6)


def launch(*extras):
    """Restarts the app. Debug builds accept `--ez demo true` and `--es notion_url <url>`."""
    adb("shell", "am", "force-stop", APP)
    adb("shell", "am", "start", "-n", APP + "/.MainActivity", *extras)
    time.sleep(4)


def wait_for_label(label, seconds=20):
    """Waits until something labelled [label] is on screen; a first launch after an install is slow."""
    deadline = time.time() + seconds
    while time.time() < deadline:
        if any(name == label for name, _ in nodes()):
            time.sleep(1)  # let the list finish fading in
            return
        time.sleep(0.5)
    sys.exit(f"{label!r} never appeared")


def type_text(value):
    adb("shell", "input", "text", value.replace(" ", "%s"))
    time.sleep(0.6)


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.8)


def shot(name):
    time.sleep(0.8)
    top = status_bar()
    raw = os.path.join(OUT, name + ".raw.png")
    with open(raw, "wb") as file:
        file.write(adb("exec-out", "screencap", "-p", binary=True))
    Image.open(raw).convert("RGB").crop((0, top, PAGE[0], top + PAGE[1])).save(os.path.join(OUT, name + ".png"))
    os.remove(raw)


def compare(name):
    """Saves mockup | app | difference and returns the share of pixels that clearly differ."""
    mockup = Image.open(os.path.join(MOCKUPS, name + ".png")).convert("RGB")
    app = Image.open(os.path.join(OUT, name + ".png")).convert("RGB")
    difference = ImageChops.difference(mockup, app).convert("L")
    sheet = Image.new("RGB", (PAGE[0] * 3 + 20, PAGE[1]), "white")
    sheet.paste(mockup, (0, 0))
    sheet.paste(app, (PAGE[0] + 10, 0))
    sheet.paste(difference.point(lambda v: 255 - min(255, v * 3)).convert("RGB"), (PAGE[0] * 2 + 20, 0))
    sheet.save(os.path.join(OUT, "compare", name + ".png"))
    return sum(difference.point(lambda v: 255 if v > 40 else 0).histogram()[255:]) / (PAGE[0] * PAGE[1])


def main():
    os.makedirs(os.path.join(OUT, "compare"), exist_ok=True)
    # Demo mode shows the mockups' sample tasks on their fixed "today", whatever is on the phone.
    launch("--ez", "demo", "true")
    wait_for_label("Mark Reply to Marco about Friday as done")

    shot("01-today")

    tap("Add a task")
    time.sleep(1)
    shot("02-new-task-keyboard")
    back()  # hides the keyboard, which the mockup doesn't draw
    shot("02-new-task")

    type_text("Buy a birthday card for Giulia")
    tap("Set date and time")
    tap("Next day")
    shown = next(label for label, _ in nodes() if re.fullmatch(r"\d\d:\d\d", label))
    minutes = int(shown[:2]) * 60 + int(shown[3:])
    tap("30 minutes later", times=(18 * 60 - minutes) // 30 % 48)
    shot("03-new-task-date-time")

    tap("Set priority")
    tap("High")
    shot("04-new-task-priority")
    back()

    tap("CALENDAR")
    shot("05-calendar")
    tap("PROJECTS")
    shot("06-projects")  # a blank page for now, so there is nothing to compare it with
    tap("TASKS")

    # Settings is compared by tools/notion_e2e.py, which can show it connected like the mockup.
    for name in ("01-today", "02-new-task", "03-new-task-date-time", "04-new-task-priority", "05-calendar"):
        print(f"{name}: {compare(name):.1%} of pixels differ")


if __name__ == "__main__":
    main()
