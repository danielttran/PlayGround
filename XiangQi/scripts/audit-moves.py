"""Compare all Java legal moves with official Pikafish perft at reachable positions."""
import argparse, os, pathlib, re, subprocess
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--engine',type=pathlib.Path,required=True)
p.add_argument('--java-home',type=pathlib.Path,default=pathlib.Path(os.environ.get('JAVA_HOME',r'C:\Dev\Tools\jdk-21.0.12.1+1')))
a=p.parse_args()
classes=ROOT/'build'/'move-oracle-tests'; classes.mkdir(parents=True,exist_ok=True)
sources=list((ROOT/'app/src/main/java/org/xiangqi/study/core').glob('*.java'))+[ROOT/'tests/core/MoveOracleCases.java']
subprocess.run([str(a.java_home/'bin/javac.exe'),'-encoding','UTF-8','-d',str(classes),*map(str,sources)],check=True,timeout=60)
fixtures=subprocess.check_output([str(a.java_home/'bin/java.exe'),'-cp',str(classes),'org.xiangqi.study.core.MoveOracleCases'],text=True,timeout=60)
cases=[line.split('\t') for line in fixtures.splitlines()]
commands=['uci','isready']
for name,fen,moves in cases:commands+=['position fen '+fen,'go perft 1']
commands+=['position startpos','go perft 2','go perft 3']
commands+=['quit']
result=subprocess.run([str(a.engine.resolve())],cwd=a.engine.resolve().parent,input='\n'.join(commands)+'\n',text=True,encoding='utf-8',errors='replace',capture_output=True,check=True,timeout=90)
if 'CRITICAL ERROR' in result.stdout:raise AssertionError(result.stdout[-3000:])
groups=[]; totals=[]; current=set()
for line in result.stdout.splitlines():
    match=re.fullmatch(r'([a-i][0-9][a-i][0-9]):\s+\d+',line.strip())
    if match:current.add(match.group(1))
    total=re.fullmatch(r'Nodes searched:\s*(\d+)',line.strip())
    if total:groups.append(current);totals.append(int(total.group(1)));current=set()
assert len(groups)==len(cases)+2,(len(groups),len(cases)+2,result.stdout[-2000:])
for (name,fen,moves),actual in zip(cases,groups):
    expected=set(moves.split())
    if actual!=expected:raise AssertionError(f'{name} {fen}\nJava-only: {expected-actual}\nPikafish-only: {actual-expected}')
assert totals[-2:]==[1920,79666],totals[-2:]
out=ROOT/'test-results';out.mkdir(exist_ok=True)
(out/'move-oracle-cases.tsv').write_text(fixtures,encoding='utf-8')
(out/'move-oracle-native.txt').write_text(result.stdout,encoding='utf-8')
summary=f'PASS: Java legal moves exactly match official Pikafish perft for {len(cases)} positions (initial, checkmate, stalemate, 256 deterministic reachable positions); initial perft 44 / 1920 / 79666 independently confirmed.\n'
(out/'move-oracle.txt').write_text(summary,encoding='utf-8');print(summary,end='')
