package dev.magicrecoil.firing;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.UseEffects;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

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
        if (!supports(item.getType()) || item.isDataOverridden(DataComponentTypes.CONSUMABLE)
                || item.isDataOverridden(DataComponentTypes.USE_EFFECTS)) return false;
        var meta = item.getItemMeta(); meta.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().consumeSeconds(3600)
                .animation(ItemUseAnimation.NONE).sound(Key.key("minecraft:intentionally_empty")).hasConsumeParticles(false));
        item.setData(DataComponentTypes.USE_EFFECTS, UseEffects.useEffects().canSprint(true).interactVibrations(false).speedMultiplier(1));
        return true;
    }
    public static void restore(ItemStack item) {
        if (!marked(item)) return;
        item.resetData(DataComponentTypes.CONSUMABLE); item.resetData(DataComponentTypes.USE_EFFECTS);
        var meta = item.getItemMeta(); meta.getPersistentDataContainer().remove(MARKER); item.setItemMeta(meta);
    }
}
