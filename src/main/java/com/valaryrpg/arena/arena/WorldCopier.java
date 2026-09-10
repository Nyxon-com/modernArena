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

    public WorldCopier(Tasks tasks) {
        this.tasks = tasks;
    }

    public void copy(Path source, Path destination, List<String> exclude, Runnable onDone, Consumer<Exception> onFail) {
        this.tasks.async(() -> {
            try {
                if (Files.exists(destination)) {
                    deleteSync(destination);
                }
                copySync(source, destination, new HashSet<>(exclude));
                this.tasks.sync(onDone);
            } catch (Exception exception) {
                this.tasks.sync(() -> onFail.accept(exception));
            }
        });
    }

    public void delete(Path folder, Runnable onDone) {
        this.tasks.async(() -> {
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
        });
    }

    public void deleteSync(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void copySync(Path source, Path destination, Set<String> exclude) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path target = relative.toString().isEmpty() ? destination : destination.resolve(relative);
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (exclude.contains(file.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                Path target = destination.resolve(source.relativize(file));
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
