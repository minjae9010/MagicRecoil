package dev.magicrecoil.packet;

import dev.magicrecoil.recoil.RotationDelta;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaperRotationAdapterTest {
    public interface RelativePlayer extends Player { RelativeHandle getHandle(); }
    public interface AbsolutePlayer extends Player { AbsoluteHandle getHandle(); }
    public interface UnsupportedPlayer extends Player { Object getHandle(); }

    public static class BaseHandle {
        public Object connection = new Object();
        float yaw = 10, pitch = -89;
        int calls;
        public float getYRot() { return yaw; }
        public float getXRot() { return pitch; }
    }

    public static class RelativeHandle extends BaseHandle {
        boolean relativeYaw, relativePitch;
        public void forceSetRotation(float yaw, boolean relativeYaw, float pitch, boolean relativePitch) {
            this.relativeYaw = relativeYaw;
            this.relativePitch = relativePitch;
            this.yaw += yaw;
            this.pitch += pitch;
            calls++;
        }
    }

    public static class AbsoluteHandle extends BaseHandle {
        public void forceSetRotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
            calls++;
        }
    }

    @Test void relativePathUsesDeltasAndClampsPitch() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var handle = new RelativeHandle();
            var player = mock(RelativePlayer.class);
            when(player.getHandle()).thenReturn(handle);
            var adapter = new PaperRotationAdapter(RelativePlayer.class, "1.21.11");
            assertTrue(adapter.supportsRelativeRotation());
            assertEquals(new RotationDelta(2, -1), adapter.rotate(player, new RotationDelta(2, -3)));
            assertTrue(handle.relativeYaw && handle.relativePitch);
            assertEquals(12, handle.yaw);
            assertEquals(-90, handle.pitch);
            assertEquals(1, handle.calls);
        }
    }

    @Test void olderPathAddsDeltasToCurrentAimWithoutTeleporting() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var handle = new AbsoluteHandle();
            var player = mock(AbsolutePlayer.class);
            when(player.getHandle()).thenReturn(handle);
            var adapter = new PaperRotationAdapter(AbsolutePlayer.class, "1.21.4");
            assertFalse(adapter.supportsRelativeRotation());
            assertEquals(new RotationDelta(2, -1), adapter.rotate(player, new RotationDelta(2, -3)));
            handle.yaw = 70; // Subsequent recoil must read the player's new aim.
            assertEquals(new RotationDelta(-3, 2), adapter.rotate(player, new RotationDelta(-3, 2)));
            assertEquals(67, handle.yaw);
            assertEquals(-88, handle.pitch);
            verify(player, never()).teleport(any(org.bukkit.Location.class));
            assertEquals(2, handle.calls);
        }
    }

    @Test void boundsImpulseAndSkipsDisconnectedInvalidAndZeroRotations() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var handle = new RelativeHandle();
            handle.pitch = 0;
            var player = mock(RelativePlayer.class);
            when(player.getHandle()).thenReturn(handle);
            var adapter = new PaperRotationAdapter(RelativePlayer.class, "26.2");
            assertEquals(new RotationDelta(45, 0), adapter.rotate(player, new RotationDelta(90, 0)));
            assertEquals(RotationDelta.ZERO, adapter.rotate(player, RotationDelta.ZERO));
            handle.connection = null;
            assertEquals(RotationDelta.ZERO, adapter.rotate(player, new RotationDelta(1, 1)));
            handle.connection = new Object();
            handle.pitch = Float.NaN;
            assertEquals(RotationDelta.ZERO, adapter.rotate(player, new RotationDelta(1, 1)));
            assertEquals(1, handle.calls);
        }
    }

    @Test void refusesAsyncCallsAndUnknownNativeApi() throws Exception {
        var adapter = new PaperRotationAdapter(RelativePlayer.class, "26.3");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> adapter.rotate(mock(RelativePlayer.class), RotationDelta.ZERO));
        }
        assertThrows(ReflectiveOperationException.class, () -> new PaperRotationAdapter(UnsupportedPlayer.class, "future"));
    }

    @Test void wrapsNativeFailuresForManagerToDisablePlugin() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var player = mock(RelativePlayer.class);
            when(player.getHandle()).thenThrow(new IllegalStateException("native failure"));
            var adapter = new PaperRotationAdapter(RelativePlayer.class, "future");
            var error = assertThrows(IllegalStateException.class, () -> adapter.rotate(player, new RotationDelta(1, 1)));
            assertEquals("native failure", error.getCause().getMessage());
        }
    }
}
