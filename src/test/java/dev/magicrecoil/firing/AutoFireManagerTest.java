package dev.magicrecoil.firing;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.events.SpellCastEvent;
import dev.magicrecoil.spells.AutoFireSpell;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static dev.magicrecoil.firing.AutoFireSettingsTest.settings;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutoFireManagerTest {
    private static final class Fixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final MockedStatic<UseItemSupport> items = mockStatic(UseItemSupport.class);
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final BukkitTask task = mock(BukkitTask.class);
        final Player player = mock(Player.class);
        final UUID id = UUID.randomUUID();
        final ItemStack item = mock(ItemStack.class);
        final AutoFireSpell gun = mock(AutoFireSpell.class);
        final Spell fire = mock(Spell.class);
        final AtomicInteger tick = new AtomicInteger(10);
        final AutoFireManager manager;
        final Runnable runner;
        boolean using;
        Fixture() throws Exception {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getCurrentTick).thenAnswer(i -> tick.get());
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);
            when(plugin.isEnabled()).thenReturn(true); when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            var inventory = mock(PlayerInventory.class); when(player.getInventory()).thenReturn(inventory);
            when(inventory.getItemInMainHand()).thenReturn(item); when(inventory.getContents()).thenReturn(new ItemStack[]{item});
            when(player.hasActiveItem()).thenAnswer(i -> using); when(player.getActiveItem()).thenReturn(item);
            when(player.getActiveItemHand()).thenReturn(EquipmentSlot.HAND);
            when(item.clone()).thenReturn(item); when(item.isSimilar(item)).thenReturn(true);
            items.when(() -> UseItemSupport.marked(item)).thenReturn(true);
            items.when(() -> UseItemSupport.prepare(item)).thenReturn(true);
            when(gun.settings()).thenReturn(settings("input-mode: NATIVE_USE\nstop-on-spells: [Reload]"));
            when(gun.resolve()).thenReturn(true); when(gun.matches(item)).thenReturn(true); when(gun.fireSpell()).thenReturn(fire);
            manager = new AutoFireManager(plugin, p -> true);
            when(gun.hardCast(any())).thenAnswer(i -> {
                manager.start(player, gun);
                return new Spell.SpellCastResult(Spell.SpellCastState.NORMAL, Spell.PostCastAction.HANDLE_NORMALLY);
            });
            manager.configure(List.of(gun));
            var capture = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskTimer(eq(plugin), capture.capture(), eq(1L), eq(1L)); runner = capture.getValue();
        }
        void step() { runner.run(); tick.incrementAndGet(); }
        void click() {
            var event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(player); when(event.getHand()).thenReturn(EquipmentSlot.HAND);
            when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR); when(event.getItem()).thenReturn(item);
            when(event.useItemInHand()).thenReturn(Event.Result.DEFAULT);
            manager.interact(event);
        }
        @Override public void close() { items.close(); bukkit.close(); }
    }
    @Test void singleNativeUseStartsOnceAndFiresAtServerTickIntervals() throws Exception {
        try (var f = new Fixture()) {
            f.step(); verify(f.fire, never()).hardCast(any());
            f.using = true;
            for (int i = 0; i < 6; i++) f.step();
            verify(f.gun).hardCast(any()); verify(f.fire, times(3)).hardCast(any());
            assertEquals(1, f.manager.activePlayers());
            f.manager.released(new PlayerStopUsingItemEvent(f.player, f.item, 6)); f.using = false;
            for (int i = 0; i < 6; i++) f.step();
            verify(f.fire, times(3)).hardCast(any()); assertEquals(0, f.manager.activePlayers());
        }
    }
    @Test void reloadLatchesUntilNativeReleaseInsteadOfRestartingWhenAnEventIsMissing() throws Exception {
        try (var f = new Fixture()) {
            f.using = true; f.step();
            var reload = mock(Spell.class); when(reload.getInternalName()).thenReturn("Reload");
            var event = mock(SpellCastEvent.class); when(event.getCaster()).thenReturn(f.player); when(event.getSpell()).thenReturn(reload);
            f.manager.attempting(event);
            for (int i = 0; i < 8; i++) f.step();
            verify(f.fire).hardCast(any()); assertEquals(0, f.manager.activePlayers());
            f.using = false; f.step(); f.using = true; f.step();
            verify(f.fire, times(2)).hardCast(any());
        }
    }
    @Test void changingItemOrHandStopsAndClearCancelsTheTask() throws Exception {
        try (var f = new Fixture()) {
            f.using = true; f.step(); when(f.player.getActiveItemHand()).thenReturn(EquipmentSlot.OFF_HAND);
            f.step(); assertEquals(0, f.manager.activePlayers()); verify(f.fire).hardCast(any());
            f.manager.clear(); verify(f.task).cancel();
            f.items.verify(() -> UseItemSupport.restore(f.item), atLeastOnce());
        }
    }
    @Test void failedStartCannotLeakASecondAttemptDuringTheSameUse() throws Exception {
        try (var f = new Fixture()) {
            doReturn(new Spell.SpellCastResult(Spell.SpellCastState.CANCELLED, Spell.PostCastAction.ALREADY_HANDLED)).when(f.gun).hardCast(any());
            f.using = true; for (int i = 0; i < 6; i++) f.step();
            verify(f.gun).hardCast(any()); verify(f.fire, never()).hardCast(any()); assertEquals(0, f.manager.activePlayers());
        }
    }
    @Test void delayedTopLevelFireIsRejectedBeforeMagicSpellsCanScheduleIt() throws Exception {
        try (var f = new Fixture()) {
            var event = mock(SpellCastEvent.class); when(event.getSpell()).thenReturn(f.fire);
            when(event.getCaster()).thenReturn(f.player); when(event.getCastTime()).thenReturn(10);
            when(f.fire.hardCast(any())).thenAnswer(i -> { f.manager.rejectCastTime(event); return null; });
            f.using = true; f.step();
            verify(event).setCancelled(true); verify(event).setSpellCastState(Spell.SpellCastState.CANCELLED);
        }
    }
    @Test void repeatedClickTimesOutAfterConfiguredTicksWithoutEditingTheItem() throws Exception {
        try (var f = new Fixture()) {
            when(f.gun.settings()).thenReturn(settings("input-mode: REPEATED_CLICK\nrelease-timeout-ticks: 6"));
            var event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(f.player); when(event.getHand()).thenReturn(EquipmentSlot.HAND);
            when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR); when(event.getItem()).thenReturn(f.item);
            when(event.useItemInHand()).thenReturn(Event.Result.DEFAULT);
            f.manager.interact(event);
            for (int i = 0; i < 12; i++) f.step();
            verify(f.fire, times(3)).hardCast(any()); assertEquals(0, f.manager.activePlayers());
            f.items.verify(() -> UseItemSupport.prepare(f.item), never());
            f.manager.interact(event); f.step(); verify(f.fire, times(4)).hardCast(any());
        }
    }
    @Test void bufferedClickQueuesFiveFutureTicksWithoutChangingTheItemOrBurstingOnDuplicateEvents() throws Exception {
        try (var f = new Fixture()) {
            when(f.gun.settings()).thenReturn(settings("interval-ticks: 1"));
            for (int i = 0; i < 20; i++) f.click();
            f.step(); verify(f.fire, never()).hardCast(any()); // first opportunity is next tick
            for (int i = 0; i < 10; i++) f.step();
            verify(f.fire, times(5)).hardCast(any()); assertEquals(0, f.manager.activePlayers());
            f.items.verify(() -> UseItemSupport.prepare(f.item), never());
        }
    }
    @Test void bufferedRefillsContinueOneShotPerTickAndStopWithinFiveTicksOfTheLastInput() throws Exception {
        try (var f = new Fixture()) {
            when(f.gun.settings()).thenReturn(settings("interval-ticks: 1"));
            f.click(); f.step();
            for (int i = 0; i < 4; i++) { f.step(); f.click(); f.click(); }
            for (int i = 0; i < 8; i++) f.step();
            verify(f.fire, times(9)).hardCast(any()); assertEquals(0, f.manager.activePlayers());
        }
    }
    @Test void bufferedReloadDiscardsFiringButRepeatedInputCannotBypassTheLatch() throws Exception {
        try (var f = new Fixture()) {
            when(f.gun.settings()).thenReturn(settings("interval-ticks: 1\nstop-on-spells: [Reload]"));
            f.click(); f.step(); f.step(); verify(f.fire).hardCast(any());
            f.manager.stop(f.id);
            for (int i = 0; i < 12; i++) { f.click(); f.step(); }
            verify(f.fire).hardCast(any());
            for (int i = 0; i < 7; i++) f.step();
            f.click(); f.step(); f.step(); verify(f.fire, times(2)).hardCast(any());
        }
    }
    @Test void bufferedItemChangeDiscardsPendingOpportunities() throws Exception {
        try (var f = new Fixture()) {
            when(f.gun.settings()).thenReturn(settings("interval-ticks: 1"));
            f.click(); f.step(); f.step();
            when(f.gun.matches(f.item)).thenReturn(false);
            for (int i = 0; i < 8; i++) f.step();
            verify(f.fire).hardCast(any()); assertEquals(0, f.manager.activePlayers());
        }
    }
}
