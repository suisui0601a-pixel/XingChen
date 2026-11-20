package online.wanan.xingchen.core.agent;

import java.util.Locale;
import java.util.regex.Pattern;

/** Recognition is opt-in for this user turn, never inferred from old history or image contents. */
public final class ImageRecognitionIntent {
    private static final Pattern DENY=Pattern.compile("(?:不要|不用|无需|不必|别|不需要).{0,8}(?:识别|识图|看图|读图|分析|描述|解读|看|处理)|(?:只|仅).{0,4}(?:保存|记录|存档)|(?:do not|don't|no need to).{0,20}(?:analy[sz]e|describe|recognize|read)");
    private static final Pattern ASK=Pattern.compile("识图|读图|看图|识别.{0,12}(?:图片|图中|图上|截图|照片|文字)|(?:看一下|看一眼|看看|看下|分析|解读|描述|读出|提取).{0,14}(?:图|照片|截图)|(?:图里|图中|图上|图片|照片|截图).{0,20}(?:什么|啥|怎么|内容|文字|识别|看懂|意思)|(?:这张|那张|上一张|刚才那张).{0,10}(?:什么|啥|如何)|(?:describe|analy[sz]e|recognize|read).{0,16}(?:image|photo|picture|screenshot)|\\bocr\\b");
    public static boolean requested(String currentText,boolean hasCurrentImage){
        String text=currentText==null?"":currentText.toLowerCase(Locale.ROOT);
        text=text.replaceAll("\\[图片[^\\]\\n]*]","");
        if(DENY.matcher(text).find())return false;
        return ASK.matcher(text).find()||(hasCurrentImage&&Pattern.compile("看看|看下|看一下|看一眼|这是什么|这是啥|这啥|什么东西|上面写的|帮我分析|帮我识别").matcher(text).find());
    }
    public static boolean recordOnly(String text){return text!=null&&Pattern.compile("(?:只|仅).{0,4}(?:保存|记录|存档)").matcher(text).find();}
    private ImageRecognitionIntent(){}
}
