package online.wanan.xingchen.console;

import online.wanan.xingchen.adapter.snowluma.FakeGatewayManagementPort;
import online.wanan.xingchen.core.gateway.GatewayState;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test/gateway")
@Profile("gateway-fake-e2e")
public class FakeGatewayE2eController {
    private final FakeGatewayManagementPort gateway;
    public FakeGatewayE2eController(FakeGatewayManagementPort gateway){this.gateway=gateway;}
    @PostMapping("/state") public GatewayState state(@RequestBody StateUpdate update){gateway.setState(update.state());return gateway.getStatus().state();}
    public record StateUpdate(GatewayState state){}
}
