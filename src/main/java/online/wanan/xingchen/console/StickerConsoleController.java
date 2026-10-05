package online.wanan.xingchen.console;

import online.wanan.xingchen.core.sticker.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/stickers")
public class StickerConsoleController {
    private static final Set<String> MIME=Set.of("image/png","image/jpeg","image/gif","image/webp");
    private final StickerAssetService assets;
    public StickerConsoleController(StickerAssetService assets){this.assets=assets;}

    @GetMapping("/admin") public ResponseEntity<Map<String,Object>> page(@RequestParam(required=false) String q,@RequestParam(required=false) Boolean enabled,@RequestParam(required=false) String format,@RequestParam(required=false) Boolean animated,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size){int n=Math.max(1,Math.min(100,size)),p=Math.max(0,Math.min(100_000/n,page));StickerPage result=assets.page(q,enabled,format,animated,p*n,n);return noStore(Map.of("items",result.items(),"total",result.total(),"page",p,"pageSize",n,"maxFileBytes",StickerAssetService.MAX_FILE_BYTES,"sendTest","UNSUPPORTED_ADMIN_SAFE_PATH_NOT_AVAILABLE","formats",Map.of("png",cap("image/png"),"jpg",cap("image/jpeg"),"jpeg",cap("image/jpeg"),"gif",cap("image/gif"),"webp",cap("image/webp"))));}
    @PostMapping("/admin/scan") public ResponseEntity<StickerAssetService.ScanResult> scan(){return noStore(assets.scan());}
    @PostMapping(value="/admin/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResponseEntity<StickerMetadata> upload(@RequestPart("file") MultipartFile file,@RequestParam(required=false) List<String> tags)throws IOException{if(file.isEmpty())throw new IllegalArgumentException("sticker upload is empty");try(var input=file.getInputStream()){StickerMetadata result=assets.upload(file.getOriginalFilename(),input,tags==null?List.of():tags);return noStore(result);}}
    @PatchMapping("/admin/{id}") public ResponseEntity<StickerMetadata> update(@PathVariable UUID id,@RequestBody MetadataUpdate update){return noStore(assets.update(id,update.tags(),update.note(),update.enabled(),update.expectedUpdatedAt()).orElseThrow(()->new NoSuchElementException("sticker not found")));}
    @GetMapping("/{id}/content") public ResponseEntity<byte[]> content(@PathVariable UUID id){StickerAssetService.Preview preview=assets.preview(id);if(!MIME.contains(preview.mimeType()))throw new NoSuchElementException("sticker content unavailable");String ext=switch(preview.mimeType()){case "image/png"->"png";case "image/jpeg"->"jpg";case "image/gif"->"gif";case "image/webp"->"webp";default->throw new NoSuchElementException("sticker content unavailable");};return ResponseEntity.ok().contentType(MediaType.parseMediaType(preview.mimeType())).contentLength(preview.bytes().length).cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePrivate()).header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename("sticker-"+id+"."+ext,StandardCharsets.UTF_8).build().toString()).eTag('"'+preview.sha256()+'"').body(preview.bytes());}
    private static Map<String,Object> cap(String mime){return Map.of("mimeType",mime,"importSupported",true,"previewSupported",true,"sendSupported",true);}
    private static <T> ResponseEntity<T> noStore(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);}
    public record MetadataUpdate(List<String> tags,String note,boolean enabled,String expectedUpdatedAt){}
}
