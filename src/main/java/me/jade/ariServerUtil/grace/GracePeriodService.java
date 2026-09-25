package me.jade.ariServerUtil.grace;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.persistence.Database;
import me.jade.ariServerUtil.util.DurationParser;
import me.jade.ariServerUtil.util.Permissions;
import me.jade.ariServerUtil.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;

public final class GracePeriodService implements Listener {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;
    private final Database database;
    private final AuditService audit;
    private Instant endTime;

    public GracePeriodService(JavaPlugin plugin, ServerUtilConfig config, Database database, AuditService audit) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.audit = audit;
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean active() {
        return endTime != null && endTime.isAfter(Instant.now());
    }

    public Duration remaining() {
        return active() ? Duration.between(Instant.now(), endTime) : Duration.ZERO;
    }

    public void start(Player staff, Duration duration, boolean replace) {
        if (active() && !replace) {
            Text.send(staff, "<red>A grace period is already active.");
            return;
        }
        endTime = Instant.now().plus(duration);
        save();
        Bukkit.broadcast(Text.mm(config.message("grace.start").replace("<duration>", DurationParser.human(duration))));
        audit.record(staff, "GRACE_START", null, "", DurationParser.human(duration), "success", "");
    }

    public void stop(Player staff) {
        endTime = null;
        save();
        Bukkit.broadcast(Text.mm(config.message("grace.stop")));
        audit.record(staff, "GRACE_STOP", null, "", "", "success", "");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!active() || !(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player)) {
            return;
        }
        if (attacker.hasPermission(Permissions.GRACE_BYPASS)) {
            return;
        }
        event.setCancelled(true);
        Text.send(attacker, config.message("grace.blocked"));
    }

    private void tick() {
        if (endTime != null && !endTime.isAfter(Instant.now())) {
            endTime = null;
            save();
            Bukkit.broadcast(Text.mm(config.message("grace.expired")));
        }
    }

    private void save() {
        database.execute(conn -> {
            try (var ps = conn.prepareStatement("INSERT OR REPLACE INTO state(key,value) VALUES('grace_end',?)")) {
                ps.setString(1, endTime == null ? "0" : Long.toString(endTime.toEpochMilli()));
                ps.executeUpdate();
            }
        });
    }

    private void load() {
        try {
            String value = database.query(conn -> {
                try (var ps = conn.prepareStatement("SELECT value FROM state WHERE key='grace_end'")) {
                    try (var rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString(1) : "0";
                    }
                }
            }).join();
            long millis = Long.parseLong(value);
            endTime = millis > System.currentTimeMillis() ? Instant.ofEpochMilli(millis) : null;
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not load grace period state: " + ex.getMessage());
        }
    }
}
