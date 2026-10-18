package com.teamcombat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class TeamCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("create", "invite", "accept", "deny", "leave", "kick",
            "transfer", "disband", "info", "top", "ff", "sethome", "home", "chat", "help");

    private final TeamPlugin plugin;
    private final TeamManager tm;
    private final CombatManager cm;

    public TeamCommand(TeamPlugin plugin, TeamManager tm, CombatManager cm) {
        this.plugin = plugin; this.tm = tm; this.cm = cm;
    }

    private void err(CommandSender s, String m) { plugin.msg(s, ChatColor.RED + m); }
    private void ok(CommandSender s, String m) { plugin.msg(s, ChatColor.GREEN + m); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }

        if (cmd.getName().equalsIgnoreCase("tc")) {
            if (args.length == 0) { err(p, "ใช้: /tc <ข้อความ>"); return true; }
            tm.teamChat(p, String.join(" ", args));
            return true;
        }

        if (args.length == 0) { help(p); return true; }
        String sub = args[0].toLowerCase();
        Team mine = tm.getTeam(p.getUniqueId());

        switch (sub) {
            case "create" -> {
                if (args.length < 2) { err(p, "ใช้: /team create <ชื่อ>"); return true; }
                if (mine != null) { err(p, "คุณมีทีมอยู่แล้ว"); return true; }
                String name = args[1];
                int min = plugin.getConfig().getInt("name-min-length", 3);
                int max = plugin.getConfig().getInt("name-max-length", 12);
                if (!name.matches("[A-Za-z0-9_]{" + min + "," + max + "}")) {
                    err(p, "ชื่อต้องเป็น A-Z 0-9 _ ความยาว " + min + "-" + max + " ตัว"); return true;
                }
                if (tm.getByName(name) != null) { err(p, "ชื่อนี้ถูกใช้แล้ว"); return true; }
                tm.create(name, p);
                ok(p, "สร้างทีม " + name + " สำเร็จ! เชิญเพื่อนด้วย /team invite <ผู้เล่น>");
            }
            case "invite" -> {
                if (mine == null) { err(p, "คุณไม่ได้อยู่ในทีม"); return true; }
                if (!mine.getLeader().equals(p.getUniqueId())) { err(p, "เฉพาะหัวหน้าทีมเท่านั้น"); return true; }
                if (args.length < 2) { err(p, "ใช้: /team invite <ผู้เล่น>"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { err(p, "ไม่พบผู้เล่นออนไลน์ชื่อนี้"); return true; }
                if (tm.getTeam(t.getUniqueId()) != null) { err(p, "ผู้เล่นนี้มีทีมแล้ว"); return true; }
                if (mine.getMembers().size() >= plugin.getConfig().getInt("max-members", 5)) {
                    err(p, "ทีมเต็มแล้ว"); return true;
                }
                int secs = plugin.getConfig().getInt("invite-expire-seconds", 60);
                tm.invite(t.getUniqueId(), new TeamManager.Invite(mine.getName(), p.getUniqueId(),
                        System.currentTimeMillis() + secs * 1000L));
                ok(p, "ส่งคำเชิญไปยัง " + t.getName() + " แล้ว");
                plugin.msg(t, ChatColor.YELLOW + p.getName() + " เชิญคุณเข้าทีม " + mine.getName()
                        + " — พิมพ์ /team accept หรือ /team deny (หมดอายุใน " + secs + " วิ)");
            }
            case "accept" -> {
                if (mine != null) { err(p, "คุณมีทีมอยู่แล้ว"); return true; }
                TeamManager.Invite inv = tm.getInvite(p.getUniqueId());
                if (inv == null) { err(p, "คุณไม่มีคำเชิญ หรือคำเชิญหมดอายุแล้ว"); return true; }
                Team t = tm.getByName(inv.team());
                if (t == null) { err(p, "ทีมนี้ถูกยุบแล้ว"); tm.clearInvite(p.getUniqueId()); return true; }
                if (t.getMembers().size() >= plugin.getConfig().getInt("max-members", 5)) {
                    err(p, "ทีมเต็มแล้ว"); return true;
                }
                tm.addMember(t, p);
                tm.broadcast(t, ChatColor.GREEN + p.getName() + " เข้าร่วมทีม!");
            }
            case "deny" -> {
                TeamManager.Invite inv = tm.getInvite(p.getUniqueId());
                if (inv == null) { err(p, "คุณไม่มีคำเชิญ"); return true; }
                tm.clearInvite(p.getUniqueId());
                ok(p, "ปฏิเสธคำเชิญแล้ว");
                Player from = Bukkit.getPlayer(inv.from());
                if (from != null) plugin.msg(from, ChatColor.RED + p.getName() + " ปฏิเสธคำเชิญ");
            }
            case "leave" -> {
                if (mine == null) { err(p, "คุณไม่ได้อยู่ในทีม"); return true; }
                if (mine.getLeader().equals(p.getUniqueId())) {
                    if (mine.getMembers().size() > 1) {
                        err(p, "คุณเป็นหัวหน้า: โอนตำแหน่งด้วย /team transfer <ผู้เล่น> หรือ /team disband"); return true;
                    }
                    tm.remove(mine);
                    ok(p, "ทีมถูกยุบเพราะไม่มีสมาชิกเหลือ");
                    return true;
                }
                tm.removeMember(mine, p.getUniqueId());
                tm.broadcast(mine, ChatColor.YELLOW + p.getName() + " ออกจากทีม");
                ok(p, "คุณออกจากทีมแล้ว");
            }
            case "kick" -> {
                if (mine == null) { err(p, "คุณไม่ได้อยู่ในทีม"); return true; }
                if (!mine.getLeader().equals(p.getUniqueId())) { err(p, "เฉพาะหัวหน้าทีมเท่านั้น"); return true; }
                if (args.length < 2) { err(p, "ใช้: /team kick <ผู้เล่น>"); return true; }
                UUID target = findMember(mine, args[1]);
                if (target == null) { err(p, "ไม่พบสมาชิกชื่อนี้"); return true; }
                if (target.equals(p.getUniqueId())) { err(p, "เตะตัวเองไม่ได้ ใช้ /team disband"); return true; }
                tm.removeMember(mine, target);
                tm.broadcast(mine, ChatColor.YELLOW + tm.nameOf(target) + " ถูกเตะออกจากทีม");
                Player tp = Bukkit.getPlayer(target);
                if (tp != null) plugin.msg(tp, ChatColor.RED + "คุณถูกเตะออกจากทีม " + mine.getName());
            }
            case "transfer" -> {
                if (mine == null) { err(p, "คุณไม่ได้อยู่ในทีม"); return true; }
                if (!mine.getLeader().equals(p.getUniqueId())) { err(p, "เฉพาะหัวหน้าทีมเท่านั้น"); return true; }
                if (args.length < 2) { err(p, "ใช้: /team transfer <ผู้เล่น>"); return true; }
                UUID target = findMember(mine, args[1]);
                if (target == null) { err(p, "ไม่พบสมาชิกชื่อนี้"); return true; }
                mine.setLeader(target);
                tm.broadcast(mine, ChatColor.GOLD + tm.nameOf(target) + " เป็นหัวหน้าทีมคนใหม่");
            }
            case "disband" -> {
                if (mine == null) { err(p, "คุณไม่ได้อยู่ในทีม"); return true; }
                if (!mine.getLeader().equals(p.getUniqueId())) { err(p, "เฉพาะหัวหน้าทีมเท่านั้น"); return true; }
                tm.broadcast(mine, ChatColor.RED + "ทีมถูกยุบโดยหัวหน้า");
                tm.remove(mine);
            }
            case "admin" -> {
                if (!p.hasPermission("teamcombat.admin")) { err(p, "ไม่มีสิทธิ์"); return true; }
                if (args.length < 3 || !args[1].equalsIgnoreCase("disband")) {
                    err(p, "ใช้: /team admin disband <ทีม>"); return true;
                }
                Team t = tm.getByName(args[2]);
                if (t == null) { err(p, "ไม่พบทีม"); return true; }
                tm.broadcast(t, ChatColor.RED + "ทีมถูกยุบโดยแอดมิน");
                tm.remove(t);
                ok(p, "ยุบทีม " + t.getName() + " แล้ว");
            }
            case "info" -> {
                Team t = args.length >= 2 ? tm.getByName(args[1]) : mine;
                if (t == null) { err(p, args.length >= 2 ? "ไม่พบทีม" : "คุณไม่ได้อยู่ในทีม"); return true; }
                p.sendMessage(ChatColor.GOLD + "══ ทีม " + tm.colorOf(t) + t.getName() + ChatColor.GOLD + " ══");
                p.sendMessage(ChatColor.YELLOW + "หัวหน้า: " + ChatColor.WHITE + tm.nameOf(t.getLeader()));
                String members = t.getMembers().stream().map(id ->
                        (Bukkit.getPlayer(id) != null ? ChatColor.GREEN : ChatColor.GRAY) + tm.nameOf(id))
                        .collect(Collectors.joining(ChatColor.DARK_GRAY + ", "));
                p.sendMessage(ChatColor.YELLOW + "สมาชิก (" + t.getMembers().size() + "/"
                        + plugin.getConfig().getInt("max-members", 5) + "): " + members);
                double kdr = t.getDeaths() == 0 ? t.getKills() : (double) t.getKills() / t.getDeaths();
                p.sendMessage(ChatColor.YELLOW + "Kills: " + ChatColor.WHITE + t.getKills()
                        + ChatColor.YELLOW + "  Deaths: " + ChatColor.WHITE + t.getDeaths()
                        + ChatColor.YELLOW + "  K/D: " + ChatColor.WHITE + String.format("%.2f", kdr));
                p.sendMessage(ChatColor.YELLOW + "Friendly Fire: " + (t.isFriendlyFire()
                        ? ChatColor.RED + "เปิด" : ChatColor.GREEN + "ปิด"));
            }
            case "top" -> {
                List<Team> list = new ArrayList<>(tm.all());
                list.sort(Comparator.comparingInt(Team::getKills).reversed());
                p.sendMessage(ChatColor.GOLD + "══ Top Teams (Kills) ══");
                if (list.isEmpty()) p.sendMessage(ChatColor.GRAY + "ยังไม่มีทีม");
                for (int i = 0; i < Math.min(10, list.size()); i++) {
                    Team t = list.get(i);
                    p.sendMessage(ChatColor.YELLOW + "#" + (i + 1) + " " + tm.colorOf(t) + t.getName()
                            + ChatColor.GRAY + " — " + t.getKills() + " kills / " + t.getDeaths() + " deaths");
                }
            }
            case "ff" -> {
                if (mine == null) { err(p, "คุณไม่ได้อยู่ในทีม"); return true; }
                if (!mine.getLeader().equals(p.getUniqueId())) { err(p, "เฉพาะหัวหน้าทีมเท่านั้น"); return true; }
                mine.setFriendlyFire(!mine.isFriendlyFire());
                tm.broadcast(mine, "Friendly Fire: " + (mine.isFriendlyFire()
                        ? ChatColor.RED + "เปิด" : ChatColor.GREEN + "ปิด"));
            }
            case "sethome" -> {
                if (mine == null) { err(p, "คุณไม่ได้อยู่ในทีม"); return true; }
                if (!mine.getLeader().equals(p.getUniqueId())) { err(p, "เฉพาะหัวหน้าทีมเท่านั้น"); return true; }
                mine.setHome(p.getLocation());
                ok(p, "ตั้งฐานทีมเรียบร้อย");
            }
            case "home" -> {
                if (mine == null) { err(p, "คุณไม่ได้อยู่ในทีม"); return true; }
                if (mine.getHome() == null) { err(p, "ทีมยังไม่ได้ตั้งฐาน (/team sethome)"); return true; }
                if (cm.isTagged(p)) { err(p, "วาร์ปไม่ได้ขณะต่อสู้ (เหลือ " + cm.remaining(p) + " วิ)"); return true; }
                int secs = plugin.getConfig().getInt("home-warmup-seconds", 5);
                Location start = p.getLocation();
                Location dest = mine.getHome();
                ok(p, "กำลังวาร์ปใน " + secs + " วินาที อย่าขยับ!");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!p.isOnline()) return;
                    if (cm.isTagged(p)) { err(p, "ยกเลิกวาร์ป: คุณอยู่ในโหมดต่อสู้"); return; }
                    if (!p.getWorld().equals(start.getWorld()) || p.getLocation().distanceSquared(start) > 1.0) {
                        err(p, "ยกเลิกวาร์ป: คุณขยับ"); return;
                    }
                    p.teleport(dest);
                    ok(p, "วาร์ปถึงฐานทีมแล้ว");
                }, secs * 20L);
            }
            case "chat" -> {
                if (mine == null) { err(p, "คุณไม่ได้อยู่ในทีม"); return true; }
                if (tm.chatMode().remove(p.getUniqueId())) ok(p, "ปิดโหมดแชททีม (กลับแชทปกติ)");
                else { tm.chatMode().add(p.getUniqueId()); ok(p, "เปิดโหมดแชททีม — ข้อความจะส่งเฉพาะทีม"); }
            }
            default -> help(p);
        }
        return true;
    }

    private UUID findMember(Team t, String name) {
        for (UUID id : t.getMembers()) {
            if (tm.nameOf(id).equalsIgnoreCase(name)) return id;
        }
        return null;
    }

    private void help(Player p) {
        p.sendMessage(ChatColor.GOLD + "══ TeamCombat ══");
        String[] lines = {
                "/team create <ชื่อ> — สร้างทีม", "/team invite <ผู้เล่น> — เชิญ",
                "/team accept | deny — รับ/ปฏิเสธคำเชิญ", "/team leave — ออกจากทีม",
                "/team kick <ผู้เล่น> — เตะสมาชิก", "/team transfer <ผู้เล่น> — โอนหัวหน้า",
                "/team disband — ยุบทีม", "/team info [ทีม] — ข้อมูลทีม",
                "/team top — อันดับทีม", "/team ff — เปิด/ปิด Friendly Fire",
                "/team sethome | home — ฐานทีม", "/team chat หรือ /tc <ข้อความ> — แชททีม"
        };
        for (String l : lines) p.sendMessage(ChatColor.YELLOW + l);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] args) {
        if (!c.getName().equalsIgnoreCase("team")) return List.of();
        String cur = args[args.length - 1].toLowerCase();
        if (args.length == 1) {
            return SUBS.stream().filter(x -> x.startsWith(cur)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "invite", "kick", "transfer" -> {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(cur)).collect(Collectors.toList());
                }
                case "info" -> {
                    return tm.all().stream().map(Team::getName)
                            .filter(n -> n.toLowerCase().startsWith(cur)).collect(Collectors.toList());
                }
            }
        }
        return List.of();
    }
}
