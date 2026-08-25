package com.aigreentick.services.storage.infrastructure.storage.local;

import com.aigreentick.services.storage.application.port.out.StoragePort;
import com.aigreentick.services.storage.config.properties.StorageProperties;
import com.aigreentick.services.storage.domain.exception.StorageOperationException;
import com.aigreentick.services.storage.domain.exception.UnsupportedStorageOperationException;
import com.aigreentick.services.storage.domain.media.StorageKey;
import com.aigreentick.services.storage.domain.shared.ByteSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements {@link StoragePort} against a local directory. DEVELOPMENT ONLY.
 *
 * <p>Not a production option, and the constraint is ENFORCED at startup rather
 * than documented (ADR-005): two replicas without a shared volume means uploads to
 * one pod 404 on the other. Docker Compose uses MinIO so the normal development
 * path exercises the same S3 code as production; this adapter exists only for a
 * no-container run.
 */
@Slf4j
public class LocalFileSystemStorageAdapter implements StoragePort {

    private final Path root;
    private final String baseUrl;

    public LocalFileSystemStorageAdapter(StorageProperties properties) {
        this.root = Path.of(properties.local().rootPath()).toAbsolutePath().normalize();
        this.baseUrl = properties.local().baseUrl().replaceAll("/+$", "");
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create local storage root " + root, e);
        }
        log.info("local filesystem storage active at {} (development only)", root);
    }

    /**
     * Resolves and re-checks containment. {@link StorageKey} already rejects
     * traversal sequences; this is the second, independent check that the resolved
     * path really is under the root.
     */
    private Path resolve(StorageKey key) {
        Path candidate = root.resolve(key.value()).normalize();
        if (!candidate.startsWith(root)) {
            throw new StorageOperationException("LOCAL", "resolve", "path escapes storage root", null);
        }
        return candidate;
    }

    /**
     * Writes to a temp file then moves atomically, so a partially-written file is
     * never visible under its final name. CREATE_NEW on the move target makes a key
     * collision an error rather than silent data loss — the predecessor used
     * REPLACE_EXISTING.
     */
    @Override
    public StoredObject put(InputStream content, PutRequest request) {
        Path target = resolve(request.key());
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                throw new StorageOperationException("LOCAL", "put", "key already exists", null);
            }
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".part");
            try {
                long written = Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                return new StoredObject(request.key(), ByteSize.of(written), request.contentType(),
                        etagOf(target), null, Files.getLastModifiedTime(target).toInstant());
            } catch (IOException e) {
                Files.deleteIfExists(temp);
                throw e;
            }
        } catch (IOException e) {
            throw new StorageOperationException("LOCAL", "put", "key=" + request.key().value(), e);
        }
    }

    private String etagOf(Path path) throws IOException {
        return Long.toHexString(Files.size(path)) + "-"
                + Long.toHexString(Files.getLastModifiedTime(path).toMillis());
    }

    @Override
    public Optional<StoredObject> head(StorageKey key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StoredObject(key, ByteSize.of(Files.size(path)),
                    Files.probeContentType(path), etagOf(path), null,
                    Files.getLastModifiedTime(path).toInstant()));
        } catch (IOException e) {
            throw new StorageOperationException("LOCAL", "head", "key=" + key.value(), e);
        }
    }

    @Override
    public InputStream read(StorageKey key) {
        try {
            return Files.newInputStream(resolve(key), StandardOpenOption.READ);
        } catch (IOException e) {
            throw new StorageOperationException("LOCAL", "read", "key=" + key.value(), e);
        }
    }

    @Override
    public byte[] readRange(StorageKey key, long offset, int length) {
        Path path = resolve(key);
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long available = Math.max(0, file.length() - offset);
            int toRead = (int) Math.min(length, available);
            byte[] buffer = new byte[toRead];
            file.seek(offset);
            file.readFully(buffer);
            return buffer;
        } catch (IOException e) {
            throw new StorageOperationException("LOCAL", "readRange", "key=" + key.value(), e);
        }
    }

    /** Idempotent: deleting an absent key is a success. */
    @Override
    public boolean delete(StorageKey key) {
        try {
            return Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageOperationException("LOCAL", "delete", "key=" + key.value(), e);
        }
    }

    @Override
    public int deleteAll(List<StorageKey> keys) {
        int deleted = 0;
        for (StorageKey key : keys) {
            if (delete(key)) {
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * Direct-to-storage upload has no meaning for a local directory. Callers select
     * the proxied path when {@link #supportsPresignedUpload()} is false.
     */
    @Override
    public PresignedUpload presignPut(PresignRequest request) {
        throw new UnsupportedStorageOperationException("local storage cannot presign uploads");
    }

    @Override
    public PresignedUpload presignMultipart(PresignRequest request, int partCount) {
        throw new UnsupportedStorageOperationException("local storage cannot presign multipart uploads");
    }

    @Override
    public StoredObject completeMultipart(StorageKey key, String providerUploadId, List<PartRef> parts) {
        throw new UnsupportedStorageOperationException("local storage has no multipart uploads");
    }

    @Override
    public void abortMultipart(StorageKey key, String providerUploadId) {
        // Nothing to abort.
    }

    /**
     * Returns the frozen serve URL. Note this is NOT a capability: the serve
     * endpoint performs its own authentication and ownership check.
     */
    @Override
    public String presignGet(StorageKey key, Duration ttl) {
        return baseUrl + "/" + key.value();
    }

    @Override
    public KeyPage listKeys(String prefix, String cursor, int limit) {
        Path base = root.resolve(prefix).normalize();
        if (!base.startsWith(root) || !Files.isDirectory(base)) {
            return new KeyPage(List.of(), null, false);
        }
        try (Stream<Path> walk = Files.walk(base)) {
            List<StorageKey> keys = new ArrayList<>();
            walk.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(p -> keys.add(new StorageKey(root.relativize(p).toString()
                            .replace(java.io.File.separatorChar, '/'))));

            int from = cursor == null ? 0 : Math.max(0, Integer.parseInt(cursor));
            int to = Math.min(from + limit, keys.size());
            boolean hasMore = to < keys.size();
            return new KeyPage(keys.subList(from, to), hasMore ? String.valueOf(to) : null, hasMore);
        } catch (IOException | NumberFormatException e) {
            throw new StorageOperationException("LOCAL", "list", "prefix=" + prefix, e);
        }
    }

    @Override
    public boolean isHealthy() {
        return Files.isDirectory(root) && Files.isWritable(root) && freeBytes() > 0;
    }

    public long freeBytes() {
        try {
            return Files.getFileStore(root).getUsableSpace();
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.LOCAL;
    }

    @Override
    public boolean supportsPresignedUpload() {
        return false;
    }
}
