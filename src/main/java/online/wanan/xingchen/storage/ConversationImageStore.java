package online.wanan.xingchen.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** References only: no image download or image bytes in the database. */
@Repository
public class ConversationImageStore {
    public record Reference(String file,String url){@Override public String toString(){return "ImageReference[redacted]";}}
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    public ConversationImageStore(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    public List<Reference> find(UUID conversation,String currentEvent,String quotedMessage){
        String cid=conversation.toString();
        var current=jdbc.query("SELECT rowid FROM messages WHERE conversation_id=? AND platform_message_id=?",(r,n)->r.getLong(1),cid,currentEvent);
        if(current.isEmpty())return List.of();
        long generation=jdbc.queryForObject("SELECT MAX(g) FROM (SELECT generation g FROM conversation_reset_controls WHERE conversation_id=? UNION ALL SELECT history_reset_generation g FROM session_state WHERE conversation_id=? UNION ALL SELECT 0)",Long.class,cid,cid);
        var starts=jdbc.query("SELECT history_start_message_id FROM session_state WHERE conversation_id=?",(r,n)->r.getString(1),cid);long floor=0;
        if(!starts.isEmpty()&&starts.getFirst()!=null){var ids=jdbc.query("SELECT rowid FROM messages WHERE conversation_id=? AND id=?",(r,n)->r.getLong(1),cid,starts.getFirst());if(ids.isEmpty())return List.of();floor=ids.getFirst();}
        var rows=jdbc.queryForList("SELECT platform_message_id,raw_metadata_json FROM messages WHERE conversation_id=? AND rowid>=? AND rowid<=? AND context_generation>=? AND message_type='message' ORDER BY rowid DESC LIMIT 40",cid,floor,current.getFirst(),generation);
        for(var row:rows)if(currentEvent.equals(row.get("platform_message_id"))){var attached=references((String)row.get("raw_metadata_json"));if(!attached.isEmpty())return attached;}
        if(quotedMessage!=null&&!quotedMessage.isBlank()){
            for(var row:rows)if(quotedMessage.equals(row.get("platform_message_id")))return references((String)row.get("raw_metadata_json"));
            return List.of(); // A missing quote must not silently select another person's image.
        }
        for(var row:rows){var found=references((String)row.get("raw_metadata_json"));if(!found.isEmpty())return found;}
        return List.of();
    }
    private List<Reference> references(String raw){
        if(raw==null||raw.length()>262144)return List.of();
        try{var result=new ArrayList<Reference>();var segments=mapper.readTree(raw).path("segments");
            if(segments.isArray())for(var s:segments){if(!s.path("type").asText().equals("image"))continue;var d=s.path("data");String file=d.path("file").asText(""),url=d.path("url").asText("");
                if(file.length()>1024||url.length()>8192||file.startsWith("base64://")||file.startsWith("data:"))continue;
                if(!file.isBlank())result.add(new Reference(file,url));if(result.size()==2)break;}
            return List.copyOf(result);
        }catch(Exception invalid){return List.of();}
    }
}
