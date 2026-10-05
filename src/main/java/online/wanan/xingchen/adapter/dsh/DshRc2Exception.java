package online.wanan.xingchen.adapter.dsh;

/** Sanitized DSH protocol failure. Never retains Cookie or launch-token material. */
public final class DshRc2Exception extends RuntimeException {
    private final String code;
    public DshRc2Exception(String code, String safeMessage) { super(safeMessage); this.code = code; }
    public DshRc2Exception(String code, String safeMessage, Throwable cause) { super(safeMessage, cause); this.code = code; }
    public String code() { return code; }
}
