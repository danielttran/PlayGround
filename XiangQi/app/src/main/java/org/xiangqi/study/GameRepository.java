package org.xiangqi.study;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xiangqi.study.core.Pgn;
import org.xiangqi.study.core.Position;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Private, offline library. Imports are checked in full before the library is changed. */
public final class GameRepository {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_PLIES = 2000;
    private static final String LEGACY_COLLECTION_ID = "legacy-imports";
    private static final Object FILE_LOCK = new Object();
    private final Context context;
    private final List<Game> games = new ArrayList<>();
    private final List<Game> imported = new ArrayList<>();
    private final List<ImportedCollection> collections = new ArrayList<>();
    public GameRepository(Context context) { this.context = context.getApplicationContext(); }

    public static final class Game {
        public String id, event, round, date, red, black, result, sourceUrl, startFen, timeControl="", collection="";
        public boolean imported, engineGame;
        public String importCollectionId="";
        public final List<String> moves = new ArrayList<>();
        public String title() { return red + " · " + black; }
        public Position initial() { return startFen.isEmpty() ? Position.initial() : Position.fromFen(startFen); }
        public JSONObject json() throws Exception {
            JSONObject j = new JSONObject();
            j.put("id", id); j.put("event", event); j.put("round", round); j.put("date", date);
            j.put("red", red); j.put("black", black); j.put("result", result); j.put("sourceUrl", sourceUrl);
            j.put("startFen", startFen); j.put("timeControl",timeControl); j.put("collection",collection);
            j.put("engineGame",engineGame); j.put("importCollectionId",importCollectionId);
            j.put("moves", new JSONArray(moves)); return j;
        }
    }

    /** A user-owned import batch; its name is independent of source game metadata. */
    public static final class ImportedCollection {
        public final String id, name;
        public final boolean engineGame;
        public final int gameCount;
        private ImportedCollection(String id,String name,boolean engineGame,int gameCount) {
            this.id=id;this.name=name;this.engineGame=engineGame;this.gameCount=gameCount;
        }
    }

    public synchronized List<Game> load() throws Exception {
        synchronized (FILE_LOCK) { return loadStored(); }
    }

    private List<Game> loadStored() throws Exception {
        List<Game> bundled = new ArrayList<>();
        JSONArray packs;
        try (InputStream in = context.getAssets().open("game-packs.json")) { packs = new JSONObject(read(in)).getJSONArray("packs"); }
        Set<String> ids = new HashSet<>();
        for (int i=0;i<packs.length();i++) {
            JSONObject pack=packs.getJSONObject(i); String name=pack.getString("file");
            if(!name.matches("[a-zA-Z0-9-]+\\.json"))throw new IOException("Invalid bundled pack name");
            List<Game> entries;
            try(InputStream in=context.getAssets().open(name)){entries=jsonGames(read(in),false);}
            for(Game game:entries){
                if(!ids.add(game.id))throw new IOException("Duplicate bundled game ID");
                if(game.collection.isEmpty())game.collection=pack.optString("label","");
                game.engineGame=pack.optBoolean("engineGame",game.engineGame);
            }
            bundled.addAll(entries);
        }
        File file = new File(context.getFilesDir(), "imported-games.json");
        List<Game> saved = new ArrayList<>();
        List<ImportedCollection> savedCollections = new ArrayList<>();
        if (file.isFile() || new File(file.getPath()+".bak").isFile())
            try (InputStream in = new android.util.AtomicFile(file).openRead()) {
                String text=read(in);Object root=jsonRoot(text);
                if(root instanceof JSONObject&&((JSONObject)root).optInt("schema",0)==2) {
                    JSONObject stored=(JSONObject)root;JSONArray groups=stored.getJSONArray("collections");
                    if(groups.length()>5000)throw new IOException("Too many saved import collections");
                    Map<String,ImportedCollection> byId=new LinkedHashMap<>();
                    for(int i=0;i<groups.length();i++) {
                        JSONObject group=groups.getJSONObject(i);String id=group.getString("id"),name=collectionName(group.getString("name"));
                        if(!id.matches("[a-zA-Z0-9-]{1,128}")||byId.containsKey(id))throw new IOException("Invalid saved import collection ID");
                        byId.put(id,new ImportedCollection(id,name,group.optBoolean("engineGame",false),0));
                    }
                    saved=jsonGames(stored.getJSONArray("games"),true,true);
                    Map<String,Integer> counts=new HashMap<>();
                    for(Game game:saved) {
                        ImportedCollection group=byId.get(game.importCollectionId);
                        if(group==null)throw new IOException("Saved game has no import collection");
                        game.engineGame=group.engineGame;counts.put(group.id,counts.getOrDefault(group.id,0)+1);
                    }
                    for(ImportedCollection group:byId.values())if(counts.containsKey(group.id))
                        savedCollections.add(new ImportedCollection(group.id,group.name,group.engineGame,counts.get(group.id)));
                } else {
                    if(root instanceof JSONObject&&((JSONObject)root).has("schema"))
                        throw new IOException("Unsupported saved import-library schema");
                    // Before schema 2 all imports shared a flat array. Preserve their
                    // content-derived IDs so existing saved study positions still resolve.
                    saved=jsonGames(text,true);
                    for(Game game:saved){game.importCollectionId=LEGACY_COLLECTION_ID;game.engineGame=false;}
                    if(!saved.isEmpty())savedCollections.add(new ImportedCollection(LEGACY_COLLECTION_ID,"Imported games",false,saved.size()));
                }
            }
        for(Game game:saved)if(!ids.add(game.id))throw new IOException("Duplicate saved game ID");
        games.clear(); imported.clear(); games.addAll(bundled); imported.addAll(saved); games.addAll(saved);
        collections.clear();collections.addAll(savedCollections);
        return new ArrayList<>(games);
    }

    public synchronized List<Game> all() { return new ArrayList<>(games); }
    public synchronized List<ImportedCollection> importedCollections() { return new ArrayList<>(collections); }

    /** Compatibility entry point for the pre-collection importer and its dedup semantics. */
    public synchronized int importText(String text) throws Exception {
        return importBatch(text,null,false);
    }

    /** Every successful explicit import becomes a separate named collection. */
    public synchronized int importText(String text,String name,boolean engineGame) throws Exception {
        return importBatch(text,collectionName(name),engineGame);
    }

    private int importBatch(String text,String name,boolean engineGame) throws Exception {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("The file is empty.");
        if (text.length() > MAX_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("File exceeds the 5 MB import limit.");
        List<Game> candidates=null;
        String clean = text.replaceFirst("^\\uFEFF", "").trim();
        if(clean.startsWith("{")) {
            Object root;
            try{root=jsonRoot(clean);}
            catch(org.json.JSONException|IllegalArgumentException invalidJson){if(!hasLeadingPgnComments(clean))throw invalidJson;root=null;}
            // A valid JSON root always wins, even when quoted metadata contains
            // braces that resemble the end of a PGN comment. Schema errors do
            // not fall back to another format.
            if(root!=null)candidates=jsonGames(((JSONObject)root).getJSONArray("games"),true,false);
        } else if (clean.startsWith("[{") || clean.matches("(?s)^\\[\\s*\\{.*")) candidates = jsonGames(clean, true);
        if(candidates==null) {
            candidates = new ArrayList<>();
            for (Pgn.Game source : Pgn.parse(clean)) {
                Game g = new Game();
                g.event = value(source.event, "Imported game"); g.round = value(source.round, "");
                g.date = value(source.date, ""); g.red = value(source.red, "Red"); g.black = value(source.black, "Black");
                g.result = value(source.result, "*"); g.sourceUrl = value(source.sourceUrl, "");
                g.startFen = value(source.startFen, ""); g.timeControl=value(source.timeControl, ""); g.collection=value(source.collection, ""); g.moves.addAll(source.moves); g.imported = true;
                validate(g); g.id = fingerprint(g); candidates.add(g);
            }
        }
        if (candidates.isEmpty()) throw new IllegalArgumentException("No games found. Import ICCS PGN or a JSON game pack.");
        if (candidates.size() > 2000) throw new IllegalArgumentException("Import no more than 2,000 games at once.");
        synchronized (FILE_LOCK) { return commit(candidates,name,engineGame); }
    }

    /** Recognize PGN's leading comments before choosing JSON versus PGN. */
    private static boolean hasLeadingPgnComments(String text) {
        int i=0;boolean comment=false;
        while(i<text.length()) {
            char c=text.charAt(i);
            if(Character.isWhitespace(c)){i++;continue;}
            if(c=='{'){int end=text.indexOf('}',i+1);if(end<0)return false;i=end+1;comment=true;continue;}
            if(c==';'||c=='%'){while(i<text.length()&&text.charAt(i)!='\n'&&text.charAt(i)!='\r')i++;continue;}
            // A JSON object/array cannot continue with a PGN tag or move token.
            return comment&&(c=='['&&i+1<text.length()&&Character.isLetter(text.charAt(i+1))
                ||Character.isDigit(c)||c>='a'&&c<='i'||c>='A'&&c<='I');
        }
        return false;
    }

    private int commit(List<Game> candidates,String name,boolean engineGame) throws Exception {
        // Another Activity/repository may have imported since this instance loaded.
        // Read the latest committed snapshot under the same lock as the write.
        loadStored();
        Set<String> fingerprints = new HashSet<>();
        boolean legacy=name==null;
        if(legacy)for (Game g : games) fingerprints.add(fingerprint(g));
        String collectionId=legacy?LEGACY_COLLECTION_ID:"collection-"+UUID.randomUUID();
        List<Game> next = new ArrayList<>(imported);
        for (Game g : candidates) {
            String fingerprint=fingerprint(g);
            if(fingerprints.add(fingerprint)) {
                g.importCollectionId=collectionId;g.engineGame=engineGame;
                g.id=legacy?fingerprint:"user-"+collectionId.substring("collection-".length())+"-"+fingerprint.substring("import-".length());
                next.add(g);
            }
        }
        int added = next.size() - imported.size();
        if (added == 0) return 0;
        if (next.size() > 5000) throw new IllegalArgumentException("This import exceeds the 5,000 imported-game library limit.");
        List<ImportedCollection> nextCollections=new ArrayList<>(collections);
        if(legacy) {
            int oldCount=0,index=-1;
            for(int i=0;i<nextCollections.size();i++)if(nextCollections.get(i).id.equals(collectionId)){index=i;oldCount=nextCollections.get(i).gameCount;break;}
            ImportedCollection group=new ImportedCollection(collectionId,"Imported games",false,oldCount+added);
            if(index<0)nextCollections.add(group);else nextCollections.set(index,group);
        } else nextCollections.add(new ImportedCollection(collectionId,name,engineGame,added));
        JSONArray array = new JSONArray(); for (Game g : next) array.put(g.json());
        JSONArray groups=new JSONArray();for(ImportedCollection group:nextCollections)
            groups.put(new JSONObject().put("id",group.id).put("name",group.name).put("engineGame",group.engineGame));
        byte[] bytes = new JSONObject().put("schema",2).put("collections",groups).put("games",array).toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Imported library would exceed 5 MB. Import fewer games.");
        android.util.AtomicFile file = new android.util.AtomicFile(new File(context.getFilesDir(), "imported-games.json"));
        FileOutputStream out = null;
        try { out = file.startWrite(); out.write(bytes); file.finishWrite(out); }
        catch (Exception e) { if (out != null) file.failWrite(out); throw e; }
        games.removeAll(imported); imported.clear(); imported.addAll(next); games.addAll(imported);
        collections.clear();collections.addAll(nextCollections);
        return added;
    }

    private static String collectionName(String name) {
        String clean=name==null?"":name.trim();
        if(clean.isEmpty())throw new IllegalArgumentException("Enter a name for this import collection.");
        if(clean.length()>80)throw new IllegalArgumentException("Collection names can contain at most 80 characters.");
        for(int i=0;i<clean.length();i++)if(Character.isISOControl(clean.charAt(i)))
            throw new IllegalArgumentException("Collection names must use a single line of text.");
        return clean;
    }

    private static List<Game> jsonGames(String text, boolean imported) throws Exception {
        Object root=jsonRoot(text);
        JSONArray list = root instanceof JSONArray ? (JSONArray) root : ((JSONObject) root).getJSONArray("games");
        return jsonGames(list,imported,false);
    }

    private static Object jsonRoot(String text) throws Exception {
        checkJsonDepth(text);
        org.json.JSONTokener tokener = new org.json.JSONTokener(text);
        Object root = tokener.nextValue();
        if (tokener.nextClean() != 0) throw new IllegalArgumentException("Unexpected content after the JSON game pack.");
        if (!(root instanceof JSONArray) && !(root instanceof JSONObject))
            throw new IllegalArgumentException("Import a JSON game array or an object containing games.");
        return root;
    }

    private static List<Game> jsonGames(JSONArray list,boolean imported,boolean saved) throws Exception {
        if (list.length() > 5000) throw new IllegalArgumentException("Too many games in one file.");
        List<Game> output = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject j = list.getJSONObject(i); Game g = new Game();
            g.event = j.optString("event", "Imported game"); g.round = j.optString("round", "");
            g.date = j.optString("date", ""); g.red = j.optString("red", "Red"); g.black = j.optString("black", "Black");
            g.result = j.optString("result", "*"); g.sourceUrl = j.optString("sourceUrl", "");
            g.startFen = j.optString("startFen", ""); g.timeControl=j.optString("timeControl", ""); g.collection=j.optString("collection", ""); g.imported = imported;
            g.engineGame=j.optBoolean("engineGame",false);
            g.importCollectionId=saved?j.optString("importCollectionId",""):"";
            JSONArray moves = j.getJSONArray("moves");
            if (moves.length() > MAX_PLIES) throw new IllegalArgumentException("Game " + (i+1) + " exceeds 2,000 half-moves.");
            // Shipped records are canonical and exhaustively checked by the corpus audit.
            // Normalize untrusted imports before validation, storage and deduplication.
            for (int k = 0; k < moves.length(); k++) {
                String move=moves.getString(k);g.moves.add(imported?Position.normalizeMove(move):move);
            }
            if (imported) validate(g);
            String stableId=imported&&!saved?"":j.optString("id", "");
            if(saved&&!stableId.matches("[a-zA-Z0-9-]{1,128}"))throw new IOException("Invalid saved game ID");
            g.id = stableId.isEmpty()?fingerprint(g):stableId; output.add(g);
        }
        return output;
    }

    /** Android's JSONTokener recurses before schema validation; bound its stack use. */
    private static void checkJsonDepth(String text) {
        int depth=0;char quote=0;boolean escaped=false,lineComment=false,blockComment=false,literal=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i),next=i+1<text.length()?text.charAt(i+1):0;
            if(lineComment){if(c=='\n'||c=='\r'){lineComment=false;literal=false;}continue;}
            if(blockComment){if(c=='*'&&next=='/'){blockComment=false;i++;}continue;}
            if(quote!=0){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c==quote){quote=0;literal=true;}continue;}
            if((c=='"'||c=='\'')&&!literal){quote=c;continue;}
            // Match the Android parser's accepted comments without counting their text.
            if(c=='#'){lineComment=true;continue;}
            if(c=='/'&&next=='/'){lineComment=true;i++;continue;}
            if(c=='/'&&next=='*'){blockComment=true;i++;continue;}
            if(c=='['||c=='{'){
                literal=false;if(++depth>32)throw new IllegalArgumentException("JSON nesting exceeds the 32-level import limit.");
            } else if(c==']'||c=='}') {
                literal=false;if(--depth<0)throw new IllegalArgumentException("Unbalanced JSON container.");
            } else if(Character.isWhitespace(c)||c==','||c==':'||c=='='||c==';')literal=false;
            else literal=true;
        }
    }

    private static void validate(Game g) {
        metadata("Event",g.event,1024);metadata("Round",g.round,256);metadata("Date",g.date,128);
        metadata("Red player",g.red,256);metadata("Black player",g.black,256);metadata("Result",g.result,16);
        metadata("Source URL",g.sourceUrl,4096);metadata("Time control",g.timeControl,128);metadata("FEN",g.startFen,512);metadata("Collection",g.collection,1024);
        if (g.moves.isEmpty()) throw new IllegalArgumentException(g.title() + ": no moves found.");
        if (g.moves.size() > MAX_PLIES) throw new IllegalArgumentException(g.title() + ": exceeds 2,000 half-moves.");
        Position position = g.initial();
        validateStart(position);
        for (int i = 0; i < g.moves.size(); i++) try { position = position.play(g.moves.get(i)); }
        catch (RuntimeException e) { throw new IllegalArgumentException(g.title() + ": illegal move at half-move " + (i+1) + " (" + g.moves.get(i) + "). " + e.getMessage()); }
        if (!Arrays.asList("1-0", "0-1", "1/2-1/2", "*").contains(g.result))
            throw new IllegalArgumentException(g.title() + ": unsupported result " + g.result);
    }
    private static void metadata(String field,String value,int maximum) {
        if(value!=null&&value.length()>maximum)throw new IllegalArgumentException(field+" exceeds the "+maximum+" character metadata limit.");
    }

    /** Sparse study positions are welcome; impossible piece geometry is rejected at import. */
    private static void validateStart(Position position) {
        int[][] counts=new int[2][128];
        for(int index=0;index<90;index++) {
            char piece=position.board[index];if(piece==0)continue;
            boolean red=Character.isUpperCase(piece);char kind=Character.toUpperCase(piece);int side=red?0:1;
            int maximum=kind=='K'?1:kind=='P'?5:2;
            if(++counts[side][kind]>maximum)throw new IllegalArgumentException("Starting position has too many "+kind+" pieces for "+(red?"Red":"Black")+".");
            String square=Position.square(index);int file=index%9,row=index/9;
            if(kind=='K'&&(file<3||file>5||(red?(row<7):(row>2))))
                throw new IllegalArgumentException("Starting position: the "+(red?"Red":"Black")+" general must be inside its own palace.");
            if(kind=='A'&&!Arrays.asList(red?new String[]{"d0","f0","e1","d2","f2"}:new String[]{"d9","f9","e8","d7","f7"}).contains(square))
                throw new IllegalArgumentException("Starting position: advisor on an unreachable square ("+square+").");
            if(kind=='B'&&!Arrays.asList(red?new String[]{"c0","g0","a2","e2","i2","c4","g4"}:new String[]{"c9","g9","a7","e7","i7","c5","g5"}).contains(square))
                throw new IllegalArgumentException("Starting position: elephant on an unreachable square ("+square+").");
            if(kind=='P'&&(red?(row>6||row>=5&&file%2!=0):(row<3||row<=4&&file%2!=0)))
                throw new IllegalArgumentException("Starting position: pawn on an unreachable square ("+square+").");
        }
        if(position.isInCheck(!position.redToMove))
            throw new IllegalArgumentException("Starting position: the side that just moved is still in check.");
    }

    private static String fingerprint(Game g) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(g.initial().toFen().getBytes(StandardCharsets.UTF_8));
        for (String move : g.moves) md.update(move.getBytes(StandardCharsets.US_ASCII));
        StringBuilder key = new StringBuilder("import-"); for (byte b : md.digest()) key.append(String.format(Locale.ROOT, "%02x", b));
        return key.toString();
    }

    public static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = in.read(buffer)) != -1) { if (out.size()+count > MAX_BYTES) throw new IOException("File exceeds the 5 MB import limit."); out.write(buffer, 0, count); }
        return out.toString("UTF-8");
    }

    public static String export(Game game, List<String> moves, boolean variation) {
        StringBuilder s = new StringBuilder();
        tag(s,"Event", game.event + (variation ? " · analysis variation" : "")); tag(s,"Date",game.date);
        tag(s,"Round",game.round); tag(s,"Red",game.red); tag(s,"Black",game.black);
        tag(s,"Result",variation ? "*" : game.result); tag(s,"Variant","Xiangqi"); tag(s,"Format","ICCS");
        if (!game.timeControl.isEmpty()) tag(s,"TimeControl",game.timeControl);
        if (!game.collection.isEmpty()) tag(s,"Collection",game.collection);
        if (!game.sourceUrl.isEmpty()) tag(s,"Source",game.sourceUrl);
        if (!game.startFen.isEmpty()) { tag(s,"SetUp","1"); tag(s,"FEN",game.startFen); }
        s.append('\n');
        boolean startsRed = game.initial().redToMove;
        for (int i=0; i<moves.size(); i++) {
            if ((i + (startsRed ? 0 : 1)) % 2 == 0) s.append((i+(startsRed?0:1))/2+1).append(". ");
            else if (i==0) s.append("1... ");
            s.append(moves.get(i)).append(' '); if (i%12==11) s.append('\n');
        }
        return s.append(variation ? "*" : game.result).append('\n').toString();
    }
    private static void tag(StringBuilder s,String name,String value) { s.append('[').append(name).append(" \"").append(value.replace("\\","\\\\").replace("\"","\\\"").replace('\n',' ')).append("\"]\n"); }
    private static String value(String value,String fallback) { return value==null || value.trim().isEmpty() ? fallback : value; }
}
