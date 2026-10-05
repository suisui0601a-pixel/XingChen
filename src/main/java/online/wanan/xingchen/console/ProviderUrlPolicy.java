package online.wanan.xingchen.console;

import java.net.*;
import java.util.*;

/** Restricts provider targets to HTTPS public hosts; loopback HTTP is reserved for local/test profiles. */
public final class ProviderUrlPolicy {
    private ProviderUrlPolicy(){}
    public static String validate(String text,boolean allowLoopbackHttp){
        if(text==null||text.isBlank()||text.length()>512)throw new IllegalArgumentException("Provider base URL is invalid");
        try{URI uri=URI.create(text);String scheme=uri.getScheme(),host=uri.getHost();if(host==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||uri.getPort()==0||uri.getPort()>65535)throw new IllegalArgumentException();
            boolean local=isNumericLoopback(host);if(!"https".equalsIgnoreCase(scheme)&&!(allowLoopbackHttp&&local&&"http".equalsIgnoreCase(scheme)))throw new IllegalArgumentException();
            if(uri.getRawPath()!=null&&(uri.getRawPath().contains("..")||uri.getRawPath().contains("%2f")||uri.getRawPath().contains("%5c")))throw new IllegalArgumentException();
            InetAddress[] addresses=InetAddress.getAllByName(host);if(addresses.length==0)throw new IllegalArgumentException();for(InetAddress address:addresses)if(local?!address.isLoopbackAddress():!isPublic(address))throw new IllegalArgumentException();
            return new URI(scheme.toLowerCase(Locale.ROOT),null,host.toLowerCase(Locale.ROOT),uri.getPort(),trimSlash(uri.getPath()),null,null).toString();
        }catch(Exception e){throw new IllegalArgumentException("Provider URL must use HTTPS and a public host; local HTTP is allowed only for loopback development");}
    }
    private static String trimSlash(String path){if(path==null||path.isEmpty())return "";return path.replaceAll("/+$","");}
    private static boolean isNumericLoopback(String host){try{return (host.matches("[0-9.]+")||host.contains(":"))&&InetAddress.getByName(host).isLoopbackAddress();}catch(Exception e){return false;}}
    private static boolean isPublic(InetAddress a){if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;byte[] b=a.getAddress();if(a instanceof Inet4Address){int x=b[0]&255,y=b[1]&255;return !(x==0||x==10||x==127||x>=224||x==169&&y==254||x==172&&y>=16&&y<=31||x==192&&y==168||x==100&&y>=64&&y<=127);}if(a instanceof Inet6Address){int x=b[0]&255;return (x&0xfe)!=0xfc&&!(x==0xfe&&(b[1]&0xc0)==0x80);}return false;}
}
