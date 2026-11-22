package dev.magicrecoil.packet;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/** Sends a private attack-speed snapshot and inventory view to the casting client. */
public final class PaperAttackCooldownPackets {
    private final Method getHandle;
    private final Field connection;
    private final Method send;
    private final Method getAttribute;
    private final Object attackSpeed;
    private final Constructor<?> attributeInstance;
    private final Method setBaseValue;
    private final Constructor<?> attributesPacket;
    private final Method asNmsCopy;
    private final Constructor<?> inventoryPacket;

    public PaperAttackCooldownPackets() throws ReflectiveOperationException {
        Class<?> server = Bukkit.getServer().getClass();
        ClassLoader loader = server.getClassLoader();
        String craftPackage = server.getPackageName();
        Class<?> craftPlayer = loader.loadClass(craftPackage + ".entity.CraftPlayer");
        getHandle = craftPlayer.getMethod("getHandle");
        Class<?> handle = getHandle.getReturnType();
        connection = handle.getField("connection");
        send = connection.getType().getMethod("send", loader.loadClass("net.minecraft.network.protocol.Packet"));

        String attributes = "net.minecraft.world.entity.ai.attributes.";
        Class<?> holder = loader.loadClass("net.minecraft.core.Holder");
        Class<?> instance = loader.loadClass(attributes + "AttributeInstance");
        getAttribute = handle.getMethod("getAttribute", holder);
        attackSpeed = loader.loadClass(attributes + "Attributes").getField("ATTACK_SPEED").get(null);
        attributeInstance = instance.getConstructor(holder, Consumer.class);
        setBaseValue = instance.getMethod("setBaseValue", double.class);
        String protocol = "net.minecraft.network.protocol.game.";
        attributesPacket = loader.loadClass(protocol + "ClientboundUpdateAttributesPacket")
                .getConstructor(int.class, Collection.class);
        asNmsCopy = loader.loadClass(craftPackage + ".inventory.CraftItemStack")
                .getMethod("asNMSCopy", ItemStack.class);
        inventoryPacket = loader.loadClass(protocol + "ClientboundSetPlayerInventoryPacket")
                .getConstructor(int.class, loader.loadClass("net.minecraft.world.item.ItemStack"));
    }

    public void showSpeed(Player player, double speed) throws ReflectiveOperationException {
        Object snapshot = attributeInstance.newInstance(attackSpeed, (Consumer<Object>) ignored -> {});
        setBaseValue.invoke(snapshot, speed);
        sendSpeed(player, snapshot);
    }

    public void restoreSpeed(Player player) throws ReflectiveOperationException {
        Object actual = getAttribute.invoke(getHandle.invoke(player), attackSpeed);
        if (actual != null) sendSpeed(player, actual);
    }

    private void sendSpeed(Player player, Object snapshot) throws ReflectiveOperationException {
        send(player, attributesPacket.newInstance(player.getEntityId(), List.of(snapshot)));
    }

    public void showSlot(Player player, int slot, ItemStack item) throws ReflectiveOperationException {
        send(player, inventoryPacket.newInstance(slot, asNmsCopy.invoke(null, item)));
    }

    private void send(Player player, Object packet) throws ReflectiveOperationException {
        Object listener = connection.get(getHandle.invoke(player));
        if (listener != null) send.invoke(listener, packet);
    }
}
