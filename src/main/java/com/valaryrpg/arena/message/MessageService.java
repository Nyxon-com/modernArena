package com.valaryrpg.arena.message;

import com.valaryrpg.arena.ValaryArenaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Testi MiniMessage da lang/messages.yml, brand incluso. */
public final class MessageService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final ValaryArenaPlugin plugin;

    public MessageService(ValaryArenaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // i YAML sono già ricaricati da ConfigManager
    }

    public String raw(String path) {
        FileConfiguration yaml = this.plugin.configs().messages();
        return yaml.getString(path, path);
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
}
