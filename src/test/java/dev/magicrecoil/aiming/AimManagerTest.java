package dev.magicrecoil.aiming;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.events.SpellCastEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static dev.magicrecoil.aiming.AimSettingsTest.settings;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AimManagerTest {
    private static final NamespacedKey SPEED = new NamespacedKey("magicrecoil", "aim_speed");
    private static final class Fixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final BukkitTask task = mock(BukkitTask.class);
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final ItemStack item = mock(ItemStack.class);
        final AttributeInstance speed = mock(AttributeInstance.class);
        final Map<net.kyori.adventure.key.Key, AttributeModifier> modifiers = new HashMap<>();
        final AimManager manager;
        Fixture() {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(plugin.isEnabled()).thenReturn(true);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);
            UUID id = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true); when(player.getInventory()).thenReturn(inventory);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            when(inventory.getItemInMainHand()).thenReturn(item); when(item.clone()).thenReturn(item);
            when(item.getType()).thenReturn(mock(Material.class)); when(item.isSimilar(item)).thenReturn(true);
            when(speed.getModifier(any(net.kyori.adventure.key.Key.class))).thenAnswer(i -> modifiers.get(i.getArgument(0)));
            doAnswer(i -> { AttributeModifier m = i.getArgument(0); modifiers.put(m.getKey(), m); return null; }).when(speed).addTransientModifier(any());
            doAnswer(i -> { modifiers.remove(i.getArgument(0)); return null; }).when(speed).removeModifier(any(net.kyori.adventure.key.Key.class));
            manager = new AimManager(plugin, p -> speed);
        }
        void tick() {
            var capture = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler, atLeastOnce()).runTaskTimer(eq(plugin), capture.capture(), eq(1L), eq(1L));
            capture.getValue().run();
        }
        @Override public void close() { bukkit.close(); }
    }

    @Test void toggleRestoresOnlyOurModifierAndNeverOverwritesBaseSpeed() throws Exception {
        try (var f = new Fixture()) {
            var externalKey = new NamespacedKey("other", "buff");
            var external = new AttributeModifier(externalKey, 0.3, AttributeModifier.Operation.ADD_NUMBER);
            f.modifiers.put(externalKey, external);
            var s = settings("");
            for (int i = 0; i < 10; i++) {
                assertTrue(f.manager.cast(f.player, "aim", s)); f.tick(); f.tick();
                assertEquals(-0.35, f.modifiers.get(SPEED).getAmount(), 1e-9);
                assertEquals(AttributeModifier.Operation.MULTIPLY_SCALAR_1, f.modifiers.get(SPEED).getOperation());
                assertEquals(0.5, f.manager.recoilMultiplier(f.player, "Gun_Recoil"));
                assertEquals(1, f.manager.recoilMultiplier(f.player, "Other_Recoil"));
                f.manager.cast(f.player, "aim", s);
                assertEquals(Map.of(externalKey, external), f.modifiers);
                assertEquals(0, f.manager.activePlayers());
            }
            verify(f.speed, never()).setBaseValue(anyDouble());
            verify(f.speed, times(10)).addTransientModifier(any());
        }
    }

    @Test void retainedSlotStateSuspendsOtherWeaponsAndResumesOnlyOriginalSlotAndItem() throws Exception {
        try (var f = new Fixture()) {
            f.manager.cast(f.player, "aim", settings("cancel-on-item-swap: false"));
            f.manager.held(new PlayerItemHeldEvent(f.player, 0, 1));
            assertFalse(f.modifiers.containsKey(SPEED));
            when(f.inventory.getHeldItemSlot()).thenReturn(1); f.tick();
            assertEquals(1, f.manager.activePlayers()); assertEquals(1, f.manager.recoilMultiplier(f.player, "gun_recoil"));
            when(f.inventory.getHeldItemSlot()).thenReturn(0); f.tick();
            assertTrue(f.modifiers.containsKey(SPEED)); assertEquals(0.5, f.manager.recoilMultiplier(f.player, "gun_recoil"));
            when(f.inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class)); f.tick();
            assertFalse(f.modifiers.containsKey(SPEED)); assertEquals(1, f.manager.recoilMultiplier(f.player, "gun_recoil"));
            f.manager.clear(); assertEquals(0, f.manager.activePlayers());
        }
    }

    @Test void slotAndHandCancellationAreIndependent() throws Exception {
        try (var f = new Fixture()) {
            var s = settings("cancel-on-item-swap: false\ncancel-on-hand-swap: true");
            f.manager.cast(f.player, "aim", s);
            f.manager.swap(new PlayerSwapHandItemsEvent(f.player, f.item, f.item));
            assertEquals(0, f.manager.activePlayers()); assertFalse(f.modifiers.containsKey(SPEED));
            f.manager.cast(f.player, "aim", settings("cancel-on-item-swap: true\ncancel-on-hand-swap: false"));
            f.manager.swap(new PlayerSwapHandItemsEvent(f.player, f.item, f.item));
            assertEquals(1, f.manager.activePlayers());
            f.manager.held(new PlayerItemHeldEvent(f.player, 0, 1));
            assertEquals(0, f.manager.activePlayers());
        }
    }

    @Test void nonToggleIsIdempotentAndDifferentAimReplacesWithoutStacking() throws Exception {
        try (var f = new Fixture()) {
            var s = settings("toggle: false");
            f.manager.cast(f.player, "a", s); f.manager.cast(f.player, "a", s);
            verify(f.speed).addTransientModifier(any()); assertEquals(1, f.manager.activePlayers());
            f.manager.cast(f.player, "b", settings("movement-speed-multiplier: 0.3\nrecoil-multiplier: 0.2"));
            assertEquals(-0.7, f.modifiers.get(SPEED).getAmount(), 1e-9);
            f.manager.stopSpell("a"); assertEquals(1, f.manager.activePlayers());
            f.manager.stopSpell("b"); assertEquals(0, f.manager.activePlayers());
        }
    }

    @Test void reloadAttemptStopsAimEvenIfCastWasCancelledAndOfflineStateIsRemoved() throws Exception {
        try (var f = new Fixture()) {
            var s = settings("stop-on-spells: [Reload]"); f.manager.cast(f.player, "aim", s);
            var spell = mock(Spell.class); when(spell.getInternalName()).thenReturn("Reload");
            var event = mock(SpellCastEvent.class); when(event.getCaster()).thenReturn(f.player); when(event.getSpell()).thenReturn(spell);
            when(event.isCancelled()).thenReturn(true); f.manager.attempting(event);
            assertEquals(0, f.manager.activePlayers()); assertFalse(f.modifiers.containsKey(SPEED));
            f.manager.cast(f.player, "aim", s); when(f.player.isOnline()).thenReturn(false); f.tick();
            assertEquals(0, f.manager.activePlayers()); assertFalse(f.modifiers.containsKey(SPEED));
        }
    }

    @Test void disabledAndAsyncCallsCannotStartAim() throws Exception {
        try (var f = new Fixture()) {
            assertFalse(f.manager.cast(f.player, "aim", settings("enabled: false")));
            f.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> f.manager.cast(f.player, "aim", settings("")));
            assertEquals(0, f.manager.activePlayers());
        }
    }

    @Test void differentPlayersShareOneTaskButKeepTheirOwnModifiersAndSettings() throws Exception {
        try (var f = new Fixture()) {
            var other = mock(Player.class); var id = UUID.randomUUID();
            when(other.getUniqueId()).thenReturn(id); when(other.isOnline()).thenReturn(true);
            when(other.getInventory()).thenReturn(f.inventory);
            f.bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(other);
            var otherSpeed = mock(AttributeInstance.class);
            var manager = new AimManager(f.plugin, p -> p == f.player ? f.speed : otherSpeed);
            manager.cast(f.player, "gun_a", settings(""));
            manager.cast(other, "gun_b", settings("movement-speed-multiplier: 0.8\nrecoil-multiplier: 0.2"));
            verify(f.scheduler).runTaskTimer(eq(f.plugin), any(Runnable.class), eq(1L), eq(1L));
            var added = ArgumentCaptor.forClass(AttributeModifier.class);
            verify(otherSpeed).addTransientModifier(added.capture());
            assertEquals(-0.2, added.getValue().getAmount(), 1e-9);
            assertEquals(0.5, manager.recoilMultiplier(f.player, "gun_recoil"));
            assertEquals(0.2, manager.recoilMultiplier(other, "gun_recoil"));
            manager.stop(f.player.getUniqueId());
            assertEquals(1, manager.activePlayers());
            assertEquals(0.2, manager.recoilMultiplier(other, "gun_recoil"));
            verify(otherSpeed, never()).removeModifier(any(net.kyori.adventure.key.Key.class));
            manager.clear(); assertEquals(0, manager.activePlayers()); verify(f.task).cancel();
        }
    }
    @Test void optionalZoomFollowsItemOwnershipAndIsRestoredOnStop() throws Exception {
        try (var f = new Fixture()) {
            var zoom = mock(ZoomAdapter.class);
            var manager = new AimManager(f.plugin, p -> f.speed, zoom);
            manager.cast(f.player, "aim", settings("zoom:\n  enabled: true\n  mode: FOV_EFFECT\n  fov-factor: 0.7"));
            verify(zoom).zoom(f.player, 0.7);
            manager.stop(f.player.getUniqueId()); verify(zoom, atLeastOnce()).clear(f.player);
            assertEquals(0, manager.activePlayers()); assertFalse(f.modifiers.containsKey(SPEED));
        }
    }
}
