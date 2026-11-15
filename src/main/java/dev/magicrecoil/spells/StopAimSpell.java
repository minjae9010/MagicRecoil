package dev.magicrecoil.spells;

import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import dev.magicrecoil.MagicRecoilPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Explicit ADS end hook for reload chains and toggle:false. Does not alter the existing recoil reset hook. */
public final class StopAimSpell extends InstantSpell {
    public StopAimSpell(MagicConfig config, String spellName) { super(config, spellName); }
    @Override public CastResult cast(SpellData data) {
        var plugin = MagicRecoilPlugin.instance();
        if (!Bukkit.isPrimaryThread() || plugin == null || !plugin.isEnabled() || plugin.aimManager() == null
                || !(data.caster() instanceof Player player)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
        plugin.aimManager().stop(player.getUniqueId());
        return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
    }
}
