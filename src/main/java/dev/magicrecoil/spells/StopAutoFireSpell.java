package dev.magicrecoil.spells;

import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.*;
import dev.magicrecoil.MagicRecoilPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Explicit eventless reload hook. Current native use/pulses stay latched until release. */
public final class StopAutoFireSpell extends InstantSpell {
    public StopAutoFireSpell(MagicConfig config, String name) { super(config, name); }
    @Override public CastResult cast(SpellData data) {
        var plugin = MagicRecoilPlugin.instance();
        if (!Bukkit.isPrimaryThread() || plugin == null || !plugin.isEnabled() || plugin.autoFireManager() == null
                || !(data.caster() instanceof Player player)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
        plugin.autoFireManager().stop(player.getUniqueId());
        return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
    }
}
