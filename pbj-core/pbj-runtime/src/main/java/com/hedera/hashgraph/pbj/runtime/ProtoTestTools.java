package com.hedera.hashgraph.pbj.runtime;

import com.hedera.hashgraph.pbj.runtime.io.Bytes;
import com.hedera.hashgraph.pbj.runtime.io.DataBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Static tools and test cases used by generated test classes.
 * <p>
 * It was very slow in testing when new buffers were created each test, so there is a thread local cache of buffers
 * here. That are used in unit tests. This saves a huge amount of GC work and reduced test time from hours to minutes.
 * </p>
 */
public final class ProtoTestTools {

    /** Size for reusable test buffers */
    private static final int BUFFER_SIZE = 64*1024*1024;

    /** Instance should never be created */
    private ProtoTestTools() {}

    /** Thread local set of reusable buffers */
    private static final ThreadLocal<DataBuffer> THREAD_LOCAL_BUFFERS =
            ThreadLocal.withInitial(() -> DataBuffer.allocate(BUFFER_SIZE, true));

    /** Thread local set of reusable buffers, second buffer for each thread */
    private static final ThreadLocal<DataBuffer> THREAD_LOCAL_BUFFERS_2 =
            ThreadLocal.withInitial(() -> DataBuffer.allocate(BUFFER_SIZE, true));

    /**
     * Get the thread local instance of DataBuffer, reset and ready to use.
     *
     * @return a DataBuffer that can be reused by current thread
     */
    public static DataBuffer getThreadLocalDataBuffer() {
        final var local = THREAD_LOCAL_BUFFERS.get();
        local.reset();
        return local;
    }

    /**
     * Get the second thread local instance of DataBuffer, reset and ready to use.
     *
     * @return a DataBuffer that can be reused by current thread
     */
    public static DataBuffer getThreadLocalDataBuffer2() {
        final var local = THREAD_LOCAL_BUFFERS_2.get();
        local.reset();
        return local;
    }

    /** Thread local set of reusable buffers */
    private static final ThreadLocal<ByteBuffer> THREAD_LOCAL_BYTE_BUFFERS =
            ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(BUFFER_SIZE));

    /**
     * Get the thread local instance of ByteBuffer, reset and ready to use.
     *
     * @return a ByteBuffer that can be reused by current thread
     */
    public static ByteBuffer getThreadLocalByteBuffer() {
        final var local = THREAD_LOCAL_BYTE_BUFFERS.get();
        local.clear();
        return local;
    }

    /**
     * Take a list of objects and create a new list with those objects wrapped in optionals and adding a empty optional.
     *
     * @param list List of objects to wrap
     * @return list of optionals
     * @param <T> type of objects to wrap
     */
    public static <T> List<Optional<T>> makeListOptionals(List<T> list) {
        ArrayList<Optional<T>> optionals = new ArrayList<>(list.size()+1);
        optionals.add(Optional.empty());
        for (T value:list) {
            optionals.add(Optional.ofNullable(value));
        }
        return optionals;
    }

    /**
     * Util method to create a list of lists of objects. Given a list of test cases it creates an empty list and then a
     * sub list of first 3 elements of input {code list}, then the complete input {@code list}. So result is a list of
     * 3 lists.
     *
     * @param list Input list
     * @return list of lists derived from input list
     * @param <T> the type for lists
     */
    public static <T> List<List<T>> generateListArguments(final List<T> list) {
        return List.of(
            Collections.emptyList(),
            list.subList(0,Math.min(3, list.size())),
            list
        );
    }

    // =================================================================================================================
    // Standard lists of values to test with

    /** integer type test cases */
    public static final List<Integer> INTEGER_TESTS_LIST = List.of(Integer.MIN_VALUE, -42, -21, 0, 21, 42, Integer.MAX_VALUE);
    /** unsigned integer type test cases */
    public static final List<Integer> UNSIGNED_INTEGER_TESTS_LIST = List.of(0, 1, 2, Integer.MAX_VALUE);
    /** long type test cases */
    public static final List<Long> LONG_TESTS_LIST = List.of(Long.MIN_VALUE, -42L, -21L, 0L, 21L, 42L, Long.MAX_VALUE);
    /** unsigned long type test cases */
    public static final List<Long> UNSIGNED_LONG_TESTS_LIST = List.of(0L, 21L, 42L, Long.MAX_VALUE);
    /** bytes float test cases */
    public static final List<Float> FLOAT_TESTS_LIST = List.of(Float.MIN_NORMAL, -102.7f, -5f, 1.7f, 0f, 3f, 5.2f, 42.1f, Float.MAX_VALUE);
    /** double type test cases */
    public static final List<Double> DOUBLE_TESTS_LIST = List.of(Double.MIN_NORMAL, -102.7d, -5d, 1.7d, 0d, 3d, 5.2d, 42.1d, Double.MAX_VALUE);
    /** boolean type test cases */
    public static final List<Boolean> BOOLEAN_TESTS_LIST = List.of(true, false);
    /** bytes type test cases */
    public static final List<Bytes> BYTES_TESTS_LIST = List.of(
            Bytes.wrap(new byte[0]),
            Bytes.wrap(new byte[]{0b001}),
            Bytes.wrap(new byte[]{0b001, 0b010, 0b011, (byte)0xFF, Byte.MIN_VALUE, Byte.MAX_VALUE})
    );

    /** Simple multi-line text test block */
    private static final String SAMPLE_TEXT_BLOCK = """
                    To be, or not to be, that is the question:
                    Whether ’tis nobler in the mind to suffer
                    The slings and arrows of outrageous fortune,
                    Or to take arms against a sea of troubles
                    And by opposing end them. To die—to sleep,
                    No more; and by a sleep to say we end
                    The heart-ache and the thousand natural shocks
                    That flesh is heir to: ’tis a consummation
                    Devoutly to be wish’d. To die, to sleep;
                    To sleep, perchance to dream—ay, there’s the rub:
                    For in that sleep of death what dreams may come,
                    When we have shuffled off this mortal coil,
                    Must give us pause—there’s the respect
                    That makes calamity of so long life…""";

    /** UTF-8 language test block containing pangrams in a bunch of languages */
    private static final String UTF8_LANGUAGES_TEXT_BLOCK_1 = """
            English : A quick brown fox jumps over the lazy dog
            Arabic : صِف خَلقَ خَودِ كَمِثلِ الشَمسِ إِذ بَزَغَت — يَحظى الضَجيعُ بِها نَجلاءَ مِعطارِ
            Arabic : نصٌّ حكيمٌ لهُ سِرٌّ قاطِعٌ وَذُو شَأنٍ عَظيمٍ مكتوبٌ على ثوبٍ أخضرَ ومُغلفٌ بجلدٍ أزرق
            Bulgarian : Ах чудна българска земьо, полюшвай цъфтящи жита.
            Bulgarian : Жълтата дюля беше щастлива, че пухът, който цъфна, замръзна като гьон.
            Catalan : «Dóna amor que seràs feliç!». Això, il·lús company geniüt, ja és un lluït rètol blavís d’onze kWh.
            Cherokee : ᎠᏍᎦᏯᎡᎦᎢᎾᎨᎢᎣᏍᏓᎤᎩᏍᏗᎥᎴᏓᎯᎲᎢᏔᎵᏕᎦᏟᏗᏖᎸᎳᏗᏗᎧᎵᎢᏘᎴᎩ ᏙᏱᏗᏜᏫᏗᏣᏚᎦᏫᏛᏄᏓᎦᏝᏃᎠᎾᏗᎭᏞᎦᎯᎦᏘᏓᏠᎨᏏᏕᏡᎬᏢᏓᏥᏩᏝᎡᎢᎪᎢ ᎠᎦᏂᏗᎮᎢᎫᎩᎬᏩᎴᎢᎠᏆᏅᏛᎫᏊᎾᎥᎠᏁᏙᎲᏐᏈᎵᎤᎩᎸᏓᏭᎷᏤᎢᏏᏉᏯᏌᏊ ᎤᏂᏋᎢᏡᎬᎢᎰᏩᎬᏤᎵᏍᏗᏱᎩᎱᎱᎤᎩᎴᎢᏦᎢᎠᏂᏧᏣᏨᎦᏥᎪᎥᏌᏊᎤᎶᏒᎢᎢᏡᎬᎢ ᎹᎦᎺᎵᏥᎻᎼᏏᎽᏗᏩᏂᎦᏘᎾᎿᎠᏁᎬᎢᏅᎩᎾᏂᎡᎢᏌᎶᎵᏎᎷᎠᏑᏍᏗᏪᎩ ᎠᎴ ᏬᏗᏲᏭᎾᏓᏍᏓᏴᏁᎢᎤᎦᏅᏮᏰᎵᏳᏂᎨᎢ.
            Croatian : Gojazni đačić s biciklom drži hmelj i finu vatu u džepu nošnje.
            Czech : Nechť již hříšné saxofony ďáblů rozezvučí síň úděsnými tóny waltzu, tanga a quickstepu.
            Danish : Quizdeltagerne spiste jordbær med fløde, mens cirkusklovnen Walther spillede på xylofon.
            Dzongkha : ཨ་ཡིག་དཀར་མཛེས་ལས་འཁྲུངས་ཤེས་བློའི་གཏེར༎ ཕས་རྒོལ་ཝ་སྐྱེས་ཟིལ་གནོན་གདོང་ལྔ་བཞིན༎ ཆགས་ཐོགས་ཀུན་བྲལ་མཚུངས་མེད་འཇམ་དབྱངསམཐུས༎ མཧཱ་མཁས་པའི་གཙོ་བོ་ཉིད་འགྱུར་ཅིག།ཨ་ཡིག་དཀར་མཛེས་ལས་འཁྲུངས་ཤེས་བློའི་གཏེར༎ ཕས་རྒོལ་ཝ་སྐྱེས་ཟིལ་གནོན་གདོང་ལྔ་བཞིན༎ ཆགས་ཐོགས་ཀུན་བྲལ་མཚུངས་མེད་འཇམ་དབྱངསམཐུས༎ མཧཱ་མཁས་པའི་གཙོ་བོ་ཉིད་འགྱུར་ཅིག།ཨ་ཡིག་དཀར་མཛེས་ལས་འཁྲུངས་ཤེས་བློའི་གཏེར༎ ཕས་རྒོལ་ཝ་སྐྱེས་ཟིལ་གནོན་གདོང་ལྔ་བཞིན༎ ཆགས་ཐོགས་ཀུན་བྲལ་མཚུངས་མེད་འཇམ་དབྱངསམཐུས༎ མཧཱ་མཁས་པའི་གཙོ་བོ་ཉིད་འགྱུར་ཅིག།
            Estonian : Põdur Zagrebi tšellomängija-följetonist Ciqo külmetas kehvas garaažis
            Finnish : Törkylempijävongahdus
            French : Voix ambiguë d’un cœur qui au zéphyr préfère les jattes de kiwi
            German : Victor jagt zwölf Boxkämpfer quer über den großen Sylter Deich
            Greek : Ταχίστη αλώπηξ βαφής ψημένη γη, δρασκελίζει υπέρ νωθρού κυνός Takhístè alôpèx vaphês psèménè gè, draskelízei ypér nòthroý kynós
            Hebrew : דג סקרן שט בים מאוכזב ולפתע מצא חברה dg sqrn šṭ bjM mʾwkzb wlptʿ mṣʾ ḥbrh
            Hindi : ऋषियों को सताने वाले दुष्ट राक्षसों के राजा रावण का सर्वनाश करने वाले विष्णुवतार भगवान श्रीराम, अयोध्या के महाराज दशरथ के बड़े सपुत्र थे।
            Hungarian : Jó foxim és don Quijote húszwattos lámpánál ülve egy pár bűvös cipőt készít.
            Icelandic : Kæmi ný öxi hér, ykist þjófum nú bæði víl og ádrepa.
            Igbo : Nne, nna, wepụ he’l’ụjọ dum n’ime ọzụzụ ụmụ, vufesi obi nye Chukwu, ṅụrịanụ, gbakọọnụ kpaa, kwee ya ka o guzoshie ike; ọ ghaghị ito, nwapụta ezi agwa
            Indonesian : Saya lihat foto Hamengkubuwono XV bersama enam zebra purba cantik yang jatuh dari Alquranmu.
            Italian : Quel vituperabile xenofobo zelante assaggia il whisky ed esclama: alleluja!
            Japanese : あめ つち ほし そら / やま かは みね たに / くも きり むろ こけ / ひと いぬ うへ すゑ / ゆわ さる おふ せよ / えのえを なれ ゐて
            """;

    /** UTF-8 language test block containing pangrams in a bunch of languages, continued */
    private static final String UTF8_LANGUAGES_TEXT_BLOCK_2 = """
            Japanese : あめ つち ほし そら / やま かは みね たに / くも きり むろ こけ / ひと いぬ うへ すゑ / ゆわ さる おふ せよ / えのえを なれ ゐて
            Japanese : 天 地 星 空 / 山 川 峰 谷 / 雲 霧 室 苔 / 人 犬 上 末 / 硫黄 猿 生ふ 為よ / 榎の 枝を 馴れ 居て
            Japanese : いろはにほへと ちりぬるを わかよたれそ つねならむ うゐのおくやま けふこえて あさきゆめみし ゑひもせす（ん）
            Japanese : 色は匂へど 散りぬるを 我が世誰ぞ 常ならむ 有為の奥山 今日越えて 浅き夢見じ 酔ひもせず（ん）
            Javanese : ꧋ ꦲꦤꦕꦫꦏ꧈ ꦢꦠꦱꦮꦭ꧈ ꦥꦝꦗꦪꦚ꧈ ꦩꦒꦧꦛꦔ꧉ Hanacaraka, datasawala, padhajayanya, magabathanga.
            Korean : 키스의 고유조건은 입술끼리 만나야 하고 특별한 기술은 필요치 않다. Kiseu-ui goyujogeoneun ipsulkkiri mannaya hago teukbyeolhan gisureun pilyochi antha.
            Latin: Sic fugiens, dux, zelotypos, quam Karus haberis.
            Malayalam : അജവും ആനയും ഐരാവതവും ഗരുഡനും കഠോര സ്വരം പൊഴിക്കെ ഹാരവും ഒഢ്യാണവും ഫാലത്തില്‍ മഞ്ഞളും ഈറന്‍ കേശത്തില്‍ ഔഷധ എണ്ണയുമായി ഋതുമതിയും അനഘയും ഭൂനാഥയുമായ ഉമ ദുഃഖഛവിയോടെ ഇടതു പാദം ഏന്തി ങ്യേയാദൃശം നിര്‍ഝരിയിലെ ചിറ്റലകളെ ഓമനിക്കുമ്പോള്‍ ബാ‍ലയുടെ കണ്‍കളില്‍ നീര്‍ ഊര്‍ന്നു വിങ്ങി.
            Myanmar : သီဟိုဠ်မှ ဉာဏ်ကြီးရှင်သည် အာယုဝဍ္ဎနဆေးညွှန်းစာကို ဇလွန်ဈေးဘေးဗာဒံပင်ထက် အဓိဋ္ဌာန်လျက် ဂဃနဏဖတ်ခဲ့သည်။
            Norwegian : Vår sære Zulu fra badeøya spilte jo whist og quickstep i min taxi.
            Polish : Jeżu klątw, spłódź Finom część gry hańb!
            Portuguese : Luís argüia à Júlia que «brações, fé, chá, óxido, pôr, zângão» eram palavras do português.
            Romanian : Bând whisky, jazologul șprițuit vomă fix în tequila.
            Russian : Съешь же ещё этих мягких французских булок, да выпей чаю. S’eš’ že eŝë ètih mjagkih francuzskih bulok, da vypej čaju.
            Sanskrit : कः खगौघाङचिच्छौजा झाञ्ज्ञोऽटौठीडडण्ढणः। तथोदधीन् पफर्बाभीर्मयोऽरिल्वाशिषां सहः।।
            Serbian : Gojazni đačić s biciklom drži hmelj i finu vatu u džepu nošnje.
            Slovak : Kŕdeľ šťastných ďatľov učí pri ústí Váhu mĺkveho koňa obhrýzať kôru a žrať čerstvé mäso.
            Slovenian : Besni dirkač iz formule žuga cehu poštarjev.
            Spanish : Benjamín pidió una bebida de kiwi y fresa; Noé, sin vergüenza, la más exquisita champaña del menú.
            Swedish : Yxskaftbud, ge vår WC-zonmö IQ-hjälp.
            Thai : เป็นมนุษย์สุดประเสริฐเลิศคุณค่า กว่าบรรดาฝูงสัตว์เดรัจฉาน จงฝ่าฟันพัฒนาวิชาการ อย่าล้างผลาญฤๅเข่นฆ่าบีฑาใคร ไม่ถือโทษโกรธแช่งซัดฮึดฮัดด่า หัดอภัยเหมือนกีฬาอัชฌาสัย ปฏิบัติประพฤติกฎกำหนดใจ พูดจาให้จ๊ะๆ จ๋าๆ น่าฟังเอยฯ bpenM maH nootH sootL bpraL saehR ritH leertF khoonM khaaF gwaapL raawnM daaM fuungR satL daehM ratH chaanR johngM faaL fanM phatH naaM wiH chaaM gaanM aL yaaF laangH phlaanR reuuM khenL khaaF beeM thaaM khraiM maiF theuuR tho:htF gro:htL chaaengF satH heutH hatH daaL hatL aL phaiM meuuanR geeM laaM atL chaaM saiR bpaL dtiL batL bpraL phriH dtikL daL gamM nohtL jaiM phuutF jaaM haiF jaH jaH jaaR jaaR naaF fangM eeuyM
            Tibetan : ༈ དཀར་མཛེས་ཨ་ཡིག་ལས་འཁྲུངས་ཡེ་ཤེས་གཏེར། །ཕས་རྒོལ་ཝ་སྐྱེས་ཟིལ་གནོན་གདོང་ལྔ་བཞིན། །ཆགས་ཐོགས་ཀུན་བྲལ་མཚུངས་མེད་འཇམ་བྱངས་མཐུས། །མ་ཧཱ་མཁས་པའི་གཙོ་བོ་ཉིད་གྱུར་ཅིག།
            Turkish : Fahiş bluz güvencesi yağdırma projesi çöktü.
            Ukrainian : Чуєш їх, доцю, га? Кумедна ж ти, прощайся без ґольфів!
            Urdu : ٹھنڈ میں، ایک قحط زدہ گاؤں سے گذرتے وقت ایک چڑچڑے، باأثر و فارغ شخص کو بعض جل پری نما اژدہے نظر آئے۔
            Uyghur : ئاۋۇ بىر جۈپ خوراز فرانسىيەنىڭ پارىژ شەھرىگە يېقىن تاغقا كۆچەلمىدى.
            Welsh : Parciais fy jac codi baw hud llawn dŵr ger tŷ Mabon.
            """;

    /** Example Unicode Math symbols */
    private static final String MATH_SYMBOLS = """
            U+220x  ∀	∁	∂	∃	∄	∅	∆	∇	∈	∉	∊	∋	∌	∍	∎	∏
            U+221x	∐	∑	−	∓	∔	∕	∖	∗	∘	∙	√	∛	∜	∝	∞	∟
            U+222x	∠	∡	∢	∣	∤	∥	∦	∧	∨	∩	∪	∫	∬	∭	∮	∯
            U+223x	∰	∱	∲	∳	∴	∵	∶	∷	∸	∹	∺	∻	∼	∽	∾	∿
            U+224x	≀	≁	≂	≃	≄	≅	≆	≇	≈	≉	≊	≋	≌	≍	≎	≏
            U+225x	≐	≑	≒	≓	≔	≕	≖	≗	≘	≙	≚	≛	≜	≝	≞	≟
            U+226x	≠	≡	≢	≣	≤	≥	≦	≧	≨	≩	≪	≫	≬	≭	≮	≯
            U+227x	≰	≱	≲	≳	≴	≵	≶	≷	≸	≹	≺	≻	≼	≽	≾	≿
            U+228x	⊀	⊁	⊂	⊃	⊄	⊅	⊆	⊇	⊈	⊉	⊊	⊋	⊌	⊍	⊎	⊏
            U+229x	⊐	⊑	⊒	⊓	⊔	⊕	⊖	⊗	⊘	⊙	⊚	⊛	⊜	⊝	⊞	⊟
            U+22Ax	⊠	⊡	⊢	⊣	⊤	⊥	⊦	⊧	⊨	⊩	⊪	⊫	⊬	⊭	⊮	⊯
            U+22Bx	⊰	⊱	⊲	⊳	⊴	⊵	⊶	⊷	⊸	⊹	⊺	⊻	⊼	⊽	⊾	⊿
            U+22Cx	⋀	⋁	⋂	⋃	⋄	⋅	⋆	⋇	⋈	⋉	⋊	⋋	⋌	⋍	⋎	⋏
            U+22Dx	⋐	⋑	⋒	⋓	⋔	⋕	⋖	⋗	⋘	⋙	⋚	⋛	⋜	⋝	⋞	⋟
            U+22Ex	⋠	⋡	⋢	⋣	⋤	⋥	⋦	⋧	⋨	⋩	⋪	⋫	⋬	⋭	⋮	⋯
            U+22Fx	⋰	⋱	⋲	⋳	⋴	⋵	⋶	⋷	⋸	⋹	⋺	⋻	⋼	⋽	⋾	⋿
            U+2A0x	⨀	⨁	⨂	⨃	⨄	⨅	⨆	⨇	⨈	⨉	⨊	⨋	⨌	⨍	⨎	⨏
            U+2A1x	⨐	⨑	⨒	⨓	⨔	⨕	⨖	⨗	⨘	⨙	⨚	⨛	⨜	⨝	⨞	⨟
            U+2A2x	⨠	⨡	⨢	⨣	⨤	⨥	⨦	⨧	⨨	⨩	⨪	⨫	⨬	⨭	⨮	⨯
            U+2A3x	⨰	⨱	⨲	⨳	⨴	⨵	⨶	⨷	⨸	⨹	⨺	⨻	⨼	⨽	⨾	⨿
            U+2A4x	⩀	⩁	⩂	⩃	⩄	⩅	⩆	⩇	⩈	⩉	⩊	⩋	⩌	⩍	⩎	⩏
            U+2A5x	⩐	⩑	⩒	⩓	⩔	⩕	⩖	⩗	⩘	⩙	⩚	⩛	⩜	⩝	⩞	⩟
            U+2A6x	⩠	⩡	⩢	⩣	⩤	⩥	⩦	⩧	⩨	⩩	⩪	⩫	⩬	⩭	⩮	⩯
            U+2A7x	⩰	⩱	⩲	⩳	⩴	⩵	⩶	⩷	⩸	⩹	⩺	⩻	⩼	⩽	⩾	⩿
            U+2A8x	⪀	⪁	⪂	⪃	⪄	⪅	⪆	⪇	⪈	⪉	⪊	⪋	⪌	⪍	⪎	⪏
            U+2A9x	⪐	⪑	⪒	⪓	⪔	⪕	⪖	⪗	⪘	⪙	⪚	⪛	⪜	⪝	⪞	⪟
            U+2AAx	⪠	⪡	⪢	⪣	⪤	⪥	⪦	⪧	⪨	⪩	⪪	⪫	⪬	⪭	⪮	⪯
            U+2ABx	⪰	⪱	⪲	⪳	⪴	⪵	⪶	⪷	⪸	⪹	⪺	⪻	⪼	⪽	⪾	⪿
            U+2ACx	⫀	⫁	⫂	⫃	⫄	⫅	⫆	⫇	⫈	⫉	⫊	⫋	⫌	⫍	⫎	⫏
            U+2ADx	⫐	⫑	⫒	⫓	⫔	⫕	⫖	⫗	⫘	⫙	⫚	⫛	⫝̸	⫝	⫞	⫟
            U+2AEx	⫠	⫡	⫢	⫣	⫤	⫥	⫦	⫧	⫨	⫩	⫪	⫫	⫬	⫭	⫮	⫯
            U+2AFx	⫰	⫱	⫲	⫳	⫴	⫵	⫶	⫷	⫸	⫹	⫺	⫻	⫼	⫽	⫾	⫿
            """;
    private static final String ARROW_SYMBOLS = """
            U+219x	←	↑	→	↓	↔	↕	↖	↗	↘	↙	↚	↛	↜	↝	↞	↟
            U+21Ax	↠	↡	↢	↣	↤	↥	↦	↧	↨	↩	↪	↫	↬	↭	↮	↯
            U+21Bx	↰	↱	↲	↳	↴	↵	↶	↷	↸	↹	↺	↻	↼	↽	↾	↿
            U+21Cx	⇀	⇁	⇂	⇃	⇄	⇅	⇆	⇇	⇈	⇉	⇊	⇋	⇌	⇍	⇎	⇏
            U+21Dx	⇐	⇑	⇒	⇓	⇔	⇕	⇖	⇗	⇘	⇙	⇚	⇛	⇜	⇝	⇞	⇟
            U+21Ex	⇠	⇡	⇢	⇣	⇤	⇥	⇦	⇧	⇨	⇩	⇪	⇫	⇬	⇭	⇮	⇯
            U+21Fx	⇰	⇱	⇲	⇳	⇴	⇵	⇶	⇷	⇸	⇹	⇺	⇻	⇼	⇽	⇾	⇿
            U+290x	⤀	⤁	⤂	⤃	⤄	⤅	⤆	⤇	⤈	⤉	⤊	⤋	⤌	⤍	⤎	⤏
            U+291x	⤐	⤑	⤒	⤓	⤔	⤕	⤖	⤗	⤘	⤙	⤚	⤛	⤜	⤝	⤞	⤟
            U+292x	⤠	⤡	⤢	⤣	⤤	⤥	⤦	⤧	⤨	⤩	⤪	⤫	⤬	⤭	⤮	⤯
            U+293x	⤰	⤱	⤲	⤳	⤴	⤵	⤶	⤷	⤸	⤹	⤺	⤻	⤼	⤽	⤾	⤿
            U+294x	⥀	⥁	⥂	⥃	⥄	⥅	⥆	⥇	⥈	⥉	⥊	⥋	⥌	⥍	⥎	⥏
            U+295x	⥐	⥑	⥒	⥓	⥔	⥕	⥖	⥗	⥘	⥙	⥚	⥛	⥜	⥝	⥞	⥟
            U+296x	⥠	⥡	⥢	⥣	⥤	⥥	⥦	⥧	⥨	⥩	⥪	⥫	⥬	⥭	⥮	⥯
            U+297x	⥰	⥱	⥲	⥳	⥴	⥵	⥶	⥷	⥸	⥹	⥺	⥻	⥼	⥽	⥾	⥿
            """;
    /** string type test cases */
    public static final List<String> STRING_TESTS_LIST = List.of(
            "",
            "Dude",
            "©«",
            "I need some HBAR to run work on Hedera!",
            "I need some ℏ to run work on Hedera!",
            SAMPLE_TEXT_BLOCK,
            UTF8_LANGUAGES_TEXT_BLOCK_1,
            UTF8_LANGUAGES_TEXT_BLOCK_2,
            MATH_SYMBOLS,
            ARROW_SYMBOLS
    );
}
