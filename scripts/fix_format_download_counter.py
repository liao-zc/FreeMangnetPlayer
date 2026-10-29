from pathlib import Path
p=Path('scripts/verify_video_formats.py');s=p.read_text(encoding='utf-8').replace("st.get('done',0)>=fixture['size']","st.get('downloaded',0)>=fixture['size']");p.write_text(s,encoding='utf-8')
p=Path('scripts/build_current_report.py');s=p.read_text(encoding='utf-8').replace("'verify_video_formats.py','video_format_fixture.py'","'verify_video_formats.py','verify_published_docs.py','video_format_fixture.py'");p.write_text(s,encoding='utf-8')
