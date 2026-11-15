package dev.magicrecoil.spells;

import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import dev.magicrecoil.MagicRecoilPlugin;
import dev.magicrecoil.aiming.AimSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Input binding, costs, cooldowns and modifiers belong to MagicSpells' normal casting pipeline. */
public final class AimSpell extends InstantSpell {
    private final AimSettings settings;
    public AimSpell(MagicConfig config, String spellName) {
        super(config, spellName);
        try { settings = AimSettings.from(config.getSection("spells." + spellName)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Aim spell '" + spellName + "': " + error.getMessage(), error); }
    }
    @Override public CastResult cast(SpellData data) {
        var plugin = MagicRecoilPlugin.instance();
        if (!Bukkit.isPrimaryThread() || plugin == null || !plugin.isEnabled() || plugin.aimManager() == null
                || !(data.caster() instanceof Player player)
                || !plugin.aimManager().cast(player, getInternalName(), settings)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
        playSpellEffects(data);
        return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
    }
    @Override protected void turnOff() {
        var plugin = MagicRecoilPlugin.instance();
        if (plugin != null && plugin.aimManager() != null) plugin.aimManager().stopSpell(getInternalName());
        super.turnOff();
    }
    public AimSettings settings() { return settings; }
}
