package com.valaryrpg.arena.arena;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.valaryrpg.arena.ValaryArenaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Template anvil letti una volta in RAM. Ogni istanza è un clone read-only:
 * non viene scritta su disco e non diventa una cartella mondo.
 */
public final class SlimeInstances {

    private final ValaryArenaPlugin plugin;
    private final Map<String, SlimeWorld> templates = new ConcurrentHashMap<>();
    private final Object importMonitor = new Object();

    public SlimeInstances(ValaryArenaPlugin plugin) {
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
            SlimeWorld template = AdvancedSlimePaperAPI.instance().readVanillaWorld(templateDir, templateName, null);
            this.templates.put(templateName, template);
        }
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
            World world = AdvancedSlimePaperAPI.instance().loadWorld(clone, false).getBukkitWorld();
            world.setAutoSave(false);
            onReady.accept(world);
        } catch (Exception exception) {
            onFail.accept(exception);
        }
    }
}
