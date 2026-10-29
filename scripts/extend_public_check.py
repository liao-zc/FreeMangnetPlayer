from pathlib import Path
p = Path('scripts/verify_performance.py')
s = p.read_text(encoding='utf-8')
needle = "    report.append({'check': 'return-during-plain-magnet-discovery', 'passed': True})"
assert needle in s
s = s.replace(needle, needle + '''
    baseline_path = Path('public-network-baseline.json')
    if baseline_path.exists():
        public = json.loads(baseline_path.read_text())
        if public.get('plain_magnet'):
            clear_app()
            paste(public['plain_magnet'])
            find_and_tap('解析资源')
            started = time.monotonic()
            public_samples = []
            while time.monotonic() - started < 75:
                current = state()
                public_samples.append(current)
                if current.get('payloadBytes', 0) >= 8 * 1024 * 1024:
                    break
                time.sleep(1)
            screenshot('perf-11-public-dht-resource.png')
            report.append({'check': 'public-pure-hash-comparison', 'measured': True,
                'metadata_received': current.get('metadata', False),
                'payload_received': current.get('payloadBytes', 0),
                'peak_payload_bytes_per_second': max(s.get('payloadRate', 0) for s in public_samples),
                'native_baseline': public, 'state': current, 'samples': public_samples,
                'note': 'A public network measurement is not converted into a pass when no peers or bytes are received.'})
            find_and_tap('返回搜索')
''')
p.write_text(s, encoding='utf-8')
print('Added real public network comparison')
