package online.wanan.xingchen.core.agent;

import java.util.Base64;
import java.util.Set;

/** Ephemeral image input. Never include its contents in diagnostics or durable state. */
public final class ModelImage {
    private final String mimeType;
    private final byte[] bytes;
    public ModelImage(String mimeType,byte[] bytes){
        if(!Set.of("image/png","image/jpeg","image/gif","image/webp").contains(mimeType)||bytes==null||bytes.length==0||bytes.length>4*1024*1024)throw new IllegalArgumentException("invalid bounded model image");
        this.mimeType=mimeType;this.bytes=bytes.clone();
    }
    public String dataUrl(){return "data:"+mimeType+";base64,"+Base64.getEncoder().encodeToString(bytes);}
    public int size(){return bytes.length;}
    @Override public String toString(){return "ModelImage["+mimeType+", bytes="+bytes.length+", contents=redacted]";}
}
