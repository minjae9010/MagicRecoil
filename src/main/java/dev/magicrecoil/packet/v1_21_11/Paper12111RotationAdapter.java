package dev.magicrecoil.packet.v1_21_11;

import dev.magicrecoil.packet.RotationAdapter;
import dev.magicrecoil.recoil.RotationDelta;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Uses the vanilla rotation-only relative packet and its server-side update together. */
public final class Paper12111RotationAdapter implements RotationAdapter {
    @Override
    public RotationDelta rotate(Player player, RotationDelta requested) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Rotation requires the server thread");
        var handle = ((CraftPlayer) player).getHandle();
        if (handle.connection == null || !Float.isFinite(handle.getXRot()) || !Float.isFinite(handle.getYRot())) {
            return RotationDelta.ZERO;
        }
        RotationDelta bounded = requested.limit(45);
        float pitch = (float) (Math.clamp(handle.getXRot() + bounded.pitch(), -90, 90) - handle.getXRot());
        float yaw = (float) bounded.yaw();
        if (Math.abs(yaw) < 1.0e-7 && Math.abs(pitch) < 1.0e-7) return RotationDelta.ZERO;
        // Updates real aim, then sends ClientboundPlayerRotationPacket(yaw, true, pitch, true).
        // No coordinates, velocity, teleport id, or teleport confirmation are involved.
        handle.forceSetRotation(yaw, true, pitch, true);
        return new RotationDelta(yaw, pitch);
    }

    @Override
    public String name() { return "Paper 1.21.11 relative rotation"; }
}
