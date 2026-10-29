from pathlib import Path
p=Path('.github/workflows/comparison.yml');s=p.read_text(encoding='utf-8')
s=s.replace('      - name: Enable KVM','''      - name: Start controlled search website
        run: |
          python3 scripts/search_page_fixture.py > search-fixture.log 2>&1 &
          for i in $(seq 1 30); do test -f search-pages-expected.json && break; sleep 1; done
      - name: Enable KVM''')
s=s.replace('python3 scripts/compare_reference_player.py','python3 scripts/compare_reference_player.py; python3 scripts/verify_search_pages.py')
p.write_text(s,encoding='utf-8')
