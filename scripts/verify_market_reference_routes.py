#!/usr/bin/env python3
"""Static regression smoke check for the completed Market reference navigation batch.

This intentionally uses only Python's standard library and does not launch the app,
touch Firebase, or modify project files.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
UI_DIR = ROOT / "app/src/main/java/com/superiorstudent/app/feature/market/ui"
TEST_DIR = ROOT / "app/src/test/java/com/superiorstudent/app/feature/market/ui"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
ANDROID = "{http://schemas.android.com/apk/res/android}"

SCREENS = (
    "MarketHelp",
    "MarketFaq",
    "MarketRules",
    "MarketSupport",
    "MarketMyReports",
    "MarketSettings",
    "MarketBlockedUsers",
)


def fail(message: str) -> None:
    print(f"Market reference route check FAILED: {message}", file=sys.stderr)
    raise SystemExit(1)


def require_file(path: Path) -> str:
    if not path.is_file():
        fail(f"required file is missing: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


manifest_root = ET.parse(MANIFEST).getroot()
activities = {
    node.get(f"{ANDROID}name"): node
    for node in manifest_root.iter("activity")
}
for screen in SCREENS:
    activity_name = f".feature.market.ui.{screen}Activity"
    if activity_name not in activities:
        fail(f"{activity_name} is not registered in AndroidManifest.xml")
    if activities[activity_name].get(f"{ANDROID}exported") != "false":
        fail(f"{activity_name} must remain non-exported")

    require_file(UI_DIR / f"{screen}Activity.kt")
    require_file(UI_DIR / f"{screen}UiPolicy.kt")
    require_file(UI_DIR / f"{screen}Icons.kt")
    require_file(TEST_DIR / f"{screen}UiPolicyTest.kt")


def require_route(source_activity: str, target_activity: str) -> None:
    source = require_file(UI_DIR / f"{source_activity}.kt")
    pattern = (
        r"startActivity\s*\(\s*Intent\s*\(\s*"
        r"this(?:@\w+)?\s*,\s*"
        + re.escape(target_activity)
        + r"::class\.java\s*\)"
    )
    if not re.search(pattern, source, flags=re.DOTALL):
        fail(f"missing explicit route {source_activity} -> {target_activity}")


ROUTES = {
    "MarketRequestsActivity": ("MarketSettingsActivity",),
    "MarketOffersActivity": ("MarketSettingsActivity",),
    "MarketSettingsActivity": (
        "MarketBlockedUsersActivity",
        "MarketHelpActivity",
        "MarketMyReportsActivity",
    ),
    "MarketHelpActivity": (
        "MarketFaqActivity",
        "MarketRulesActivity",
        "MarketSupportActivity",
        "MarketMyReportsActivity",
    ),
}

for source_activity, targets in ROUTES.items():
    for target_activity in targets:
        require_route(source_activity, target_activity)

print(
    "Market reference route check passed: "
    f"{len(SCREENS)} registered non-exported screens, "
    f"{len(SCREENS)} UI policy/icon/test file groups, "
    f"{sum(len(targets) for targets in ROUTES.values())} navigation edges."
)
