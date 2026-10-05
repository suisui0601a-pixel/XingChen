package online.wanan.xingchen.core.conversation;

import jakarta.annotation.PostConstruct;
import online.wanan.xingchen.adapter.onebot.DeliveryStatus;
import online.wanan.xingchen.adapter.onebot.SendReceipt;
import online.wanan.xingchen.storage.SqliteOutboundExecutionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Durable at-most-once boundary for social replies sent through OneBot. */
@Service
public final class OutboundExecutionService {
    private final SqliteOutboundExecutionRepository repository;
    private final Clock clock;
    public OutboundExecutionService(SqliteOutboundExecutionRepository repository,Clock clock){this.repository=repository;this.clock=clock;}

    @PostConstruct public void recoverAmbiguousDispatches(){
        repository.recoverStartedPending(clock.instant());
    }

    public OutboundExecutionRecord dispatch(String executionKey,UUID conversationId,String turnId,long generation,
            String logicalMessageId,String target,SendOperation operation){
        Objects.requireNonNull(operation);
        var now=clock.instant();
        var proposed=new OutboundExecutionRecord(executionKey,conversationId,turnId,generation,logicalMessageId,
                OutboundExecutionRecord.Status.PENDING,null,null,now,now,null);
        repository.reserve(proposed);
        var existing=repository.find(executionKey).orElseThrow();
        if(!existing.conversationId().equals(conversationId)||!existing.turnId().equals(turnId)
                ||existing.generation()!=generation||!existing.logicalMessageId().equals(logicalMessageId))
            throw new SecurityException("outbound execution key conflicts with its durable binding");
        if(existing.status()!=OutboundExecutionRecord.Status.PENDING)return existing;
        if(target==null||target.isBlank()){
            repository.complete(executionKey,OutboundExecutionRecord.Status.FAILED,null,"invalid-target",clock.instant());
            return repository.find(executionKey).orElseThrow();
        }
        if(!repository.startDispatch(executionKey,clock.instant()))return repository.find(executionKey).orElseThrow();
        try {
            SendReceipt receipt=operation.send();
            if(receipt==null){repository.complete(executionKey,OutboundExecutionRecord.Status.UNKNOWN,null,"missing-transport-result",clock.instant());}
            else if(receipt.status()==DeliveryStatus.ACCEPTED){
                repository.complete(executionKey,OutboundExecutionRecord.Status.SUCCESS,receipt.target(),null,clock.instant());
            } else if(receipt.status()==DeliveryStatus.REJECTED){
                repository.complete(executionKey,OutboundExecutionRecord.Status.FAILED,null,"onebot-rejected",clock.instant());
            } else {
                repository.complete(executionKey,OutboundExecutionRecord.Status.UNKNOWN,null,"delivery-unconfirmed",clock.instant());
            }
        } catch(RuntimeException uncertain) {
            repository.complete(executionKey,OutboundExecutionRecord.Status.UNKNOWN,null,"transport-exception-after-dispatch",clock.instant());
        }
        return repository.find(executionKey).orElseThrow();
    }

    @FunctionalInterface public interface SendOperation { SendReceipt send(); }
}
