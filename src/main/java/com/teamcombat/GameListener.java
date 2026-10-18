package com.teamcombat;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GameListener implements Listener {
    private final TeamPlugin plugin;
    private final TeamManager tm;
    private final CombatManager cm;

    public GameListener(TeamPlugin plugin, TeamManager tm, CombatManager cm) {
        this.plugin = plugin; this.tm = tm; this.cm = cm;
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { tm.applyTag(e.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        Team at = tm.getTeam(attacker.getUniqueId());
        Team vt = tm.getTeam(victim.getUniqueId());
        if (at != null && at == vt && !at.isFriendlyFire()) {
            e.setCancelled(true);
            attacker.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GREEN + "ตีเพื่อนร่วมทีมไม่ได้ (Friendly Fire ปิดอยู่)"));
            return;
        }
        cm.tag(victim, attacker);
        cm.tag(attacker, victim);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) killer = cm.lastAttacker(victim);
        cm.untag(victim);

        Team vt = tm.getTeam(victim.getUniqueId());
        Team kt = killer == null ? null : tm.getTeam(killer.getUniqueId());
        if (vt != null) {
            vt.addDeath();
            tm.broadcast(vt, ChatColor.RED + "☠ " + victim.getName() + " ตาย");
        }
        if (killer != null && kt != null && kt != vt) {
            kt.addKill();
            tm.broadcast(kt, ChatColor.GREEN + "⚔ " + killer.getName() + " ฆ่า " + victim.getName()
                    + ChatColor.GRAY + " (ทีมคิล: " + kt.getKills() + ")");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        tm.chatMode().remove(p.getUniqueId());
        if (plugin.getConfig().getBoolean("combat.kill-on-logout", true) && cm.isTagged(p) && !p.isDead()) {
            Bukkit.broadcastMessage(ChatColor.RED + "☠ " + p.getName() + " ออกเกมระหว่างต่อสู้ และถูกสังหาร!");
            p.setHealth(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!cm.isTagged(p) || p.hasPermission("teamcombat.admin")) return;
        String msg = e.getMessage().toLowerCase().trim();
        for (String b : plugin.getConfig().getStringList("combat.blocked-commands")) {
            b = b.toLowerCase();
            if (msg.equals(b) || msg.startsWith(b + " ")) {
                e.setCancelled(true);
                plugin.msg(p, ChatColor.RED + "ใช้คำสั่งนี้ไม่ได้ขณะต่อสู้ (เหลือ " + cm.remaining(p) + " วินาที)");
                return;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!tm.chatMode().contains(p.getUniqueId())) return;
        if (tm.getTeam(p.getUniqueId()) == null) { tm.chatMode().remove(p.getUniqueId()); return; }
        e.setCancelled(true);
        tm.teamChat(p, e.getMessage());
    }
}
