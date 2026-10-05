package online.wanan.xingchen.core;

import online.wanan.xingchen.console.ConsoleApiErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;

class ConsoleErrorContractTest {
    @Test void error001_validationStatusAndInternalErrorsNeverExposeExceptionMessages() {
        var handler=new ConsoleApiErrorHandler();
        var marker="SELECT private_message FROM secrets /synthetic/private/path <html>stack password=fixture";
        var responses=java.util.List.of(
            handler.invalidRequest(new IllegalArgumentException(marker)),
            handler.forbiddenOperation(),handler.missingRecord(),handler.stateConflict(),
            handler.statusFailure(new ResponseStatusException(HttpStatus.UNAUTHORIZED,marker)),
            handler.statusFailure(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,marker)),
            handler.internalFailure());
        assertThat(responses).extracting(r->r.getStatusCode().value()).containsExactly(400,403,404,409,401,429,500);
        for(var response:responses) {
            assertThat(response.getBody()).containsOnlyKeys("code","message","traceId");
            assertThat(response.getBody().toString()).doesNotContain("SELECT","private_message","secrets","/synthetic/","<html>","stack","password");
            assertThat(response.getHeaders().getFirst("X-Trace-Id")).isEqualTo(response.getBody().get("traceId"));
        }
    }
}
