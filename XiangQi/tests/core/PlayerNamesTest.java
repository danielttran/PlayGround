package org.xiangqi.study.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Country spellings, source variants, graceful imports, and optional pack coverage. */
public final class PlayerNamesTest {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        check("Lai Ly Huynh".equals(PlayerNames.readable("赖理兄")),"Vietnamese champion spelling");
        check("Nguyen Thanh Bao".equals(PlayerNames.readable("Nguyen Thanh Bao · 阮成保")),"Decorated source alias resolves");
        check(PlayerNames.display("吴宗翰").startsWith("Wu Zonghan (Alvin Woo Tsung Han)"),"Singapore official registered spelling");
        check(PlayerNames.searchText("吴宗翰").contains("andy wo"),"User supplied alias remains searchable");
        check(PlayerNames.searchText("吴宗翰").contains("andy wu"),"Alternative user supplied alias remains searchable");
        check("Hua Say Ty".equals(PlayerNames.readable("许松浩")),"French participant uses established form");
        check("Dam Thi Thuy Dung".equals(PlayerNames.readable("谭氏垂容")),"Vietnamese participant is not Mandarin transliterated");
        check("Sura Sinthuyodsakun".equals(PlayerNames.readable("单文杰")),"Thai participant uses registration spelling");
        check("Aung Naing Win".equals(PlayerNames.readable("匡家卓")),"Burmese registration spelling");
        check("Kani Hiroaki".equals(PlayerNames.readable("可儿宏晖")),"Japanese registration spelling");
        check("Kieu Bich Thuy".equals(PlayerNames.readable("乔碧")),"Truncated record crosschecked against source title");
        check("Liu Jiaqi".equals(PlayerNames.readable("刘嘉")),"Second truncated record crosschecked against source title");
        check("Li Kang Wah".equals(PlayerNames.readable("李镜华 Li Jinghua")),"Established Hong Kong form takes display priority");
        check(PlayerNames.searchText("李镜华 Li Jinghua").contains("li jinghua"),"Source spelling remains searchable");
        check("Joachim Schmidt-Brauns".equals(PlayerNames.readable("德国 SCHMIDT BRAUNS Joachim")),"German country-prefixed source display");
        check("Unknown imported player".equals(PlayerNames.display("Unknown imported player")),"Unknown Latin import stays unchanged");
        check("张三".equals(PlayerNames.display("张三")),"Unknown Chinese import stays unchanged");
        check("".equals(PlayerNames.display(null)),"Missing optional source name is safe");
        check(PlayerNames.fold("Nguyễn Thành Bảo").equals("nguyen thanh bao"),"Diacritic-insensitive search");
        check(PlayerNames.fold("Đặng Hùng Việt").equals("dang hung viet"),"Vietnamese crossed-D search");
        check("Yu Jianguo".equals(PlayerNames.readable("余建国")),"Contemporary Italian federation spelling");
        check(!PlayerNames.pinyinFallback("余建国"),"Contemporary ranking confirms the established Italian participant spelling");
        check(!PlayerNames.pinyinFallback("邓雄越"),"Verified Vietnamese form is not a fallback");
        check(PlayerNames.source("胡荣华").startsWith("https://www.wxf-xiangqi.org/"),"Curated spelling carries its primary source");
        for(String arg:args)for(String original:Files.readAllLines(Path.of(arg),StandardCharsets.UTF_8)) {
            if(original.codePoints().noneMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN))continue;
            check(PlayerNames.mapped(original),"Every bundled Chinese source name is mapped: "+original);
            check(PlayerNames.readable(original).matches("[A-Za-z].*"),"Readable spelling starts in Latin letters: "+original);
            check(!PlayerNames.source(original).isEmpty(),"Each mapping has source metadata: "+original);
        }
        System.out.println("Player names: "+assertions+" assertions passed");
    }
}
