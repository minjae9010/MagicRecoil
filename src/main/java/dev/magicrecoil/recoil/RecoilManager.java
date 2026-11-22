package dev.magicrecoil.recoil;

import dev.magicrecoil.packet.RotationAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class RecoilManager {
    private final JavaPlugin plugin;
    private final RotationAdapter adapter;
    private final Map<UUID, RecoilSession> sessions = new HashMap<>();
    private BukkitTask task;
    private long packets;

    public RecoilManager(JavaPlugin plugin, RotationAdapter adapter) {
        this.plugin = plugin;
        this.adapter = adapter;
    }

    public boolean shoot(Player player, String weapon, RecoilSettings settings) {
        return shoot(player, weapon, settings, 1);
    }

    public boolean shoot(Player player, String weapon, RecoilSettings settings, double aimMultiplier) {
        requireMainThread();
        if (!Double.isFinite(aimMultiplier) || aimMultiplier < 0 || aimMultiplier > 1) throw new IllegalArgumentException("Recoil multiplier must be in [0, 1]");
        if (!plugin.isEnabled() || !player.isOnline() || player.isDead()) return false;
        RecoilSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.weapon().equals(weapon) || session.settings() != settings) {
            session = new RecoilSession(weapon, settings);
            sessions.put(player.getUniqueId(), session);
        }
        var input = player.getCurrentInput();
        boolean moving = input != null && (input.isForward() != input.isBackward() || input.isLeft() != input.isRight());
        session.shoot(System.nanoTime(), ThreadLocalRandom.current(), aimMultiplier, moving);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        return true;
    }

    private void tick() {
        long now = System.nanoTime();
        var iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || player.isDead()) { iterator.remove(); continue; }
            RecoilSession session = entry.getValue();
            RotationDelta delta = session.next(now);
            try {
                if (!delta.isZero()) {
                    RotationDelta actual = adapter.rotate(player, delta);
                    session.recordApplied(actual);
                    if (!actual.isZero()) packets++;
                }
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Rotation adapter failed; disabling MagicRecoil", error);
                Bukkit.getPluginManager().disablePlugin(plugin);
                return;
            }
            if (session.finished(now)) iterator.remove();
        }
        stopTaskIfIdle();
    }

    public void stop(UUID player) {
        requireMainThread();
        sessions.remove(player);
        stopTaskIfIdle();
    }

    public void stopWeapon(String weapon) {
        requireMainThread();
        sessions.values().removeIf(session -> session.weapon().equals(weapon));
        stopTaskIfIdle();
    }

    public void stopForSpell(UUID player, String spell, boolean buffEnd) {
        requireMainThread();
        var session = sessions.get(player);
        if (session == null) return;
        var names = buffEnd ? session.settings().stopOnBuffEnd() : session.settings().stopOnSpells();
        if (names.contains(spell)) stop(player);
    }

    public void clear() {
        requireMainThread();
        sessions.clear();
        stopTaskIfIdle();
    }

    private void stopTaskIfIdle() {
        if (sessions.isEmpty() && task != null) { task.cancel(); task = null; }
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Recoil state requires the server thread");
    }

    public int activePlayers() { return sessions.size(); }
    public long packetsSent() { return packets; }
    public String adapterName() { return adapter.name(); }
}
