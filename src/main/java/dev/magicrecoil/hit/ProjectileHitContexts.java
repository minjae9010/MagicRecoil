package dev.magicrecoil.hit;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Single-use, same-tick projectile receipts. No per-hit tasks or retained entities. */
public final class ProjectileHitContexts {
    private record Key(UUID caster, UUID target) {}
    public record Context(Location origin, Location previous, Vector incoming, double radius) {
        public Context {
            origin = origin.clone();
            previous = previous.clone();
            incoming = incoming.clone();
        }
        @Override public Location origin() { return origin.clone(); }
        @Override public Location previous() { return previous.clone(); }
        @Override public Vector incoming() { return incoming.clone(); }
    }

    private final Map<Key, Context> contexts = new HashMap<>();
    private int tick;

    public void put(UUID caster, UUID target, int now, Context context) {
        expire(now);
        if (contexts.size() < 4096) contexts.put(new Key(caster, target), context);
    }

    public Context consume(UUID caster, UUID target, int now) {
        expire(now);
        return contexts.remove(new Key(caster, target));
    }

    private void expire(int now) {
        if (tick != now) { contexts.clear(); tick = now; }
    }

    public void clear() { contexts.clear(); }
}
