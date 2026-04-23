#!/usr/bin/env python3
"""Helper: complete voice verification → land on MainActivity → open Profile → Edit Profile."""
import time
from pathlib import Path
from ui_driver import (
    PKG, sh, current_activity, dump_ui, screenshot, tap, long_press, key,
    parse_dump, find_by_id, find_by_text, center,
    dismiss_permission_dialogs, dismiss_anr_dialogs,
)

DUMP = Path(__file__).resolve().parent / "dumps"
SHOT = Path(__file__).resolve().parent / "screenshots"


def step(msg): print(f"  · {msg}")


def relaunch():
    dismiss_anr_dialogs()
    sh(f"am force-stop {PKG}"); time.sleep(2)
    sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")
    for _ in range(30):
        time.sleep(1.2)
        dismiss_permission_dialogs(); dismiss_anr_dialogs()
        act = current_activity()
        if "VoiceIdentificationActivity" in act or "MainActivity" in act:
            time.sleep(1.5); return act
    return current_activity()


def record_and_submit() -> bool:
    p = DUMP / "_advance.xml"; dump_ui(p)
    nodes = parse_dump(p)
    mic = find_by_id(nodes, "cl_microphone")
    if not mic:
        # Maybe we're already on the submit screen — look for btn_submit
        sub = find_by_id(nodes, "btn_submit")
        if sub and sub.bounds[3] > sub.bounds[1]:
            tap(*center(sub)); time.sleep(3); return True
        return False
    step("holding mic 5 s")
    long_press(*center(mic), ms=5000)
    time.sleep(2.5)
    dump_ui(p); nodes = parse_dump(p)
    sub = find_by_id(nodes, "btn_submit")
    if not sub:
        step("submit not visible after record"); return False
    step(f"tapping submit at {sub.bounds}")
    tap(*center(sub))
    time.sleep(4)
    return True


def main():
    act = relaunch()
    step(f"landed on {act}")
    if "MainActivity" in act:
        return "main"
    if "VoiceIdentificationActivity" not in act:
        step(f"unexpected activity {act}"); return "fail"

    # Attempt up to 3 voice submits (first attempt may fail on duration)
    for attempt in range(3):
        step(f"voice submit attempt {attempt+1}")
        if record_and_submit():
            time.sleep(5)
            act = current_activity()
            step(f"after submit: {act}")
            if "MainActivity" in act:
                screenshot(SHOT / "main_landed.png")
                return "main"
            if "YoutubeActivity" in act:
                step("on YouTube screen — back out")
                key("KEYCODE_BACK"); time.sleep(2)
                return "main"
        time.sleep(2)

    screenshot(SHOT / "_stuck.png")
    return "fail"


if __name__ == "__main__":
    r = main()
    print(f"RESULT: {r}")
    print(f"FINAL ACTIVITY: {current_activity()}")
