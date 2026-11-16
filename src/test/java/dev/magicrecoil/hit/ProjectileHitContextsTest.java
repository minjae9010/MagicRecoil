package dev.magicrecoil.hit;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileHitContextsTest {
    private final UUID caster = UUID.randomUUID(), target = UUID.randomUUID();

    private ProjectileHitContexts.Context context(double x) {
        return new ProjectileHitContexts.Context(new Location(null, x, 2, 3), new Location(null, 4, 2, 3), new Vector(1, 0, 0), 0.2);
    }

    @Test void launchSnapshotSurvivesMovementAndCannotBeMutatedByCallers() {
        var store = new ProjectileHitContexts();
        var context = context(1);
        store.put(caster, target, 12, context);
        context.origin().setX(100);
        assertEquals(1, store.consume(caster, target, 12).origin().getX());
        assertNull(store.consume(caster, target, 12));
    }

    @Test void simultaneousShotsAreConsumedSeparatelyAndOldOrOtherTargetsAreRejected() {
        var store = new ProjectileHitContexts();
        store.put(caster, target, 10, context(1));
        assertNull(store.consume(caster, UUID.randomUUID(), 10));
        assertEquals(1, store.consume(caster, target, 10).origin().getX());
        store.put(caster, target, 10, context(2));
        assertEquals(2, store.consume(caster, target, 10).origin().getX());
        store.put(caster, target, 10, context(3));
        assertNull(store.consume(caster, target, 11));
        store.put(caster, target, 11, context(4));
        store.clear();
        assertNull(store.consume(caster, target, 11));
    }
}
