package com.teamcombat;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class TeamPlugin extends JavaPlugin {
    public static final String PREFIX = ChatColor.GOLD + "[Team] " + ChatColor.RESET;
    private TeamManager teams;
    private CombatManager combat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        teams = new TeamManager(this);
        teams.load();
        combat = new CombatManager(this);
        combat.start();

        getServer().getPluginManager().registerEvents(new GameListener(this, teams, combat), this);

        TeamCommand cmd = new TeamCommand(this, teams, combat);
        getCommand("team").setExecutor(cmd);
        getCommand("team").setTabCompleter(cmd);
        getCommand("tc").setExecutor(cmd);

        // autosave every 5 minutes
        getServer().getScheduler().runTaskTimer(this, teams::save, 6000L, 6000L);
        getServer().getOnlinePlayers().forEach(teams::applyTag);
        getLogger().info("TeamCombat enabled: " + teams.all().size() + " teams loaded.");
    }

    @Override
    public void onDisable() {
        if (teams != null) teams.save();
    }

    public void msg(CommandSender s, String m) {
        s.sendMessage(PREFIX + m);
    }
}
