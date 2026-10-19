package com.modern.arena.message;

import com.modern.arena.ModernArenaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Testi MiniMessage da lang/messages.yml, brand incluso. */
public final class MessageService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final ModernArenaPlugin plugin;

    public MessageService(ModernArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // i YAML sono già ricaricati da ConfigManager
    }

    public String raw(String path) {
        FileConfiguration yaml = this.plugin.configs().messages();
        String value = yaml.getString(path);
        if (value != null && !value.isBlank()) {
            return value;
        }
        Configuration defaults = yaml.getDefaults();
        if (defaults != null) {
            String fallback = defaults.getString(path);
            if (fallback != null && !fallback.isBlank()) {
                return fallback;
            }
        }
        return path;
    }

    public String brand(String key) {
        return this.plugin.configs().messages().getString("brand." + key, "");
    }

    public Component component(String path, Map<String, String> placeholders) {
        return MINI.deserialize(apply(raw(path), placeholders));
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        FileConfiguration yaml = this.plugin.configs().messages();
        if (yaml.isList(path)) {
            for (String line : yaml.getStringList(path)) {
                sender.sendMessage(MINI.deserialize(apply(line, placeholders)));
            }
            return;
        }
        sender.sendMessage(component(path, placeholders));
    }

    public void sendRaw(CommandSender sender, String mini, Map<String, String> placeholders) {
        if (mini == null || mini.isEmpty()) {
            return;
        }
        sender.sendMessage(MINI.deserialize(apply(mini, placeholders)));
    }

    public List<String> list(String path) {
        FileConfiguration yaml = this.plugin.configs().messages();
        if (yaml.isList(path)) {
            return yaml.getStringList(path);
        }
        String value = yaml.getString(path);
        if (value == null) {
            return List.of();
        }
        List<String> single = new ArrayList<>(1);
        single.add(value);
        return single;
    }

    public String apply(String text, Map<String, String> placeholders) {
        String result = text
                .replace("{prefix}", brand("prefix"))
                .replace("{separator}", brand("separator"))
                .replace("{brand}", brand("name"));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public Component mini(String text, Map<String, String> placeholders) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(apply(text, placeholders));
    }

    public void intro(
            Player player,
            String title,
            String subtitle,
            String actionbar,
            int fadeInTicks,
            int stayTicks,
            int fadeOutTicks,
            Map<String, String> placeholders
    ) {
        boolean titled = (title != null && !title.isBlank()) || (subtitle != null && !subtitle.isBlank());
        if (titled) {
            Title.Times times = Title.Times.times(
                    Duration.ofMillis(Math.max(0, fadeInTicks) * 50L),
                    Duration.ofMillis(Math.max(1, stayTicks) * 50L),
                    Duration.ofMillis(Math.max(0, fadeOutTicks) * 50L)
            );
            player.showTitle(Title.title(
                    mini(title == null ? "" : title, placeholders),
                    mini(subtitle == null ? "" : subtitle, placeholders),
                    times
            ));
        }
        if (actionbar != null && !actionbar.isBlank()) {
            player.sendActionBar(mini(actionbar, placeholders));
        }
    }
}
