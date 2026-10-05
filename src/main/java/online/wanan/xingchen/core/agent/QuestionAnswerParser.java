package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.adapter.dsh.DshInteractionEvent;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses one answer or explicit per-question lines without copying text across questions. */
public final class QuestionAnswerParser {
    private static final Pattern ANSWER=Pattern.compile("^\\s*(?:#([a-fA-F0-9]{6})\\s+)?([A-Za-z0-9_-]{1,80}|[0-9]+)\\s*[:：]\\s*(.+?)\\s*$");
    public Map<String,String> parse(List<DshInteractionEvent.Question> questions,String text) {
        if(questions==null||questions.isEmpty()||text==null||text.isBlank())return Map.of();
        List<String> lines=text.lines().map(String::trim).filter(s->!s.isEmpty()).toList();Map<String,String> result=new LinkedHashMap<>();
        if(questions.size()==1&&lines.size()==1){String candidate=stripSelector(lines.getFirst());Matcher m=ANSWER.matcher(candidate);if(!m.matches())return Map.of(questions.getFirst().id(),candidate);String ref=m.group(2),answer=m.group(3);if(ref.matches("\\d+")){if(Integer.parseInt(ref)!=1)return Map.of();}else if(!questions.getFirst().id().equals(ref))return Map.of();return Map.of(questions.getFirst().id(),answer);}
        for(String line:lines){Matcher m=ANSWER.matcher(line);if(!m.matches())return Map.of();String ref=m.group(2),id;if(ref.matches("\\d+")){int n=Integer.parseInt(ref);if(n<1||n>questions.size())return Map.of();id=questions.get(n-1).id();}else{id=ref;if(questions.stream().noneMatch(q->q.id().equals(id)))return Map.of();}if(result.putIfAbsent(id,m.group(3))!=null)return Map.of();}
        return Map.copyOf(result);
    }
    private static String stripSelector(String value){return value.replaceFirst("^#([a-fA-F0-9]{6})\\s+","");}
}
