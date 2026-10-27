#!/usr/bin/env bash
# Build launcher/ and install it on the living-room Xiaomi MiTV-ASTP0, keeping the stock launcher
# com.mitv.tvhome enabled. That firmware ignores set-home-activity, so build-and-install.sh stops at its
# Home check; HomeKeyService (stock_home_redirect + home_long_press_home in config.xml) takes Home instead.
# Then the HomeKeyService entry is handed from the shell to the launcher itself: turning ADB debugging off
# and rebooting resets every setting adb wrote, and only the launcher's own ServiceRepair entry survives.
#
# Usage:  TV_HOST=<tv ip> ./install.sh
# Network ADB closes at every reboot on this TV: Settings -> Account & Security -> ADB debugging first.
set -uo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export TV_HOST
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
PY=tools/venv/bin/python
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }

# Expected to end with "Home resolves to com.mitv.tvhome..., not to the new launcher": that is this firmware.
STOCK_LAUNCHER=none RESTORE_KEY_SERVICES=1 launcher/build-and-install.sh 2>&1 | grep -E 'Success|Status|ERROR|Failure' || true
"$PY" tools/tv.py 'pm list packages com.gathin.tvhome' | grep -q com.gathin.tvhome || { echo "launcher not installed" >&2; exit 1; }

# Android turns accessibility services off on every reinstall; write the helper back.
svc=com.gathin.tvhome/.HomeKeyService
"$PY" tools/tv.py "s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in *HomeKeyService*) settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$s\" ;; null|'') settings put secure enabled_accessibility_services $svc ;; *) settings put secure enabled_accessibility_services \"\$s:$svc\" ;; esac; settings put secure accessibility_enabled 1; echo HomeKeyService enabled"
sleep 2

# Let the launcher own that entry: grant WRITE_SECURE_SETTINGS (build-and-install skips it after the failed
# Home check), force-stop so the helper drops off the list, start Home so ServiceRepair writes it back.
"$PY" tools/tv.py 'pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS'
"$PY" tools/tv.py 'dumpsys package com.gathin.tvhome' | grep -q "WRITE_SECURE_SETTINGS: granted=true" \
  || { echo "WRITE_SECURE_SETTINGS not granted" >&2; exit 1; }
# Standby force-stops every third-party app (MitvActivityManagerService killAllThirdApp), which drops the
# helper, and a 小爱 wake is a resume, not a boot. GuardRevival is bound again by the system after a
# force-stop and writes the helper back once the screen is on; HomeKeyService then probes what is in front.
# The listener entry is written by the system (pkg:android), so it survives turning ADB off.
"$PY" tools/tv.py 'cmd notification allow_listener com.gathin.tvhome/.GuardRevival'
"$PY" tools/tv.py 'am broadcast -a com.gathin.tvhome.GUARD -n com.gathin.tvhome/.GuardConfig --ez guard_off_in_standby true' \
  | grep -o 'guard_off_in_standby=[a-z]*' || { echo "GuardConfig did not answer" >&2; exit 1; }
"$PY" tools/tv.py 'am force-stop com.gathin.tvhome'
sleep 2
"$PY" tools/tv.py 'am start -n com.gathin.tvhome/.MainActivity >/dev/null'
sleep 6   # ServiceRepair adds, removes and adds again with 1 s pauses
"$PY" tools/tv.py 'dumpsys settings | grep -E "name:(enabled_accessibility_services|accessibility_enabled) " | sed -E "s/ default:.*//"'
"$PY" tools/tv.py 'dumpsys accessibility | grep -o "label=[^,]*"'
echo "Both rows should say pkg:com.gathin.tvhome and label=主页键 should be listed. Press Home on the real remote to check."
