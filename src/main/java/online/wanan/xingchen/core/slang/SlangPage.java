package online.wanan.xingchen.core.slang;

import java.util.List;

public record SlangPage(List<SlangEntry> items,long total,int offset,int limit){public SlangPage{items=List.copyOf(items);}}
