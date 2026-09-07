package me.whereareiam.anvil.provisioning.cache;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactLease;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns atomic cache publication and bounded transfers. Entry locks also protect derived JDKs.
 */
public final class ArtifactCache implements ArtifactStore {
    private static final ConcurrentHashMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final Path root;
    private final boolean offline;
    private final boolean refresh;
    private final Semaphore downloads;

    public ArtifactCache(@NotNull Path root, boolean offline, boolean refresh, int parallelism) {
        if (parallelism < 1) throw new IllegalArgumentException("Download parallelism must be positive");
        if (offline && refresh) throw new IllegalArgumentException("Offline and refresh cannot be combined");

        this.root = root.toAbsolutePath().normalize();
        this.offline = offline;
        this.refresh = refresh;
        this.downloads = new Semaphore(parallelism);
    }

    @Override
    public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, @Nullable String expectedSha256) {
        Path target = destination.toAbsolutePath().normalize();
        try (ArtifactLease ignored = lock(target)) {
            if (Files.isRegularFile(target) && matches(target, expectedSha256)) return target;
            if (offline) throw new ProvisioningException("Artifact is unavailable offline: " + uri);

            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
            try {
                download(uri, temporary);
                if (!matches(temporary, expectedSha256)) throw new ProvisioningException("SHA-256 mismatch for " + uri);
                publish(temporary, target);

                return target;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new ProvisioningException("Could not cache " + uri, failure);
        }
    }

    @Override
    public @NotNull String read(@NotNull URI uri) {
        Path target = root.resolve("metadata").resolve(hash(uri.toString().getBytes(StandardCharsets.UTF_8)) + ".json");
        try (ArtifactLease ignored = lock(target)) {
            if (Files.isRegularFile(target) && !refresh) return Files.readString(target);
            if (offline) throw new ProvisioningException("Resolution metadata is unavailable offline: " + uri);

            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "metadata-", ".part");
            try {
                download(uri, temporary);
                String body = Files.readString(temporary);
                publish(temporary, target);

                return body;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new ProvisioningException("Could not read resolution metadata: " + uri, failure);
        }
    }

    @Override
    public @NotNull ArtifactLease lock(@NotNull Path entry) {
        String key = hash(entry.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
        ReentrantLock threadLock = LOCKS.computeIfAbsent(key, ignored -> new ReentrantLock());
        try {
            threadLock.lockInterruptibly();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ProvisioningException("Interrupted acquiring cache entry " + entry, failure);
        }

        try {
            Path file = root.resolve("locks").resolve(key + ".lock");
            Files.createDirectories(file.getParent());
            FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            return acquire(channel, threadLock);
        } catch (IOException | RuntimeException | Error failure) {
            threadLock.unlock();
            throw new ProvisioningException("Could not acquire cache entry " + entry, failure);
        }
    }

    private ArtifactLease acquire(FileChannel channel, ReentrantLock threadLock) throws IOException {
        try {
            FileLock fileLock = channel.lock();

            return new Lease(channel, fileLock, threadLock);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                channel.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private void download(URI uri, Path target) throws IOException {
        try {
            downloads.acquire();
            try {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    if (response.statusCode() < 200 || response.statusCode() >= 300)
                        throw new ProvisioningException("Download failed with HTTP " + response.statusCode() + ": " + uri);

                    Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                downloads.release();
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new ProvisioningException("Interrupted downloading " + uri, failure);
        }
    }

    private boolean matches(Path file, @Nullable String expected) throws IOException {
        if (expected == null) return true;

        MessageDigest digest = digest();
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = stream.read(buffer)) >= 0)
				digest.update(buffer, 0, count);
        }

        return expected.equalsIgnoreCase(HexFormat.of().formatHex(digest.digest()));
    }

    private static String hash(byte[] input) {
        return HexFormat.of().formatHex(digest().digest(input));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static void publish(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }


    @Override
    public void close() {
        client.close();
    }

    private static final class Lease implements ArtifactLease {
        private final FileChannel channel;
        private final FileLock fileLock;
        private final ReentrantLock threadLock;
        private boolean closed;

        private Lease(FileChannel channel, FileLock fileLock, ReentrantLock threadLock) {
            this.channel = channel;
            this.fileLock = fileLock;
            this.threadLock = threadLock;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;

            try (channel; fileLock) {
                // Resource closure releases both operating-system handles.
            } catch (IOException failure) {
                throw new ProvisioningException("Could not release cache entry", failure);
            } finally {
                threadLock.unlock();
            }
        }
    }
}
