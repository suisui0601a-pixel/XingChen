package online.wanan.xingchen.console;

public interface ModelConnectionTester {
    TestResult test(String baseUrl,String model,String credential,int maxOutputTokens,int timeoutMillis);
    record TestResult(String status,String safeSummary){public TestResult{if(!java.util.Set.of("SUCCESS","AUTH_FAILED","UNREACHABLE","TIMEOUT","INVALID_CONFIG","UNSUPPORTED").contains(status))throw new IllegalArgumentException("invalid connection test status");safeSummary=safeSummary==null?"":safeSummary;}}
}
