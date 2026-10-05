package online.wanan.xingchen.adapter.dsh;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Launch-token exchange and in-memory Cookie lifecycle for the pinned RC.2 browser-session contract. */
final class DshRc2AuthClient {
    private final HttpClient http;
    private final URI baseUri;
    private final String launchToken;
    private final Duration timeout;
    private volatile String cookie;

    DshRc2AuthClient(HttpClient http, URI baseUri, String launchToken, Duration timeout) {
        this.http=Objects.requireNonNull(http);this.baseUri=baseUri;this.launchToken=launchToken;this.timeout=timeout;
    }
    String cookie() {
        String current=cookie;if(current!=null)return current;
        synchronized(this){if(cookie==null)cookie=exchange();return cookie;}
    }
    synchronized void invalidate(String rejectedCookie){if(Objects.equals(cookie,rejectedCookie))cookie=null;}

    private String exchange(){
        if(launchToken.isBlank())throw new DshRc2Exception("auth/not-configured","DSH authentication is not configured");
        try {
            URI current=URI.create(baseUri.getScheme()+"://"+baseUri.getRawAuthority()+"/?token="+URLEncoder.encode(launchToken,StandardCharsets.UTF_8));
            for(int redirects=0;redirects<=5;redirects++){
                HttpRequest request=HttpRequest.newBuilder(current).timeout(timeout).GET().build();
                HttpResponse<Void> response=http.send(request,HttpResponse.BodyHandlers.discarding());
                String found=firstCookie(response.headers().allValues("set-cookie"));
                if(found!=null)return found;
                int status=response.statusCode();
                if(status>=300&&status<400){String location=response.headers().firstValue("location").orElseThrow(()->new DshRc2Exception("auth/invalid-response","DSH authentication redirect is malformed"));URI next=current.resolve(location);if(!sameOrigin(baseUri,next))throw new DshRc2Exception("auth/invalid-response","DSH authentication redirect changed origin");current=next;continue;}
                throw new DshRc2Exception(status==401||status==403?"auth/rejected":"auth/invalid-response","DSH authentication exchange failed");
            }
            throw new DshRc2Exception("auth/invalid-response","DSH authentication redirect limit exceeded");
        } catch(DshRc2Exception e){throw e;} catch(java.net.http.HttpTimeoutException e){throw new DshRc2Exception("transport/timeout","DSH authentication timed out");} catch(InterruptedException e){Thread.currentThread().interrupt();throw new DshRc2Exception("transport/interrupted","DSH authentication interrupted");} catch(Exception e){throw new DshRc2Exception("transport/unavailable","DSH authentication unavailable");}
    }
    private static String firstCookie(List<String> headers){for(String header:headers){int semi=header.indexOf(';');String pair=(semi<0?header:header.substring(0,semi)).trim();int equals=pair.indexOf('=');if(equals>0&&equals<pair.length()-1)return pair;}return null;}
    private static boolean sameOrigin(URI a,URI b){return Objects.equals(a.getScheme(),b.getScheme())&&Objects.equals(a.getHost(),b.getHost())&&effectivePort(a)==effectivePort(b);}
    private static int effectivePort(URI u){return u.getPort()>=0?u.getPort():"https".equals(u.getScheme())?443:80;}
}
