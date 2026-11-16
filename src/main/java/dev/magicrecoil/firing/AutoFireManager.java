package dev.magicrecoil.firing;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.events.BuffEndEvent;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.util.SpellData;
import dev.magicrecoil.spells.AutoFireSpell;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.function.Predicate;

/** Shared server-tick firing: a short input-refilled queue, native use, or a repeat timeout. */
public final class AutoFireManager implements Listener {
    private final JavaPlugin plugin;
    private final Predicate<Player> inventoryReady;
    private List<AutoFireSpell> guns = List.of();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> observedUses = new HashSet<>();
    private final Map<UUID, Pulse> pulses = new HashMap<>();
    private static final int BUFFER_TICKS = 5;
    private record Pulse(AutoFireSpell gun, int tick, ArrayDeque<Integer> pending) {
        void refill(int now) {
            // Refill only missing opportunities, as in CrackShot; clicks never stack complete bursts.
            for (int offset = 1; offset <= BUFFER_TICKS && pending.size() < BUFFER_TICKS; offset++) {
                int due = now + offset;
                if (pending.isEmpty() || due - pending.peekLast() > 0) pending.addLast(due);
            }
        }
        boolean consume(int now) {
            boolean due = false;
            while (!pending.isEmpty() && now - pending.peekFirst() >= 0) { pending.removeFirst(); due = true; }
            return due; // Never catch up with multiple shots in one server tick.
        }
    }
    private BukkitTask task;
    private Spell invoking;
    private UUID invokingPlayer;
    private static final class Session {
        final AutoFireSpell gun;
        final int slot;
        final ItemStack item;
        int nextShot;
        Session(Player player, AutoFireSpell gun) {
            this.gun = gun; slot = player.getInventory().getHeldItemSlot();
            item = player.getInventory().getItemInMainHand().clone(); nextShot = Bukkit.getCurrentTick();
        }
    }
    public AutoFireManager(JavaPlugin plugin) { this(plugin, p -> p.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING); }
    AutoFireManager(JavaPlugin plugin, Predicate<Player> inventoryReady) { this.plugin = plugin; this.inventoryReady = inventoryReady; }
    public void configure(List<AutoFireSpell> definitions) {
        clear();
        guns = definitions.stream().filter(g -> g.settings().enabled() && g.resolve()).filter(g -> {
            if (g.settings().inputMode() != AutoFireSettings.InputMode.NATIVE_USE || UseItemSupport.nativeUseAvailable()) return true;
            plugin.getLogger().warning(g.getInternalName() + ": NATIVE_USE requires the Paper USE_EFFECTS API; use BUFFERED_CLICK on this server");
            return false;
        }).toList();
        if (!guns.isEmpty()) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }
    private AutoFireSpell matching(ItemStack item) {
        AutoFireSpell result = null;
        for (var gun : guns) if (gun.matches(item)) {
            if (result != null) return null; // Ambiguous bindings fail closed.
            result = gun;
        }
        return result;
    }
    private boolean usable(Player player, AutoFireSpell gun) {
        return player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                && inventoryReady.test(player)
                && gun.matches(player.getInventory().getItemInMainHand())
                && switch (gun.settings().inputMode()) {
                    case BUFFERED_CLICK -> {
                        Pulse pulse = pulses.get(player.getUniqueId());
                        yield pulse != null && pulse.gun() == gun && !pulse.pending().isEmpty();
                    }
                    case REPEATED_CLICK -> pulseAlive(player, gun);
                    case NATIVE_USE -> player.hasActiveItem() && player.getActiveItemHand() == EquipmentSlot.HAND
                        && UseItemSupport.marked(player.getInventory().getItemInMainHand())
                        && player.getActiveItem().isSimilar(player.getInventory().getItemInMainHand());
                };
    }
    private boolean pulseAlive(Player player, AutoFireSpell gun) {
        Pulse pulse = pulses.get(player.getUniqueId());
        return pulse != null && pulse.gun() == gun && Bukkit.getCurrentTick() - pulse.tick() < gun.settings().releaseTimeoutTicks();
    }
    public boolean start(Player player, AutoFireSpell gun) {
        requireMainThread();
        if (!plugin.isEnabled() || !guns.contains(gun) || !usable(player, gun)) return false;
        sessions.putIfAbsent(player.getUniqueId(), new Session(player, gun));
        return true;
    }
    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            try {
                ItemStack item = player.getInventory().getItemInMainHand();
                AutoFireSpell gun = matching(item);
                if (gun == null || player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                        || !inventoryReady.test(player)) {
                    stop(id); observedUses.remove(id); restore(player); continue;
                }
                if (gun.settings().inputMode() == AutoFireSettings.InputMode.NATIVE_USE && !UseItemSupport.prepare(item)) { stop(id); continue; }
                if (!usable(player, gun)) { stop(id); observedUses.remove(id); pulses.remove(id); continue; }
                if (observedUses.add(id)) castImmediate(gun, player);
                Pulse pulse = pulses.get(id);
                boolean opportunity = gun.settings().inputMode() != AutoFireSettings.InputMode.BUFFERED_CLICK
                        || pulse != null && pulse.consume(Bukkit.getCurrentTick());
                Session session = sessions.get(id);
                if (session == null || !opportunity) continue;
                if (session.gun != gun || session.slot != player.getInventory().getHeldItemSlot() || !session.item.isSimilar(item)) {
                    stop(id); continue;
                }
                int tick = Bukkit.getCurrentTick();
                if (tick - session.nextShot < 0) continue;
                // Recheck immediately before calling the whole firing spell. Success gates remain in RecoilSpell.
                castImmediate(gun.fireSpell(), player);
                session.nextShot = tick + gun.settings().intervalTicks();
            } catch (RuntimeException error) {
                stop(id); plugin.getLogger().log(java.util.logging.Level.SEVERE, "AutoFire stopped for " + id, error);
            }
        }
    }
    private void castImmediate(Spell spell, Player player) {
        invoking = spell; invokingPlayer = player.getUniqueId();
        try { spell.hardCast(new SpellData(player)); }
        finally { invoking = null; invokingPlayer = null; }
    }
    public void stop(UUID id) { requireMainThread(); sessions.remove(id); }
    private void restore(Player player) {
        for (ItemStack item : player.getInventory().getContents()) UseItemSupport.restore(item);
    }
    private void reset(Player player) {
        stop(player.getUniqueId()); observedUses.remove(player.getUniqueId()); pulses.remove(player.getUniqueId());
        if (UseItemSupport.marked(player.getActiveItem())) player.clearActiveItem();
        restore(player);
    }
    public void clear() {
        requireMainThread();
        if (task != null) { task.cancel(); task = null; }
        guns = List.of(); sessions.clear(); observedUses.clear(); pulses.clear();
        for (Player player : Bukkit.getOnlinePlayers()) reset(player);
    }
    public void remove(AutoFireSpell spell) {
        requireMainThread();
        guns = guns.stream().filter(g -> g != spell).toList();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Session session = sessions.get(player.getUniqueId());
            if (session != null && session.gun == spell) reset(player);
        }
        if (guns.isEmpty()) clear();
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        AutoFireSpell gun = matching(event.getItem());
        if (gun == null || event.useItemInHand() == Event.Result.DENY) return;
        if (gun.settings().inputMode() != AutoFireSettings.InputMode.NATIVE_USE) {
            UUID id = event.getPlayer().getUniqueId();
            int now = Bukkit.getCurrentTick();
            Pulse previous = pulses.get(id);
            if (previous != null && (previous.gun() != gun || (gun.settings().inputMode() == AutoFireSettings.InputMode.BUFFERED_CLICK
                    && now - previous.tick() > BUFFER_TICKS))) {
                stop(id); observedUses.remove(id); previous = null;
            }
            Pulse pulse = new Pulse(gun, now, previous == null ? new ArrayDeque<>() : previous.pending());
            if (gun.settings().inputMode() == AutoFireSettings.InputMode.BUFFERED_CLICK) pulse.refill(now);
            pulses.put(id, pulse);
            event.setUseInteractedBlock(Event.Result.DENY); event.setUseItemInHand(Event.Result.DENY);
        } else if (UseItemSupport.marked(event.getItem())) {
            // Suppress hoe tilling/container opening, while leaving item-use cancellation to protection plugins.
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void released(PlayerStopUsingItemEvent event) {
        // A different/offhand item's native release is not a release signal for buffered clicks.
        if (UseItemSupport.marked(event.getItem())) {
            stop(event.getPlayer().getUniqueId()); observedUses.remove(event.getPlayer().getUniqueId());
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        if (UseItemSupport.marked(event.getItem())) { event.setCancelled(true); stop(event.getPlayer().getUniqueId()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void rejectCastTime(SpellCastEvent event) {
        if (event.getSpell() == invoking && event.getCaster().getUniqueId().equals(invokingPlayer) && event.getCastTime() > 0) {
            event.setSpellCastState(Spell.SpellCastState.CANCELLED); event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void attempting(SpellCastEvent event) {
        if (!Bukkit.isPrimaryThread() || !(event.getCaster() instanceof Player player)) return;
        Session s = sessions.get(player.getUniqueId());
        if (s != null && s.gun.settings().stopOnSpells().contains(event.getSpell().getInternalName().toLowerCase(Locale.ROOT))) stop(player.getUniqueId());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void buffEnd(BuffEndEvent event) {
        if (!Bukkit.isPrimaryThread() || !(event.getTarget() instanceof Player player)) return;
        Session s = sessions.get(player.getUniqueId());
        if (s != null && s.gun.settings().stopOnBuffEnd().contains(event.getBuffSpell().getInternalName().toLowerCase(Locale.ROOT))) stop(player.getUniqueId());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void held(PlayerItemHeldEvent e) { reset(e.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void swap(PlayerSwapHandItemsEvent e) {
        reset(e.getPlayer()); UseItemSupport.restore(e.getMainHandItem()); UseItemSupport.restore(e.getOffHandItem());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void drop(PlayerDropItemEvent e) { reset(e.getPlayer()); var item = e.getItemDrop().getItemStack(); UseItemSupport.restore(item); e.getItemDrop().setItemStack(item); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void teleport(PlayerTeleportEvent e) { reset(e.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent e) { reset(e.getEntity()); e.getDrops().forEach(UseItemSupport::restore); }
    @EventHandler public void quit(PlayerQuitEvent e) { reset(e.getPlayer()); }
    @EventHandler public void join(PlayerJoinEvent e) { restore(e.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST) public void open(InventoryOpenEvent e) { if (e.getPlayer() instanceof Player p) reset(p); }
    @EventHandler(priority = EventPriority.LOWEST) public void click(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p) { reset(p); UseItemSupport.restore(e.getCurrentItem()); UseItemSupport.restore(e.getCursor()); } }
    @EventHandler(priority = EventPriority.LOWEST) public void drag(InventoryDragEvent e) { if (e.getWhoClicked() instanceof Player p) { reset(p); UseItemSupport.restore(e.getOldCursor()); e.getNewItems().values().forEach(UseItemSupport::restore); } }
    private static void requireMainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("AutoFire requires the server thread"); }
    public int activePlayers() { return sessions.size(); }
}
