package org.xiangqi.study.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Date precision and ordering regressions, runnable without Android. */
public final class DateSortTest {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static final class Record {
        final String name,date;
        Record(String name,String date){this.name=name;this.date=date;}
    }
    private static String names(List<Record> records){List<String> names=new ArrayList<>();for(Record record:records)names.add(record.name);return String.join(",",names);}
    public static void main(String[] ignored) {
        for(String value:Arrays.asList(null,""," ","1982","1982.??.??","1982-05-??","????.??.??","2025-2-03","2025.01-02","2025-01.02","2025-13-01","2025-00-01","2025-04-31","2025-02-29","0000-01-01","2025-01-01extra"))
            check(GameDateSort.completeDate(value)==null,"Unknown, partial or invalid date must stay unknown: "+value);
        check(LocalDate.of(2024,2,29).equals(GameDateSort.completeDate("2024-02-29")),"Leap day is valid");
        check(LocalDate.of(1982,10,4).equals(GameDateSort.completeDate("1982.10.04")),"PGN dotted dates are supported");
        check(LocalDate.of(2025,1,2).equals(GameDateSort.completeDate(" 2025-01-02 ")),"Surrounding whitespace is ignored");
        check("newest".equals(GameDateSort.order(null)),"Missing preference defaults to newest");
        check("newest".equals(GameDateSort.order("bogus")),"Invalid preference defaults to newest");
        List<Record> input=Arrays.asList(new Record("partial","1982.??.??"),new Record("new","2025-09-27"),
            new Record("old","1982.10.04"),new Record("tie","2025.09.27"),new Record("invalid","2023-02-29"),
            new Record("leap","2024-02-29"),new Record("unknown",""));
        String original=names(input);
        check("new,tie,leap,old,partial,invalid,unknown".equals(names(GameDateSort.sorted(input,r->r.date,"newest"))),"Newest first is stable, undated last");
        check("old,leap,new,tie,partial,invalid,unknown".equals(names(GameDateSort.sorted(input,r->r.date,"oldest"))),"Oldest first is stable, undated last");
        check(original.equals(names(GameDateSort.sorted(input,r->r.date,"collection"))),"Collection order is retained");
        check(original.equals(names(input)),"Sorting never mutates the library");
        check(GameDateSort.sorted(input,r->r.date,"collection")!=input,"Collection order also returns a separate list");
        check(GameDateSort.sorted(new ArrayList<Record>(),r->r.date,"newest").isEmpty(),"Empty collections are valid");
        System.out.println("Date sorting: "+assertions+" assertions passed");
    }
}
