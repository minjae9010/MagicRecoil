package dev.magicrecoil.firing;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.lang.reflect.Method;

/** Only our temporary component overrides are removed. Native weapons/food are deliberately excluded. */
public final class UseItemSupport {
    private static final NamespacedKey MARKER = new NamespacedKey("magicrecoil", "use_item");
    private UseItemSupport() {}
    public static boolean supports(Material material) {
        return switch (material) {
            case STICK, BLAZE_ROD, WOODEN_HOE, STONE_HOE, IRON_HOE, GOLDEN_HOE, DIAMOND_HOE, NETHERITE_HOE -> true;
            default -> false;
        };
    }
    public static boolean marked(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(MARKER, PersistentDataType.BYTE);
    }
    public static boolean prepare(ItemStack item) {
        if (marked(item)) return true;
        if (!nativeUseAvailable()) return false;
        if (!supports(item.getType()) || item.isDataOverridden(DataComponentTypes.CONSUMABLE)
                || item.isDataOverridden(UseEffectsApi.TYPE)) return false;
        Object effects = UseEffectsApi.create();
        var meta = item.getItemMeta(); meta.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().consumeSeconds(3600)
                .animation(ItemUseAnimation.NONE).sound(Key.key("minecraft:intentionally_empty")).hasConsumeParticles(false));
        item.setData(UseEffectsApi.TYPE, effects);
        return true;
    }
    public static void restore(ItemStack item) {
        if (!marked(item)) return;
        item.resetData(DataComponentTypes.CONSUMABLE);
        if (nativeUseAvailable()) item.resetData(UseEffectsApi.TYPE);
        var meta = item.getItemMeta(); meta.getPersistentDataContainer().remove(MARKER); item.setItemMeta(meta);
    }

    /** USE_EFFECTS was added after the minimum Paper API. Buffered clicking does not need it. */
    public static boolean nativeUseAvailable() {
        return NativeUseAvailability.AVAILABLE;
    }

    private static final class NativeUseAvailability {
        static final boolean AVAILABLE = detect();
        private static boolean detect() {
            try {
                DataComponentTypes.class.getField("USE_EFFECTS");
                return true;
            } catch (NoSuchFieldException error) { return false; }
        }
    }

    private static final class UseEffectsApi {
        static final DataComponentType.Valued<Object> TYPE;
        static final Method BUILDER, SPRINT, VIBRATIONS, SPEED, BUILD;
        static {
            try {
                @SuppressWarnings("unchecked")
                var type = (DataComponentType.Valued<Object>) DataComponentTypes.class.getField("USE_EFFECTS").get(null);
                TYPE = type;
                Class<?> api = Class.forName("io.papermc.paper.datacomponent.item.UseEffects");
                BUILDER = api.getMethod("useEffects");
                Class<?> builder = BUILDER.getReturnType();
                SPRINT = builder.getMethod("canSprint", boolean.class);
                VIBRATIONS = builder.getMethod("interactVibrations", boolean.class);
                SPEED = builder.getMethod("speedMultiplier", float.class);
                BUILD = builder.getMethod("build");
            } catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
        }
        static Object create() {
            try {
                Object builder = BUILDER.invoke(null);
                SPRINT.invoke(builder, true);
                VIBRATIONS.invoke(builder, false);
                SPEED.invoke(builder, 1f);
                return BUILD.invoke(builder);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot configure native item use", error); }
        }
    }
}
