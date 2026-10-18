package com.teamcombat;

import org.bukkit.Location;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class Team {
    private final String name;
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();
    private boolean friendlyFire;
    private int kills, deaths;
    private Location home;

    public Team(String name, UUID leader, boolean friendlyFire) {
        this.name = name;
        this.leader = leader;
        this.friendlyFire = friendlyFire;
        this.members.add(leader);
    }

    public String getName() { return name; }
    public UUID getLeader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }
    public Set<UUID> getMembers() { return members; }
    public boolean isMember(UUID id) { return members.contains(id); }
    public boolean isFriendlyFire() { return friendlyFire; }
    public void setFriendlyFire(boolean ff) { this.friendlyFire = ff; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public void setKills(int k) { kills = k; }
    public void setDeaths(int d) { deaths = d; }
    public void addKill() { kills++; }
    public void addDeath() { deaths++; }
    public Location getHome() { return home; }
    public void setHome(Location home) { this.home = home; }
}
