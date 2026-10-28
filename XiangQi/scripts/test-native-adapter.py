"""Run production PikafishEngine on the JVM with test-only Android Context stubs.

Uses the verified official Windows engine and a small compiled C# UCI fixture.
This tests the Android adapter code and real subprocess lifecycle, not ARM64 code.
"""
import argparse, os, pathlib, subprocess, sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--engine',type=pathlib.Path,required=True)
p.add_argument('--network',type=pathlib.Path)
p.add_argument('--java-home',type=pathlib.Path,default=pathlib.Path(os.environ.get('JAVA_HOME',r'C:\Dev\Tools\jdk-21.0.12.1+1')))
a=p.parse_args()
engine=a.engine.resolve();network=(a.network or engine.parent/'pikafish.nnue').resolve()
work=ROOT/'build'/'native-adapter-audit';classes=work/'classes';fixtures=work/'fixtures'
classes.mkdir(parents=True,exist_ok=True);fixtures.mkdir(exist_ok=True)
csc=pathlib.Path(os.environ.get('WINDIR',r'C:\Windows'))/'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
fake=work/'FakeUciEngine.exe'
subprocess.run([str(csc),'/nologo','/target:exe','/out:'+str(fake),str(ROOT/'tests/native/FakeUciEngine.cs')],check=True,timeout=60)
sources=list((ROOT/'app/src/main/java/org/xiangqi/study/core').glob('*.java'))+list((ROOT/'tests/native').rglob('*.java'))+[ROOT/'app/src/main/java/org/xiangqi/study/PikafishEngine.java',ROOT/'app/src/main/java/org/xiangqi/study/PikafishRelease.java']
subprocess.run([str(a.java_home/'bin/javac.exe'),'-encoding','UTF-8','-d',str(classes),*map(str,sources)],check=True,timeout=60)
result=subprocess.run([str(a.java_home/'bin/java.exe'),'-cp',str(classes),'org.xiangqi.study.NativeAdapterAuditTest',str(engine),str(fake),str(network),str(fixtures)],capture_output=True,text=True,encoding='utf-8',timeout=90)
(ROOT/'test-results').mkdir(exist_ok=True)
(ROOT/'test-results/native-adapter-audit.txt').write_text(result.stdout+result.stderr,encoding='utf-8')
sys.stdout.write(result.stdout);sys.stderr.write(result.stderr);sys.exit(result.returncode)
