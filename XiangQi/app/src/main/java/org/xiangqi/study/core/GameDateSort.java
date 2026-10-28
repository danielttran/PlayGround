package org.xiangqi.study.core;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Complete recorded dates sort chronologically; unknown/partial dates never invent a day. */
public final class GameDateSort {
    private GameDateSort() {}
    public static LocalDate completeDate(String value) {
        if(value==null)return null;
        String date=value.trim();
        if(!date.matches("[0-9]{4}[-.][0-9]{2}[-.][0-9]{2}")||date.charAt(4)!=date.charAt(7))return null;
        try {
            int year=Integer.parseInt(date.substring(0,4));if(year<1)return null;
            return LocalDate.of(year,Integer.parseInt(date.substring(5,7)),Integer.parseInt(date.substring(8,10)));
        } catch(DateTimeException invalid) { return null; }
    }
    public static String order(String value) {
        return "oldest".equals(value)||"collection".equals(value)?value:"newest";
    }
    public static <T> List<T> sorted(List<T> input,Function<T,String> dates,String requestedOrder) {
        List<T> output=new ArrayList<>(input);String mode=order(requestedOrder);
        if("collection".equals(mode))return output;
        Map<T,LocalDate> known=new IdentityHashMap<>();for(T item:input)known.put(item,completeDate(dates.apply(item)));
        output.sort((a,b)->{
            LocalDate first=known.get(a),second=known.get(b);
            if(first==null)return second==null?0:1;
            if(second==null)return -1;
            return "oldest".equals(mode)?first.compareTo(second):second.compareTo(first);
        });
        // List.sort is stable: equal and undated records retain their collection order.
        return output;
    }
}
