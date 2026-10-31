#!/usr/bin/env bash
set +e
mkdir -p manual-shots
failed=0
: > manual-shots/ui-regression-status.tsv
for test_script in scripts/verify_library_revision.py scripts/verify_preferences_revision.py scripts/verify_search_pages.py scripts/verify_recent_requirements.py; do
  printf 'START %s\n' "$test_script"
  /usr/bin/python3 "$test_script"
  result=$?
  printf '%s\t%s\n' "$test_script" "$result" | tee -a manual-shots/ui-regression-status.tsv
  if [ "$result" -ne 0 ]; then failed=1; fi
done
/usr/bin/python3 scripts/summarize_ci_results.py
exit "$failed"
