package online.wanan.xingchen.core.agent;

import java.time.*;
import java.util.*;

/** Calendar greetings and inbound silence are independent from random proactive quiet hours. */
public record SocialSchedule(boolean greetingsEnabled,LocalTime morning,LocalTime evening,
        boolean nightMuteEnabled,int nightStartHour,int nightEndHour,ZoneId zone) {
    public static SocialSchedule from(Map<String,Object> v){return new SocialSchedule(Boolean.TRUE.equals(v.get("dailyGreetingsEnabled")),
        parseTime((String)v.get("morningGreetingTime")),parseTime((String)v.get("eveningGreetingTime")),Boolean.TRUE.equals(v.get("nightReplyMuteEnabled")),
        ((Number)v.get("nightReplyStartHour")).intValue(),((Number)v.get("nightReplyEndHour")).intValue(),ZoneId.of((String)v.get("proactiveZoneId")));}
    public SocialSchedule{if(nightStartHour<0||nightStartHour>23||nightEndHour<0||nightEndHour>23)throw new IllegalArgumentException("invalid night reply hours");}
    public static LocalTime parseTime(String text){if(text==null||!text.matches("[0-2][0-9]:[0-5][0-9]"))throw new IllegalArgumentException("greeting time must be HH:mm");try{return LocalTime.parse(text);}catch(DateTimeException invalid){throw new IllegalArgumentException("invalid greeting time");}}
    public boolean muted(Instant now){int hour=now.atZone(zone).getHour();return nightMuteEnabled&&nightStartHour!=nightEndHour&&(nightStartHour<nightEndHour?hour>=nightStartHour&&hour<nightEndHour:hour>=nightStartHour||hour<nightEndHour);}
    public List<Intent> due(Instant now){if(!greetingsEnabled)return List.of();var date=now.atZone(zone).toLocalDate();var result=new ArrayList<Intent>();
        for(var entry:Map.of("MORNING",morning,"EVENING",evening).entrySet()){Instant at=date.atTime(entry.getValue()).atZone(zone).toInstant();if(!now.isBefore(at)&&now.isBefore(at.plusSeconds(600)))result.add(new Intent(entry.getKey(),date+":"+entry.getKey(),at,at.plusSeconds(600)));}
        result.sort(Comparator.comparing(Intent::dueAt).thenComparing(Intent::kind));return List.copyOf(result);}
    public record Intent(String kind,String key,Instant dueAt,Instant expiresAt){public boolean greeting(){return !kind.equals("RANDOM");}public boolean current(Instant now){return !greeting()||(!now.isBefore(dueAt)&&now.isBefore(expiresAt));}public static Intent random(){return new Intent("RANDOM",null,null,null);}}
}
