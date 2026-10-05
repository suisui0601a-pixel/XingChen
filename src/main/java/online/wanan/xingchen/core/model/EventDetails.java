package online.wanan.xingchen.core.model;

import java.util.List;

public record EventDetails(List<String> mentionIds,ReplyReference reply,String pokeTargetId) {
    public EventDetails { mentionIds=mentionIds==null?List.of():List.copyOf(mentionIds); }
    public static EventDetails empty(){return new EventDetails(List.of(),null,null);}
}
