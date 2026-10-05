package online.wanan.xingchen.console;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class JdbcConsoleUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;
    public JdbcConsoleUserDetailsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public UserDetails loadUserByUsername(String username) {
        var users = jdbc.query("SELECT username,password_hash FROM console_admin_credentials WHERE username=?",
                (rs, row) -> User.withUsername(rs.getString("username")).password(rs.getString("password_hash")).roles("ADMIN").build(),
                username);
        if (users.isEmpty()) throw new UsernameNotFoundException("Console account not found");
        return users.getFirst();
    }
}
