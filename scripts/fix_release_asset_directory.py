from pathlib import Path
p=Path('.github/workflows/android.yml');s=p.read_text(encoding='utf-8')
s=s.replace('run: cp app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/debug/FreeMangnetPlayer-${GITHUB_REF_NAME}.apk', 'run: |\n          mkdir -p release-assets\n          cp app/build/outputs/apk/debug/app-debug.apk release-assets/FreeMangnetPlayer-${GITHUB_REF_NAME}.apk')
s=s.replace('"app/build/outputs/apk/debug/FreeMangnetPlayer-${GITHUB_REF_NAME}-${abi}.apk"','"release-assets/FreeMangnetPlayer-${GITHUB_REF_NAME}-${abi}.apk"')
s=s.replace('app/build/outputs/apk/debug/FreeMangnetPlayer-', 'release-assets/FreeMangnetPlayer-')
p.write_text(s,encoding='utf-8')
p=Path('scripts/verify_video_formats.py');s=p.read_text(encoding='utf-8')
s=s.replace("        if st.get('source')==fixture['source'] and st.get('playing')", "        if st.get('source')==fixture['source'] and not st.get('playRequested') and '播放失败' in st.get('playerError',''):break\n        if st.get('source')==fixture['source'] and st.get('playing')",1)
p.write_text(s,encoding='utf-8')
