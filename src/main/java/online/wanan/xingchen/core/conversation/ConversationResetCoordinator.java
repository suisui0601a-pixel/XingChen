package online.wanan.xingchen.core.conversation;

import online.wanan.xingchen.core.agent.SimulationStateService;
import online.wanan.xingchen.core.model.Platform;
import online.wanan.xingchen.core.model.ConversationType;
import online.wanan.xingchen.core.model.PlatformEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Two-phase control interrupt: generation/cancellation now, state/session cleanup in the serial lane. */
public class ConversationResetCoordinator {
    private final JdbcTemplate jdbc;private final SimulationStateService simulation;
    private final Map<String,AtomicLong> epochs=new ConcurrentHashMap<>();private final Map<UUID,Long> resetting=new ConcurrentHashMap<>();
    public ConversationResetCoordinator(JdbcTemplate jdbc,SimulationStateService simulation){this.jdbc=jdbc;this.simulation=simulation;}
    @Transactional public ResetTicket interrupt(PlatformEvent event,String ownerQq,ActiveTurnRegistry active){
        if(!isReset(event)||event.platform()!=Platform.QQ||event.conversation().type()!=ConversationType.PRIVATE||ownerQq==null||ownerQq.isBlank()||!ownerQq.equals(event.actor().platformUserId()))return null;
        String key=key(event);var ids=jdbc.query("SELECT id FROM conversations WHERE platform=? AND type=? AND platform_conversation_id=?",(rs,n)->UUID.fromString(rs.getString(1)),"QQ","PRIVATE",event.conversation().platformConversationId());
        if(ids.isEmpty())return null;
        long epoch=epochs.computeIfAbsent(key,k->new AtomicLong()).incrementAndGet();UUID conversation=ids.getFirst();long generation=active.invalidateAndCancel(conversation,()->simulation.invalidateForReset(conversation));jdbc.update("INSERT INTO conversation_reset_controls(conversation_id,reset_epoch,generation,status,updated_at) VALUES(?,?,?,'RESETTING',CURRENT_TIMESTAMP) ON CONFLICT(conversation_id) DO UPDATE SET reset_epoch=excluded.reset_epoch,generation=excluded.generation,status='RESETTING',updated_at=excluded.updated_at",conversation.toString(),epoch,generation);resetting.put(conversation,epoch);return new ResetTicket(conversation,generation,epoch,key);
    }
    @Transactional public synchronized ResetTicket consoleInterrupt(UUID conversation,long expectedGeneration,String laneKey,ActiveTurnRegistry active){
        if(laneKey==null||laneKey.isBlank())throw new IllegalArgumentException("conversation lane is required");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM conversation_reset_controls WHERE conversation_id=? AND status='RESETTING'",Integer.class,conversation.toString())>0)throw new java.util.ConcurrentModificationException("conversation reset is already in progress");
        var exists=jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE id=?",Integer.class,conversation.toString());if(exists==0)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"conversation not found");
        long generation=active.invalidateAndCancel(conversation,()->simulation.invalidateForReset(conversation,expectedGeneration));
        long epoch=epochs.computeIfAbsent(laneKey,k->new AtomicLong()).incrementAndGet();
        jdbc.update("INSERT INTO conversation_reset_controls(conversation_id,reset_epoch,generation,status,updated_at) VALUES(?,?,?,'RESETTING',CURRENT_TIMESTAMP) ON CONFLICT(conversation_id) DO UPDATE SET reset_epoch=excluded.reset_epoch,generation=excluded.generation,status='RESETTING',updated_at=excluded.updated_at",conversation.toString(),epoch,generation);
        resetting.put(conversation,epoch);return new ResetTicket(conversation,generation,epoch,laneKey);
    }
    public long epoch(PlatformEvent event){return epochs.computeIfAbsent(key(event),k->new AtomicLong()).get();}
    public boolean current(PlatformEvent event,long expected){return epoch(event)==expected;}
    public void complete(ResetTicket ticket){if(ticket!=null){jdbc.update("UPDATE conversation_reset_controls SET status='COMPLETE',updated_at=CURRENT_TIMESTAMP WHERE conversation_id=? AND generation=? AND reset_epoch=? AND status='RESETTING'",ticket.conversationId().toString(),ticket.generation(),ticket.epoch());resetting.remove(ticket.conversationId(),ticket.epoch());}}
    public int resettingCount(){try{return jdbc.queryForObject("SELECT COUNT(*) FROM conversation_reset_controls WHERE status='RESETTING'",Integer.class);}catch(org.springframework.dao.DataAccessException unavailable){return resetting.size();}}
    public java.util.List<ResetTicket> pendingResets(){return jdbc.query("SELECT r.conversation_id,r.generation,r.reset_epoch,c.platform||':'||c.type||':'||c.platform_conversation_id FROM conversation_reset_controls r JOIN conversations c ON c.id=r.conversation_id WHERE r.status='RESETTING' ORDER BY r.updated_at",(rs,n)->new ResetTicket(UUID.fromString(rs.getString(1)),rs.getLong(2),rs.getLong(3),rs.getString(4)));}
    private static boolean isReset(PlatformEvent e){return e.text()!=null&&e.text().trim().equals("/reset");}
    private static String key(PlatformEvent e){return e.platform()+":"+e.conversation().type()+":"+e.conversation().platformConversationId();}
    public record ResetTicket(UUID conversationId,long generation,long epoch,String key){}
}
