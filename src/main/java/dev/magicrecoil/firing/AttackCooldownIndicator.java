package dev.magicrecoil.firing;

import com.nisovin.magicspells.Spell;
import dev.magicrecoil.packet.PaperAttackCooldownPackets;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class AttackCooldownIndicator {
    // Leave the alternate item in the client view long enough for its held-item check to run.
    private static final int RESET_TICKS = 2;
    private static final double READY_ATTACK_SPEED = 1024;
    private final Plugin plugin;
    private final PaperAttackCooldownPackets packets;
    private final Map<UUID, Display> active = new HashMap<>();

    public AttackCooldownIndicator(Plugin plugin, PaperAttackCooldownPackets packets) {
        this.plugin = plugin;
        this.packets = packets;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void start(Player player, Spell spell) {
        float seconds = spell.getCooldown(player);
        if (!Float.isFinite(seconds) || seconds <= 0 || !spell.onCooldown(player)) return;
        stop(player.getUniqueId());
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack held = player.getInventory().getItemInMainHand();
        Display display = new Display(player, spell, slot, held.getType(), Bukkit.getCurrentTick());
        active.put(player.getUniqueId(), display);
        try {
            packets.showSpeed(player, 0);
            packets.showSlot(player, slot, resetItem(held));
        } catch (ReflectiveOperationException | RuntimeException error) {
            stop(player.getUniqueId());
            logFailure(error);
        }
    }

    private static ItemStack resetItem(ItemStack held) {
        if (held.getType().isAir()) return new ItemStack(Material.STICK);
        Material alternate = held.getType() == Material.STICK ? Material.BLAZE_ROD : Material.STICK;
        ItemStack item = held.withType(alternate);
        var meta = item.getItemMeta();
        if (!meta.hasItemModel()) {
            meta.setItemModel(held.getType().getKey());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void tick() {
        int tick = Bukkit.getCurrentTick();
        var iterator = active.values().iterator();
        while (iterator.hasNext()) {
            Display display = iterator.next();
            Player player = display.player;
            var inventory = player.getInventory();
            if (!player.isOnline() || player.isDead() || inventory.getHeldItemSlot() != display.slot
                    || inventory.getItemInMainHand().getType() != display.material
                    || (display.ready && tick - display.readyTick >= display.readyTicks)) {
                iterator.remove();
                restore(display);
                continue;
            }
            if (display.ready) continue;
            try {
                float seconds = display.spell.getCooldown(player);
                if (!Float.isFinite(seconds) || seconds <= 0 || !display.spell.onCooldown(player)) {
                    boolean resetting = !display.slotRestored;
                    restoreSlot(display);
                    // Short spells can finish before the client's ordinary melee gauge refills.
                    var speed = player.getAttribute(Attribute.ATTACK_SPEED);
                    double naturalTicks = speed != null && speed.getValue() > 0 ? 20 / speed.getValue() : 1;
                    int elapsed = resetting ? 0 : Math.max(0, tick - display.startTick - RESET_TICKS);
                    display.readyTicks = (int) Math.ceil(Math.clamp(naturalTicks - elapsed, 1, 20));
                    display.readyTick = tick;
                    display.ready = true;
                    packets.showSpeed(player, READY_ATTACK_SPEED);
                } else if (!display.slotRestored && tick - display.startTick >= RESET_TICKS) {
                    restoreSlot(display);
                    packets.showSpeed(player, 1.0 / seconds);
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                iterator.remove();
                restore(display);
                logFailure(error);
            }
        }
    }

    public void stop(UUID player) {
        Display display = active.remove(player);
        if (display != null) restore(display);
    }

    public void clear() {
        active.values().forEach(this::restore);
        active.clear();
    }

    private void restore(Display display) {
        if (!display.player.isOnline()) return;
        try {
            restoreSlot(display);
        } catch (ReflectiveOperationException | RuntimeException error) {
            logFailure(error);
        }
        try {
            packets.restoreSpeed(display.player);
        } catch (ReflectiveOperationException | RuntimeException error) {
            logFailure(error);
        }
    }

    private void restoreSlot(Display display) throws ReflectiveOperationException {
        if (display.slotRestored) return;
        ItemStack actual = display.player.getInventory().getItem(display.slot);
        packets.showSlot(display.player, display.slot, actual);
        display.slotRestored = true;
    }

    private void logFailure(Exception error) {
        plugin.getLogger().log(Level.WARNING, "Could not update the attack cooldown indicator", error);
    }

    private static final class Display {
        final Player player;
        final Spell spell;
        final int slot;
        final Material material;
        final int startTick;
        boolean slotRestored;
        boolean ready;
        int readyTick;
        int readyTicks;

        Display(Player player, Spell spell, int slot, Material material, int startTick) {
            this.player = player;
            this.spell = spell;
            this.slot = slot;
            this.material = material;
            this.startTick = startTick;
        }
    }
}
