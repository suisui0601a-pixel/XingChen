package online.wanan.xingchen.console;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConsoleCookieConfiguration {
    @Bean ServletContextInitializer consoleSessionCookie(EffectiveConsoleConfiguration effective) {
        return servletContext -> {
            var config = servletContext.getSessionCookieConfig();
            config.setHttpOnly(true);
            config.setSecure(effective.cookieSecure());
            config.setPath("/");
        };
    }
}
