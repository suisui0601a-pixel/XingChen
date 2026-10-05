package online.wanan.xingchen.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.adapter.deepseek.DeepSeekConfiguration;
import online.wanan.xingchen.adapter.dsh.*;
import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.adapter.snowluma.FakeGatewayManagementPort;
import online.wanan.xingchen.core.gateway.GatewayManagementPort;
import online.wanan.xingchen.core.FakeOneBotServer;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.security.XingChenProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.context.annotation.Primary;
import java.io.IOException;
import java.net.URI;
import java.time.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Test-owned graph: local protocol fakes, fixture model, deterministic clock and scheduler. */
@TestConfiguration(proxyBeanMethods=false)
@Profile("runtime-test")
@Import(RuntimeGraphConfiguration.class)
public class TestRuntimeConfiguration {
    @Bean(destroyMethod="close") public FakeDshRc2Server fakeDshRc2Server() throws IOException { return new FakeDshRc2Server("fixture-launch-token"); }
    @Bean(destroyMethod="close") public FakeOneBotServer fakeOneBotServer() throws IOException { return new FakeOneBotServer(""); }
    @Bean public RuntimeIntegrationStatus runtimeIntegrationStatus() { return new RuntimeIntegrationStatus(false,false,false,false); }
    @Bean @Primary public online.wanan.xingchen.console.AccessControlService fixtureAccessControlService(org.springframework.jdbc.core.JdbcTemplate jdbc,online.wanan.xingchen.security.XingChenProperties properties) { return new online.wanan.xingchen.console.AccessControlService(jdbc,properties) { @Override public Decision evaluate(String platform,String actorId,String scope,String conversationId,boolean owner) { return new Decision(true,"TEST_FIXTURE"); } }; }
    @Bean public GatewayManagementPort gatewayManagementPort() { return new FakeGatewayManagementPort(); }
    @Bean @Primary public TestTurnBoundaryObserver testTurnBoundaryObserver(){return new TestTurnBoundaryObserver();}
    @Bean @Primary public SocialTriggerPolicy stressSocialTriggerPolicy(online.wanan.xingchen.security.XingChenProperties p){return new SocialTriggerPolicy(p.onebot().loginUserId(),java.util.List.of("stress-keyword"),java.util.List.of("大肥鱼"));}
    @Bean public DeepSeekConfiguration deepSeekConfiguration() { return new DeepSeekConfiguration("http://127.0.0.1:9","fixture-model","UNUSED_TEST_SECRET",100,100,0); }
    @Bean public ModelProvider modelProvider() { return new MockModelProvider(); }
    @Bean public OneBotV11Configuration oneBotConfiguration(FakeOneBotServer fake) { return new OneBotV11Configuration(fake.httpUri(),fake.wsUri(),"UNUSED_TEST_TOKEN","test-bot",false,5000,100,50,500,32,90_000); }
    @Bean(destroyMethod="close") public OneBotGateway oneBotGateway(OneBotV11Configuration config,ObjectMapper mapper,OneBotEventNormalizer normalizer) { return new OneBotV11Gateway(config,mapper,normalizer,java.util.Map.of(),()->true); }
    @Bean public DshRc2Configuration dshConfiguration(FakeDshRc2Server fake) { return new DshRc2Configuration(fake.baseUri(),java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath(),"fixture-launch-token","deepseek-official",Duration.ofMillis(500)); }
    @Bean(destroyMethod="close") public DshGateway dshGateway(DshRc2Configuration config,ObjectMapper mapper) { return new DshRc2Adapter(config,mapper,()->true); }
    @Bean public Clock runtimeClock() { return new ManualClock(Instant.parse("2026-01-01T00:00:00Z")); }
    @Bean(destroyMethod="close") public Scheduler runtimeScheduler() { return new ManualScheduler(); }

    static final class ManualClock extends Clock {
        private volatile Instant now;
        ManualClock(Instant now){this.now=now;}
        void advance(Duration d){now=now.plus(d);}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now;}
    }
    static final class ManualScheduler implements Scheduler,AutoCloseable {
        private final AtomicLong sequence=new AtomicLong();private final PriorityBlockingQueue<Entry> queue=new PriorityBlockingQueue<>();private volatile boolean closed;private long nowMillis;
        @Override public synchronized Cancellable schedule(Duration delay,Runnable task){if(closed)throw new IllegalStateException("test scheduler closed");Entry e=new Entry(nowMillis+Math.max(0,delay.toMillis()),sequence.getAndIncrement(),task);queue.add(e);return new Cancellable(){public boolean cancel(){return e.cancelled=true;}public boolean isCancelled(){return e.cancelled;}};}
        synchronized void advanceBy(Duration duration){nowMillis+=Math.max(0,duration.toMillis());runDue();}
        synchronized void runDue(){Entry e;while((e=queue.peek())!=null&&e.dueMillis<=nowMillis){queue.poll();if(!e.cancelled)e.task.run();}}
        @Override public void close(){closed=true;queue.clear();}
        private static final class Entry implements Comparable<Entry>{final long dueMillis,sequence;final Runnable task;volatile boolean cancelled;Entry(long d,long s,Runnable r){dueMillis=d;sequence=s;task=r;}public int compareTo(Entry e){int c=Long.compare(dueMillis,e.dueMillis);return c!=0?c:Long.compare(sequence,e.sequence);}}
    }
}
