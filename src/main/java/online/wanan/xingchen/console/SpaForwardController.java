package online.wanan.xingchen.console;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {
    @GetMapping({"/", "/login", "/initialize", "/settings", "/gateway", "/conversations", "/conversations/{*path}",
            "/people", "/people/{*path}", "/relationships", "/memory", "/persona", "/simulation", "/social-settings",
            "/stickers", "/slang", "/voice", "/models", "/usage", "/access",
            "/security", "/logs", "/operations"})
    public String applicationShell() { return "forward:/index.html"; }
}
