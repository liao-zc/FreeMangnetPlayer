from pathlib import Path
p=Path('scripts/verify_video_formats.py');s=p.read_text(encoding='utf-8').replace('import hashlib,json,shlex,subprocess,time','import hashlib,json,re,shlex,subprocess,time')
old="path=st['folder']+'/Video.'+fixture['extension'];data=subprocess.check_output(['adb','exec-out','run-as',PACKAGE,'cat',path])\n        row['download_sha256_matches']=hashlib.sha256(data).hexdigest()==fixture['sha256']"
new="""path=st['folder']+'/Video.'+fixture['extension']
        checksum=adb('shell','sha256sum',path).split()[0]
        row['device_sha256']=checksum
        row['device_file_size']=int(adb('shell','wc','-c',path).split()[0])
        row['download_sha256_matches']=checksum==fixture['sha256'] and row['device_file_size']==fixture['size']"""
assert old in s;s=s.replace(old,new);p.write_text(s,encoding='utf-8')
p=Path('scripts/video_format_fixture.py');s=p.read_text(encoding='utf-8')
s=s.replace("    subprocess.run(command+[str(path)],check=True)","""    if ext=='rm':
        command=['ffmpeg','-y','-stream_loop','-1','-i',str(source),'-t','90','-vf','scale=320:240','-r','12','-c:v','rv20','-b:v','300k','-c:a','real_144','-ar','8000','-ac','1']
    subprocess.run(command+[str(path)],check=True)""",1);p.write_text(s,encoding='utf-8')
p=Path('scripts/compare_reference_player.py');s=p.read_text(encoding='utf-8')
s=s.replace("    adb('shell','monkey','-p',ref,'1');time.sleep(15);snap('compare-01-reference-home')", "    activity=re.search(r\"launchable-activity: name='([^']+)'\",badging)[1]\n    result['reference_launch']=adb('shell','am','start','-W','-n',ref+'/'+activity)\n    time.sleep(8);snap('compare-01-reference-home')")
p.write_text(s,encoding='utf-8')
p=Path('scripts/build_current_report.py');s=p.read_text(encoding='utf-8')
s=s.replace('对标 APK：[官方仓库]', '实际安装包的应用名与包名也在原始 badging 记录中，不能仅凭下载文件名认定与用户手机安装版本完全相同。\n\n对标 APK：[官方仓库]',1)
p.write_text(s,encoding='utf-8')
