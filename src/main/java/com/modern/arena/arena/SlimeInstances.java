//made with love by nyxon

package com.modern.arena.arena;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.modern.arena.ModernArenaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SlimeInstances {

  private final ModernArenaPlugin plugin;
  private final Map<String, SlimeWorld> templates = new ConcurrentHashMap<>();
  private final Object importMonitor = new Object();

  public SlimeInstances(ModernArenaPlugin plugin) {
    this.plugin = plugin;
  }

  public boolean available() {
    try {
      Class.forName("com.infernalsuite.asp.api.AdvancedSlimePaperAPI");
      return true;
    } catch (ClassNotFoundException exception) {
      return false;
    }
  }

  public void open(String templateName, File templateDir, String instanceName, Consumer<World> onReady, Consumer<Exception> onFail) {
    if (!available()) {
      onFail.accept(new IllegalStateException("AdvancedSlimePaper assente"));
      return;
    }
    World loaded = Bukkit.getWorld(templateName);
    if (loaded != null) {
      if (!loaded.getPlayers().isEmpty() || !Bukkit.unloadWorld(loaded, true)) {
        onFail.accept(new IllegalStateException("Template non scaricato: " + templateName));
        return;
      }
    }
    boolean started = this.plugin.tasks().async(() -> {
      try {
        importTemplate(templateName, templateDir);
        this.plugin.tasks().sync(() -> mount(templateName, instanceName, onReady, onFail));
      } catch (Exception exception) {
        this.plugin.tasks().sync(() -> onFail.accept(exception));
      }
    });
    if (!started) {
      onFail.accept(new IllegalStateException("Import slime non avviato"));
    }
  }

  private void importTemplate(String templateName, File templateDir) throws Exception {
    if (this.templates.containsKey(templateName)) {
      return;
    }
    synchronized (this.importMonitor) {
      if (this.templates.containsKey(templateName)) {
        return;
      }
      File importDir = sanitizeTemplateDir(templateDir, templateName);
      try {

        this.plugin.getLogger().info("Import slime template '" + templateName + "' da " + importDir.getAbsolutePath());
        SlimeWorld template = AdvancedSlimePaperAPI.instance().readVanillaWorld(importDir, templateName, null);
        this.templates.put(templateName, template);
      } finally {
        if (!importDir.equals(templateDir)) {
          deleteRecursive(importDir);
        }
      }
    }
  }

  private File sanitizeTemplateDir(File templateDir, String templateName) throws Exception {
    File dim1 = new File(templateDir, "DIM-1");
    File dimEnd = new File(templateDir, "DIM1");
    File region = new File(templateDir, "region");
    if (!dim1.isDirectory() && !dimEnd.isDirectory()) {
      return templateDir;
    }
    if (!region.isDirectory()) {
      throw new IllegalStateException(
          "Template '" + templateName + "' ha DIM-* ma nessuna cartella region overworld. "
              + "Sposta la mappa in region/ e aggiorna i chunk (forceUpgrade).");
    }
    File tempRoot = new File(this.plugin.getDataFolder(), "slime-import-tmp");
    File temp = new File(tempRoot, templateName + "-" + System.nanoTime());
    if (!temp.mkdirs()) {
      throw new IllegalStateException("Impossibile creare temp import: " + temp);
    }
    copyFile(new File(templateDir, "level.dat"), new File(temp, "level.dat"));
    copyFile(new File(templateDir, "level.dat_old"), new File(temp, "level.dat_old"));
    copyFile(new File(templateDir, "uid.dat"), new File(temp, "uid.dat"));
    copyFile(new File(templateDir, "paper-world.yml"), new File(temp, "paper-world.yml"));
    copyTree(region, new File(temp, "region"));
    copyTree(new File(templateDir, "entities"), new File(temp, "entities"));
    copyTree(new File(templateDir, "poi"), new File(temp, "poi"));
    copyTree(new File(templateDir, "data"), new File(temp, "data"));
    this.plugin.getLogger().warning(
        "Template '" + templateName + "': ignorati DIM-* durante import slime (usa solo overworld).");
    return temp;
  }

  private static void copyFile(File from, File to) throws Exception {
    if (!from.isFile()) {
      return;
    }
    java.nio.file.Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }

  private static void copyTree(File from, File to) throws Exception {
    if (!from.isDirectory()) {
      return;
    }
    java.nio.file.Path source = from.toPath();
    java.nio.file.Path target = to.toPath();
    java.nio.file.Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<>() {
      @Override
      public java.nio.file.FileVisitResult preVisitDirectory(
          java.nio.file.Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
        java.nio.file.Files.createDirectories(target.resolve(source.relativize(dir)));
        return java.nio.file.FileVisitResult.CONTINUE;
      }

      @Override
      public java.nio.file.FileVisitResult visitFile(
          java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
        java.nio.file.Files.copy(
            file,
            target.resolve(source.relativize(file)),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return java.nio.file.FileVisitResult.CONTINUE;
      }
    });
  }

  private static void deleteRecursive(File root) {
    if (root == null || !root.exists()) {
      return;
    }
    File[] children = root.listFiles();
    if (children != null) {
      for (File child : children) {
        deleteRecursive(child);
      }
    }

    root.delete();
  }

  private void mount(String templateName, String instanceName, Consumer<World> onReady, Consumer<Exception> onFail) {
    try {
      SlimeWorld template = this.templates.get(templateName);
      if (template == null) {
        throw new IllegalStateException("Template slime assente: " + templateName);
      }
      World existing = Bukkit.getWorld(instanceName);
      if (existing != null && !Bukkit.unloadWorld(existing, false)) {
        throw new IllegalStateException("Istanza ancora caricata: " + instanceName);
      }
      SlimeWorld clone = template.clone(instanceName);

      clone.getPropertyMap().setValue(SlimeProperties.DIFFICULTY, "easy");
      clone.getPropertyMap().setValue(SlimeProperties.ALLOW_MONSTERS, true);
      World world = AdvancedSlimePaperAPI.instance().loadWorld(clone, true).getBukkitWorld();
      world.setAutoSave(false);
      onReady.accept(world);
    } catch (Exception exception) {
      onFail.accept(exception);
    }
  }
}
