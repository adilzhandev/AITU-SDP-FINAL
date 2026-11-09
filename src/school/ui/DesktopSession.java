package school.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import school.application.SchoolFacade;
import school.storage.FileSchoolStore;
import school.storage.SchoolFileLock;

/** A desktop load/edit/save session, owned by the Swing event-dispatch thread. */
public final class DesktopSession implements AutoCloseable {
    private final SchoolFacade school;
    private final FileSchoolStore store;
    private final Path file;
    private final SchoolFileLock lock;
    private boolean dirty;
    private boolean closed;

    private DesktopSession(SchoolFacade school, FileSchoolStore store, Path file, SchoolFileLock lock) {
        this.school = school;
        this.store = store;
        this.file = file;
        this.lock = lock;
    }

    /** A null path starts an in-memory session; a failed load releases the acquired lock. */
    public static DesktopSession open(Path path) throws IOException {
        FileSchoolStore store = new FileSchoolStore();
        if (path == null) return new DesktopSession(new SchoolFacade(), store, null, null);
        Path file = path.toAbsolutePath().normalize();
        SchoolFileLock lock = SchoolFileLock.acquire(file);
        try {
            return new DesktopSession(store.load(file), store, file, lock);
        } catch (IOException | RuntimeException error) {
            try {
                lock.close();
            } catch (IOException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    public SchoolFacade school() { return school; }
    public Path file() { return file; }
    public boolean isDirty() { return dirty; }

    /** Call after a successful domain change. Failed autosaves leave the session dirty. */
    public void changed() throws IOException {
        requireOpen();
        dirty = true;
        if (file != null) save();
    }

    /** Memory sessions cannot save in place; save a copy to export their state. */
    public void save() throws IOException {
        requireOpen();
        if (file == null) throw new IOException("This is an in-memory session. Save a backup copy to keep it.");
        store.save(school, file);
        dirty = false;
    }

    /** Saves to a chosen path, taking its lock without changing this session's active file. */
    public void saveCopy(Path destination) throws IOException {
        requireOpen();
        if (destination == null) throw new IOException("Choose a file for the backup copy.");
        Path copy = destination.toAbsolutePath().normalize();
        if (copy.equals(file)) {
            save();
            return;
        }
        try (SchoolFileLock copyLock = SchoolFileLock.acquire(copy)) {
            store.save(school, copy);
        }
    }

    /** Exports a complete snapshot without changing the active file or its dirty state. */
    public Path saveRecovery() throws IOException {
        requireOpen();
        Path directory = Files.createTempDirectory("school-desktop-recovery-");
        Path recovery = directory.resolve("school.properties");
        try {
            store.save(school, recovery);
            return recovery;
        } catch (IOException | RuntimeException error) {
            try {
                Files.deleteIfExists(recovery);
                Files.deleteIfExists(directory);
            } catch (IOException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }

    /** Releases resources only. The window decides whether unsaved changes may be discarded. */
    @Override
    public void close() throws IOException {
        if (closed) return;
        try {
            if (lock != null) lock.close();
        } finally {
            closed = true;
        }
    }

    private void requireOpen() throws IOException {
        if (closed) throw new IOException("The desktop session is already closed.");
    }
}
