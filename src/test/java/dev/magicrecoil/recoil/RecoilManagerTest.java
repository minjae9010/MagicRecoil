package dev.magicrecoil.recoil;

import dev.magicrecoil.packet.RotationAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.UUID;
import static dev.magicrecoil.recoil.RecoilSettingsTest.settings;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecoilManagerTest {
    @Test void invalidAimMultiplierCannotLeaveAnUnscheduledSession() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var manager = new RecoilManager(mock(JavaPlugin.class), mock(RotationAdapter.class));
            var player = mock(Player.class); var config = settings("");
            for (double multiplier : new double[]{-1, 2, Double.NaN}) {
                assertThrows(IllegalArgumentException.class, () -> manager.shoot(player, "gun", config, multiplier));
                assertEquals(0, manager.activePlayers());
            }
        }
    }

    @Test void eachShotUsesCurrentMovementInputAndAimMultiplier() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var plugin = mock(JavaPlugin.class);
            when(plugin.isEnabled()).thenReturn(true);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L)))
                    .thenReturn(mock(BukkitTask.class));
            var player = mock(Player.class);
            var id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            var input = mock(Input.class);
            when(player.getCurrentInput()).thenReturn(input);
            var adapter = mock(RotationAdapter.class);
            when(adapter.rotate(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            var manager = new RecoilManager(plugin, adapter);
            var config = settings("pattern: vertical\nrecoil: 2\nhorizontal: 0.5\nhorizontal-bias: 1\ngrowth: 0\nrecoil-steps: 1");

            manager.shoot(player, "ar", config);
            var tick = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskTimer(eq(plugin), tick.capture(), eq(1L), eq(1L));
            tick.getValue().run();
            manager.shoot(player, "ar", config, 0.5);
            tick.getValue().run();

            when(input.isForward()).thenReturn(true);
            manager.shoot(player, "ar", config);
            tick.getValue().run();
            manager.shoot(player, "ar", config, 0.5);
            tick.getValue().run();

            when(input.isBackward()).thenReturn(true);
            manager.shoot(player, "ar", config);
            tick.getValue().run();

            var delta = ArgumentCaptor.forClass(RotationDelta.class);
            verify(adapter, times(5)).rotate(eq(player), delta.capture());
            assertEquals(List.of(new RotationDelta(1, -2), new RotationDelta(0.5, -1),
                    new RotationDelta(1.5, -3), new RotationDelta(0.75, -1.5), new RotationDelta(1, -2)),
                    delta.getAllValues());
        }
    }

    @Test void playersShareOneTaskAndEachGetsOneRotationPerTick() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var scheduler = mock(BukkitScheduler.class); var task = mock(BukkitTask.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var plugin = mock(JavaPlugin.class); when(plugin.isEnabled()).thenReturn(true);
            var adapter = mock(RotationAdapter.class);
            when(adapter.rotate(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);
            var manager = new RecoilManager(plugin, adapter); var config = settings("recoil-steps: 3");
            for (int i = 0; i < 100; i++) {
                var player = mock(Player.class); var id = UUID.randomUUID();
                when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true);
                bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
                for (int j = 0; j < 10; j++) assertTrue(manager.shoot(player, "ar", config));
            }
            var tick = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler, times(1)).runTaskTimer(eq(plugin), tick.capture(), eq(1L), eq(1L));
            tick.getValue().run();
            verify(adapter, times(100)).rotate(any(), any()); assertEquals(100, manager.activePlayers());
            manager.clear(); assertEquals(0, manager.activePlayers()); verify(task).cancel();
        }
    }

    @Test void weaponSwitchDiscardsOldTailAndResetsPattern() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var scheduler = mock(BukkitScheduler.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var plugin = mock(JavaPlugin.class); when(plugin.isEnabled()).thenReturn(true);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(mock(BukkitTask.class));
            var player = mock(Player.class); var id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            var adapter = mock(RotationAdapter.class); when(adapter.rotate(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            var manager = new RecoilManager(plugin, adapter);
            manager.shoot(player, "ar", settings("recoil: 20\nrecoil-steps: 20"));
            manager.shoot(player, "pistol", settings("recoil: 1\nrecoil-steps: 1"));
            var tick = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskTimer(eq(plugin), tick.capture(), eq(1L), eq(1L)); tick.getValue().run();
            verify(adapter).rotate(player, new RotationDelta(0, -1)); assertEquals(1, manager.activePlayers());
            manager.stop(id); assertEquals(0, manager.activePlayers());
        }
    }
}
