package com.modern.arena.milestone;

import com.modern.arena.ModernArenaPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** GUI /milestones: ordine boss per espansione + reward. */
public final class MilestoneGui implements Listener {

    private final ModernArenaPlugin plugin;

    public MilestoneGui(ModernArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        this.plugin.kills().withKills(player.getUniqueId(), kills -> {
            if (player.isOnline()) {
                player.openInventory(build(player, kills));
            }
        });
    }

    private Inventory build(Player player, Map<String, Integer> kills) {
        FileConfiguration yaml = this.plugin.configs().milestones();
        String titleRaw = yaml.getString("gui.title", "<b><gradient:#4AFF5C:#00FF07>ᴍɪʟᴇꜱᴛᴏɴᴇꜱ</gradient></b>");
        int rows = Math.min(6, Math.max(1, yaml.getInt("gui.rows", 4)));
        Component title = this.plugin.messages().mini(titleRaw, Map.of("player", player.getName()));
        Holder holder = new Holder(title, rows * 9);
        Inventory inventory = holder.getInventory();

        Material filler = material(yaml.getString("gui.filler", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
        ItemStack pane = named(filler, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane);
        }

        List<Map<?, ?>> entries = yaml.getMapList("gui.entries");
        for (Map<?, ?> raw : entries) {
            if (raw == null) {
                continue;
            }
            int slot = intOf(raw.get("slot"), -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            String mob = stringOf(raw.get("mob"));
            String name = stringOf(raw.get("name"));
            Material material = material(stringOf(raw.get("material")), Material.BOOK);
            List<String> loreLines = new ArrayList<>();
            Object loreObj = raw.get("lore");
            if (loreObj instanceof List<?> list) {
                for (Object line : list) {
                    if (line != null) {
                        loreLines.add(String.valueOf(line));
                    }
                }
            }
            int killCount = killCount(kills, mob);
            loreLines.add("");
            loreLines.add("<dark_gray>· · · · · · ·");
            loreLines.add("<gray>ʟᴇ ᴛᴜᴇ ᴋɪʟʟ</gray> <dark_gray>»</dark_gray> <white>" + killCount);
            int next = nextThreshold(mob, killCount);
            if (next > 0) {
                loreLines.add("<gray>ᴘʀᴏꜱꜱɪᴍᴀ ꜱᴏɢʟɪᴀ</gray> <dark_gray>»</dark_gray> <white>" + next);
            } else if (killCount > 0) {
                loreLines.add("<green>ᴛᴜᴛᴛᴇ ʟᴇ ꜱᴏɢʟɪᴇ ꜱʙʟᴏᴄᴄᴀᴛᴇ");
            }
            inventory.setItem(slot, named(material, name, loreLines));
        }

        ConfigurationSection labels = yaml.getConfigurationSection("gui.labels");
        if (labels != null) {
            for (String key : labels.getKeys(false)) {
                ConfigurationSection node = labels.getConfigurationSection(key);
                if (node == null) {
                    continue;
                }
                int slot = node.getInt("slot", -1);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                Material material = material(node.getString("material"), Material.PAPER);
                inventory.setItem(slot, named(material, node.getString("name", " "), node.getStringList("lore")));
            }
        }
        return inventory;
    }

    private int killCount(Map<String, Integer> kills, String mob) {
        if (mob == null || mob.isBlank() || kills == null || kills.isEmpty()) {
            return 0;
        }
        Integer exact = kills.get(mob);
        if (exact != null) {
            return exact;
        }
        String canonical = this.plugin.kills().catalog().canonical(mob);
        Integer byCanonical = kills.get(canonical);
        if (byCanonical != null) {
            return byCanonical;
        }
        for (Map.Entry<String, Integer> entry : kills.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(mob) || entry.getKey().equalsIgnoreCase(canonical)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private int nextThreshold(String mob, int kills) {
        for (MilestoneDefinition milestone : this.plugin.kills().catalog().of(mob)) {
            if (kills < milestone.threshold) {
                return milestone.threshold;
            }
        }
        return -1;
    }

    private ItemStack named(Material material, String name, List<String> loreMini) {
        ItemStack stack = new ItemStack(material == null ? Material.BOOK : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(this.plugin.messages().mini(name == null ? " " : name, Map.of()));
            List<Component> lore = new ArrayList<>();
            if (loreMini != null) {
                for (String line : loreMini) {
                    lore.add(this.plugin.messages().mini(line == null ? "" : line, Map.of()));
                }
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private static int intOf(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static final class Holder implements InventoryHolder {
        private final Inventory inventory;

        private Holder(Component title, int size) {
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }
}
