package com.modern.arena.arena;

import com.modern.arena.util.Tasks;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class WorldCopier {

  private final Tasks tasks;
  private final int maxConcurrent;
  private final Object operationsMonitor = new Object();
  private final Object[] pathLocks = new Object[16];
  private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
  private volatile boolean accepting = true;
  private int activeOperations;

  public WorldCopier(Tasks tasks, int maxConcurrent) {
    this.tasks = tasks;
    this.maxConcurrent = Math.max(1, maxConcurrent);
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

        }
        if (onDone != null) {
          this.tasks.sync(onDone);
        }
      }
    });
  }

  public boolean cleanup(List<Path> folders, Runnable onDone, Consumer<Exception> onFail) {
    return submit(() -> {
      try {
        for (Path folder : folders) {
          synchronized (pathLock(folder)) {
            if (Files.isDirectory(folder)) {
              deleteSync(folder);
            }
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
      this.pending.clear();
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
      this.pending.addLast(operation);
      pumpLocked();
      return true;
    }
  }

  private void pumpLocked() {
    while (this.accepting && this.activeOperations < this.maxConcurrent && !this.pending.isEmpty()) {
      Runnable operation = this.pending.pollFirst();
      this.activeOperations++;
      boolean started = this.tasks.async(() -> {
        try {
          operation.run();
        } finally {
          endOperation();
        }
      });
      if (!started) {
        this.activeOperations--;
        break;
      }
    }
  }

  private void endOperation() {
    synchronized (this.operationsMonitor) {
      this.activeOperations--;
      if (this.activeOperations == 0 && this.pending.isEmpty()) {
        this.operationsMonitor.notifyAll();
      }
      pumpLocked();
    }
  }

  private Object pathLock(Path path) {
    int hash = path.toAbsolutePath().normalize().hashCode();
    return this.pathLocks[(hash & Integer.MAX_VALUE) % this.pathLocks.length];
  }
}
