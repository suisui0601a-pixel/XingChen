package online.wanan.xingchen.console;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ConsoleApiErrorHandler {
    @ExceptionHandler(java.util.ConcurrentModificationException.class)
    public ResponseEntity<Map<String,String>> stateConflict() { return error(HttpStatus.CONFLICT,"STATE_CONFLICT","会话状态已变化，请刷新后重试"); }
    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String,String>> missingRecord() { return error(HttpStatus.NOT_FOUND,"NOT_FOUND","请求的记录不存在"); }
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String,String>> forbiddenOperation() { return error(HttpStatus.FORBIDDEN,"OPERATION_FORBIDDEN","当前会话不允许此操作"); }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String,String>> invalidState() { return error(HttpStatus.CONFLICT,"INVALID_STATE","当前会话状态不支持此操作"); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> invalidRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","输入无效，请检查字段与范围");
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,String>> unreadableRequest() {
        return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","请求内容格式不正确");
    }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String,String>> unsupportedMethod() {
        return error(HttpStatus.METHOD_NOT_ALLOWED,"METHOD_NOT_ALLOWED","此资源不支持该操作");
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String,String>> missingRoute() {
        return error(HttpStatus.NOT_FOUND,"NOT_FOUND","请求的资源不存在");
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> statusFailure(ResponseStatusException exception) {
        return error(org.springframework.http.HttpStatusCode.valueOf(exception.getStatusCode().value()),
                exception.getStatusCode().value()==404?"NOT_FOUND":"REQUEST_REJECTED",
                exception.getStatusCode().value()==404?"请求的资源不存在":"请求未能完成");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> internalFailure() {
        return error(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","服务暂时无法完成请求，请稍后重试");
    }
    private ResponseEntity<Map<String,String>> error(org.springframework.http.HttpStatusCode status,String code,String message) {
        String traceId=UUID.randomUUID().toString();
        return ResponseEntity.status(status).header("X-Trace-Id",traceId)
                .body(Map.of("code",code,"message",message==null?"请求无效":message,"traceId",traceId));
    }
}
