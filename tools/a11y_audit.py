"""Audits whatever screen is open on the connected device.

Reports touch targets smaller than 48dp, in real pixels at the device's own density — the check that is
easy to get wrong by eye and easy to regress when a row gains a second line.

    python3 tools/a11y_audit.py "expense list"

It also lists clickable nodes with no text of their own. Treat that as a prompt to look, not a defect:
Compose reports a control's label on child nodes, so a correctly labelled Material button appears here
too. What it cannot tell you is whether a screen reader reads the whole row as one thing — that needs
TalkBack, or a Compose test asserting merged semantics.
"""
import re, subprocess, sys

def sh(*a): return subprocess.run(["adb", *a], capture_output=True, text=True).stdout

density = int(re.search(r"(\d+)", sh("shell", "wm", "density").splitlines()[-1]).group(1))
min_px = round(48 * density / 160)

sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
xml = sh("shell", "cat", "/sdcard/ui.xml")

nodes = re.findall(
    r'<node[^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?checkable="(\w+)"[^>]*?clickable="(\w+)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
    xml,
)
print(f"screen: {sys.argv[1] if len(sys.argv) > 1 else '?'}  (48dp = {min_px}px at density {density})")
small, unlabelled = [], []
for text, desc, checkable, clickable, x1, y1, x2, y2 in nodes:
    if clickable != "true" and checkable != "true":
        continue
    w, h = int(x2) - int(x1), int(y2) - int(y1)
    label = text or desc
    if w < min_px or h < min_px:
        small.append(f"    {w}x{h}px  {label or '(no label)'!r}")
    if not label:
        unlabelled.append(f"    {w}x{h}px at [{x1},{y1}]")
print("  too small:" if small else "  touch targets: all at least 48dp")
print("\n".join(small))
if unlabelled:
    print("  no label for a screen reader:")
    print("\n".join(unlabelled))
