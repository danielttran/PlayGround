"""Capture/inspect the running development app via Android platform tools."""
import argparse, pathlib, re, subprocess, sys, time, xml.etree.ElementTree as ET
sys.stdout.reconfigure(encoding='utf-8')

p = argparse.ArgumentParser()
p.add_argument('--adb', default=r'C:\Dev\Tools\android-sdk\platform-tools\adb.exe')
p.add_argument('--serial', default='emulator-5554')
p.add_argument('--screenshot', type=pathlib.Path)
p.add_argument('--inspect', action='store_true')
p.add_argument('--tap-text', help='Exact visible text from a fresh UI hierarchy')
a = p.parse_args()
def adb(*args):
    return subprocess.check_output([a.adb, '-s', a.serial, *args], timeout=60)
if a.inspect or a.tap_text:
    for attempt in range(3):
        remote = '/sdcard/xiangqi-view-' + str(time.time_ns()) + '.xml'
        result = adb('shell', 'uiautomator', 'dump', remote)
        if b'dumped to:' in result:
            tree = ET.fromstring(adb('exec-out', 'cat', remote))
            adb('shell', 'rm', remote)
            break
        time.sleep(0.3)
    else: raise SystemExit('Android UI hierarchy is not ready')
    if a.tap_text:
        nodes = [n for n in tree.iter('node') if n.get('text') == a.tap_text]
        if len(nodes) != 1: raise SystemExit('Expected one visible text match, found ' + str(len(nodes)))
        x1,y1,x2,y2 = map(int,re.findall(r'\d+',nodes[0].get('bounds')))
        adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    if a.inspect:
        for n in tree.iter('node'):
            if n.get('text') or n.get('content-desc'):
                print(n.get('bounds'), n.get('class').split('.')[-1], n.get('text'), n.get('content-desc'))
if a.screenshot:
    a.screenshot.parent.mkdir(parents=True,exist_ok=True)
    a.screenshot.write_bytes(adb('exec-out','screencap','-p'))
    print(a.screenshot.resolve())
