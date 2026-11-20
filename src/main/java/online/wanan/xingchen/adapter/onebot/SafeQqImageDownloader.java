package online.wanan.xingchen.adapter.onebot;

import online.wanan.xingchen.core.agent.ModelImage;
import javax.net.ssl.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** HTTPS QQ-CDN only. Validate every DNS answer, then pin the connection to that answer with verified TLS/SNI. */
public final class SafeQqImageDownloader implements QqImageInputService.Downloader,AutoCloseable {
    public static final int MAX_BYTES=4*1024*1024;
    private final java.util.concurrent.ThreadPoolExecutor dns=new java.util.concurrent.ThreadPoolExecutor(0,2,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.SynchronousQueue<>(),r->{var t=new Thread(r,"qq-image-dns");t.setDaemon(true);return t;},new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    public static URI validatedUri(String value){
        try{URI u=URI.create(value);String host=Objects.requireNonNull(u.getHost()).toLowerCase(Locale.ROOT);
            if(!(host.endsWith(".qpic.cn")||host.endsWith(".qq.com")||host.endsWith(".qq.com.cn"))||u.getUserInfo()!=null||u.getFragment()!=null||value.length()>8192||u.getPort()!=-1&&u.getPort()!=443)throw new IllegalArgumentException();
            if(!Set.of("http","https").contains(u.getScheme()))throw new IllegalArgumentException();
            // Some OneBot versions return HTTP CDN links. Upgrade the same allowed host/path to TLS.
            return URI.create("https://"+host+(u.getRawPath()==null||u.getRawPath().isEmpty()?"/":u.getRawPath())+(u.getRawQuery()==null?"":"?"+u.getRawQuery()));
        }catch(Exception invalid){throw new IllegalArgumentException("image source is not an allowed QQ CDN");}
    }
    public static boolean publicAddress(InetAddress a){
        if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isSiteLocalAddress()||a.isLinkLocalAddress()||a.isMulticastAddress())return false;
        byte[] b=a.getAddress();int x=b[0]&255,y=b[1]&255;
        if(a instanceof Inet6Address)return (x&0xe0)==0x20; // globally routed unicast only
        return !(x==0||x==10||x==127||x>=224||x==169&&y==254||x==100&&y>=64&&y<=127||x==172&&y>=16&&y<=31||x==192&&(y==168||y==0||y==2)||x==198&&(y==18||y==19||y==51)||x==203&&y==0);
    }
    @Override public ModelImage download(String source)throws Exception{
        long deadline=System.nanoTime()+12_000_000_000L;
        URI uri=validatedUri(source);InetAddress[] addresses;
        var lookup=dns.submit(()->InetAddress.getAllByName(uri.getHost()));
        try{addresses=lookup.get(2500,java.util.concurrent.TimeUnit.MILLISECONDS);}finally{lookup.cancel(true);}
        if(addresses.length==0||Arrays.stream(addresses).anyMatch(a->!publicAddress(a)))throw new IOException("unsafe image destination");
        try(Socket tcp=new Socket()){
            tcp.connect(new InetSocketAddress(addresses[0],443),3000);tcp.setSoTimeout(8000);
            try(SSLSocket tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(tcp,uri.getHost(),443,true)){
                var params=tls.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");params.setServerNames(List.of(new SNIHostName(uri.getHost())));tls.setSSLParameters(params);tls.setSoTimeout((int)Math.max(1,Math.min(8000,(deadline-System.nanoTime())/1_000_000)));tls.startHandshake();
                String target=uri.getRawPath()+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());
                tls.getOutputStream().write(("GET "+target+" HTTP/1.1\r\nHost: "+uri.getHost()+"\r\nAccept: image/*\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));tls.getOutputStream().flush();
                InputStream timed=new FilterInputStream(tls.getInputStream()){
                    private void check()throws IOException{long left=deadline-System.nanoTime();if(left<=0||Thread.currentThread().isInterrupted())throw new java.net.SocketTimeoutException("image time budget exceeded");tls.setSoTimeout((int)Math.max(1,Math.min(8000,left/1_000_000)));}
                    @Override public int read()throws IOException{check();return in.read();}
                    @Override public int read(byte[] b,int off,int len)throws IOException{check();return in.read(b,off,len);}
                };
                return decode(readBody(new BufferedInputStream(timed)));
            }
        }
    }
    static byte[] readBody(InputStream in)throws IOException{
        String status=line(in,2048);if(!status.matches("HTTP/1\\.[01] 200(?: .*)?"))throw new IOException("image HTTP response rejected");
        Map<String,String> headers=new HashMap<>();int size=status.length();
        for(;;){String l=line(in,8192);size+=l.length();if(size>16384)throw new IOException("image headers too large");if(l.isEmpty())break;int colon=l.indexOf(':');if(colon<1)throw new IOException("invalid image headers");String k=l.substring(0,colon).toLowerCase(Locale.ROOT);if(headers.put(k,l.substring(colon+1).trim())!=null)throw new IOException("duplicate image header");}
        if(!headers.getOrDefault("content-encoding","identity").equalsIgnoreCase("identity"))throw new IOException("compressed image transfer rejected");
        var out=new ByteArrayOutputStream();
        if(headers.containsKey("transfer-encoding")){
            if(!headers.get("transfer-encoding").equalsIgnoreCase("chunked")||headers.containsKey("content-length"))throw new IOException("invalid image framing");
            for(int chunks=0;chunks<8192;chunks++){String chunk=line(in,128);long length;try{length=Long.parseLong(chunk.split(";",2)[0].trim(),16);}catch(Exception e){throw new IOException("invalid image chunk");}
                if(length<0||length>MAX_BYTES-out.size())throw new IOException("image exceeds byte limit");if(length==0)return out.toByteArray();copy(in,out,(int)length);if(!line(in,2).isEmpty())throw new IOException("invalid image chunk terminator");}
            throw new IOException("too many image chunks");
        }
        if(headers.containsKey("content-length")){
            long length;try{length=Long.parseLong(headers.get("content-length"));}catch(Exception e){throw new IOException("invalid image length");}
            if(length<=0||length>MAX_BYTES)throw new IOException("image exceeds byte limit");copy(in,out,(int)length);
        }else{byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("image exceeds byte limit");out.write(buf,0,n);}}
        return out.toByteArray();
    }
    private static String line(InputStream in,int max)throws IOException{var out=new ByteArrayOutputStream();for(int i=0;i<=max;i++){int b=in.read();if(b<0)throw new EOFException();if(b=='\n'){byte[] a=out.toByteArray();if(a.length==0||a[a.length-1]!='\r')throw new IOException("invalid HTTP line");return new String(a,0,a.length-1,StandardCharsets.US_ASCII);}out.write(b);}throw new IOException("image HTTP line too long");}
    private static void copy(InputStream in,OutputStream out,int remaining)throws IOException{byte[] b=new byte[8192];while(remaining>0){int n=in.read(b,0,Math.min(remaining,b.length));if(n<0)throw new EOFException();out.write(b,0,n);remaining-=n;}}
    static ModelImage decode(byte[] b)throws IOException{
        String mime;
        if(b.length>=8&&b[0]==(byte)137&&b[1]=='P'&&b[2]=='N'&&b[3]=='G')mime="image/png";
        else if(b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)mime="image/jpeg";
        else if(b.length>=6&&new String(b,0,6,StandardCharsets.US_ASCII).matches("GIF8[79]a"))mime="image/gif";
        else if(b.length>=30&&new String(b,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(b,8,4,StandardCharsets.US_ASCII).equals("WEBP"))mime="image/webp";
        else throw new IOException("unsupported image contents");
        int w,h;
        if(mime.equals("image/webp")){
            String format=new String(b,12,4,StandardCharsets.US_ASCII);
            if(format.equals("VP8X")){w=1+le24(b,24);h=1+le24(b,27);}
            else if(format.equals("VP8L")&&b[20]==0x2f){int n=(b[21]&255)|((b[22]&255)<<8)|((b[23]&255)<<16)|((b[24]&255)<<24);w=(n&0x3fff)+1;h=((n>>>14)&0x3fff)+1;}
            else if(format.equals("VP8 ")&&b[23]==(byte)0x9d&&b[24]==1&&b[25]==0x2a){w=((b[26]&255)|((b[27]&255)<<8))&0x3fff;h=((b[28]&255)|((b[29]&255)<<8))&0x3fff;}
            else throw new IOException("invalid webp dimensions");
        }else{try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(b))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("invalid image");var reader=readers.next();try{reader.setInput(input,true,true);w=reader.getWidth(0);h=reader.getHeight(0);}finally{reader.dispose();}}}
        if(w<1||h<1||w>8192||h>8192||(long)w*h>16_000_000)throw new IOException("image dimensions exceed safety limit");
        return new ModelImage(mime,b);
    }
    private static int le24(byte[] b,int i){return (b[i]&255)|((b[i+1]&255)<<8)|((b[i+2]&255)<<16);}
    @Override public void close(){dns.shutdownNow();}
}
