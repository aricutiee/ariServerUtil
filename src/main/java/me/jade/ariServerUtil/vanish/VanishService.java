package me.jade.ariServerUtil.vanish;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VanishService implements Listener {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;
    private final Database database;
    private final AuditService audit;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishService(JavaPlugin plugin, ServerUtilConfig config, Database database, AuditService audit) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.audit = audit;
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::remind, 40L, 40L);
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public void toggle(Player staff) {
        if (vanished.contains(staff.getUniqueId())) {
            unvanish(staff);
        } else {
            vanish(staff);
        }
    }

    public void vanish(Player staff) {
        if (!vanished.add(staff.getUniqueId())) {
            return;
        }
        Bukkit.getOnlinePlayers().forEach(viewer -> {
            if (!viewer.hasPermission(Permissions.VANISH_SEE)) {
                viewer.hidePlayer(plugin, staff);
            }
        });
        Bukkit.broadcast(Text.mm(config.message("vanish.fake-leave").replace("<player>", staff.getName())));
        database.execute(conn -> {
            try (var ps = conn.prepareStatement("INSERT OR REPLACE INTO vanished(uuid,name,updated_at) VALUES(?,?,?)")) {
                ps.setString(1, staff.getUniqueId().toString());
                ps.setString(2, staff.getName());
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
        audit.record(staff, "VANISH", staff.getUniqueId(), staff.getName(), "", "success", "");
    }

    public void unvanish(Player staff) {
        if (!vanished.remove(staff.getUniqueId())) {
            return;
        }
        Bukkit.getOnlinePlayers().forEach(viewer -> viewer.showPlayer(plugin, staff));
        Bukkit.broadcast(Text.mm(config.message("vanish.fake-join").replace("<player>", staff.getName())));
        database.execute(conn -> {
            try (var ps = conn.prepareStatement("DELETE FROM vanished WHERE uuid=?")) {
                ps.setString(1, staff.getUniqueId().toString());
                ps.executeUpdate();
            }
        });
        audit.record(staff, "UNVANISH", staff.getUniqueId(), staff.getName(), "", "success", "");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        for (UUID uuid : vanished) {
            Player vanishedPlayer = Bukkit.getPlayer(uuid);
            if (vanishedPlayer != null && !joining.hasPermission(Permissions.VANISH_SEE)) {
                joining.hidePlayer(plugin, vanishedPlayer);
            }
        }
        if (vanished.contains(joining.getUniqueId())) {
            Bukkit.getOnlinePlayers().forEach(viewer -> {
                if (!viewer.hasPermission(Permissions.VANISH_SEE)) {
                    viewer.hidePlayer(plugin, joining);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (vanished.contains(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
        }
    }

    private void remind() {
        for (UUID uuid : vanished) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendActionBar(Text.mm(config.message("vanish.reminder")));
            }
        }
    }

    private void load() {
        database.query(conn -> {
            try (var ps = conn.prepareStatement("SELECT uuid FROM vanished")) {
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        vanished.add(UUID.fromString(rs.getString(1)));
                    }
                }
            }
            return null;
        });
    }
}
