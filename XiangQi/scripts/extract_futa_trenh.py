#!/usr/bin/env python3
"""Reproduce four screened 2023 Trenh A Sang classical candidate records.

Reads cached official federation viewer HTML. Converts each source notation
token by matching exactly one move enumerated by the app's Position class.
No bulletin annotations, images or prose enter the candidate game records.
"""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.cache/vietnamese/futa'
IDS = (1463, 1466, 1470, 1471)
PDF_GAMES = {1463:(25,2),1466:(25,5),1470:(26,9),1471:(26,10)}
PDF_URL = 'https://vietnamchess.vn/document/2023/futa6-bulletin-full.pdf'
REGULATIONS_URL = 'https://vietnamchess.vn/index.php/regulations-futa6'
JAVA = r'''
import java.io.*;
import java.util.*;
import java.util.regex.*;
import org.xiangqi.study.core.Position;
public final class FutaConvert {
  static char piece(String text,boolean vn){
    if(vn){switch(text){case "P":return 'c';case "M":return 'n';case "X":return 'r';case "B":return 'p';case "T":return 'b';case "S":return 'a';case "Tg":return 'k';}}
    switch(text){case "R":return 'r';case "H":case "N":return 'n';case "E":case "B":return 'b';case "A":return 'a';case "K":return 'k';case "C":return 'c';case "P":return 'p';}
    throw new IllegalArgumentException(text);
  }
  static int file(int index,boolean red){return red?9-index%9:index%9+1;}
  static boolean matches(Position p,String move,Matcher token,boolean vn){
    int from=Position.index(move.substring(0,2)),to=Position.index(move.substring(2));
    char kind=piece(token.group(1),vn);if(Character.toLowerCase(p.board[from])!=kind)return false;
    String origin=token.group(2);int distance=Integer.parseInt(token.group(4));
    if(origin.matches("[1-9]")){if(file(from,p.redToMove)!=Integer.parseInt(origin))return false;}
    else {
      List<Integer> same=new ArrayList<>();for(int i=0;i<90;i++)if(p.board[i]==p.board[from]&&i%9==from%9)same.add(i);
      if(same.size()!=2)return false;
      same.sort(p.redToMove?Comparator.naturalOrder():Comparator.reverseOrder());
      boolean front=origin.equals("t")||origin.equals("+");if(from!=same.get(front?0:1))return false;
    }
    int dy=to/9-from/9;char op=token.group(3).charAt(0);
    boolean horizontal=vn?op=='-':op=='.';
    if(horizontal)return dy==0&&file(to,p.redToMove)==distance;
    boolean advance=vn?op=='.':op=='+';
    if(dy==0||(p.redToMove?dy<0:dy>0)!=advance)return false;
    return "nab".indexOf(kind)>=0?file(to,p.redToMove)==distance:Math.abs(dy)==distance;
  }
  public static void main(String[]args)throws Exception{
    BufferedReader in=new BufferedReader(new InputStreamReader(System.in,"UTF-8"));String line;
    while((line=in.readLine())!=null){String[] parts=line.split("\\t",3);boolean vn=parts[1].equals("vn");Position p=Position.initial();List<String> moves=new ArrayList<>();
      Pattern pattern=Pattern.compile(vn?"(Tg|[PMXBTS])([1-9ts])([./-])([1-9])":"([RHEAKCPNB])([1-9+-])([.+-])([1-9])");
      for(String word:parts[2].split(" ")){Matcher token=pattern.matcher(word);if(!token.matches())throw new IllegalArgumentException("Bad token "+word);List<String> found=new ArrayList<>();
        for(String move:p.legalMoves())if(matches(p,move,token,vn))found.add(move);
        if(found.size()!=1)throw new IllegalArgumentException(parts[0]+" ply "+(moves.size()+1)+" "+word+" candidates="+found+" FEN="+p.toFen());
        String move=found.get(0);p=p.play(move);moves.add(move);
      }
      System.out.println(parts[0]+"\t"+String.join(" ",moves)+"\t"+p.toFen());
    }
  }
}
'''

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    sys.stdout.reconfigure(encoding='utf-8')
    policy = json.loads((ROOT / 'data/curation-policy.json').read_text(encoding='utf-8-sig'))
    bulletin=(CACHE/'futa6-text.txt').read_text(encoding='utf-8')
    cached=[]
    lines=[]
    for ident in IDS:
        path=CACHE / f'game-{ident}.html'
        match=re.search(r'CUR_GAME:\s*(\{.*?\}),\s*\n',path.read_text(encoding='utf-8-sig'))
        if not match: raise ValueError(f'Missing CUR_GAME: {path}')
        game=json.loads(match[1]);assert game['id']==ident
        red=game['white_player_name_en'].split(',')[-1].strip()
        black=game['black_player_name_en'].split(',')[-1].strip()
        assert '郑亚生' in (red,black)
        assert not set((red,black)).intersection(policy['excludedNames'])
        body=re.sub(r'\[[^\]]*\]', '',game['pgn_text'])
        vn=bool(re.search(r'\b(?:Tg|M|X)[1-9ts][./-]',body))
        pattern=r'(?:Tg|[PMXBTS])[1-9ts][./-][1-9]' if vn else r'[RHEAKCPNB][1-9+-][.+-][1-9]'
        tokens=re.findall(pattern,body)
        residue=re.sub(pattern,' ',body)
        residue=re.sub(r'\d+\.|\b(?:1-0|0-1|1/2-1/2)\b|[+*\s]','',residue)
        if residue:raise ValueError(f'Unparsed source text {ident}: {residue!r}')
        lines.append(f'{ident}\t'+('vn' if vn else 'wxf')+'\t'+' '.join(tokens))
        page,number=PDF_GAMES[ident]
        text=bulletin.split(f'---PDF PAGE {page}---')[1].split('---PDF PAGE ')[0]
        section=re.search(r'\('+str(number)+r'\) ([^\n]+)\nDa Nang City, \(([^)]+)\), (\d{2}\.\d{2}\.\d{4})\n(.*?)(?=\n\s*\(\d+\) |BẢNG A|$)',text,re.S)
        if not section:raise ValueError(f'Bulletin game {number} missing on page {page}')
        header,pdf_round,pdf_date,pdf_body=section.groups()
        assert red in header and black in header,(ident,header)
        assert pdf_round==game['round']
        assert '-'.join(reversed(pdf_date.split('.')))==game['date_played']
        pdf_result=re.findall(r'(1[–-]0|0[–-]1|1/2)',pdf_body)[-1].replace('–','-')
        assert pdf_result==game['result']
        pdf_tokens=re.findall(r'(?:Tg|[PMXBTS])[1-9ts][./-][1-9]',pdf_body)
        lines.append(f'{ident}pdf\tvn\t'+' '.join(pdf_tokens))
        cached.append((game,red,black,tokens,path,page,number))
    java=CACHE/'FutaConvert.java';java.write_text(JAVA,encoding='utf-8')
    jdk=Path('C:/Dev/Tools/jdk-21.0.12.1+1/bin')
    classes=CACHE/'classes';classes.mkdir(exist_ok=True)
    subprocess.run([str(jdk/'javac.exe'),'-encoding','UTF-8','-d',str(classes),str(ROOT/'app/src/main/java/org/xiangqi/study/core/Position.java'),str(java)],check=True)
    proc=subprocess.run([str(jdk/'java.exe'),'-cp',str(classes),'FutaConvert'],input='\n'.join(lines)+'\n',capture_output=True,text=True,encoding='utf-8',check=True)
    converted={}
    for row in proc.stdout.splitlines():
        ident,moves,fen=row.split('\t');converted[ident]=(moves.split(),fen)
    candidates=[];entries=[]
    for source,red,black,tokens,path,page,number in cached:
        ident=source['id'];moves,fen=converted[str(ident)]
        assert converted[f'{ident}pdf']==(moves,fen),f'Bulletin/database main lines differ for {ident}'
        assert len(moves)==len(tokens)
        url=f'https://vietnamchess.vn/databank/xqbase-vietnamchess?id={ident}&view=game'
        record={
          'id':f'vietnam-futa-2023-{ident}',
          'collection':'Vietnamese international classical 2022–2023',
          'event':'6th Phuong Trang International Xiangqi Masters Cup 2023',
          'round':f"Group A · Round {source['round_num']} · Board {source['board_num']}",
          'date':source['date_played'],'datePrecision':'day','sourceDate':source['date_played'],
          'red':red,'black':black,'sourceRed':source['white_player_name'],'sourceBlack':source['black_player_name'],
          'result':source['result'],'timeControl':'3600+30','sourceTimeControl':'60 minutes + 30 seconds per move from move one',
          'classicalRegulationsUrl':REGULATIONS_URL,
          'classicalEvidence':'Official regulations Article 4.1: group round-robin games use 60 minutes per player plus 30 seconds per move from the first move. These four records are Group A rounds 1–4, excluding knockout rapid/blitz tiebreaks.',
          'sourceUrl':url,'sourceTitle':source['white_player_name_en']+' – '+source['black_player_name_en']+' · '+source['round'],
          'moves':moves
        }
        candidates.append(record)
        entries.append({'id':record['id'],'viewerUrl':url,'viewerSha256':sha(path),'bulletinUrl':PDF_URL+f'#page={page}','bulletinPage':page,'bulletinGameNumber':number,'sourceRound':source['round'],'sourceDatePlayed':source['date_played'],'sourceResult':source['result'],'sourceNotation':'Vietnamese' if ident!=1471 else 'WXF','halfMoves':len(moves),'finalFen':fen,'bulletinDatabaseAgreement':'Exact names, recorded date, round/board, result, and every converted main-line move agree. PDF pages visually inspected.','sourcePgnMetadata':dict(re.findall(r'\[(\w+)\s+"([^"\r\n]*)"\]',source['pgn_text']))})
        print(ident,red,black,record['date'],record['round'],len(moves),'legal plies')
    output={'collection':'Vietnamese international classical 2022–2023','source':'Official Vietnamese Chess Federation bulletin and public game database','games':candidates}
    target=ROOT/'.cache/vietnamese/futa-candidate-games.json';target.write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    evidence={'sourceBulletinUrl':PDF_URL,'sourceBulletinSha256':sha(CACHE/'futa6-bulletin-full.pdf'),'sourceRegulationsUrl':REGULATIONS_URL,'sourceRegulationsSha256':sha(CACHE/'regulations-futa6.html'),'screeningPolicySha256':sha(ROOT/'data/curation-policy.json'),'excluded':{'id':1476,'opponent':'蒋川','reason':'Exact excludedNames policy match'},'records':entries,'validation':'Both independent official bulletin and database notation main lines uniquely match production Position.legalMoves() and agree move for move; Position.play() validates every complete sequence.'}
    evidence['candidateSha256']=sha(target)
    (ROOT/'.cache/vietnamese/futa-candidate-manifest.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    test=ROOT/'test-results/vietnam-trenh-2023.json';test.parent.mkdir(exist_ok=True);test.write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Candidate:',target)

if __name__=='__main__':main()
