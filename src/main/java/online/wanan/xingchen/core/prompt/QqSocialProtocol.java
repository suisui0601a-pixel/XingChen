package online.wanan.xingchen.core.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Shared QQ behavior, adapted from the MIT-licensed qq-bridge reserved2 preset. */
public final class QqSocialProtocol {
    public static final String TEXT = load();

    private static String load() {
        try (var input = QqSocialProtocol.class.getResourceAsStream("/prompts/qq-social-protocol.txt")) {
            if (input == null) throw new IllegalStateException("QQ social protocol resource missing");
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
            if (text.isBlank()) throw new IllegalStateException("QQ social protocol resource empty");
            return text;
        } catch (IOException e) {
            throw new IllegalStateException("QQ social protocol resource unreadable", e);
        }
    }

    private QqSocialProtocol() { }
}
