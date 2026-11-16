package dev.magicrecoil.packet;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaperZoomAdapterTest {
    public interface FixturePlayer extends Player { Handle getHandle(); }
    public interface PacketBase {}
    public static class Abilities {
        private float walkingSpeed = .1f, flyingSpeed = .05f;
        public boolean flying = true, invulnerable = true, mayfly = true;
    }
    public static class Packet implements PacketBase {
        final Abilities abilities;
        public Packet(Abilities abilities) { this.abilities = abilities; }
    }
    public static class Connection {
        Packet last;
        int sends;
        public void send(PacketBase packet) { last = (Packet) packet; sends++; }
    }
    public static class Handle {
        public Connection connection = new Connection();
        final Abilities actual = new Abilities();
        public Abilities getAbilities() { return actual; }
    }

    @Test void packetsCopyFlightFlagsWithoutMutatingServerAbilitiesAndRestoreCurrentState() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var player = mock(FixturePlayer.class);
            var handle = new Handle();
            when(player.getHandle()).thenReturn(handle);
            var adapter = new PaperZoomAdapter(FixturePlayer.class, Packet.class, PacketBase.class);
            adapter.send(player, .3f);
            assertEquals(.1f, handle.actual.walkingSpeed);
            assertNotSame(handle.actual, handle.connection.last.abilities);
            assertEquals(.3f, handle.connection.last.abilities.walkingSpeed);
            assertTrue(handle.connection.last.abilities.flying);
            assertTrue(handle.connection.last.abilities.invulnerable);
            assertEquals(.05f, handle.connection.last.abilities.flyingSpeed);
            handle.actual.walkingSpeed = .12f;
            handle.actual.flying = false;
            adapter.send(player, null);
            assertEquals(.12f, handle.connection.last.abilities.walkingSpeed);
            assertFalse(handle.connection.last.abilities.flying);
            assertEquals(2, handle.connection.sends);
        }
    }

    @Test void disconnectedPlayersAndAsyncCallsCannotSendPackets() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var player = mock(FixturePlayer.class);
            var handle = new Handle();
            handle.connection = null;
            when(player.getHandle()).thenReturn(handle);
            var adapter = new PaperZoomAdapter(FixturePlayer.class, Packet.class, PacketBase.class);
            assertDoesNotThrow(() -> adapter.send(player, .2f));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> adapter.send(player, .2f));
        }
    }
}
