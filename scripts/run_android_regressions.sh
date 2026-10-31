#!/usr/bin/env bash
# Called by android-emulator-runner. Keep this logic in a file: the action's
# `script` input is passed through a shell command and multiline blocks can be
# split incorrectly by the action's wrapper.
set +e
mkdir -p manual-shots
adb install -r ui-inspector/build/outputs/apk/debug/ui-inspector-debug.apk
install_code=$?
instrumentation_code=125
ui_code=125
if [ "$install_code" -eq 0 ]; then
  gradle --no-daemon connectedDebugAndroidTest
  instrumentation_code=$?
  bash scripts/run_ui_regressions.sh
  ui_code=$?
fi
printf 'apk_install\t%s\ninstrumentation\t%s\nui_regressions\t%s\n' \
  "$install_code" "$instrumentation_code" "$ui_code" \
  > manual-shots/android-stage-status.tsv
test "$install_code" -eq 0 &&
  test "$instrumentation_code" -eq 0 &&
  test "$ui_code" -eq 0
