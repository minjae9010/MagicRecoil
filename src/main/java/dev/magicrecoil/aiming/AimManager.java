package dev.magicrecoil.aiming;

import com.nisovin.magicspells.events.BuffEndEvent;
import com.nisovin.magicspells.events.SpellCastEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** One main-hand weapon per UUID; all access and the single reconciliation task use the server thread. */
public final class AimManager implements Listener {
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("magicrecoil", "aim_speed");
    private final JavaPlugin plugin;
    private final Function<Player, AttributeInstance> movementAttribute;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private BukkitTask task;

    private static final class Session {
        final String spell;
        final AimSettings settings;
        final int slot;
        final ItemStack item;
        Session(String spell, AimSettings settings, Player player) {
            this.spell = spell;
            this.settings = settings;
            slot = player.getInventory().getHeldItemSlot();
            item = player.getInventory().getItemInMainHand().clone();
        }
        boolean matches(Player player) {
            return slot == player.getInventory().getHeldItemSlot()
                    && item.isSimilar(player.getInventory().getItemInMainHand());
        }
    }

    public AimManager(JavaPlugin plugin) { this(plugin, player -> player.getAttribute(Attribute.MOVEMENT_SPEED)); }
    AimManager(JavaPlugin plugin, Function<Player, AttributeInstance> movementAttribute) {
        this.plugin = plugin;
        this.movementAttribute = movementAttribute;
    }

    public boolean cast(Player player, String spell, AimSettings settings) {
        requireMainThread();
        if (!settings.enabled() || !plugin.isEnabled() || !player.isOnline() || player.isDead()) return false;
        Session current = sessions.get(player.getUniqueId());
        if (current != null && current.spell.equals(spell)) {
            if (settings.toggle()) stop(player.getUniqueId());
            return true; // toggle:false is idempotent activation, never mouse hold.
        }
        if (player.getInventory().getItemInMainHand().getType().isAir()) return false;
        stop(player.getUniqueId());
        sessions.put(player.getUniqueId(), new Session(spell, settings, player));
        try {
            reconcile(player, sessions.get(player.getUniqueId()));
            if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
            return true;
        } catch (RuntimeException error) {
            stop(player.getUniqueId());
            throw error;
        }
    }

    public double recoilMultiplier(Player player, String recoilSpell) {
        requireMainThread();
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.matches(player)
                && session.settings.recoilSpells().contains(recoilSpell.toLowerCase(Locale.ROOT))
                ? session.settings.recoilMultiplier() : 1;
    }

    private void tick() {
        for (UUID id : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || player.isDead()) { stop(id); continue; }
            try { reconcile(player, sessions.get(id)); }
            catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "ADS update failed for " + id, error);
                stop(id);
            }
        }
    }

    private void reconcile(Player player, Session session) {
        var speed = movementAttribute.apply(player);
        if (speed == null) throw new IllegalStateException("Player has no movement speed attribute");
        boolean active = session.matches(player) && session.settings.movementSpeedMultiplier() != 1;
        var existing = speed.getModifier(SPEED_KEY);
        if (!active) {
            if (existing != null) speed.removeModifier(SPEED_KEY);
        } else if (existing == null) {
            speed.addTransientModifier(new AttributeModifier(SPEED_KEY,
                    session.settings.movementSpeedMultiplier() - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private void removeSpeed(Player player) {
        var speed = movementAttribute.apply(player);
        if (speed != null && speed.getModifier(SPEED_KEY) != null) speed.removeModifier(SPEED_KEY);
    }

    public void stop(UUID id) {
        requireMainThread();
        sessions.remove(id);
        if (sessions.isEmpty() && task != null) { task.cancel(); task = null; }
        Player player = Bukkit.getPlayer(id);
        if (player != null) removeSpeed(player);
    }

    public void stopSpell(String spell) {
        requireMainThread();
        for (UUID id : List.copyOf(sessions.keySet())) if (sessions.get(id).spell.equals(spell)) stop(id);
    }

    public void clear() {
        requireMainThread();
        for (UUID id : List.copyOf(sessions.keySet())) {
            try { stop(id); }
            catch (RuntimeException error) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "ADS cleanup failed for " + id, error); }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void held(PlayerItemHeldEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (session.settings.cancelOnItemSwap()) stop(event.getPlayer().getUniqueId());
        else removeSpeed(event.getPlayer()); // before the new slot takes effect; next tick may resume.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (session.settings.cancelOnHandSwap()) stop(event.getPlayer().getUniqueId());
        else removeSpeed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR) public void death(PlayerDeathEvent event) { stop(event.getEntity().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent event) { stop(event.getPlayer().getUniqueId()); }
    @EventHandler public void join(PlayerJoinEvent event) { removeSpeed(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) { stop(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) { stop(event.getPlayer().getUniqueId()); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { stop(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void attempting(SpellCastEvent event) {
        if (Bukkit.isPrimaryThread() && event.getCaster() instanceof Player player) stopFor(player, event.getSpell().getInternalName(), false);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void buffEnded(BuffEndEvent event) {
        if (Bukkit.isPrimaryThread() && event.getTarget() instanceof Player player) stopFor(player, event.getBuffSpell().getInternalName(), true);
    }
    private void stopFor(Player player, String spell, boolean buff) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null && (buff ? session.settings.stopOnBuffEnd() : session.settings.stopOnSpells())
                .contains(spell.toLowerCase(Locale.ROOT))) stop(player.getUniqueId());
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("ADS state requires the server thread");
    }
    public int activePlayers() { return sessions.size(); }
}
