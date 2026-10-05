package online.wanan.xingchen.console;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/console")
public class ConsoleSettingsController {
    private final ConfigService config;
    private final org.springframework.beans.factory.ObjectProvider<online.wanan.xingchen.adapter.onebot.OneBotConfigurationApplier> gatewayApplier;
    public ConsoleSettingsController(ConfigService config,org.springframework.beans.factory.ObjectProvider<online.wanan.xingchen.adapter.onebot.OneBotConfigurationApplier> gatewayApplier) { this.config = config;this.gatewayApplier=gatewayApplier; }
    @GetMapping("/categories") public Map<String, Object> categories() { return Map.of("categories", config.categories()); }
    @GetMapping("/config/{category}") public Map<String, Object> config(@PathVariable String category) { return config.describe(category); }
    @PutMapping("/config/{category}/{key}") public Map<String, Object> update(@PathVariable String category, @PathVariable String key,
            @RequestBody ConfigUpdate update, org.springframework.security.core.Authentication authentication) {
        Map<String,Object> result=config.update(category, key, update.value(), authentication.getName());
        if(category.equals("Gateway")){var applier=gatewayApplier.getIfAvailable();if(applier!=null)applier.apply();}
        return result;
    }
    @GetMapping("/secrets/status") public Map<String, Boolean> secrets() { return config.secretStatus(); }
    public record ConfigUpdate(Object value) {}
}
