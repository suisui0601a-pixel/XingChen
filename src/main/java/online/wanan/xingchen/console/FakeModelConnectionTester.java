package online.wanan.xingchen.console;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Deterministic fake-only connection tester; no network requests are possible in these profiles. */
@Component
@Profile("runtime-test | model-fake-e2e")
public final class FakeModelConnectionTester implements ModelConnectionTester {
    private volatile TestResult next=new TestResult("SUCCESS","Fake provider connection succeeded.");
    public void next(String status){next=new TestResult(status,"Fake provider test result.");}
    @Override public TestResult test(String baseUrl,String model,String credential,int maxOutputTokens,int timeoutMillis){return next;}
}
