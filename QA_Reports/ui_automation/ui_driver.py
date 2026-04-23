#!/usr/bin/env python3
"""
Tiny Android UI automation driver built on `adb shell` + `uiautomator dump`.
Designed for the Hima app (com.gmwapp.hima.dev) on emulator-5554.

No external dependencies — pure Python 3.9+.
"""
from __future__ import annotations

import base64
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional


DEVICE = "emulator-5554"
PKG    = "com.gmwapp.hima.dev"


def adb(*args: str, timeout: int = 30) -> str:
    cmd = ["adb", "-s", DEVICE] + list(args)
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return (r.stdout or "") + (r.stderr or "")
    except subprocess.TimeoutExpired:
        return f"<TIMEOUT after {timeout}s>"


def sh(cmd: str, timeout: int = 20) -> str:
    return adb("shell", cmd, timeout=timeout)


def current_activity() -> str:
    out = sh("dumpsys activity activities | grep topResumedActivity")
    m = re.search(r"(\S+/\S+)", out)
    return m.group(1) if m else ""


def dump_ui(out_path: Path) -> Path:
    sh("uiautomator dump /sdcard/dump.xml")
    adb("pull", "/sdcard/dump.xml", str(out_path))
    return out_path


def screenshot(out_path: Path):
    # exec-out avoids line-ending corruption
    r = subprocess.run(
        ["adb", "-s", DEVICE, "exec-out", "screencap", "-p"],
        capture_output=True, timeout=15
    )
    out_path.write_bytes(r.stdout)
    return out_path


def tap(x: int, y: int):
    sh(f"input tap {x} {y}")


def swipe(x1: int, y1: int, x2: int, y2: int, ms: int = 300):
    sh(f"input swipe {x1} {y1} {x2} {y2} {ms}")


def long_press(x: int, y: int, ms: int = 1500):
    # swipe with same start+end = long press
    sh(f"input swipe {x} {y} {x} {y} {ms}", timeout=max(30, ms // 1000 + 10))


def type_text(s: str):
    # input text does not handle special chars well; escape spaces
    escaped = s.replace(" ", "%s").replace("'", r"\'").replace('"', r'\"')
    sh(f"input text '{escaped}'")


def key(keycode: str):
    sh(f"input keyevent {keycode}")


def logcat_snapshot(since_ts: Optional[str] = None) -> str:
    # Use adb logcat -d for a snapshot. Filter to our package name.
    r = subprocess.run(
        ["adb", "-s", DEVICE, "logcat", "-d", "-v", "threadtime", "*:W"],
        capture_output=True, text=True, timeout=15
    )
    out = r.stdout or ""
    # Keep our-app lines plus any crash/FATAL/ANR
    lines = [l for l in out.splitlines()
             if PKG in l or "FATAL" in l or "AndroidRuntime" in l or "ANR" in l]
    return "\n".join(lines[-400:])


def logcat_clear():
    adb("logcat", "-c", timeout=10)


@dataclass
class UINode:
    cls: str
    rid: str
    text: str
    bounds: tuple[int, int, int, int]
    clickable: bool
    enabled: bool
    content_desc: str = ""


def parse_dump(path: Path) -> list[UINode]:
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        return []
    nodes = []
    for n in tree.iter("node"):
        b = n.get("bounds", "")
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        nodes.append(UINode(
            cls=n.get("class", ""),
            rid=n.get("resource-id", ""),
            text=(n.get("text") or "").strip(),
            bounds=(x1, y1, x2, y2),
            clickable=(n.get("clickable") == "true"),
            enabled=(n.get("enabled") == "true"),
            content_desc=(n.get("content-desc") or "").strip(),
        ))
    return nodes


def center(n: UINode) -> tuple[int, int]:
    x1, y1, x2, y2 = n.bounds
    return (x1 + x2) // 2, (y1 + y2) // 2


def find_by_id(nodes: list[UINode], rid: str) -> Optional[UINode]:
    for n in nodes:
        if n.rid == rid or n.rid.endswith(f"/{rid}"):
            return n
    return None


def find_by_text(nodes: list[UINode], text: str, exact: bool = False) -> Optional[UINode]:
    for n in nodes:
        if exact and n.text == text:
            return n
        if not exact and text.lower() in n.text.lower():
            return n
    return None


def wait_for(pred, timeout_s: float = 5.0, step: float = 0.4):
    t0 = time.time()
    while time.time() - t0 < timeout_s:
        if pred():
            return True
        time.sleep(step)
    return False


def dismiss_permission_dialogs():
    """If a runtime permission dialog is up, tap 'While using the app' / 'Allow'."""
    sh("uiautomator dump /sdcard/dump.xml")
    adb("pull", "/sdcard/dump.xml", "/tmp/_perm.xml")
    nodes = parse_dump(Path("/tmp/_perm.xml"))
    for n in nodes:
        if any(p in n.text.lower() for p in (
            "while using the app", "allow", "only this time"
        )):
            tap(*center(n))
            time.sleep(0.8)
            return True
    return False


def dismiss_anr_dialogs() -> bool:
    """If an 'isn't responding' ANR dialog is up, tap Wait to keep the app alive."""
    sh("uiautomator dump /sdcard/dump.xml")
    adb("pull", "/sdcard/dump.xml", "/tmp/_anr.xml")
    nodes = parse_dump(Path("/tmp/_anr.xml"))
    for n in nodes:
        if n.text.strip().lower() == "wait" and n.clickable:
            tap(*center(n))
            time.sleep(1.0)
            return True
    return False
