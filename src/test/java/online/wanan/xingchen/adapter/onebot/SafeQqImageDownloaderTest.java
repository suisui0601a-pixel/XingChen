package online.wanan.xingchen.adapter.onebot;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class SafeQqImageDownloaderTest {
    @Test void onlyQqCdnTlsTargetsAreAccepted(){
        for(String s:new String[]{"http://127.0.0.1/a","https://localhost/a","file:///etc/passwd","https://gchat.qpic.cn.evil.test/a","https://user@gchat.qpic.cn/a","https://gchat.qpic.cn:444/a","https://gchat.qpic.cn/a#b"})assertThatThrownBy(()->SafeQqImageDownloader.validatedUri(s)).as(s).isInstanceOf(IllegalArgumentException.class);
        assertThat(SafeQqImageDownloader.validatedUri("http://multimedia.nt.qq.com.cn/download?fixture=1").toString()).isEqualTo("https://multimedia.nt.qq.com.cn/download?fixture=1");
    }
    @Test void privateAndSpecialAddressesAreRejectedBeforeSocketCreation()throws Exception{
        for(String s:new String[]{"127.0.0.1","10.1.2.3","169.254.169.254","192.168.1.1","172.16.0.1","100.64.1.1","0.0.0.0","::1","fc00::1","fe80::1","::"})assertThat(SafeQqImageDownloader.publicAddress(InetAddress.getByName(s))).as(s).isFalse();
        assertThat(SafeQqImageDownloader.publicAddress(InetAddress.getByName("1.1.1.1"))).isTrue();
    }
    @Test void boundedHttpFramingRejectsRedirectTruncationOversizeAndAmbiguity()throws Exception{
        for(String s:new String[]{"HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1\r\n\r\n","HTTP/1.1 200 OK\r\nContent-Length: 4194305\r\n\r\n","HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\na","HTTP/1.1 200 OK\r\nContent-Length: 1\r\nTransfer-Encoding: chunked\r\n\r\n"})assertThatThrownBy(()->SafeQqImageDownloader.readBody(new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII)))).isInstanceOf(IOException.class);
        String good="HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n0\r\n\r\n";
        assertThat(SafeQqImageDownloader.readBody(new ByteArrayInputStream(good.getBytes(StandardCharsets.US_ASCII)))).isEqualTo("abc".getBytes());
    }
    @Test void contentIsSniffedAndModelImageDiagnosticsNeverPrintBase64()throws Exception{
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        var image=SafeQqImageDownloader.decode(png);assertThat(image.dataUrl()).startsWith("data:image/png;base64,");assertThat(image.toString()).contains("redacted").doesNotContain("iVBOR");
        assertThatThrownBy(()->SafeQqImageDownloader.decode("not an image".getBytes())).isInstanceOf(IOException.class);
    }
}
