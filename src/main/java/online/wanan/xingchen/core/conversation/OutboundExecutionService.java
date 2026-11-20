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
        return dispatch(executionKey,conversationId,turnId,generation,logicalMessageId,target,()->null,operation);
    }

    /** Admission runs once, after the durable claim and before any transport call. */
    public OutboundExecutionRecord dispatch(String executionKey,UUID conversationId,String turnId,long generation,
            String logicalMessageId,String target,java.util.function.Supplier<String> rejectionReason,SendOperation operation){
        Objects.requireNonNull(operation);
        Objects.requireNonNull(rejectionReason);
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
            String rejection=rejectionReason.get();
            if(rejection!=null){
                repository.complete(executionKey,OutboundExecutionRecord.Status.FAILED,null,rejection,clock.instant());
                return repository.find(executionKey).orElseThrow();
            }
            SendReceipt receipt=operation.send();
            if(receipt==null){repository.complete(executionKey,OutboundExecutionRecord.Status.UNKNOWN,null,"missing-transport-result",clock.instant());}
            else if(receipt.status()==DeliveryStatus.ACCEPTED){
                repository.complete(executionKey,OutboundExecutionRecord.Status.SUCCESS,receipt.target(),null,clock.instant());
            } else if(receipt.status()==DeliveryStatus.REJECTED){
                repository.complete(executionKey,OutboundExecutionRecord.Status.FAILED,null,rejectionLabel(receipt),clock.instant());
            } else {
                repository.complete(executionKey,OutboundExecutionRecord.Status.UNKNOWN,null,"delivery-unconfirmed",clock.instant());
            }
        } catch(RuntimeException uncertain) {
            repository.complete(executionKey,OutboundExecutionRecord.Status.UNKNOWN,null,"transport-exception-after-dispatch",clock.instant());
        }
        return repository.find(executionKey).orElseThrow();
    }

    @FunctionalInterface public interface SendOperation { SendReceipt send(); }
    private static String rejectionLabel(SendReceipt receipt){
        if(!"sticker".equals(receipt.operation()))return "onebot-rejected";
        return switch(Objects.requireNonNullElse(receipt.content(),"")){
            case "per-turn limit"->"sticker-per-turn-limit";
            case "repetition penalty"->"sticker-repetition-penalty";
            case "cooldown"->"sticker-cooldown";
            case "sticker disabled"->"sticker-disabled";
            case "sticker asset unavailable"->"sticker-asset-unavailable";
            case "sticker integrity mismatch"->"sticker-integrity-mismatch";
            default->"onebot-rejected";
        };
    }
}
