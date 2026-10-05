package online.wanan.xingchen.core.conversation;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationSerialDispatcherConcurrencyTest {
    @Test void stress24EventsRemainFifoAndNeverOverlapWithinOneConversation() throws Exception {
        try(var dispatcher=new ConversationSerialDispatcher()) {
            int events=24;var releaseFirst=new CountDownLatch(1);var completed=new CountDownLatch(events);
            var sequence=new CopyOnWriteArrayList<Integer>();var active=new AtomicInteger();var maximum=new AtomicInteger();
            for(int i=0;i<events;i++){final int item=i;dispatcher.execute("same",()->{int now=active.incrementAndGet();maximum.accumulateAndGet(now,Math::max);try{if(item==0&&!releaseFirst.await(5,TimeUnit.SECONDS))throw new AssertionError("test release barrier timed out");sequence.add(item);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}finally{active.decrementAndGet();completed.countDown();}});}
            releaseFirst.countDown();assertThat(completed.await(5,TimeUnit.SECONDS)).as("dispatcher test infrastructure timeout (not runtime deadlock proof)").isTrue();
            assertThat(sequence).containsExactlyElementsOf(java.util.stream.IntStream.range(0,events).boxed().toList());assertThat(maximum).hasValue(1);
            assertThat(dispatcher.queuedTaskCount()).isZero();assertThat(dispatcher.queuedConversationCount()).isZero();
        }
    }

    @Test void blockedConversationDoesNotPreventOtherConversationFromFinishing() throws Exception {
        try(var dispatcher=new ConversationSerialDispatcher()) {
            var aStarted=new CountDownLatch(1);var releaseA=new CountDownLatch(1);var aFinished=new CountDownLatch(1);var bFinished=new CountDownLatch(1);var sequence=new CopyOnWriteArrayList<String>();
            dispatcher.execute("A",()->{sequence.add("A_START");aStarted.countDown();try{if(!releaseA.await(5,TimeUnit.SECONDS))throw new AssertionError("A release barrier timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}finally{sequence.add("A_FINISH");aFinished.countDown();}});
            assertThat(aStarted.await(5,TimeUnit.SECONDS)).as("A start barrier").isTrue();
            dispatcher.execute("B",()->{sequence.add("B_START");sequence.add("B_FINISH");bFinished.countDown();});
            try{assertThat(bFinished.await(5,TimeUnit.SECONDS)).as("B must finish while A is blocked").isTrue();}
            finally{sequence.add("A_RELEASE");releaseA.countDown();}
            assertThat(aFinished.await(5,TimeUnit.SECONDS)).as("A completion after release").isTrue();
            assertThat(sequence).containsSubsequence("A_START","B_START","B_FINISH","A_RELEASE","A_FINISH");
        }
    }
}
