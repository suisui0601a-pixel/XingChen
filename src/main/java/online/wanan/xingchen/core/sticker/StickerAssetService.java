package online.wanan.xingchen.core.sticker;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

/** Bounded filesystem-backed sticker import, metadata, and preview facade shared by Console and runtime. */
public final class StickerAssetService {
    public static final long MAX_FILE_BYTES=10L*1024*1024,MAX_SCAN_BYTES=100L*1024*1024;
    public static final int MAX_SCAN_FILES=200;
    private static final Map<String,String> MIME=Map.of(".png","image/png",".jpg","image/jpeg",".jpeg","image/jpeg",".gif","image/gif",".webp","image/webp");
    private final Path root;private final StickerService stickers;private final StickerRepository repository;
    public StickerAssetService(Path root,StickerService stickers,StickerRepository repository){this.root=root.toAbsolutePath().normalize();this.stickers=stickers;this.repository=repository;}

    public ScanResult scan(){
        try{Files.createDirectories(root);Path realRoot=root.toRealPath();Path library=realRoot.resolve("library").normalize();List<Path> files;
            List<Path> nodes;try(Stream<Path> walk=Files.walk(realRoot)){nodes=walk.limit((MAX_SCAN_FILES*20L)+1L).toList();}
            boolean nodeTruncated=nodes.size()>MAX_SCAN_FILES*20L;files=nodes.stream().filter(p->!p.equals(realRoot)&&!p.startsWith(library)).filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(p)).limit(MAX_SCAN_FILES+1L).toList();
            boolean truncated=nodeTruncated||files.size()>MAX_SCAN_FILES;int imported=0,duplicates=0,skipped=0;long total=0;List<StickerMetadata> items=new ArrayList<>();
            for(Path file:files.stream().limit(MAX_SCAN_FILES).toList()){
                if(Files.isSymbolicLink(file)){skipped++;continue;}try{long size=Files.size(file);if(size<1||size>MAX_FILE_BYTES||total+size>MAX_SCAN_BYTES){skipped++;continue;}String rel=realRoot.relativize(file).toString().replace('\\','/');String hash=sha256(file);boolean existed=repository.findByHash(hash).isPresent();StickerMetadata metadata=stickers.collectFromRoot(rel,List.of(),"console-scan");if(existed)duplicates++;else imported++;total+=size;items.add(metadata);}catch(IOException|IllegalArgumentException|SecurityException invalid){skipped++;}}
            return new ScanResult(imported,duplicates,skipped,truncated,items);
        }catch(IOException e){throw new IllegalStateException("sticker asset root is unavailable",e);}
    }

    public StickerMetadata upload(String originalName,InputStream input,List<String> tags)throws IOException{
        String name=safeName(originalName),ext=extension(Path.of(name));if(!MIME.containsKey(ext))throw new IllegalArgumentException("unsupported sticker file type");
        Path inbox=root.resolve("inbox");Files.createDirectories(inbox);Path realRoot=root.toRealPath(),realInbox=inbox.toRealPath();if(!realInbox.startsWith(realRoot)||Files.isSymbolicLink(inbox))throw new IllegalArgumentException("sticker inbox is not a safe asset directory");
        Path target=realInbox.resolve(UUID.randomUUID()+ext);long size=0;try(OutputStream out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1){size+=n;if(size>MAX_FILE_BYTES)throw new IllegalArgumentException("sticker file exceeds 10 MiB");out.write(b,0,n);}}
        catch(IOException|RuntimeException failure){Files.deleteIfExists(target);throw failure;}
        try{return stickers.collectFromRoot(realRoot.relativize(target).toString(),tags,"console-upload");}finally{Files.deleteIfExists(target);}
    }

    public StickerPage page(String query,Boolean enabled,String format,Boolean animated,int offset,int limit){return repository.page(query,enabled,format,animated,offset,limit);}
    public Optional<StickerMetadata> update(UUID id,List<String> tags,String note,boolean enabled,String expectedUpdatedAt){if(note==null||note.length()>500)throw new IllegalArgumentException("sticker note must be at most 500 characters");StickerMetadata old=repository.get(id).orElseThrow(()->new NoSuchElementException("sticker not found"));if(expectedUpdatedAt==null||!old.updatedAt().toString().equals(expectedUpdatedAt))throw new java.util.ConcurrentModificationException("sticker metadata changed");return repository.updateAdmin(id,StickerService.normalizeTags(tags),note.strip(),enabled,expectedUpdatedAt).or(()->{throw new java.util.ConcurrentModificationException("sticker metadata changed");});}
    public Preview preview(UUID id){StickerMetadata metadata=repository.get(id).orElseThrow(()->new NoSuchElementException("sticker not found"));try{Path realRoot=root.toRealPath(),library=realRoot.resolve("library").toRealPath(),path=realRoot.resolve(metadata.path()).normalize();if(!path.startsWith(library)||Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>MAX_FILE_BYTES)throw new NoSuchElementException("sticker content unavailable");Path real=path.toRealPath();if(!real.startsWith(library))throw new NoSuchElementException("sticker content unavailable");return new Preview(metadata.mimeType(),Files.readAllBytes(real),metadata.sha256());}catch(IOException e){throw new NoSuchElementException("sticker content unavailable");}}
    public static ImageInfo inspect(Path file,String extension){String expected=MIME.get(extension.toLowerCase(Locale.ROOT));if(expected==null)throw new IllegalArgumentException("unsupported sticker extension");byte[] header=new byte[32];try(InputStream in=Files.newInputStream(file)){int count=in.read(header);if(count<12)throw new IllegalArgumentException("sticker image header is invalid");String actual=sniff(header,count);if(!expected.equals(actual))throw new IllegalArgumentException("sticker extension and image content do not match");Integer width=null,height=null;Boolean animated=null;
        if(!extension.equalsIgnoreCase(".webp")){try(var image=ImageIO.createImageInputStream(file.toFile())){if(image==null)throw new IllegalArgumentException("image preview metadata is invalid");Iterator<ImageReader> readers=ImageIO.getImageReaders(image);if(!readers.hasNext())throw new IllegalArgumentException("image decoder is unavailable");ImageReader reader=readers.next();try{reader.setInput(image,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>8192||h>8192)throw new IllegalArgumentException("sticker dimensions exceed 8192px");width=w;height=h;animated=extension.equalsIgnoreCase(".gif")&&reader.getNumImages(true)>1;}finally{reader.dispose();}}}
        return new ImageInfo(actual,width,height,animated);
        }catch(IOException e){throw new IllegalArgumentException("sticker image is invalid");}}
    private static String sniff(byte[] b,int n){if(n>=8&&b[0]==(byte)0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G')return "image/png";if(n>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "image/jpeg";if(n>=6&&b[0]=='G'&&b[1]=='I'&&b[2]=='F'&&b[3]=='8'&&(b[4]=='7'||b[4]=='9')&&b[5]=='a')return "image/gif";if(n>=12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P')return "image/webp";return "application/octet-stream";}
    private static String safeName(String name){if(name==null||name.length()>255||name.contains("\0"))throw new IllegalArgumentException("invalid sticker filename");String base=name.replace('\\','/');base=base.substring(base.lastIndexOf('/')+1);if(base.isBlank()||base.equals(".")||base.equals(".."))throw new IllegalArgumentException("invalid sticker filename");return base;}
    private static String extension(Path p){String name=p.getFileName().toString();int i=name.lastIndexOf('.');return i<0?"":name.substring(i).toLowerCase(Locale.ROOT);}
    private static String sha256(Path p)throws IOException{try(InputStream in=Files.newInputStream(p)){MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)d.update(b,0,n);return HexFormat.of().formatHex(d.digest());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public record ImageInfo(String mimeType,Integer width,Integer height,Boolean animated){}
    public record ScanResult(int imported,int duplicates,int skipped,boolean truncated,List<StickerMetadata> items){public ScanResult{items=List.copyOf(items);}}
    public record Preview(String mimeType,byte[] bytes,String sha256){}
}
