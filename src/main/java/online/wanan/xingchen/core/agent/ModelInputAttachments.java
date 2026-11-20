package online.wanan.xingchen.core.agent;

import java.util.List;

public record ModelInputAttachments(List<ModelImage> images,String notice) {
    public static final ModelInputAttachments NONE=new ModelInputAttachments(List.of(),"");
    public ModelInputAttachments {images=List.copyOf(images);if(images.size()>2||images.stream().mapToLong(ModelImage::size).sum()>6*1024*1024)throw new IllegalArgumentException("image input limit");notice=notice==null?"":notice;}
    public int tokenReserve(){return images.size()*1024;}
}
