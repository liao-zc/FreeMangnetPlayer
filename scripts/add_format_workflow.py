from pathlib import Path
p=Path('.github/workflows/comparison.yml');s=p.read_text(encoding='utf-8')
s=s.replace('      - name: Download reference APK','''      - name: Prepare real format clips and BitTorrent seeds
        run: |
          sudo apt-get update -qq
          sudo apt-get install -y python3-libtorrent ffmpeg
          /usr/bin/python3 scripts/video_format_fixture.py > format-fixture.log 2>&1 &
          for i in $(seq 1 180); do test -f format-fixtures.json && break; sleep 1; done
          test -f format-fixtures.json || (tail -80 format-fixture.log; exit 1)
      - name: Download reference APK''')
s=s.replace('python3 scripts/verify_search_pages.py','python3 scripts/verify_search_pages.py; /usr/bin/python3 scripts/verify_video_formats.py')
p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/com/freemangnetplayer/PlayerActivity.kt');s=p.read_text(encoding='utf-8').replace('if (error.errorCode >= 3000) requested = false','if (error.errorCode >= 3000 || media is VlcTorrentPlayer) requested = false');p.write_text(s,encoding='utf-8')
