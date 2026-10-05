package online.wanan.xingchen.core.sticker;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StickerMetadata(UUID id,String path,String sha256,List<String> tags,String note,long usageCount,String source,Instant createdAt,Instant lastUsedAt,
                              String fileName,String mimeType,long fileSize,Integer width,Integer height,Boolean animated,boolean enabled,Instant updatedAt) {
    public StickerMetadata {tags=tags==null?List.of():List.copyOf(tags);fileName=fileName==null?"":fileName;mimeType=mimeType==null?"application/octet-stream":mimeType;updatedAt=updatedAt==null?createdAt:updatedAt;}
    public StickerMetadata(UUID id,String path,String sha256,List<String> tags,String note,long usageCount,String source,Instant createdAt,Instant lastUsedAt){this(id,path,sha256,tags,note,usageCount,source,createdAt,lastUsedAt,"","application/octet-stream",0,null,null,null,true,createdAt);}
}
