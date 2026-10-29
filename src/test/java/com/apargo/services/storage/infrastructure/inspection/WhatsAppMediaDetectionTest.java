package com.apargo.services.storage.infrastructure.inspection;

import com.apargo.services.storage.application.service.MediaValidationService;
import com.apargo.services.storage.config.properties.MediaValidationProperties;
import com.apargo.services.storage.domain.exception.ContentTypeNotAllowedException;
import com.apargo.services.storage.domain.media.ContentType;
import com.apargo.services.storage.domain.media.MediaType;
import com.apargo.services.storage.domain.shared.ByteSize;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The files a WhatsApp customer sends, as messaging-service uploads them: detected from their
 * first bytes and checked against the allowlist in application.yml. Before the alias and
 * {@code ftyp} fixes, every voice note and most Android videos were refused here.
 */
class WhatsAppMediaDetectionTest {

    private final TikaContentInspector inspector = new TikaContentInspector();

    /** The allowlist as application.yml declares it. */
    private final MediaValidationService validation = new MediaValidationService(
            new MediaValidationProperties(16_777_216L, 8192, true,
                    Map.of("IMAGE", 8_388_608L),
                    Map.of(
                            "IMAGE", List.of("image/jpeg", "image/png", "image/webp"),
                            "VIDEO", List.of("video/mp4", "video/3gpp"),
                            "AUDIO", List.of("audio/mpeg", "audio/mp4", "audio/ogg", "audio/aac", "audio/amr"),
                            "DOCUMENT", List.of("application/pdf", "text/plain", "text/csv",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))));

    @Test
    void anOggOpusVoiceNoteIsAudioOgg() {
        ContentType type = inspector.inspect(ogg("OpusHead"), "audio/ogg; codecs=opus", "voice.ogg");

        assertThat(type.detected()).isEqualTo("audio/ogg");
        assertThat(type.isConsistent()).isTrue();
        assertThat(validation.validateDetected(type, ByteSize.of(20_000))).isEqualTo(MediaType.AUDIO);
    }

    @Test
    void adtsAacIsAudioAac() {
        byte[] adts = pad(new byte[]{(byte) 0xFF, (byte) 0xF1, 0x50, (byte) 0x80, 0x00, 0x1F, (byte) 0xFC});

        ContentType type = inspector.inspect(adts, "audio/aac", "voice.aac");

        assertThat(type.detected()).isEqualTo("audio/aac");
        assertThat(validation.validateDetected(type, ByteSize.of(20_000))).isEqualTo(MediaType.AUDIO);
    }

    @Test
    void anIsomBrandedVideoIsMp4NotQuickTime() {
        ContentType type = inspector.inspect(ftyp("isom"), "video/mp4", "video.mp4");

        assertThat(type.detected()).isEqualTo("video/mp4");
        assertThat(validation.validateDetected(type, ByteSize.of(2_000_000))).isEqualTo(MediaType.VIDEO);
    }

    @Test
    void aRealQuickTimeFileStaysQuickTimeAndIsStillRefused() {
        ContentType type = inspector.inspect(ftyp("qt  "), null, "clip.mov");

        assertThat(type.detected()).isEqualTo("video/quicktime");
        assertThatThrownBy(() -> validation.validateDetected(type, ByteSize.of(2_000_000)))
                .isInstanceOf(ContentTypeNotAllowedException.class);
    }

    @Test
    void aWordDocumentIsRecognisedByItsName() {
        ContentType type = inspector.inspect(zip("[Content_Types].xml"), "application/octet-stream",
                "invoice.docx");

        assertThat(type.detected())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(validation.validateDetected(type, ByteSize.of(50_000))).isEqualTo(MediaType.DOCUMENT);
    }

    @Test
    void aZipIsNotOnTheAllowlist() {
        ContentType type = inspector.inspect(zip("a.txt"), "application/octet-stream", "files.zip");

        assertThatThrownBy(() -> validation.validateDetected(type, ByteSize.of(50_000)))
                .isInstanceOf(ContentTypeNotAllowedException.class);
    }

    @Test
    void mp4BrandNeedsTheFtypBox() {
        assertThat(TikaContentInspector.hasMp4Brand(ftyp("mp42"))).isTrue();
        assertThat(TikaContentInspector.hasMp4Brand(ftyp("qt  "))).isFalse();
        assertThat(TikaContentInspector.hasMp4Brand(new byte[]{1, 2, 3})).isFalse();
    }

    @Test
    void codecNamesNormaliseToTheirContainer() {
        assertThat(ContentType.normalise("audio/opus")).isEqualTo("audio/ogg");
        assertThat(ContentType.normalise("audio/vorbis")).isEqualTo("audio/ogg");
        assertThat(ContentType.normalise("audio/x-aac")).isEqualTo("audio/aac");
        assertThat(ContentType.normalise("Audio/OGG; codecs=opus")).isEqualTo("audio/ogg");
        assertThat(ContentType.normalise("image/jpg")).isEqualTo("image/jpeg");
    }

    // ------------------------------------------------------------------ headers

    private static byte[] ogg(String codecMagic) {
        byte[] b = new byte[64];
        System.arraycopy("OggS".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        b[5] = 0x02;
        b[26] = 1;
        b[27] = 19;
        byte[] magic = codecMagic.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(magic, 0, b, 28, magic.length);
        return b;
    }

    private static byte[] ftyp(String brand) {
        byte[] b = new byte[64];
        b[3] = 0x20;
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, b, 4, 4);
        System.arraycopy(brand.getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4);
        System.arraycopy(brand.getBytes(StandardCharsets.US_ASCII), 0, b, 16, 4);
        return b;
    }

    private static byte[] zip(String firstEntry) {
        byte[] b = new byte[128];
        b[0] = 'P';
        b[1] = 'K';
        b[2] = 3;
        b[3] = 4;
        b[4] = 20;
        byte[] name = firstEntry.getBytes(StandardCharsets.US_ASCII);
        b[26] = (byte) name.length;
        System.arraycopy(name, 0, b, 30, name.length);
        return b;
    }

    private static byte[] pad(byte[] header) {
        byte[] b = new byte[64];
        System.arraycopy(header, 0, b, 0, header.length);
        return b;
    }
}
