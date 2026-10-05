package online.wanan.xingchen.core.relationship;

public record AddressResolution(String address, String source, ScopeType scope, boolean explicit, double confidence) {}
