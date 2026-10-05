package online.wanan.xingchen.core.sticker;

import java.util.List;

public record StickerPage(List<StickerMetadata> items,long total,int offset,int limit) {
    public StickerPage { items=List.copyOf(items); }
}
