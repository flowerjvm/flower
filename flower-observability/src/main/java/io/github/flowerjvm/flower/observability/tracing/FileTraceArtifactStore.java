package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Local, content-addressed artifact store for development and small
 * deployments.
 *
 * <p>Artifacts are written atomically below the configured root and deduplicated
 * by SHA-256. Retention and directory lifecycle remain the host's responsibility.
 */
public final class FileTraceArtifactStore implements TraceArtifactStore {

    private final Path root;

    public FileTraceArtifactStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot create artifact root: " + this.root, failure);
        }
    }

    @Override
    public TraceArtifactReference store(
            FlowerTraceEvent event,
            String attributeName,
            TraceContent content) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (attributeName == null || attributeName.trim().isEmpty()) {
            throw new IllegalArgumentException("attributeName must not be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }

        byte[] bytes = content.utf8Bytes();
        String sha256 = sha256(bytes);
        String relativeLocation = sha256.substring(0, 2) + "/" + sha256 + ".artifact";
        Path target = root.resolve(relativeLocation).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("artifact path escaped configured root");
        }
        writeIfAbsent(target, bytes);
        return new TraceArtifactReference(
                "sha256:" + sha256,
                relativeLocation,
                content.mediaType(),
                bytes.length,
                sha256);
    }

    public Path root() {
        return root;
    }

    private static void writeIfAbsent(Path target, byte[] bytes) {
        Path parent = target.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            if (Files.exists(target)) {
                return;
            }
            temporary = Files.createTempFile(parent, ".flower-artifact-", ".tmp");
            Files.write(
                    temporary,
                    bytes,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            moveNewFile(temporary, target);
            temporary = null;
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot store trace artifact: " + target, failure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best-effort cleanup. The original storage result is authoritative.
                }
            }
        }
    }

    private static void moveNewFile(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException duplicate) {
            Files.deleteIfExists(temporary);
        } catch (AtomicMoveNotSupportedException unsupported) {
            try {
                Files.move(temporary, target);
            } catch (FileAlreadyExistsException duplicate) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
