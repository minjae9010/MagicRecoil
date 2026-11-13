package dev.magicrecoil.spells;

import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import dev.magicrecoil.MagicRecoilPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Explicit reload/weapon lifecycle hook for configurations without cast events. */
public final class StopRecoilSpell extends InstantSpell {
    public StopRecoilSpell(MagicConfig config, String spellName) { super(config, spellName); }

    @Override
    public CastResult cast(SpellData data) {
        var plugin = MagicRecoilPlugin.instance();
        if (!Bukkit.isPrimaryThread() || plugin == null || !plugin.isEnabled() || plugin.manager() == null
                || !(data.caster() instanceof Player player)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
        plugin.reset(player.getUniqueId());
        return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
    }
}
