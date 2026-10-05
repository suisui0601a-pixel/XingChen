package online.wanan.xingchen.adapter.onebot;

public record SendReceipt(boolean accepted,String operation,String target,String content,DeliveryStatus status) {
    public SendReceipt(boolean accepted,String operation,String target,String content) {
        this(accepted,operation,target,content,accepted?DeliveryStatus.ACCEPTED:DeliveryStatus.REJECTED);
    }
    public SendReceipt {
        if (status == null) status = accepted ? DeliveryStatus.ACCEPTED : DeliveryStatus.REJECTED;
        if (accepted != (status == DeliveryStatus.ACCEPTED)) throw new IllegalArgumentException("accepted must agree with delivery status");
    }
}
