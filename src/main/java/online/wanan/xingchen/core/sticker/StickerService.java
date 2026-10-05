package online.wanan.xingchen.core.sticker;

import online.wanan.xingchen.adapter.onebot.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Local-asset sticker service. Collect accepts only an inbox basename; paths cannot escape the configured root. */
public final class StickerService {
    private static final Set<String> EXT=Set.of(".png",".jpg",".jpeg",".gif",".webp");
    private final Path root,inbox,library;private final StickerRepository repository;private final StickerPolicy policy;private final OneBotGateway gateway;private final Clock clock;
    public StickerService(Path root,StickerRepository repository,StickerPolicy policy,OneBotGateway gateway,Clock clock){this.root=root.toAbsolutePath().normalize();this.inbox=this.root.resolve("inbox");this.library=this.root.resolve("library");this.repository=repository;this.policy=policy;this.gateway=gateway;this.clock=clock;}
    public void beginTurn(){policy.beginTurn();}
    public List<StickerMetadata> list(int limit){return repository.list(limit).stream().filter(StickerMetadata::enabled).toList();}
    public List<StickerMetadata> search(String q,int limit){return repository.search(q,limit).stream().filter(StickerMetadata::enabled).toList();}
    public StickerMetadata collect(String assetId,List<String> tags,String source)throws IOException{
        if(assetId==null||!assetId.matches("[A-Za-z0-9_.-]{1,120}"))throw new IllegalArgumentException("invalid local sticker asset id");return collectFromRoot("inbox/"+assetId,tags,source);
    }
    public StickerMetadata collectFromRoot(String relativePath,List<String> tags,String source)throws IOException{
        if(relativePath==null||relativePath.isBlank()||relativePath.length()>300||relativePath.startsWith("/")||relativePath.startsWith("\\")||relativePath.contains(":")||relativePath.indexOf('\0')>=0||Arrays.stream(relativePath.split("[\\\\/]+",-1)).anyMatch(s->s.isEmpty()||s.equals(".")||s.equals("..")))throw new IllegalArgumentException("invalid sticker relative path");
        Files.createDirectories(root);Path rootReal=root.toRealPath(),sourcePath=rootReal.resolve(relativePath.replace('\\','/')).normalize();if(!sourcePath.startsWith(rootReal))throw new IllegalArgumentException("sticker path escaped asset root");
        Path walk=rootReal;for(Path part:rootReal.relativize(sourcePath)){walk=walk.resolve(part);if(Files.isSymbolicLink(walk))throw new IllegalArgumentException("symbolic links are not accepted for sticker assets");}
        Path sourceReal=sourcePath.toRealPath();if(!sourceReal.startsWith(rootReal)||!Files.isRegularFile(sourceReal))throw new IllegalArgumentException("sticker asset must be a regular file under the configured root");
        Path libraryPath=rootReal.resolve("library").normalize();if(sourceReal.startsWith(libraryPath))throw new IllegalArgumentException("managed library assets are not import sources");
        long size=Files.size(sourceReal);if(size<1||size>StickerAssetService.MAX_FILE_BYTES)throw new IllegalArgumentException("unsupported or oversized sticker asset");String ext=extension(sourceReal.getFileName().toString());if(!EXT.contains(ext))throw new IllegalArgumentException("unsupported sticker extension");
        StickerAssetService.ImageInfo image=StickerAssetService.inspect(sourceReal,ext);String hash=sha256(sourceReal);Optional<StickerMetadata> prior=repository.findByHash(hash);if(prior.isPresent())return prior.get();
        Files.createDirectories(library);Path libraryReal=library.toRealPath();if(!libraryReal.startsWith(rootReal)||Files.isSymbolicLink(library))throw new IllegalArgumentException("sticker library must remain under the configured root");Path target=libraryReal.resolve(hash+ext).normalize();if(!target.startsWith(libraryReal))throw new IllegalArgumentException("invalid sticker destination");if(!Files.exists(target))Files.copy(sourceReal,target,StandardCopyOption.COPY_ATTRIBUTES);
        Instant now=clock.instant();StickerMetadata metadata=new StickerMetadata(UUID.randomUUID(),rootReal.relativize(target).toString().replace('\\','/'),hash,normalizeTags(tags),"",0,Objects.requireNonNullElse(source,"console-import"),now,null,sourceReal.getFileName().toString(),image.mimeType(),size,image.width(),image.height(),image.animated(),true,now);return repository.save(metadata);
    }
    public Optional<StickerMetadata> updateAdmin(UUID id,List<String> tags,String note,boolean enabled){return repository.updateAdmin(id,normalizeTags(tags),note,enabled);}
    public Optional<StickerMetadata> get(UUID id){return repository.get(id);}
    public Optional<StickerMetadata> note(UUID id,String note){if(note==null||note.length()>500)throw new IllegalArgumentException("sticker note is too long");return repository.updateNote(id,note);}
    public SendReceipt send(UUID id,String target,String kind){StickerMetadata s=repository.get(id).orElseThrow(()->new NoSuchElementException("sticker not found"));if(!s.enabled())return new SendReceipt(false,"sticker",target,"sticker disabled");Instant now=clock.instant();StickerPolicy.Decision d=policy.evaluate(id,now);if(!d.allowed())return new SendReceipt(false,"sticker",target,d.reason());try{Path rootReal=root.toRealPath(),libraryReal=library.toRealPath(),asset=rootReal.resolve(s.path()).normalize().toRealPath();if(!libraryReal.startsWith(rootReal)||!asset.startsWith(libraryReal)||!Files.isRegularFile(asset)||Files.isSymbolicLink(asset))return new SendReceipt(false,"sticker",target,"sticker asset unavailable");SendReceipt receipt=gateway.sendImage(target,kind,asset.toString());if(receipt.accepted()){repository.recordUse(id,now);policy.record(id,now);}return receipt;}catch(IOException e){return new SendReceipt(false,"sticker",target,"sticker asset unavailable");}}
    private static String extension(String name){int i=name.lastIndexOf('.');return i<0?"":name.substring(i).toLowerCase(Locale.ROOT);}
    static List<String> normalizeTags(List<String> tags){if(tags==null)return List.of();if(tags.size()>32)throw new IllegalArgumentException("sticker tag limit exceeded");List<String> result=tags.stream().filter(Objects::nonNull).map(String::trim).filter(s->!s.isEmpty()).peek(s->{if(s.length()>40)throw new IllegalArgumentException("sticker tag is too long");}).distinct().toList();return List.copyOf(result);}
    private static String sha256(Path p)throws IOException{try(InputStream in=Files.newInputStream(p)){MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[8192];int n;while((n=in.read(b))>0)d.update(b,0,n);return HexFormat.of().formatHex(d.digest());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
