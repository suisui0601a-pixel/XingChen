package online.wanan.xingchen.console;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/voice")
public class VoiceCapabilityController {
    @GetMapping("/capabilities") public ResponseEntity<Map<String,Object>> capabilities(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(Map.ofEntries(Map.entry("status","PARTIAL_METADATA_ONLY"),Map.entry("receiveContentTypes",java.util.List.of("record")),Map.entry("recordPayloadStoredAsAudioAsset",false),Map.entry("transcription",false),Map.entry("tts",false),Map.entry("voiceSend",false),Map.entry("sendTest",false),Map.entry("rateSetting",false),Map.entry("providerSettings",false),Map.entry("supportedSendMediaTypes",java.util.List.of()),Map.entry("message","This runtime only recognizes record-segment metadata in conversation diagnostics; it has no audio asset pipeline, transcription, synthesis, voice send adapter, provider, or voice-specific rate setting.")));}
}
