package com.valaryrpg.arena.arena;

import com.valaryrpg.arena.util.Tasks;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Copia e cancella cartelle mondo fuori dal main thread. */
public final class WorldCopier {

    private final Tasks tasks;
    private final Object operationsMonitor = new Object();
    private final Object[] pathLocks = new Object[16];
    private volatile boolean accepting = true;
    private int activeOperations;

    public WorldCopier(Tasks tasks) {
        this.tasks = tasks;
        for (int index = 0; index < this.pathLocks.length; index++) {
            this.pathLocks[index] = new Object();
        }
    }

    public void copy(Path source, Path destination, List<String> exclude, Runnable onDone, Consumer<Exception> onFail) {
        submit(() -> {
            synchronized (pathLock(destination)) {
                try {
                    if (Files.exists(destination)) {
                        deleteSync(destination);
                    }
                    copySync(source, destination, new HashSet<>(exclude));
                    this.tasks.sync(onDone);
                } catch (Exception exception) {
                    this.tasks.sync(() -> onFail.accept(exception));
                }
            }
        });
    }

    public void delete(Path folder, Runnable onDone) {
        submit(() -> {
            synchronized (pathLock(folder)) {
                try {
                    if (Files.exists(folder)) {
                        deleteSync(folder);
                    }
                } catch (IOException exception) {
                    // il cleanup all'enable ritenta
                }
                if (onDone != null) {
                    this.tasks.sync(onDone);
                }
            }
        });
    }

    public boolean cleanup(Path instancesRoot, List<Path> legacyFolders, Runnable onDone, Consumer<Exception> onFail) {
        return submit(() -> {
            try {
                Files.createDirectories(instancesRoot);
                try (var stream = Files.list(instancesRoot)) {
                    for (Path folder : stream.filter(Files::isDirectory).toList()) {
                        deleteSync(folder);
                    }
                }
                for (Path folder : legacyFolders) {
                    if (Files.isDirectory(folder)) {
                        deleteSync(folder);
                    }
                }
                this.tasks.sync(onDone);
            } catch (Exception exception) {
                this.tasks.sync(() -> onFail.accept(exception));
            }
        });
    }

    public void shutdown(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMillis);
        synchronized (this.operationsMonitor) {
            this.accepting = false;
            while (this.activeOperations > 0) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    break;
                }
                try {
                    this.operationsMonitor.wait(remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public void deleteSync(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!accepting) {
                    return FileVisitResult.TERMINATE;
                }
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (!accepting) {
                    return FileVisitResult.TERMINATE;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void copySync(Path source, Path destination, Set<String> exclude) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!accepting) {
                    return FileVisitResult.TERMINATE;
                }
                Path relative = source.relativize(dir);
                Path target = relative.toString().isEmpty() ? destination : destination.resolve(relative);
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!accepting) {
                    return FileVisitResult.TERMINATE;
                }
                if (exclude.contains(file.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                Path target = destination.resolve(source.relativize(file));
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean submit(Runnable operation) {
        synchronized (this.operationsMonitor) {
            if (!this.accepting) {
                return false;
            }
        }
        return this.tasks.async(() -> {
            if (!beginOperation()) {
                return;
            }
            try {
                operation.run();
            } finally {
                endOperation();
            }
        });
    }

    private boolean beginOperation() {
        synchronized (this.operationsMonitor) {
            if (!this.accepting) {
                return false;
            }
            this.activeOperations++;
            return true;
        }
    }

    private void endOperation() {
        synchronized (this.operationsMonitor) {
            this.activeOperations--;
            if (this.activeOperations == 0) {
                this.operationsMonitor.notifyAll();
            }
        }
    }

    private Object pathLock(Path path) {
        int hash = path.toAbsolutePath().normalize().hashCode();
        return this.pathLocks[(hash & Integer.MAX_VALUE) % this.pathLocks.length];
    }
}
