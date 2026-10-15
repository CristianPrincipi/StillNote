#!/usr/bin/env python3
"""Screenshots the designed screens on a running emulator and compares each with its mockup.

    python3 tools/screens.py

The first-run flow, Settings and the Notion states are covered by tools/notion_e2e.py. The widget's
two mockups (15, 16) are drawn by the app itself at the mockups' own size and compared; then the
real widget is put on the home screen and saved as m3-widget-home.png.

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
APP = "app.stillnote.debug"  # the debug build's id; its classes stay in app.stillnote
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


def tap_task(title):
    """Taps a task's title, on its post-it or row, which opens the task in the note."""
    found = [bounds for name, bounds in nodes() if name.startswith(title)]
    if not found:
        sys.exit(f"no task {title!r} on screen")
    x0, y0, x1, y1 = found[0]
    adb("shell", "input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2))
    time.sleep(1.2)


def close_sheet():
    """Taps the scrim above a note or card, which closes it without saving."""
    adb("shell", "input", "tap", "40", str(status_bar() + 60))
    time.sleep(0.8)


def launch(*extras):
    """Restarts the app. Debug builds accept `--ez demo true` and `--es notion_url <url>`."""
    adb("shell", "am", "force-stop", APP)
    adb("shell", "am", "start", "-n", APP + "/app.stillnote.MainActivity", *extras)
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


def home():
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(1.5)


def place_widget(*extras):
    """Puts the widget on the home screen unless one is there already, and leaves the home screen showing.

    [extras] go to the app's launch, which asks the launcher to pin the widget.
    """
    home()
    if any(label.startswith("Today, ") for label, _ in nodes()):
        return
    launch(*extras, "--ez", "pin_widget", "true")
    wait_for_label("Add to home screen")
    tap("Add to home screen")
    home()


def pull(path, name):
    """Copies [path] out of the app's private files to screenshots/[name]."""
    with open(os.path.join(OUT, name), "wb") as file:
        file.write(adb("exec-out", "run-as", APP, "cat", path, binary=True))


def shot(name):
    time.sleep(0.8)
    top = status_bar()
    raw = os.path.join(OUT, name + ".raw.png")
    with open(raw, "wb") as file:
        file.write(adb("exec-out", "screencap", "-p", binary=True))
    Image.open(raw).convert("RGB").crop((0, top, PAGE[0], top + PAGE[1])).save(os.path.join(OUT, name + ".png"))
    os.remove(raw)


def compare(name, mockup=None):
    """Saves mockup | app | difference and returns the share of pixels that clearly differ.

    [mockup] names the mockup when the screenshot isn't saved under the mockup's own name.
    """
    mockup = Image.open(os.path.join(MOCKUPS, (mockup or name) + ".png")).convert("RGB")
    app = Image.open(os.path.join(OUT, name + ".png")).convert("RGB")
    width, height = mockup.size
    difference = ImageChops.difference(mockup, app).convert("L")
    sheet = Image.new("RGB", (width * 3 + 20, height), "white")
    sheet.paste(mockup, (0, 0))
    sheet.paste(app, (width + 10, 0))
    sheet.paste(difference.point(lambda v: 255 - min(255, v * 3)).convert("RGB"), (width * 2 + 20, 0))
    sheet.save(os.path.join(OUT, "compare", name + ".png"))
    return sum(difference.point(lambda v: 255 if v > 40 else 0).histogram()[255:]) / (width * height)


def widgets():
    """The widget's two mockup frames as the app draws them, then the real widget on the home screen."""
    launch("--ez", "demo", "true", "--ez", "render_widgets", "true")
    for name in ("15-widget-2x2", "16-widget-4x2"):
        pull(f"files/widgets/{name}.png", name + ".png")
        print(f"{name}: {compare(name):.1%} of pixels differ")
    place_widget("--ez", "demo", "true")
    wait_for_label("Today, 4 to do")
    with open(os.path.join(OUT, "m3-widget-home.png"), "wb") as file:
        file.write(adb("exec-out", "screencap", "-p", binary=True))


def main():
    os.makedirs(os.path.join(OUT, "compare"), exist_ok=True)
    # Demo mode shows the mockups' sample tasks on their fixed "today", whatever is on the phone,
    # and skips the first-run flow.
    launch("--ez", "demo", "true")
    wait_for_label("Mark Reply to Marco about Friday as done")

    shot("05-today")

    tap("Add a task")
    time.sleep(1)
    shot("06-new-task-keyboard")
    back()  # hides the keyboard, which the mockup doesn't draw
    shot("06-new-task")

    type_text("Buy a birthday card for Giulia")
    tap("Set date and time")
    tap("Next day")
    shot("m3-new-task-any-time")
    tap("30 minutes later")  # from "Any time" to the next half hour
    shown = next(label for label, _ in nodes() if re.fullmatch(r"\d\d:\d\d", label))
    minutes = int(shown[:2]) * 60 + int(shown[3:])
    tap("30 minutes later", times=(18 * 60 - minutes) // 30 % 48)
    shot("07-new-task-date-time")

    tap("Set priority")
    tap("High")
    shot("08-new-task-priority")
    close_sheet()

    # Changing a task: no mockup, so these are only for looking at.
    tap_task("Reply to Marco about Friday")
    shot("m3-edit-task")
    tap("Set date and time")
    shot("m3-edit-task-date-time")
    close_sheet()
    tap_task("Water the plants")
    shot("m3-edit-done-task")
    close_sheet()

    tap("CALENDAR")
    shot("09-calendar")
    tap_task("Reply to Marco about Friday")
    shot("m3-edit-calendar-task")
    close_sheet()

    tap("PROJECTS")
    wait_for_label("Mark Draft the Q4 plan as done")
    shot("10-projects")
    tap_task("Draft the Q4 plan")
    shot("m3-edit-project-task")
    close_sheet()
    tap("Add a task or project")
    shot("11-projects-add-menu")
    tap("New task")
    tap("What needs doing?")
    type_text("Send the slides to Marco")
    back()  # hides the keyboard
    shot("12-new-task-project")
    close_sheet()
    tap("Add a task or project")
    tap("New project")
    type_text("Garden")
    back()  # hides the keyboard
    shot("13-new-project")
    close_sheet()
    tap("TASKS")

    # Night, the dark mode. No mockup draws it, so these are only for looking at; back to Canary after.
    tap("Settings")
    tap("Night")
    shot("m5-night-settings")
    back()
    shot("m5-night-today")
    tap_task("Reply to Marco about Friday")
    shot("m5-night-edit-task")
    close_sheet()
    tap("CALENDAR")
    shot("m5-night-calendar")
    tap("PROJECTS")
    wait_for_label("Mark Draft the Q4 plan as done")
    shot("m5-night-projects")
    tap("TASKS")
    tap("Settings")
    tap("Canary")
    back()

    # The first-run screens and Settings are compared by tools/notion_e2e.py, which needs a fake Notion.
    for name in ("05-today", "06-new-task", "07-new-task-date-time", "08-new-task-priority", "09-calendar",
                 "10-projects", "11-projects-add-menu", "12-new-task-project", "13-new-project"):
        print(f"{name}: {compare(name):.1%} of pixels differ")

    widgets()


if __name__ == "__main__":
    main()
