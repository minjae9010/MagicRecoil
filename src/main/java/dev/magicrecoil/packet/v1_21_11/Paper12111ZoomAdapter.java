package dev.magicrecoil.packet.v1_21_11;

import dev.magicrecoil.aiming.ZoomAdapter;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.world.entity.player.Abilities;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Sends a copied abilities packet only. Never changes server abilities, walk speed, base attributes or potions. */
public final class Paper12111ZoomAdapter implements ZoomAdapter {
    private final Set<UUID> scoped = new HashSet<>();

    public static float walkingSpeed(double movementAttribute, double factor) {
        if (!Double.isFinite(movementAttribute) || movementAttribute < 0 || !Double.isFinite(factor) || factor < 0.55 || factor > 1) {
            throw new IllegalArgumentException("FOV factor must be in [0.55, 1] and movement speed finite/nonnegative");
        }
        // Vanilla FOV: (attribute / abilities.walkingSpeed + 1) / 2, then lerp by the client's FOV Effects setting.
        return (float) Math.max(0.0001, movementAttribute / (2 * factor - 1));
    }
    @Override public void zoom(Player player, double factor) {
        requireMainThread();
        var movement = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movement == null || movement.getValue() <= 0) { clear(player); return; }
        send(player, walkingSpeed(movement.getValue(), factor));
        scoped.add(player.getUniqueId());
    }
    @Override public void clear(Player player) {
        requireMainThread();
        if (scoped.remove(player.getUniqueId())) send(player, null);
    }
    private void send(Player player, Float walkingSpeed) {
        var handle = ((CraftPlayer) player).getHandle();
        if (handle.connection == null) return;
        var actual = handle.getAbilities();
        var packetAbilities = new Abilities();
        packetAbilities.apply(actual.pack());
        if (walkingSpeed != null) packetAbilities.walkingSpeed = walkingSpeed;
        handle.connection.send(new ClientboundPlayerAbilitiesPacket(packetAbilities));
    }
    private static void requireMainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Zoom requires the server thread"); }
}
