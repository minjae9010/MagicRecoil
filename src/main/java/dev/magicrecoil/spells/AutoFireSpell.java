package dev.magicrecoil.spells;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.spells.PassiveSpell;
import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import dev.magicrecoil.MagicRecoilPlugin;
import dev.magicrecoil.firing.AutoFireSettings;
import dev.magicrecoil.firing.UseItemSupport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Registered use-item binding; the native interaction must not be cancelled by a right-click-cast-item. */
public final class AutoFireSpell extends InstantSpell {
    private final AutoFireSettings settings;
    private final CastItem item;
    private Spell fireSpell;
    public AutoFireSpell(MagicConfig config, String name) {
        super(config, name);
        settings = AutoFireSettings.from(config.getSection("spells." + name));
        var magicItem = settings.enabled() ? MagicItems.getMagicItemFromString(settings.useItem()) : null;
        item = magicItem == null ? null : new CastItem(magicItem.getItemStack());
        if (settings.enabled() && item == null) throw new IllegalArgumentException(name + ": unknown use-item: " + settings.useItem());
        if (item != null && (!item.isTypeValid() || (settings.inputMode() == AutoFireSettings.InputMode.NATIVE_USE && !UseItemSupport.supports(item.getType())))) {
            throw new IllegalArgumentException(name + ": use-item must be a stick, blaze rod, or hoe (a MagicSpells magic-item name is accepted)");
        }
    }
    public boolean resolve() {
        fireSpell = MagicSpells.getSpellByInternalName(settings.fireSpell());
        if (fireSpell == null || fireSpell instanceof AutoFireSpell || fireSpell instanceof PassiveSpell) {
            MagicSpells.error(getInternalName() + ": fire-spell must be an ordinary synchronous firing entry spell, not a PassiveSpell/AutoFireSpell");
            fireSpell = null;
        }
        return fireSpell != null;
    }
    public boolean matches(ItemStack stack) { return item != null && item.equals(stack); }
    @Override public CastResult cast(SpellData data) {
        var plugin = MagicRecoilPlugin.instance();
        if (!Bukkit.isPrimaryThread() || plugin == null || !plugin.isEnabled() || plugin.autoFireManager() == null
                || !(data.caster() instanceof Player player) || !plugin.autoFireManager().start(player, this)) {
            return new CastResult(PostCastAction.ALREADY_HANDLED, data);
        }
        playSpellEffects(data);
        return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
    }
    @Override protected void turnOff() {
        var plugin = MagicRecoilPlugin.instance();
        if (plugin != null && plugin.autoFireManager() != null) plugin.autoFireManager().remove(this);
        super.turnOff();
    }
    public AutoFireSettings settings() { return settings; }
    public Spell fireSpell() { return fireSpell; }
}
