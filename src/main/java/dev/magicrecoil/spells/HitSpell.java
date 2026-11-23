package dev.magicrecoil.spells;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.events.ParticleProjectileHitEvent;
import com.nisovin.magicspells.events.SpellApplyDamageEvent;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import dev.magicrecoil.MagicRecoilPlugin;
import dev.magicrecoil.hit.HitResult;
import dev.magicrecoil.hit.HitGeometry;
import dev.magicrecoil.hit.HitSettings;
import dev.magicrecoil.hit.ProjectileHitContexts;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Instant ray shots, particle projectile callbacks, and native beam callbacks. */
public final class HitSpell extends InstantSpell implements TargetedEntityFromLocationSpell, TargetedEntitySpell {
    private final HitSettings settings;
    private final Map<String, Subspell> hooks = new HashMap<>();
    private final ProjectileHitContexts projectiles = new ProjectileHitContexts();
    private BeamHit beamHit;
    private DamageType damageType;
    private boolean ready;
    private boolean processing;

    public HitSpell(MagicConfig config, String spellName) {
        super(config, spellName);
        try { settings = HitSettings.from(config.getSection("spells." + spellName)); }
        catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Hit spell '" + spellName + "': " + error.getMessage(), error);
        }
    }

    @Override
    protected void initialize() {
        super.initialize();
        hooks.clear();
        damageType = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).get(settings.damageType());
        ready = damageType != null;
        if (!ready) {
            MagicSpells.error(getInternalName() + ": damage-type '" + settings.damageType()
                    + "' is not registered; load its data pack before starting the server.");
            return;
        }
        settings.hooks().forEach((key, name) -> {
            Subspell hook = new Subspell(name);
            if (!hook.process() || hook.getSpell() == this) {
                MagicSpells.error(getInternalName() + ": invalid " + key + " '" + name + "'; hit spell disabled.");
                ready = false;
            } else hooks.put(key, hook);
        });
    }

    /** A miss is still a successfully fired shot, so normal recoil gating works. */
    @Override
    public CastResult cast(SpellData data) {
        if (!available(data) || processing || beamHit != null) return skipped(data);
        Location origin = data.caster().getEyeLocation();
        Vector incoming = origin.getDirection();
        var ray = origin.getWorld().rayTrace(origin, incoming, settings.range(), FluidCollisionMode.NEVER, true, 0,
                entity -> entity instanceof LivingEntity target && validTarget(data.caster(), target));
        if (ray != null && ray.getHitEntity() instanceof LivingEntity target) {
            SpellData hit = data.target(target).location(ray.getHitPosition().toLocation(origin.getWorld()));
            SpellTargetEvent event = new SpellTargetEvent(this, hit);
            if (event.callEvent()) {
                hit = event.getSpellData();
                // A retargeting plugin must not turn the original impact into a headshot on another entity.
                if (hit.target() != null) applyHit(hit, origin, incoming, hit.target().equals(target));
            }
        }
        playSpellEffects(data);
        return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
    }

    /** Use spell-on-hit-entity: "Name(targeting=entity_from_location)". */
    @Override
    public CastResult castAtEntityFromLocation(SpellData data) {
        if (!available(data) || processing || data.target() == null || data.location() == null) return skipped(data);
        BeamHit beam = beamHit;
        if (beam != null) {
            if (!beam.matches(data)) return skipped(data);
            beamHit = null;
            return applyHit(data, beam.origin(), beam.incoming(), beam.precise());
        }
        var context = projectiles.consume(data.caster().getUniqueId(), data.target().getUniqueId(), Bukkit.getCurrentTick());
        // Never substitute the shooter's current position for an unknown launch position.
        if (context == null || !context.origin().getWorld().equals(data.location().getWorld())) return skipped(data);
        var impact = HitGeometry.projectileImpact(context.previous().toVector(), data.location().toVector(),
                context.incoming(), data.target().getBoundingBox(), context.radius());
        Location sample = impact.position().toLocation(data.location().getWorld());
        return applyHit(data.location(sample), context.origin(), context.incoming(), impact.precise());
    }

    Spell.SpellCastResult subcastFromBeam(Subspell callback, SpellData data, Location origin, Vector incoming, boolean precise) {
        BeamHit outer = beamHit;
        beamHit = new BeamHit(data, origin, incoming, precise);
        try { return callback.subcast(data, false, false); }
        finally { beamHit = outer; }
    }

    private record BeamHit(SpellData impact, Location origin, Vector incoming, boolean precise) {
        private boolean matches(SpellData data) {
            return Objects.equals(impact.caster(), data.caster()) && Objects.equals(impact.target(), data.target())
                    && Objects.equals(impact.location(), data.location());
        }
    }

    /** Prevent MagicSpells' entity-first projectile ordering from falling back to a new ray shot. */
    @Override
    public CastResult castAtEntity(SpellData data) { return skipped(data); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void projectileHit(ParticleProjectileHitEvent event) {
        if (!Bukkit.isPrimaryThread() || event.getCaster() == null || event.getTarget() == null) return;
        var tracker = event.getTracker();
        if (tracker == null) return;
        var callback = event.getTarget().equals(event.getCaster()) ? tracker.getCasterSpell() : tracker.getEntitySpell();
        if (callback == null || callback.getSpell() != this || !callback.isTargetedEntityFromLocationSpell()) return;
        if (tracker.getStartLocation() == null || tracker.getPreviousLocation() == null
                || tracker.getCurrentLocation() == null || tracker.getCurrentVelocity() == null) return;
        var box = tracker.getHitBox();
        if (box == null) return;
        double radius = Math.sqrt(box.getWidthX() * box.getWidthX() + box.getHeight() * box.getHeight()
                + box.getWidthZ() * box.getWidthZ()) / 2;
        Vector incoming = tracker.getCurrentLocation().toVector().subtract(tracker.getPreviousLocation().toVector());
        if (incoming.lengthSquared() < 1e-12) incoming = tracker.getCurrentVelocity();
        projectiles.put(event.getCaster().getUniqueId(), event.getTarget().getUniqueId(), Bukkit.getCurrentTick(),
                new ProjectileHitContexts.Context(tracker.getStartLocation(), tracker.getPreviousLocation(), incoming, radius));
    }

    private CastResult applyHit(SpellData data, Location origin, Vector incoming, boolean precise) {
        LivingEntity target = data.target();
        if (target == null || !validTarget(data.caster(), target) || !target.getWorld().equals(origin.getWorld())
                || data.location() == null || !target.getWorld().equals(data.location().getWorld())) return skipped(data);
        // Also breaks synchronous A -> B -> A cycles during formula evaluation and callbacks.
        processing = true;
        try {
            HitResult result;
            try {
                result = HitResult.evaluate(settings, target.getType(), target.getBoundingBox(), data.location().toVector(),
                        precise, origin.toVector(), incoming, target.getLocation().getYaw(), data,
                        target.getPose(), target.getEyeLocation().getY());
            } catch (IllegalArgumentException error) { return skipped(data); }

            if (result.damage() > 0) {
                var damageEvent = new SpellApplyDamageEvent(this, data.caster(), target, result.damage(), damageType, "magicrecoil");
                damageEvent.callEvent();
                double damage = damageEvent.getFinalDamage();
                if (Double.isFinite(damage) && damage > 0) {
                    var source = DamageSource.builder(damageType).withDamageLocation(origin)
                            .withCausingEntity(data.caster()).withDirectEntity(data.caster()).build();
                    target.damage(damage, source);
                }
            }
            for (String key : result.hookKeys()) {
                Subspell hook = hooks.get(key);
                // false preserves the impact location for location-targeted effects.
                if (hook != null) hook.subcast(data, false, false);
            }
        } finally { processing = false; }
        return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
    }

    private boolean validTarget(LivingEntity caster, LivingEntity target) {
        return !target.equals(caster) && target.isValid() && !target.isDead()
                && (!(caster instanceof Player) || !(target instanceof Player) || target.getWorld().getPVP())
                && getValidTargetList().canTarget(caster, target);
    }

    private boolean available(SpellData data) {
        var plugin = MagicRecoilPlugin.instance();
        return ready && Bukkit.isPrimaryThread() && plugin != null && plugin.isEnabled()
                && data.caster() != null && data.caster().isValid() && !data.caster().isDead()
                && Float.isFinite(data.power()) && data.power() >= 0;
    }

    private CastResult skipped(SpellData data) { return new CastResult(PostCastAction.ALREADY_HANDLED, data); }

    @Override
    protected void turnOff() {
        ready = false;
        projectiles.clear();
        hooks.clear();
        super.turnOff();
    }

    public HitSettings settings() { return settings; }
}
