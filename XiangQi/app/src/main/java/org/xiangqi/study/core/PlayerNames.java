package org.xiangqi.study.core;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Display/search aliases only. Game metadata and exported source names are never rewritten. */
public final class PlayerNames {
    private static final String ENTRY="https://www.wxf-xiangqi.org/images/2025wxf_shanghai/19th_wxf_entry_nameb.pdf";
    private static final String RESULTS="https://www.wxf-xiangqi.org/images/2025wxf_shanghai2/2025_19th_World_Xiangqi_Championship19.pdf";
    private static final String TITLES="https://www.wxf-xiangqi.org/images/Player_Titled_List/20221209_WXF_Official_list_of_titles20221209-.pdf";
    private static final String HISTORICAL="https://www.wxf-xiangqi.org/images/past_wxc_champions/2022_2-Mens_Individual.pdf";
    private static final String NON_CHNVIE="https://www.wxf-xiangqi.org/images/past_wxc_champions/2022_3-Non_CHNVIE_Individual.pdf";
    private static final String PROGRAM_2023="https://www.wxf-xiangqi.org/images/2023_houston_wxc/Brochure_Content_11-231.pdf";
    private static final String MEN_2022="https://www.wxf-xiangqi.org/images/2022_Sarawak_17wxc_pairing_result_1028/2022_17wxf__m_ranking2.pdf";
    private static final String WOMEN_2022="https://www.wxf-xiangqi.org/images/2022_Sarawak_17wxc_pairing_result_1028/2022_17wxf__w_ranking.pdf";
    private static final String FUTA_2023="https://vietnamchess.vn/document/2023/futa6-bulletin-full.pdf";
    private static final Map<String,Name> NAMES=new HashMap<>(),EXACT=new HashMap<>();
    private static final class Name {
        final String chinese,readable,aliases,source;final boolean pinyin;
        Name(String chinese,String readable,String aliases,String source,boolean pinyin){this.chinese=chinese;this.readable=readable;this.aliases=aliases;this.source=source;this.pinyin=pinyin;}
    }
    static {
        // Bilingual entry/results lists preserve each association's established spelling.
        add("尹昇","Yin Sheng","",ENTRY);
        add("赖理兄","Lai Ly Huynh","Lai Li Huynh Lại Lý Huynh",ENTRY);
        add("阮成保","Nguyen Thanh Bao","Nguyễn Thành Bảo",ENTRY);
        add("孟繁睿","Meng Fanrui","",ENTRY);
        add("唐丹","Tang Dan","",ENTRY);
        add("陈丽淳","Chen Lichun","",ENTRY);
        add("吴宗翰","Wu Zonghan (Alvin Woo Tsung Han)","Alvin Woo Tsung Han Woo Tsung Han Alvin Andy Wo Andy Wu",ENTRY);
        add("于文彬","Winston Yu","Yu Wenbin",ENTRY);
        add("柯棋斯基","Sergej Korchitskij","Sergey Korchitsky",ENTRY);
        add("林廷安","Ling Ting Ang","",ENTRY);
        add("刘桂华","Lau Kui Hwa","",ENTRY);
        add("顾亿庆","Gu Yi Qing","Gu Yiqing",ENTRY);
        add("刘其昌","Leo Lau","Lau Kei Cheong",ENTRY);
        add("黄颢颐","Ho Yee Wong","Huang Haoyi 黃顥頤",RESULTS);alias("黃顥頤","黄颢颐");
        add("陈发佐","Fazhuo Chen","Chen Fazuo",ENTRY);
        add("陈峻","Chen Jun","",ENTRY);
        add("吴彩芳","Wu Caifang","",ENTRY);
        add("冯家俊","Fung Tony Ga Zen","Feng Jiajun Tony Fung Fung Ga Zen",ENTRY);
        add("黄学谦","Wong Hok Him","Huang Xueqian",ENTRY);
        add("林嘉欣","Lam Ka Yan","Lin Jiaxin",ENTRY);
        add("梁玮珊","Leung Wai Shan","Liang Weishan",ENTRY);
        add("林进强","Darjatno Hardjo","",ENTRY);
        add("方毅","Fang Yi","",ENTRY);
        add("王大伟","Wang Dawei","",ENTRY);
        add("古贝蒂","Agostino Guberti","",ENTRY);
        add("纳斯塔西奥","Davide Nastasio","",ENTRY);
        add("刘嘉琪","Liu Jiaqi","",ENTRY);alias("刘嘉","刘嘉琪");
        add("张和","Zhang He","",ENTRY);
        add("可儿宏晖","Kani Hiroaki","",ENTRY);
        add("所司和晴","Shoshi Kazuharu","",ENTRY);
        add("曾根敏彦","Sone Toshihiko","",ENTRY);
        add("麻生达郎","Aso Tatsuro","",ENTRY);
        add("甘建希","Kam Kin Hei","",ENTRY);
        add("苏俊豪","Sou Chon Hou","",ENTRY);
        add("李凯雯","Li Kevin","Kevin Li",ENTRY);
        add("黎德志","Li Dezhi","",ENTRY);
        add("李虢綕","Lee Wen Ze","Li Wenze",ENTRY);
        add("唐语萱","Thong Yu Xuan","",ENTRY);
        add("黄宇晨","Wong Yu Cheng","",ENTRY);
        add("卢玄斌","Loo Yien Bin","",ENTRY);
        add("郑义霖","Calvin Tay Yelin","",ENTRY);
        add("周盈萱","Chiew Ying Xuan","",ENTRY);
        add("罗宝莹","Law Bou Ying","",ENTRY);
        add("莱恩","Ryan Emmanuel Haris","",ENTRY);
        add("匡家卓","Aung Naing Win","匡家桌",RESULTS);alias("匡家桌","匡家卓");
        add("匡家楼","Htet Myat Thura","",ENTRY);
        add("李志珊","Li Zhi Shan","Li Zhishan",ENTRY);
        add("吴永生","Ng Wing Sang","Ng Wing San",ENTRY);
        add("庄宏明","Chong Heung Ming","",ENTRY);
        add("陈万威","Chen Wanwei","Chen Wan Wei",ENTRY);
        add("萨波夫斯基","Tsapovskii Igor","Igor Tsapovskii",ENTRY);
        add("梅荻涵","Mai Ti Tkhan","",ENTRY);
        add("阮忠周","Nguyen Trung Chau","",ENTRY);
        add("斯克利亚罗夫","Skliarov Sergei","Sergei Skliarov",ENTRY);
        add("梅德韦杰夫","Medvedev Vladimir","Vladimir Medvedev",ENTRY);
        add("陈茗芳","Fiona Tan Min Fang","",ENTRY);
        add("吴兰香","Ngo Lan Huong","Ngô Lan Hương Ngo Lan Huang",RESULTS);
        add("单文杰","Sura Sinthuyodsakun","",ENTRY);
        add("李玉芬","Daungruadee Saelee","",ENTRY);
        add("林文瑶","Suwannee Mee-Asa","",ENTRY);
        add("葛振衣","Ge Jen-Yi","Ge Zhenyi",ENTRY);
        add("郭胜文","Kuo Sheng Wen","",ENTRY);
        add("李思谊","Li Sih-Yi","",ENTRY);
        add("林祐萱","Lin Yu-Hsuan","",ENTRY);
        add("李铭坚","Mingjian Li","Li Mingjian",ENTRY);
        add("龚勤","Qin Gong","Gong Qin",ENTRY);
        add("张婉昱","Wanyu Zhang","Zhang Wanyu",ENTRY);
        add("谭氏垂容","Dam Thi Thuy Dung","Đàm Thị Thùy Dung",ENTRY);
        add("乔碧水","Kieu Bich Thuy","Kiều Bích Thủy",ENTRY);alias("乔碧","乔碧水");
        add("阮日光","Quang Nhat Nguyen","阮光日",RESULTS);alias("阮光日","阮日光");
        // Additional adult 2022/2023 championships: official bilingual program,
        // results and federation bulletin preserve association-specific names.
        add("阮明日光","Nguyen Minh Nhat Quang","Nguyễn Minh Nhật Quang",PROGRAM_2023);
        add("阮黄燕","Nguyen Hoang Yen","Nguyễn Hoàng Yến 阮黃燕",PROGRAM_2023);alias("阮黃燕","阮黄燕");
        add("黎氏金鸾","Le Thi Kim Loan","Lê Thị Kim Loan 黎氏金銮",PROGRAM_2023);alias("黎氏金銮","黎氏金鸾");
        add("刘亿豪","Low Yi Hao","Liu Yihao",PROGRAM_2023);
        add("叶乃传","Yek Nai Tuong","",PROGRAM_2023);
        add("梅俊海","Matthew Boey","",PROGRAM_2023);
        add("洪千惠","Vespera Ang Cheng Wuey","",PROGRAM_2023);
        add("陈慧盈","Tan Hui Ying","",PROGRAM_2023);
        add("田荷","Tian He","田菏",PROGRAM_2023);alias("田菏","田荷");
        add("谢汶谕","Hsieh Wenyu","Hsieh-Wenyu",PROGRAM_2023);
        add("郭宝荣","Radtai Lokutarapol","",PROGRAM_2023);
        add("武冕","Mian Wu","Wu Mian",PROGRAM_2023);
        add("奥利维尔","Olivier Tessier","",PROGRAM_2023);
        add("许鲁斌","Robin Hii Lu Bin","Hii Lu Bin",PROGRAM_2023);
        add("素丽达","Suratsada Promsirinimit","",PROGRAM_2023);
        // The 2023 program identifies the only corresponding Taiwanese and
        // Japanese entrants; a few round records misspell their Chinese names.
        alias("谢汶瀚","谢汶谕");alias("麻生太郎","麻生达郎");
        add("唐思楠","Tang Sinan","",PROGRAM_2023);
        add("余欣如","Jee Xin Ru","",TITLES);
        add("刘欢","Liu Huan","",TITLES);
        add("左文静","Zuo Wenjing","",TITLES);
        add("彭柔安","Peng Jou An","",TITLES);
        add("沈毅豪","Sim Yip How","",TITLES);
        add("郑彦隆","Cheng Yin Lung","",TITLES);
        add("郑德铭","Cheng Tak Ming Clement","",TITLES);
        add("陈幸琳","Chen Xinglin","",TITLES);
        add("黎英豪","Le Anh Hao","Lê Anh Hào",TITLES);
        add("郭泽辉","Tawee Danwirunhawanich","",MEN_2022);
        add("布莱恩乔治","Brian George A/L A S Anthony Dass","布莱恩乔",MEN_2022);alias("布莱恩乔","布莱恩乔治");
        add("李雯宜","Lee Mun Yee","",WOMEN_2022);
        add("詹敏珠","Chieng Ming Choo","Cheng Ming Chuo",WOMEN_2022);
        add("阮黄林","Nguyen Hoang Lam","Nguyễn Hoàng Lâm",FUTA_2023);
        add("何文进","Ha Van Tien","Hà Văn Tiến",FUTA_2023);
        add("宗室日新","Ton That Nhat Tan","Tôn Thất Nhật Tân 孙室日新",FUTA_2023);alias("孙室日新","宗室日新");
        add("汪洋北","Uong Duong Bac","Uông Dương Bắc",FUTA_2023);
        // Historical names verified against WXF title and bilingual winners tables.
        add("胡荣华","Hu Ronghua","",TITLES);
        add("李来群","Li Laiqun","",TITLES);
        add("赵国荣","Zhao Guorong","Zhao GuoRong",TITLES);
        add("吕钦","Lv Qin","Lyu Qin Lu Qin Lü Qin",TITLES);
        add("许银川","Xu Yinchuan","",TITLES);
        add("陶汉明","Tao Hanming","",TITLES);
        add("于幼华","Yu Youhua","",TITLES);
        add("马仲威","Ma Chung Wei","Ma Zhongwei",TITLES);
        add("李锦欢","Lei Kam Fun","Li Jinhuan",TITLES);
        add("赵汝权","Chiu Yu Kuen","Chiu Yukuen Zhao Ruquan",TITLES);
        add("牟海勤","Mou Haiqin","Haiqin Mou",TITLES);
        add("许松浩","Hua Say Ty","Xu Songhao",TITLES);
        add("梁达民","Leung Tat Man","Liang Damin",TITLES);
        add("李家庆","Lee Kah Kheng","Li Jiaqing",TITLES);
        add("郑亚生","Trenh A Sang","Truong A Sang Trương Á Sáng Trềnh A Sáng Trình A Sáng",TITLES);
        add("张亚明","Truong A Minh","Trương Á Minh",TITLES);
        add("陈捷裕","Tan Cheak Joo","Chen Jieyu",TITLES);
        add("刘国华","Liu Kuo Hua","Liu Guohua",TITLES);
        add("李镜华","Li Kang Wah","Li Jinghua",TITLES);
        add("阎文清","Yan Wenqing","",TITLES);
        add("陈振国","Chen Cheng Kuo","Chen Zhenguo",TITLES);
        add("陈国良","Chin Kok Liang","Chen Guoliang",TITLES);
        add("刘永德","Lau Veng Tak","Liu Yongde",TITLES);
        add("徐宝坤","Choi Pou Kuan","CHOI Pou Kuan",TITLES);
        add("甄达新","Yan Dot Sun","Zhen Daxin",TITLES);
        add("黎池刚","Lai Chi Kong","Li Chigang",TITLES);
        add("何荣耀","Hor Weng Yeow","He Rongyao",HISTORICAL);
        add("邓雄越","Dang Hung Viet","Đặng Hùng Việt",HISTORICAL);
        add("郭英秀","Quach Anh Tu","Quách Anh Tú",NON_CHNVIE);
        add("小俣光夫","Omata Mitsuo","",NON_CHNVIE);
        add("梅清明","Mai Thanh Minh","Mai Thanh Minh", "https://www.wxf-xiangqi.org/index.php?id=406&option=com_content&view=article&lang=en");
        add("吴贵临","Wu Guilin","Wu Kui-lin Wu Kwei Lin", "https://www.wxf-xiangqi.org/index.php?id=444&option=com_content&view=article&lang=en");
        add("高维铉","Gao Weixuan","", "https://www.wxf-xiangqi.org/index.php?id=1011&option=com_content&view=article&lang=en");
        add("吴裕成","Ung Yu Dau","", "https://vietnamchess.com.vn/news/world-xq2007");
        // The Italian federation's contemporary January 1999 ranking confirms
        // this spelling for the Italian participant recorded by WXF in 1999.
        add("余建国","Yu Jianguo","", "https://www.pi.infn.it/~carosi/chess/xqmtb50.pdf");
        EXACT.put("德国 SCHMIDT BRAUNS Joachim",new Name("","Joachim Schmidt-Brauns","SCHMIDT BRAUNS Joachim",NON_CHNVIE,false));
    }
    private PlayerNames() {}
    private static void add(String chinese,String readable,String aliases,String source){NAMES.put(chinese,new Name(chinese,readable,aliases,source,false));}
    private static void alias(String variant,String canonical){NAMES.put(variant,NAMES.get(canonical));}
    private static Name name(String original){if(original==null)return null;Name exact=EXACT.get(original.trim());return exact==null?NAMES.get(han(original)):exact;}
    private static String han(String original){StringBuilder value=new StringBuilder();original.codePoints().filter(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN).forEach(value::appendCodePoint);return value.toString();}
    public static boolean mapped(String original){return name(original)!=null;}
    public static boolean pinyinFallback(String original){Name value=name(original);return value!=null&&value.pinyin;}
    public static String readable(String original){Name value=name(original);return value==null?(original==null?"":original):value.readable;}
    public static String display(String original){Name value=name(original);return value==null?(original==null?"":original):value.readable+(value.pinyin?" [Pinyin]":"")+(value.chinese.isEmpty()?"":" · "+value.chinese);}
    public static String source(String original){Name value=name(original);return value==null?"":value.source;}
    public static String searchText(String original){Name value=name(original);return fold((original==null?"":original)+(value==null?"":" "+value.readable+" "+value.chinese+" "+value.aliases));}
    public static String fold(String text){return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT);}
}
