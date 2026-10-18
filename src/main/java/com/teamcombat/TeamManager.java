package com.teamcombat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class TeamManager {
    public record Invite(String team, UUID from, long expires) {}

    private static final ChatColor[] COLORS = {
            ChatColor.AQUA, ChatColor.GREEN, ChatColor.YELLOW, ChatColor.LIGHT_PURPLE, ChatColor.GOLD,
            ChatColor.BLUE, ChatColor.RED, ChatColor.DARK_AQUA, ChatColor.DARK_GREEN, ChatColor.DARK_PURPLE
    };

    private final TeamPlugin plugin;
    private final Map<String, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, Team> byPlayer = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Set<UUID> chatMode = new HashSet<>();
    private final File file;

    public TeamManager(TeamPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
    }

    // ---------- lookup ----------
    public Collection<Team> all() { return teams.values(); }
    public Team getTeam(UUID id) { return byPlayer.get(id); }
    public Team getByName(String name) { return teams.get(name.toLowerCase()); }
    public Set<UUID> chatMode() { return chatMode; }

    public ChatColor colorOf(Team t) {
        return COLORS[Math.abs(t.getName().toLowerCase().hashCode()) % COLORS.length];
    }

    public String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    // ---------- create / remove ----------
    public Team create(String name, Player leader) {
        Team t = new Team(name, leader.getUniqueId(),
                plugin.getConfig().getBoolean("friendly-fire-default", false));
        teams.put(name.toLowerCase(), t);
        byPlayer.put(leader.getUniqueId(), t);
        applyTag(leader);
        return t;
    }

    public void remove(Team t) {
        for (UUID id : new ArrayList<>(t.getMembers())) {
            byPlayer.remove(id);
            chatMode.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) applyTag(p);
        }
        teams.remove(t.getName().toLowerCase());
        invites.values().removeIf(i -> i.team().equalsIgnoreCase(t.getName()));
    }

    public void addMember(Team t, Player p) {
        t.getMembers().add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), t);
        invites.remove(p.getUniqueId());
        applyTag(p);
    }

    public void removeMember(Team t, UUID id) {
        t.getMembers().remove(id);
        byPlayer.remove(id);
        chatMode.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null) applyTag(p);
    }

    // ---------- invites ----------
    public void invite(UUID target, Invite inv) { invites.put(target, inv); }

    public Invite getInvite(UUID target) {
        Invite i = invites.get(target);
        if (i != null && i.expires() < System.currentTimeMillis()) {
            invites.remove(target);
            return null;
        }
        return i;
    }

    public void clearInvite(UUID target) { invites.remove(target); }

    // ---------- chat ----------
    public void broadcast(Team t, String msg) {
        for (UUID id : t.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(ChatColor.AQUA + "[Team] " + ChatColor.RESET + msg);
        }
    }

    public void teamChat(Player from, String msg) {
        Team t = getTeam(from.getUniqueId());
        if (t == null) { plugin.msg(from, ChatColor.RED + "คุณไม่ได้อยู่ในทีม"); return; }
        String line = ChatColor.AQUA + "[TeamChat] " + ChatColor.GRAY + from.getName()
                + ChatColor.DARK_GRAY + ": " + ChatColor.WHITE + msg;
        for (UUID id : t.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(line);
        }
        plugin.getLogger().info("[TeamChat:" + t.getName() + "] " + from.getName() + ": " + msg);
    }

    // ---------- nametag (scoreboard) ----------
    public void applyTag(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (org.bukkit.scoreboard.Team st : sb.getTeams()) {
            if (st.getName().startsWith("tc_") && st.hasEntry(p.getName())) st.removeEntry(p.getName());
        }
        Team t = getTeam(p.getUniqueId());
        if (t == null) return;
        String id = "tc_" + Integer.toHexString(t.getName().toLowerCase().hashCode());
        org.bukkit.scoreboard.Team st = sb.getTeam(id);
        if (st == null) st = sb.registerNewTeam(id);
        ChatColor c = colorOf(t);
        st.setPrefix(c + "[" + t.getName() + "] ");
        st.setColor(c);
        st.addEntry(p.getName());
    }

    // ---------- persistence ----------
    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Team t : teams.values()) {
            String p = "teams." + t.getName() + ".";
            y.set(p + "leader", t.getLeader().toString());
            y.set(p + "members", t.getMembers().stream().map(UUID::toString).toList());
            y.set(p + "friendly-fire", t.isFriendlyFire());
            y.set(p + "kills", t.getKills());
            y.set(p + "deaths", t.getDeaths());
            if (t.getHome() != null) y.set(p + "home", t.getHome());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml: " + e.getMessage());
        }
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("teams");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Team t = new Team(key, UUID.fromString(s.getString("leader")), s.getBoolean("friendly-fire"));
                for (String m : s.getStringList("members")) t.getMembers().add(UUID.fromString(m));
                t.setKills(s.getInt("kills"));
                t.setDeaths(s.getInt("deaths"));
                Object home = s.get("home");
                if (home instanceof Location l) t.setHome(l);
                teams.put(key.toLowerCase(), t);
                for (UUID id : t.getMembers()) byPlayer.put(id, t);
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping broken team '" + key + "': " + e.getMessage());
            }
        }
    }
}
