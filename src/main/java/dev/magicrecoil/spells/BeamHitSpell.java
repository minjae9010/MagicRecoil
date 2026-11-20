package dev.magicrecoil.spells;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.instant.BeamSpell;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.config.ConfigData;
import dev.magicrecoil.hit.HitGeometry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.function.BiConsumer;

/** Native MagicSpells beam that preserves collision geometry for a direct HitSpell callback. */
public final class BeamHitSpell extends BeamSpell {
    private BeamCast activeCast;

    public BeamHitSpell(MagicConfig config, String spellName) { super(config, spellName); }

    @Override
    public void initialize() {
        super.initialize();
        if (getHitSpell() != null && getHitSpell().getSpell() instanceof HitSpell hit) {
            var callback = new HitCallback(getConfigString("spell", ""), hit);
            if (callback.process()) setHitSpell(callback);
        }
    }

    @Override
    public CastResult castAtEntityFromLocation(SpellData data) {
        if (!(getHitSpell() instanceof HitCallback)) return super.castAtEntityFromLocation(data);
        if (!Bukkit.isPrimaryThread() || !data.hasCaster() || !data.hasLocation()) {
            return new CastResult(PostCastAction.ALREADY_HANDLED, data);
        }
        BeamCast outer = activeCast;
        activeCast = new BeamCast();
        try { return super.castAtEntityFromLocation(data); }
        finally { activeCast = outer; }
    }

    // BeamSpell visits SPECIAL at each sampled center, before it checks entities at that point.
    @Override
    protected void playSpellEffects(EffectPosition position, Location location, SpellData data) {
        if (position == EffectPosition.SPECIAL && activeCast != null) {
            activeCast.previous = activeCast.sample == null ? activeCast.origin : activeCast.sample;
            activeCast.sample = location.clone();
        }
        super.playSpellEffects(position, location, data);
    }

    // Observe the values the native beam actually evaluates, including dynamic offsets and radii.
    @Override
    protected ConfigData<Vector> getConfigDataVector(String key, Vector defaultValue) {
        var value = super.getConfigDataVector(key, defaultValue);
        if (!key.equals("relative-offset")) return value;
        return observe(value, (data, offset) -> {
            activeCast.origin = Util.applyRelativeOffset(data.location(), offset.clone().setY(0));
            activeCast.relativeY = offset.getY();
        });
    }

    @Override
    protected ConfigData<Double> getConfigDataDouble(String key, double defaultValue) {
        var value = super.getConfigDataDouble(key, defaultValue);
        return switch (key) {
            case "y-offset" -> observe(value, (data, y) -> activeCast.origin.add(0, y == 0 ? activeCast.relativeY : y, 0));
            case "hit-radius" -> observe(value, (data, radius) -> activeCast.horizontalRadius = radius);
            case "vertical-hit-radius" -> observe(value, (data, radius) -> activeCast.verticalRadius = radius);
            default -> value;
        };
    }

    private <T> ConfigData<T> observe(ConfigData<T> value, BiConsumer<SpellData, T> observer) {
        return new ConfigData<>() {
            @Override public T get(SpellData data) {
                T result = value.get(data);
                if (activeCast != null) observer.accept(data, result);
                return result;
            }

            @Override public boolean isConstant() { return value.isConstant(); }
        };
    }

    private final class HitCallback extends Subspell {
        private final HitSpell hit;

        private HitCallback(String name, HitSpell hit) {
            super(name);
            this.hit = hit;
        }

        @Override
        public SpellCastResult subcast(SpellData data) {
            BeamCast cast = activeCast;
            if (cast == null || cast.origin == null || cast.previous == null || cast.sample == null || !data.hasTarget()
                    || !cast.sample.getWorld().equals(data.target().getWorld())) {
                return new SpellCastResult(SpellCastState.NORMAL, PostCastAction.ALREADY_HANDLED, data);
            }
            Vector point = cast.sample.toVector();
            Vector previous = cast.previous.toVector();
            Vector incoming = point.clone().subtract(previous);
            double radius = Math.hypot(Math.sqrt(2) * cast.horizontalRadius, cast.verticalRadius);
            HitGeometry.Impact impact = HitGeometry.projectileImpact(previous, point, incoming, data.target().getBoundingBox(), radius);
            Location location = impact.position().toLocation(cast.sample.getWorld());
            if (incoming.lengthSquared() < 1e-12) incoming = cast.sample.getDirection();
            return hit.subcastFromBeam(this, data.location(location), cast.origin, incoming, impact.precise());
        }
    }

    private static final class BeamCast {
        private Location origin;
        private Location previous;
        private Location sample;
        private double relativeY;
        private double horizontalRadius;
        private double verticalRadius;
    }
}
