package dev.magicrecoil.spells;

import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import dev.magicrecoil.MagicRecoilPlugin;
import dev.magicrecoil.recoil.RecoilSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class RecoilSpell extends InstantSpell {
    private final RecoilSettings settings;

    public RecoilSpell(MagicConfig config, String spellName) {
        super(config, spellName);
        try { settings = RecoilSettings.from(config.getSection("spells." + spellName)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Recoil spell '" + spellName + "': " + error.getMessage(), error); }
    }

    @Override
    public CastResult cast(SpellData data) {
        var plugin = MagicRecoilPlugin.instance();
        if (!Bukkit.isPrimaryThread() || plugin == null || !plugin.isEnabled() || plugin.manager() == null
                || !(data.caster() instanceof Player player)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
        if (plugin.successGate().isBlocked(player.getUniqueId(), Bukkit.getCurrentTick())) {
            return new CastResult(PostCastAction.ALREADY_HANDLED, data);
        }
        if (!settings.requireSuccessOf().isEmpty()
                && !plugin.successGate().consume(player.getUniqueId(), settings.requireSuccessOf(), Bukkit.getCurrentTick())) {
            return new CastResult(PostCastAction.ALREADY_HANDLED, data);
        }
        double multiplier = plugin.aimManager() == null ? 1 : plugin.aimManager().recoilMultiplier(player, getInternalName());
        if (!plugin.manager().shoot(player, getInternalName(), settings, multiplier)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
        playSpellEffects(data);
        return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
    }

    @Override
    protected void turnOff() {
        var plugin = MagicRecoilPlugin.instance();
        if (plugin != null && plugin.manager() != null) plugin.manager().stopWeapon(getInternalName());
        super.turnOff();
    }

    public RecoilSettings settings() { return settings; }
}
