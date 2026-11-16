package dev.magicrecoil.compat;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.events.ParticleProjectileHitEvent;
import com.nisovin.magicspells.events.SpellApplyDamageEvent;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.events.BuffEndEvent;
import com.nisovin.magicspells.events.MagicSpellsLoadedEvent;
import com.nisovin.magicspells.events.MagicSpellsLoadingEvent;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.events.SpellCastedEvent;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.ValidTargetList;
import com.nisovin.magicspells.util.trackers.ParticleProjectileTracker;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import org.bukkit.Location;
import org.bukkit.damage.DamageType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.HandlerList;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

/** Checks binary contracts before MagicSpells starts instantiating addon spells. */
public final class MagicSpellsCompatibility {
    private MagicSpellsCompatibility() {}

    public static void verifyApi() throws ReflectiveOperationException {
        staticMethod(MagicSpells.class, "getInstance", MagicSpells.class);
        staticMethod(MagicSpells.class, "getClassLoaders", List.class);
        staticMethod(MagicSpells.class, "getSpells", Map.class);
        staticMethod(MagicSpells.class, "getSpellByInternalName", Spell.class, String.class);
        InstantSpell.class.getConstructor(MagicConfig.class, String.class);
        CastResult.class.getConstructor(Spell.PostCastAction.class, SpellData.class);
        method(SpellData.class, "caster", LivingEntity.class);
        method(SpellData.class, "target", LivingEntity.class);
        method(SpellData.class, "target", SpellData.class, LivingEntity.class);
        method(SpellData.class, "location", Location.class);
        method(SpellData.class, "location", SpellData.class, Location.class);
        method(SpellData.class, "power", float.class);
        method(MagicConfig.class, "getSection", ConfigurationSection.class, String.class);
        method(Spell.class, "getInternalName", String.class);
        method(Spell.class, "getValidTargetList", ValidTargetList.class);
        var cast = method(Spell.class, "cast", CastResult.class, SpellData.class);
        if (Modifier.isFinal(cast.getModifiers())) throw new NoSuchMethodException("Spell.cast is final");
        protectedMethod("playSpellEffects", SpellData.class);
        protectedMethod("initialize");
        var turnOff = protectedMethod("turnOff");
        if (Modifier.isFinal(turnOff.getModifiers())) throw new NoSuchMethodException("Spell.turnOff is final");
        method(SpellCastEvent.class, "getCaster", LivingEntity.class);
        method(SpellCastEvent.class, "getSpell", Spell.class);
        method(SpellCastedEvent.class, "getCaster", LivingEntity.class);
        method(SpellCastedEvent.class, "getSpell", Spell.class);
        method(SpellCastedEvent.class, "getSpellCastState", Spell.SpellCastState.class);
        method(SpellCastedEvent.class, "getPostCastAction", Spell.PostCastAction.class);
        method(BuffEndEvent.class, "getTarget", LivingEntity.class);
        method(BuffEndEvent.class, "getBuffSpell", BuffSpell.class);
        staticMethod(MagicSpellsLoadingEvent.class, "getHandlerList", HandlerList.class);
        staticMethod(MagicSpellsLoadedEvent.class, "getHandlerList", HandlerList.class);
        Subspell.class.getConstructor(String.class);
        method(Subspell.class, "process", boolean.class);
        method(Subspell.class, "getSpell", Spell.class);
        method(Subspell.class, "isTargetedEntityFromLocationSpell", boolean.class);
        method(Subspell.class, "subcast", Spell.SpellCastResult.class, SpellData.class, boolean.class, boolean.class);
        method(TargetedEntityFromLocationSpell.class, "castAtEntityFromLocation", CastResult.class, SpellData.class);
        method(TargetedEntitySpell.class, "castAtEntity", CastResult.class, SpellData.class);
        SpellTargetEvent.class.getConstructor(Spell.class, SpellData.class);
        method(SpellTargetEvent.class, "getSpellData", SpellData.class);
        SpellApplyDamageEvent.class.getConstructor(Spell.class, LivingEntity.class, LivingEntity.class,
                double.class, DamageType.class, String.class);
        method(SpellApplyDamageEvent.class, "getFinalDamage", double.class);
        method(ParticleProjectileHitEvent.class, "getTarget", LivingEntity.class);
        method(ParticleProjectileHitEvent.class, "getTracker", ParticleProjectileTracker.class);
        method(ParticleProjectileTracker.class, "getEntitySpell", Subspell.class);
        method(ParticleProjectileTracker.class, "getCasterSpell", Subspell.class);
        method(ParticleProjectileTracker.class, "getStartLocation", Location.class);
        method(ParticleProjectileTracker.class, "getPreviousLocation", Location.class);
        method(ParticleProjectileTracker.class, "getCurrentLocation", Location.class);
        method(ParticleProjectileTracker.class, "getCurrentVelocity", Vector.class);
        method(ParticleProjectileTracker.class, "getHitBox", BoundingBox.class);
        Spell.SpellCastState.valueOf("NORMAL");
        for (String name : List.of("ALREADY_HANDLED", "DELAYED", "HANDLE_NORMALLY")) Spell.PostCastAction.valueOf(name);
    }

    private static java.lang.reflect.Method method(Class<?> owner, String name, Class<?> result, Class<?>... args)
            throws NoSuchMethodException {
        var method = owner.getMethod(name, args);
        if (method.getReturnType() != result) throw new NoSuchMethodException(owner.getName() + "." + name + " has an incompatible return type");
        return method;
    }

    private static void staticMethod(Class<?> owner, String name, Class<?> result, Class<?>... args)
            throws NoSuchMethodException {
        if (!Modifier.isStatic(method(owner, name, result, args).getModifiers())) {
            throw new NoSuchMethodException(owner.getName() + "." + name + " is not static");
        }
    }

    private static java.lang.reflect.Method protectedMethod(String name, Class<?>... args) throws NoSuchMethodException {
        var method = Spell.class.getDeclaredMethod(name, args);
        int modifiers = method.getModifiers();
        if ((!Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers))
                || Modifier.isStatic(modifiers) || method.getReturnType() != void.class) {
            throw new NoSuchMethodException("Spell." + name + " is not an accessible instance method");
        }
        return method;
    }
}
