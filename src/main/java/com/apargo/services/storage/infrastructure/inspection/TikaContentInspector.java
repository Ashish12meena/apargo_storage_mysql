package com.apargo.services.storage.infrastructure.inspection;

import com.apargo.services.storage.application.port.out.ContentInspectorPort;
import com.apargo.services.storage.domain.media.ContentType;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Magic-byte detection over a bounded prefix of the file.
 *
 * <p>The predecessor's only content check was the {@code Content-Type} the client
 * put in the multipart header; nothing read the file. An HTML document with
 * embedded script labelled {@code image/png} passed validation and was stored as
 * an image.
 *
 * <p>Cost is flat in file size: the same 8 KB is examined for a 5 KB file and a
 * 500 MB one.
 */
@Component
@Slf4j
public class TikaContentInspector implements ContentInspectorPort {

    private static final String OCTET_STREAM = "application/octet-stream";
    private static final String QUICKTIME = "video/quicktime";
    private static final String MP4 = "video/mp4";

    /**
     * ISO base media major brands that are MP4, not QuickTime. Tika 2.9 reports a file
     * whose {@code ftyp} brand is {@code isom} — the brand most Android phones and
     * WhatsApp write — as {@code video/quicktime}, and storage refused it. A real
     * QuickTime file has the brand {@code qt  }, which is not in this list.
     */
    private static final Set<String> MP4_BRANDS = Set.of(
            "isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "dash", "M4V ", "MSNV");

    private final Detector detector;

    public TikaContentInspector() {
        this.detector = TikaConfig.getDefaultConfig().getDetector();
    }

    @Override
    public ContentType inspect(byte[] header, String declaredType, String filename) {
        return ContentType.of(declaredType, detect(header, filename));
    }

    private String detect(byte[] header, String filename) {
        if (header == null || header.length == 0) {
            return OCTET_STREAM;
        }
        Metadata metadata = new Metadata();
        if (filename != null && !filename.isBlank()) {
            // A filename hint only refines a magic-byte match; it never overrides
            // one, so a misleading extension cannot smuggle a type through.
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        try (InputStream stream = new ByteArrayInputStream(header)) {
            MediaType type = detector.detect(stream, metadata);
            String detected = type == null ? OCTET_STREAM : type.getBaseType().toString();
            return QUICKTIME.equals(detected) && hasMp4Brand(header) ? MP4 : detected;
        } catch (IOException e) {
            log.warn("content detection failed, treating as octet-stream: {}", e.toString());
            return OCTET_STREAM;
        }
    }

    /** {@code ftyp} box at offset 4, major brand at offset 8. */
    static boolean hasMp4Brand(byte[] header) {
        if (header.length < 12) {
            return false;
        }
        String box = new String(header, 4, 4, StandardCharsets.ISO_8859_1);
        String brand = new String(header, 8, 4, StandardCharsets.ISO_8859_1);
        return "ftyp".equals(box) && MP4_BRANDS.contains(brand);
    }
}
