package school.storage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Holds an exclusive lock for the whole load/edit/save session. */
public final class SchoolFileLock implements AutoCloseable {
    private static final Set<Path> HELD_IN_JVM = new HashSet<>();
    private final FileChannel channel;
    private final FileLock lock;
    private final Path sidecar;
    private boolean closed;

    private SchoolFileLock(FileChannel channel, FileLock lock, Path sidecar) {
        this.channel = channel;
        this.lock = lock;
        this.sidecar = sidecar;
    }

    /**
     * Locks a stable sidecar file, because saving replaces the data file itself.
     * The sidecar remains after closing so every process locks the same file.
     */
    public static SchoolFileLock acquire(Path dataFile) throws IOException {
        Objects.requireNonNull(dataFile, "dataFile");
        Path absolute = dataFile.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(absolute)) {
            throw new IOException("School data file must not be a symbolic link: " + absolute);
        }
        Path parent = absolute.getParent();
        Path filename = absolute.getFileName();
        if (parent == null || filename == null) {
            throw new IOException("A data file path is required for locking");
        }
        Files.createDirectories(parent);
        // Canonicalize directory aliases before choosing the shared lock path.
        Path canonicalParent = parent.toRealPath();
        Path canonicalData = canonicalParent.resolve(filename);
        if (Files.isSymbolicLink(canonicalData)) {
            throw new IOException("School data file must not be a symbolic link: " + canonicalData);
        }
        Path sidecar = canonicalParent.resolve(filename + ".lock");
        synchronized (HELD_IN_JVM) {
            // On POSIX, closing a second descriptor can release the first descriptor's
            // process lock. Reject duplicates before opening that second descriptor.
            if (HELD_IN_JVM.contains(sidecar)) {
                throw alreadyInUse(canonicalData, null);
            }
            FileChannel channel = FileChannel.open(sidecar, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            try {
                FileLock lock;
                try {
                    lock = channel.tryLock();
                } catch (OverlappingFileLockException exception) {
                    throw alreadyInUse(canonicalData, exception);
                }
                if (lock == null) {
                    throw alreadyInUse(canonicalData, null);
                }
                HELD_IN_JVM.add(sidecar);
                return new SchoolFileLock(channel, lock, sidecar);
            } catch (IOException | RuntimeException exception) {
                try {
                    channel.close();
                } catch (IOException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
                throw exception;
            }
        }
    }

    /** Releases the operating-system lock and closes its channel; safe to call twice. */
    @Override
    public void close() throws IOException {
        synchronized (HELD_IN_JVM) {
            if (closed) return;
            try (channel) {
                if (lock.isValid()) {
                    lock.release();
                }
            } finally {
                closed = true;
                HELD_IN_JVM.remove(sidecar);
            }
        }
    }

    private static IOException alreadyInUse(Path dataFile, Throwable cause) {
        return new IOException("School data file is already in use: " + dataFile
                + ". Close the other application before opening this file.", cause);
    }
}
