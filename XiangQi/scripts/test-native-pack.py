"""Check production UCI commands for every bundled game against official Pikafish.

Samples initial, first-ply, middle and final positions, then custom checks and a
variation. Compares native perft-1 legal moves with Java and searches each legal
position to depth one. Uses one native process; ARM64 machine code is not run.
"""
import argparse, hashlib, json, os, pathlib, queue, re, subprocess, threading, time
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--engine',type=pathlib.Path,required=True)
p.add_argument('--network',type=pathlib.Path)
p.add_argument('--java-home',type=pathlib.Path,default=pathlib.Path(os.environ.get('JAVA_HOME',r'C:\Dev\Tools\jdk-21.0.12.1+1')))
a=p.parse_args();engine=a.engine.resolve();network=(a.network or engine.parent/'pikafish.nnue').resolve()
work=ROOT/'build/native-pack-audit';classes=work/'classes';classes.mkdir(parents=True,exist_ok=True)
asset=ROOT/'app/src/main/assets/games.json';pack=json.loads(asset.read_text(encoding='utf-8'))['games']
tsv=work/'games.tsv';tsv.write_text(''.join(g['id']+'\t'+g.get('startFen','')+'\t'+' '.join(g['moves'])+'\n' for g in pack),encoding='utf-8')
sources=list((ROOT/'app/src/main/java/org/xiangqi/study/core').glob('*.java'))+[ROOT/'tests/core/NativePositionCases.java']
subprocess.run([str(a.java_home/'bin/javac.exe'),'-encoding','UTF-8','-d',str(classes),*map(str,sources)],check=True,timeout=60)
result=subprocess.run([str(a.java_home/'bin/java.exe'),'-cp',str(classes),'org.xiangqi.study.core.NativePositionCases',str(tsv)],check=True,capture_output=True,text=True,encoding='utf-8',timeout=120)
cases=[line.split('\t',3) for line in result.stdout.splitlines() if line.strip()]
process=subprocess.Popen([str(engine)],cwd=network.parent,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',bufsize=1)
pending=queue.Queue();transcript=[]
def read_output():
    try:
        for line in process.stdout:pending.put(line.rstrip('\r\n'))
    finally:pending.put(None)
threading.Thread(target=read_output,daemon=True).start()
def send(command):
    transcript.append('> '+command);process.stdin.write(command+'\n');process.stdin.flush()
def until(prefix,timeout=20):
    deadline=time.monotonic()+timeout;lines=[]
    while True:
        left=deadline-time.monotonic()
        if left<=0:raise TimeoutError('Waiting for '+prefix)
        line=pending.get(timeout=left)
        if line is None:raise RuntimeError('Native process exited before '+prefix)
        transcript.append(line);lines.append(line)
        if 'CRITICAL ERROR' in line or line.startswith('ERROR'):raise RuntimeError(line)
        if line.startswith(prefix):return lines
searched=terminal=0;began=time.monotonic()
try:
    send('uci');until('uciok');send('setoption name Threads value 1');send('setoption name Hash value 32');send('setoption name MultiPV value 1');send('setoption name EvalFile value '+str(network));send('isready');until('readyok')
    for index,(label,command,moves,fen) in enumerate(cases):
        transcript.append('# '+label+' '+fen);expected=set(moves.split())
        send('ucinewgame');send(command);send('go perft 1');lines=until('Nodes searched:')
        actual=set()
        for line in lines:
            match=re.fullmatch(r'([a-i][0-9][a-i][0-9]):\s*\d+',line.strip())
            if match:actual.add(match[1])
        if actual!=expected:raise AssertionError(label+' legal moves differ; Java-only='+str(sorted(expected-actual))+' native-only='+str(sorted(actual-expected)))
        if expected:
            send('go depth 1');analysis=until('bestmove ');best=analysis[-1].split()[1]
            if best not in expected:raise AssertionError(label+' illegal native bestmove '+best)
            if not any(re.search(r'\bdepth [1-9]\d*\b.*\bscore (cp|mate) -?\d+',line) for line in analysis):raise AssertionError(label+' no completed depth-one score')
            searched+=1
        else:terminal+=1
        if (index+1)%100==0:print('Checked',index+1,'/',len(cases),'positions',flush=True)
    summary={'games':len(pack),'positions':len(cases),'searchedPositions':searched,'terminalPositions':terminal,'assetSha256':hashlib.sha256(asset.read_bytes()).hexdigest(),'elapsedSeconds':round(time.monotonic()-began,3),'scope':'Official Windows Pikafish; production UciPosition commands and Java legal move comparison, not Android ABI execution','status':'PASS'}
    (ROOT/'test-results').mkdir(exist_ok=True);(ROOT/'test-results/native-pack-audit.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary,indent=2),flush=True)
finally:
    try:send('stop');send('quit')
    except (BrokenPipeError,OSError):pass
    try:process.wait(timeout=3)
    except subprocess.TimeoutExpired:process.kill();process.wait(timeout=3)
    (ROOT/'test-results').mkdir(exist_ok=True);(ROOT/'test-results/native-pack-transcript.txt').write_text('\n'.join(transcript)+'\n',encoding='utf-8')
