package dev.magicrecoil.packet;

import dev.magicrecoil.recoil.RotationDelta;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Resolves Mojang-mapped rotation methods once, without linking to one server release. */
public final class PaperRotationAdapter implements RotationAdapter {
    private final Method getHandle;
    private final Method getYaw;
    private final Method getPitch;
    private final Field connection;
    private final Method rotate;
    private final boolean relative;
    private final String version;

    public static PaperRotationAdapter create() throws ReflectiveOperationException {
        Class<?> server = Bukkit.getServer().getClass();
        ClassLoader loader = server.getClassLoader();
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, loader);
            throw new IllegalStateException("Folia is not supported; recoil requires Paper's main-thread scheduler");
        } catch (ClassNotFoundException ignored) {
            // Normal Paper has one synchronous scheduler.
        }
        Class<?> craftPlayer = Class.forName(server.getPackageName() + ".entity.CraftPlayer", false, loader);
        return new PaperRotationAdapter(craftPlayer, Bukkit.getMinecraftVersion());
    }

    // Package access allows the same discovery and invocation path to be tested with API fixtures.
    PaperRotationAdapter(Class<?> craftPlayer, String version) throws ReflectiveOperationException {
        this.version = version;
        getHandle = craftPlayer.getMethod("getHandle");
        Class<?> handle = getHandle.getReturnType();
        connection = handle.getField("connection");
        getYaw = handle.getMethod("getYRot");
        getPitch = handle.getMethod("getXRot");
        Method selected;
        boolean supportsRelative;
        try {
            selected = handle.getMethod("forceSetRotation", float.class, boolean.class, float.class, boolean.class);
            supportsRelative = true;
        } catch (NoSuchMethodException ignored) {
            selected = handle.getMethod("forceSetRotation", float.class, float.class);
            supportsRelative = false;
        }
        if (selected.getReturnType() != void.class || getYaw.getReturnType() != float.class
                || getPitch.getReturnType() != float.class) {
            throw new NoSuchMethodException("Unsupported Paper rotation API on " + version);
        }
        rotate = selected;
        relative = supportsRelative;
    }

    @Override public RotationDelta rotate(Player player, RotationDelta requested) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Rotation requires the server thread");
        try {
            Object handle = getHandle.invoke(player);
            if (connection.get(handle) == null) return RotationDelta.ZERO;
            float oldYaw = (float) getYaw.invoke(handle);
            float oldPitch = (float) getPitch.invoke(handle);
            if (!Float.isFinite(oldYaw) || !Float.isFinite(oldPitch)) return RotationDelta.ZERO;
            RotationDelta bounded = requested.limit(45);
            float pitch = (float) (Math.clamp(oldPitch + bounded.pitch(), -90, 90) - oldPitch);
            float yaw = (float) bounded.yaw();
            if (Math.abs(yaw) < 1.0e-7 && Math.abs(pitch) < 1.0e-7) return RotationDelta.ZERO;
            // Both native methods update server aim and send a rotation-only packet.
            // Older protocols only support absolute camera angles, so return their rounded delta.
            if (relative) {
                rotate.invoke(handle, yaw, true, pitch, true);
            } else {
                float targetYaw = oldYaw + yaw;
                float targetPitch = oldPitch + pitch;
                yaw = targetYaw - oldYaw;
                pitch = targetPitch - oldPitch;
                if (Math.abs(yaw) < 1.0e-7 && Math.abs(pitch) < 1.0e-7) return RotationDelta.ZERO;
                rotate.invoke(handle, targetYaw, targetPitch);
            }
            return new RotationDelta(yaw, pitch);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException("Paper rotation failed on " + version, error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Paper rotation API changed on " + version, error);
        }
    }

    public boolean supportsRelativeRotation() { return relative; }

    @Override public String name() {
        return "Paper " + version + (relative ? " relative rotation" : " absolute rotation compatibility");
    }
}
