package dev.magicrecoil.aiming;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** CrackShot-style potion scope. Short leases bound leftover slowdown after an abnormal shutdown. */
public final class SlownessZoom {
    private static final int LEASE_TICKS = 10;
    private final Map<UUID, State> states = new HashMap<>();
    private final Set<UUID> changing = new HashSet<>();
    private static final class State {
        final PotionEffect previous;
        final int started;
        final int amplifier;
        int applied;
        boolean suppressed;
        State(PotionEffect previous, int now, int amplifier) {
            this.previous = previous; this.started = now; this.applied = now; this.amplifier = amplifier;
        }
        boolean owns(PotionEffect effect, int now) {
            return effect != null && effect.getAmplifier() == amplifier && !effect.isAmbient()
                    && !effect.hasParticles() && !effect.hasIcon()
                    && Math.abs(effect.getDuration() - (LEASE_TICKS - (now - applied))) <= 1;
        }
    }
    public void zoom(Player player, int amplifier) {
        requireMainThread();
        UUID id = player.getUniqueId();
        int now = Bukkit.getCurrentTick();
        PotionEffect current = player.getPotionEffect(PotionEffectType.SLOWNESS);
        State state = states.get(id);
        boolean first = state == null;
        if (first) {
            state = new State(current, now, amplifier); states.put(id, state);
            // A stronger or layered external effect already controls FOV; preserve it intact.
            if (current != null && (current.getAmplifier() >= amplifier || current.getHiddenPotionEffect() != null)) state.suppressed = true;
        }
        if (state.suppressed) return;
        if (!first && !state.owns(current, now)) { state.suppressed = true; return; }
        if (!first && now - state.applied < LEASE_TICKS / 2) return;
        changing.add(id);
        try {
            if (first && current != null) {
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) { state.suppressed = true; return; }
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, LEASE_TICKS, amplifier, false, false, false));
            PotionEffect applied = player.getPotionEffect(PotionEffectType.SLOWNESS);
            if (applied != null && applied.getAmplifier() == amplifier && applied.getDuration() == LEASE_TICKS) state.applied = now;
            else if (first) { state.suppressed = true; restore(player, state, now); }
        } finally { changing.remove(id); }
    }
    public void clear(Player player) {
        requireMainThread();
        UUID id = player.getUniqueId();
        State state = states.remove(id);
        int now = Bukkit.getCurrentTick();
        if (state == null || state.suppressed || !state.owns(player.getPotionEffect(PotionEffectType.SLOWNESS), now)) return;
        changing.add(id);
        try { player.removePotionEffect(PotionEffectType.SLOWNESS); restore(player, state, now); }
        finally { changing.remove(id); }
    }
    private void restore(Player player, State state, int now) {
        if (state.previous == null || player.hasPotionEffect(PotionEffectType.SLOWNESS)) return;
        int remaining = state.previous.isInfinite() ? PotionEffect.INFINITE_DURATION : state.previous.getDuration() - (now - state.started);
        if (remaining > 0 || remaining == PotionEffect.INFINITE_DURATION) player.addPotionEffect(state.previous.withDuration(remaining));
    }
    public void changed(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getModifiedType() != PotionEffectType.SLOWNESS || changing.contains(player.getUniqueId())) return;
        State state = states.get(player.getUniqueId());
        // Respect another plugin's replacement/removal instead of repeatedly overwriting it.
        if (state != null) state.suppressed = true;
    }
    private static void requireMainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Slowness zoom requires the server thread"); }
}
