package org.xiangqi.study;

import org.xiangqi.study.core.GameDateSort;
import org.xiangqi.study.core.PlayerNames;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Worker-owned folded metadata and sorted collection snapshots. No Android/UI work. */
final class LibrarySearchIndex {
    static final class Result {
        final List<GameRepository.Game> ordered,matches;
        Result(List<GameRepository.Game> ordered,List<GameRepository.Game> matches){this.ordered=ordered;this.matches=Collections.unmodifiableList(matches);}
    }
    private static final class Entry {
        final GameRepository.Game game;final String text;
        Entry(GameRepository.Game game){this.game=game;text=PlayerNames.fold(PlayerNames.searchText(game.red)+" "+PlayerNames.searchText(game.black)+" "+game.event+" "+game.round+" "+game.collection);}
    }
    private final Map<String,List<Entry>> scopes=new HashMap<>(),orders=new HashMap<>();

    LibrarySearchIndex(List<GameRepository.Game> games){
        for(GameRepository.Game game:games){checkCancelled();String scope=game.imported?"import:"+game.importCollectionId:game.engineGame?"engine":"human";scopes.computeIfAbsent(scope,key->new ArrayList<>()).add(new Entry(game));}
    }
    Result search(String scope,String order,String query){
        checkCancelled();String key=scope+"\n"+GameDateSort.order(order);List<Entry> entries=orders.get(key);
        if(entries==null){entries=Collections.unmodifiableList(GameDateSort.sorted(scopes.getOrDefault(scope,Collections.emptyList()),entry->entry.game.date,order));checkCancelled();orders.put(key,entries);}
        String needle=PlayerNames.fold(query).trim();List<GameRepository.Game> ordered=new ArrayList<>(entries.size()),matches=new ArrayList<>();
        for(Entry entry:entries){checkCancelled();ordered.add(entry.game);if(needle.isEmpty()||entry.text.contains(needle))matches.add(entry.game);}
        return new Result(Collections.unmodifiableList(ordered),matches);
    }
    private static void checkCancelled(){if(Thread.currentThread().isInterrupted())throw new CancellationException("Library search cancelled");}
}
