#!/usr/bin/env python3
"""Install old -> add real UI data -> install new with -r -> compare Room and settings.

Refuses physical devices. Uses only Python stdlib and adb. No uninstall or pm clear.
"""
import argparse
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", default="emulator-5554")
parser.add_argument("--old", required=True)
parser.add_argument("--new", required=True)
parser.add_argument("--expected-version", default="1.0.2")
parser.add_argument("--output", required=True)
args = parser.parse_args()
package = "ru.meow.reminder"
base = [args.adb, "-s", args.serial]


def adb(*parts, binary=False):
    return subprocess.check_output(base + list(parts), stderr=subprocess.PIPE, text=not binary)


if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
    raise SystemExit("Только эмулятор: проверка не запускается на личном телефоне.")

output = Path(args.output)
output.mkdir(parents=True, exist_ok=True)
marker = "UpgradeCheck_" + uuid.uuid4().hex[:10]


def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/meow-upgrade-ui.xml")
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/meow-upgrade-ui.xml"))


def find(value, attribute="text", timeout=20):
    deadline = time.monotonic() + timeout
    while True:
        for node in tree().iter("node"):
            if node.get(attribute) == value:
                bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
                if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
                    return bounds
        if time.monotonic() >= deadline:
            raise AssertionError(f"Элемент не найден: {value}")


def tap(value, attribute="text"):
    left, top, right, bottom = find(value, attribute)
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def stop():
    adb("shell", "am", "force-stop", package)


def start():
    adb("shell", "am", "start", "-W", "-n", package + "/.MainActivity")


def snapshot(label):
    folder = output / label
    folder.mkdir(exist_ok=True)
    for name in ("meow.db", "meow.db-wal"):
        try:
            (folder / name).write_bytes(adb("exec-out", "run-as", package, "cat", "databases/" + name, binary=True))
        except subprocess.CalledProcessError:
            if name == "meow.db":
                raise
    with sqlite3.connect(folder / "meow.db") as db:
        names = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
        tables = {name: sorted(db.execute('SELECT * FROM "' + name + '"').fetchall(), key=repr) for name in names}
    raw = adb("exec-out", "run-as", package, "cat", "files/datastore/settings.preferences_pb", binary=True)
    settings = json.loads(raw[raw.index(b"{"):raw.rindex(b"}") + 1])
    settings.pop("recentPhrases", None)  # opening home intentionally shows a new phrase
    return tables, settings


print("Устанавливаем старый APK на тестовый эмулятор…", flush=True)
adb("install", "-r", "-d", args.old)
start()
try:
    find("Начать заботиться о себе", timeout=5)
    tap("Начать заботиться о себе")
except AssertionError:
    pass  # already welcomed on this test emulator
find("Сегодня")
tap("Препараты")
tap("Создать препарат", "content-desc")
tap("Название *")
adb("shell", "input", "text", marker)
adb("shell", "input", "keyevent", "4")
tap("Назад", "content-desc")
tap("Заметки")
tap("Новая заметка")
tap("Заголовок")
adb("shell", "input", "text", marker + "_note")
tap("Ваш текст")
adb("shell", "input", "text", "Preserve_medications_notes_and_settings")
adb("shell", "input", "keyevent", "4")
tap("Назад", "content-desc")
find(marker + "_note")
tap("Настройки")
tap("Тёмная")
tree()  # wait for the UI/settings write to settle
stop()
before, prefs_before = snapshot("before")
assert any(marker in row for row in before["medications"])
assert any(marker + "_note" in row for row in before["notes"])
assert prefs_before["theme"] == "dark"

print("Обновляем через adb install -r, без удаления приложения…", flush=True)
adb("install", "-r", args.new)
start()
find("Сегодня")
stop()
after, prefs_after = snapshot("after")
assert before == after, "Содержимое Room изменилось при обновлении"
assert prefs_before == prefs_after, "Настройки сбросились при обновлении"
installed = adb("shell", "dumpsys", "package", package)
assert "versionName=" + args.expected_version in installed
result = {"result": "passed", "from": str(args.old), "to": args.expected_version,
          "uninstalled": False, "roomRowCounts": {k: len(v) for k, v in after.items()},
          "settingsPreserved": True, "customMedicationPreserved": True, "customNotePreserved": True}
(output / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2))
print(json.dumps(result, ensure_ascii=False, indent=2))
