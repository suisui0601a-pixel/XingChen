package online.wanan.xingchen.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Durable, conversation-bound DSH questions and owner approval decisions. */
public class PendingInteractionService {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final Clock clock;private final Set<String> ownerActors;
    public PendingInteractionService(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock){this(jdbc,mapper,clock,configuredOwner());}
    public PendingInteractionService(JdbcTemplate jdbc,ObjectMapper mapper,Clock clock,Set<String> ownerActors){this.jdbc=jdbc;this.mapper=mapper;this.clock=clock;this.ownerActors=Set.copyOf(ownerActors==null?Set.of():ownerActors);}
    @Transactional public void createQuestion(UUID id,UUID conversation,String sessionId,String requesterActor,String preset,String expectedResponder,String question,List<String> options,Instant expiresAt){
        validateExpiry(expiresAt);if(sessionId==null||sessionId.isBlank()||requesterActor==null||requesterActor.isBlank()||expectedResponder==null||expectedResponder.isBlank()||question==null||question.isBlank())throw new IllegalArgumentException("question binding is incomplete");
        try{jdbc.update("INSERT INTO pending_questions(id,conversation_id,session_id,requesting_actor,preset,expected_responder,question,options_json,expires_at,status,updated_at) VALUES(?,?,?,?,?,?,?,?,?,'PENDING',?)",id.toString(),conversation.toString(),sessionId,requesterActor,Objects.requireNonNullElse(preset,""),expectedResponder,question,mapper.writeValueAsString(options==null?List.of():List.copyOf(options)),expiresAt.toString(),clock.instant().toString());}catch(Exception e){throw new IllegalStateException("question could not be persisted",e);}
    }
    @Transactional public Resolution answerQuestion(UUID id,UUID conversation,String sessionId,String actor,String response){
        var q=jdbc.query("SELECT conversation_id,session_id,expected_responder,status,expires_at,responded_by,response FROM pending_questions WHERE id=?",(rs,n)->new QuestionBinding(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),Instant.parse(rs.getString(5)),rs.getString(6),rs.getString(7)),id.toString()).stream().findFirst().orElseThrow(()->new NoSuchElementException("question not found"));
        requireBinding(q.conversation(),q.session(),conversation,sessionId);if(!q.expectedResponder().equals(actor))throw new SecurityException("question responder is not authorized");
        if(!q.status().equals("PENDING"))return new Resolution(q.status().equals("EXPIRED")?ResolutionStatus.EXPIRED:q.status().equals("ANSWERED")?ResolutionStatus.ALREADY_RESOLVED:ResolutionStatus.CLOSED,q.response());
        if(!q.expiresAt().isAfter(clock.instant())){jdbc.update("UPDATE pending_questions SET status='EXPIRED',updated_at=? WHERE id=? AND status='PENDING'",clock.instant().toString(),id.toString());return new Resolution(ResolutionStatus.EXPIRED,null);}
        int changed=jdbc.update("UPDATE pending_questions SET status='ANSWERED',response=?,responded_by=?,updated_at=? WHERE id=? AND status='PENDING'",Objects.requireNonNullElse(response,""),actor,clock.instant().toString(),id.toString());return changed==1?new Resolution(ResolutionStatus.ACCEPTED,response):new Resolution(ResolutionStatus.ALREADY_RESOLVED,null);
    }
    @Transactional public void createApproval(UUID id,UUID conversation,String sessionId,String toolName,String reason,String requestingActor,String requiredOwnerActor,Instant expiresAt){
        if(requestingActor==null||!ownerActors.contains(requestingActor)||requiredOwnerActor==null||!ownerActors.contains(requiredOwnerActor))throw new SecurityException("only configured owner actors may create or receive high-privilege approvals");validateExpiry(expiresAt);if(sessionId==null||sessionId.isBlank()||toolName==null||toolName.isBlank()||reason==null)throw new IllegalArgumentException("approval binding is incomplete");
        jdbc.update("INSERT INTO pending_approvals(approval_id,session_id,conversation_id,tool_name,reason,required_actor,expires_at,status,updated_at) VALUES(?,?,?,?,?,?,?,'PENDING',?)",id.toString(),sessionId,conversation.toString(),toolName,reason,requiredOwnerActor,expiresAt.toString(),clock.instant().toString());
    }
    @Transactional public Resolution decideApproval(UUID id,UUID conversation,String sessionId,String actor,boolean approve){
        var a=jdbc.query("SELECT conversation_id,session_id,required_actor,status,expires_at,decided_by,decision FROM pending_approvals WHERE approval_id=?",(rs,n)->new ApprovalBinding(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),Instant.parse(rs.getString(5)),rs.getString(6),rs.getString(7)),id.toString()).stream().findFirst().orElseThrow(()->new NoSuchElementException("approval not found"));
        requireBinding(a.conversation(),a.session(),conversation,sessionId);if(!a.requiredActor().equals(actor))throw new SecurityException("approval requires its bound owner");String decision=approve?"APPROVED":"DENIED";
        if(!a.status().equals("PENDING"))return new Resolution(a.status().equals("EXPIRED")?ResolutionStatus.EXPIRED:a.status().equals(decision)?ResolutionStatus.ALREADY_RESOLVED:ResolutionStatus.CLOSED,a.decision());
        if(!a.expiresAt().isAfter(clock.instant())){jdbc.update("UPDATE pending_approvals SET status='EXPIRED',updated_at=? WHERE approval_id=? AND status='PENDING'",clock.instant().toString(),id.toString());return new Resolution(ResolutionStatus.EXPIRED,null);}
        int changed=jdbc.update("UPDATE pending_approvals SET status=?,decision=?,decided_by=?,updated_at=? WHERE approval_id=? AND status='PENDING'",decision,decision,actor,clock.instant().toString(),id.toString());return changed==1?new Resolution(approve?ResolutionStatus.ACCEPTED:ResolutionStatus.REJECTED,decision):new Resolution(ResolutionStatus.ALREADY_RESOLVED,null);
    }
    @Transactional public int expirePending(){String now=clock.instant().toString();int q=jdbc.update("UPDATE pending_questions SET status='EXPIRED',updated_at=? WHERE status='PENDING' AND expires_at<=?",now,now);return q+jdbc.update("UPDATE pending_approvals SET status='EXPIRED',updated_at=? WHERE status='PENDING' AND expires_at<=?",now,now);}
    private void validateExpiry(Instant expiresAt){if(expiresAt==null||!expiresAt.isAfter(clock.instant()))throw new IllegalArgumentException("expiry must be in the future");}
    private static Set<String> configuredOwner(){String owner=System.getenv("XINGCHEN_OWNER_ID");return owner==null||owner.isBlank()?Set.of():Set.of(owner);}
    private static void requireBinding(String storedConversation,String storedSession,UUID conversation,String session){if(!storedConversation.equals(conversation.toString())||!storedSession.equals(session))throw new SecurityException("pending interaction binding mismatch");}
    private record QuestionBinding(String conversation,String session,String expectedResponder,String status,Instant expiresAt,String respondedBy,String response){}
    private record ApprovalBinding(String conversation,String session,String requiredActor,String status,Instant expiresAt,String decidedBy,String decision){}
    public enum ResolutionStatus { ACCEPTED, REJECTED, EXPIRED, ALREADY_RESOLVED, CLOSED }
    public record Resolution(ResolutionStatus status,String value){}
}
