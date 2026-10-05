package online.wanan.xingchen.adapter.onebot;

/** UNKNOWN means the request may have reached OneBot but confirmation was lost; callers must not blindly retry. */
public enum DeliveryStatus { ACCEPTED, REJECTED, UNKNOWN }
