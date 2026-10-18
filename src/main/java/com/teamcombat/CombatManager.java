package com.teamcombat;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;

public class CombatManager {
    private final TeamPlugin plugin;
    private final Map<UUID, Long> tagged = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();

    public CombatManager(TeamPlugin plugin) { this.plugin = plugin; }

    private long duration() { return plugin.getConfig().getInt("combat.tag-seconds", 15) * 1000L; }

    public void tag(Player p, Player other) {
        boolean was = isTagged(p);
        tagged.put(p.getUniqueId(), System.currentTimeMillis() + duration());
        lastAttacker.put(p.getUniqueId(), other.getUniqueId());
        if (!was) plugin.msg(p, ChatColor.RED + "คุณเข้าสู่โหมดต่อสู้! ห้ามออกเกม/วาร์ป " +
                plugin.getConfig().getInt("combat.tag-seconds", 15) + " วินาที");
    }

    public boolean isTagged(Player p) {
        Long end = tagged.get(p.getUniqueId());
        return end != null && end > System.currentTimeMillis();
    }

    public int remaining(Player p) {
        Long end = tagged.get(p.getUniqueId());
        if (end == null) return 0;
        return (int) Math.max(0, Math.ceil((end - System.currentTimeMillis()) / 1000.0));
    }

    public Player lastAttacker(Player victim) {
        if (!isTagged(victim)) return null;
        UUID id = lastAttacker.get(victim.getUniqueId());
        return id == null ? null : Bukkit.getPlayer(id);
    }

    public void untag(Player p) {
        tagged.remove(p.getUniqueId());
        lastAttacker.remove(p.getUniqueId());
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Iterator<Map.Entry<UUID, Long>> it = tagged.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Long> e = it.next();
                Player p = Bukkit.getPlayer(e.getKey());
                if (e.getValue() <= now || p == null) {
                    it.remove();
                    lastAttacker.remove(e.getKey());
                    if (p != null) plugin.msg(p, ChatColor.GREEN + "คุณออกจากโหมดต่อสู้แล้ว");
                } else {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(ChatColor.RED + "⚔ Combat: " + remaining(p) + "s"));
                }
            }
        }, 20L, 20L);
    }
}
