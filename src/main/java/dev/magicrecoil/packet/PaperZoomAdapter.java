package dev.magicrecoil.packet;

import dev.magicrecoil.aiming.ZoomAdapter;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Copies abilities for legacy FOV packets without binding to one Minecraft version. */
public final class PaperZoomAdapter implements ZoomAdapter {
    private final Set<UUID> scoped = new HashSet<>();
    private final Method getHandle, getAbilities, send;
    private final Field connection, walkingSpeed;
    private final Field[] abilityFields;
    private final Constructor<?> newAbilities, newPacket;

    public static ZoomAdapter create() {
        try {
            Class<?> server = Bukkit.getServer().getClass();
            ClassLoader loader = server.getClassLoader();
            return new PaperZoomAdapter(Class.forName(server.getPackageName() + ".entity.CraftPlayer", false, loader),
                    Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket", false, loader),
                    Class.forName("net.minecraft.network.protocol.Packet", false, loader));
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("No compatible legacy abilities zoom adapter", error);
        }
    }

    PaperZoomAdapter(Class<?> player, Class<?> packet, Class<?> packetBase) throws ReflectiveOperationException {
        getHandle = player.getMethod("getHandle");
        Class<?> handle = getHandle.getReturnType();
        connection = handle.getField("connection");
        getAbilities = handle.getMethod("getAbilities");
        Class<?> abilities = getAbilities.getReturnType();
        newAbilities = abilities.getConstructor();
        walkingSpeed = abilities.getDeclaredField("walkingSpeed");
        walkingSpeed.setAccessible(true);
        if (walkingSpeed.getType() != float.class) throw new NoSuchFieldException("Abilities.walkingSpeed is not float");
        abilityFields = Arrays.stream(abilities.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).toArray(Field[]::new);
        for (Field field : abilityFields) field.setAccessible(true);
        newPacket = packet.getConstructor(abilities);
        send = connection.getType().getMethod("send", packetBase);
    }

    public static float walkingSpeed(double movementAttribute, double factor) {
        if (!Double.isFinite(movementAttribute) || movementAttribute < 0 || !Double.isFinite(factor) || factor < 0.55 || factor > 1) {
            throw new IllegalArgumentException("FOV factor must be in [0.55, 1] and movement speed finite/nonnegative");
        }
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

    void send(Player player, Float speed) {
        requireMainThread();
        try {
            Object handle = getHandle.invoke(player);
            Object listener = connection.get(handle);
            if (listener == null) return;
            Object actual = getAbilities.invoke(handle);
            Object copy = newAbilities.newInstance();
            for (Field field : abilityFields) field.set(copy, field.get(actual));
            if (speed != null) walkingSpeed.setFloat(copy, speed);
            send.invoke(listener, newPacket.newInstance(copy));
        } catch (InvocationTargetException error) {
            throw new IllegalStateException("Legacy zoom packet failed", error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Legacy zoom API changed", error);
        }
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Zoom requires the server thread");
    }
}
