package online.wanan.xingchen.console;

/** Declares the expected runtime action after a configuration mutation. */
public enum ApplyMode {
    HOT_APPLY,
    RESTART_REQUIRED,
    EXTERNAL_RECONNECT_REQUIRED
}
