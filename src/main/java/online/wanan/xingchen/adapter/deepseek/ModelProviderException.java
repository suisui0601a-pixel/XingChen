package online.wanan.xingchen.adapter.deepseek;

public final class ModelProviderException extends RuntimeException {
    private final int statusCode;private final boolean retryable;
    private final String providerErrorType,providerErrorCode;
    public ModelProviderException(String message,int statusCode,boolean retryable,Throwable cause){this(message,statusCode,retryable,cause,null,null);}
    ModelProviderException(String message,int statusCode,boolean retryable,Throwable cause,String type,String code){super(message,cause);this.statusCode=statusCode;this.retryable=retryable;providerErrorType=type;providerErrorCode=code;}
    public int statusCode(){return statusCode;}public boolean retryable(){return retryable;}
    public String providerErrorType(){return providerErrorType;}public String providerErrorCode(){return providerErrorCode;}
}
