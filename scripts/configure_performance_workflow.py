from pathlib import Path
p = Path('app/src/main/java/com/freemangnetplayer/MainActivity.kt')
s = p.read_text(encoding='utf-8-sig')
s = s.replace('import androidx.compose.material.icons.Icons', 'import androidx.compose.material.icons.filled.Settings\nimport androidx.compose.material.icons.Icons')
p.write_text(s, encoding='utf-8')
p = Path('.github/workflows/capture-screenshots.yml')
s = p.read_text(encoding='utf-8-sig').replace('default: optimization', 'default: performance').replace('options: [optimization,', 'options: [performance, optimization,')
s = s.replace("if: inputs.mode == 'optimization' || github.event_name == 'push'", "if: inputs.mode == 'optimization'")
needle = '      - name: Enable KVM'
assert needle in s
s = s.replace(needle, '''      - name: Prepare unlimited real seed and native baseline
        if: inputs.mode == 'performance' || github.event_name == 'push'
        run: |
          sudo apt-get update -qq
          sudo apt-get install -y python3-libtorrent ffmpeg
          /usr/bin/python3 scripts/performance_fixture.py > fixture.log 2>&1 &
          for i in $(seq 1 240); do test -f performance-fixture.json && break; sleep 1; done
          test -f performance-fixture.json || (cat fixture.log; exit 1)
''' + needle)
s = s.replace("${{ inputs.mode || 'optimization' }}", "${{ inputs.mode || 'performance' }}")
s = s.replace('script: if [ "$FMP_TEST_MODE" = optimization ]; then', 'script: if [ "$FMP_TEST_MODE" = performance ]; then /usr/bin/python3 scripts/verify_performance.py; elif [ "$FMP_TEST_MODE" = optimization ]; then')
p.write_text(s, encoding='utf-8')
print('Configured unlimited performance CI')
