package online.wanan.xingchen.console;
import jakarta.servlet.http.HttpSession;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Component;import java.util.*;import java.util.concurrent.ConcurrentHashMap;
/** Tracks only sessions authenticated by this application; embedded-container sessions do not survive a restart. */
@Component public final class ConsoleSessionGenerationRegistry {
 private final JdbcTemplate jdbc;private final Map<String,Entry> sessions=new ConcurrentHashMap<>();private record Entry(String username,long generation,HttpSession session){}
 public ConsoleSessionGenerationRegistry(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void register(String username,HttpSession session){long generation=jdbc.queryForObject("SELECT auth_generation FROM console_admin_credentials WHERE username=?",Long.class,username);session.setAttribute("authGeneration",generation);sessions.put(session.getId(),new Entry(username,generation,session));}
 public void remove(String sessionId){if(sessionId!=null)sessions.remove(sessionId);}
 public void enforce(HttpSession session){Entry e=sessions.get(session.getId());if(e==null)return;Long generation=jdbc.queryForObject("SELECT auth_generation FROM console_admin_credentials WHERE username=?",Long.class,e.username());if(generation==null||generation!=e.generation()||!(session.getAttribute("authGeneration") instanceof Number n)||n.longValue()!=generation){sessions.remove(session.getId());session.invalidate();throw new SessionExpiredException();}}
 public void invalidateUser(String username){sessions.forEach((id,e)->{if(e.username().equals(username)&&sessions.remove(id,e)){try{e.session().invalidate();}catch(IllegalStateException ignored){}}});}
 public static final class SessionExpiredException extends RuntimeException{}
}
