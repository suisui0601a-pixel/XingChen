package online.wanan.xingchen.adapter.deepseek;

public final class ModelProviderException extends RuntimeException {
    private final int statusCode;private final boolean retryable;
    public ModelProviderException(String message,int statusCode,boolean retryable,Throwable cause){super(message,cause);this.statusCode=statusCode;this.retryable=retryable;}
    public int statusCode(){return statusCode;}public boolean retryable(){return retryable;}
}
