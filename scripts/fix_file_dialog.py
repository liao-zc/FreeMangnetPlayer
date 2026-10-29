from pathlib import Path
p = Path('app/src/main/java/com/freemangnetplayer/PlayerActivity.kt')
s = p.read_text(encoding='utf-8')
start = s.index('if (fileDialog) AlertDialog')
end = s.index('if (networkDialog)', start)
part = s[start:end]
part = part.replace('                                        TextButton(onClick = { copyFileMagnet(current, file) }) { Text("复制磁力") }\n', '')
s = s[:start] + part + s[end:]
p.write_text(s, encoding='utf-8')
p = Path('scripts/capture_usage_screenshots.py')
s = p.read_text(encoding='utf-8')
s = s.replace('if not candidates and _attempt < 3:', 'if not candidates and _attempt < 8:')
old = "        adb('shell', 'input', 'swipe', str(width//2), str(int(height*.32)), str(width//2), str(int(height*.18)), '300')"
new = """        scrollables = []
        for node in tree.iter('node'):
            if node.get('scrollable') == 'true':
                bounds = re.fullmatch(r'\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]', node.get('bounds', ''))
                if bounds:
                    x1, y1, x2, y2 = map(int, bounds.groups())
                    if y2-y1 > 100 and x2-x1 > 100: scrollables.append((x1,y1,x2,y2))
        if scrollables:
            x1,y1,x2,y2 = max(scrollables, key=lambda b:(b[2]-b[0])*(b[3]-b[1]))
            x = (x1+x2)//2
            adb('shell','input','swipe',str(x),str(y1+int((y2-y1)*.85)),str(x),str(y1+int((y2-y1)*.2)),'400')
        else:
            adb('shell', 'input', 'swipe', str(width//2), str(int(height*.65)), str(width//2), str(int(height*.3)), '400')"""
assert old in s
p.write_text(s.replace(old,new), encoding='utf-8')
