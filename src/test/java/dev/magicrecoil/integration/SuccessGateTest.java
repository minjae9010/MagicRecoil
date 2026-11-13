package dev.magicrecoil.integration;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SuccessGateTest {
    @Test void reloadStartInvalidatesReceiptAndBlocksFurtherRecoilInThatTick() {
        var gate = new SuccessGate(); var player = UUID.randomUUID(); gate.watch(Set.of("pp"));
        gate.complete(player, "pp", 12, true); gate.block(player, 12);
        assertFalse(gate.consume(player, "pp", 12)); assertTrue(gate.isBlocked(player, 12));
        gate.complete(player, "pp", 12, true); assertFalse(gate.consume(player, "pp", 12));
        gate.complete(player, "pp", 13, true); assertTrue(gate.consume(player, "pp", 13));
        gate.clear(); assertFalse(gate.isBlocked(player, 12));
    }
    @Test void successIsSingleUseSameTickAndSameProjectile() {
        var gate = new SuccessGate(); var player = UUID.randomUUID();
        gate.watch(Set.of("pp")); gate.complete(player, "pp", 1, true);
        assertTrue(gate.consume(player, "pp", 1)); assertFalse(gate.consume(player, "pp", 1));
        gate.complete(player, "pp", 2, true); assertFalse(gate.consume(player, "pp", 3));
        gate.complete(player, "pp", 2, true); assertFalse(gate.consume(player, "another", 2));
    }

    @Test void failureOrNewAttemptCannotReusePreviousSuccess() {
        var gate = new SuccessGate(); var player = UUID.randomUUID(); gate.watch(Set.of("pp"));
        gate.complete(player, "pp", 1, true); gate.attempt(player, "pp");
        assertFalse(gate.consume(player, "pp", 1));
        gate.complete(player, "pp", 1, true); gate.complete(player, "pp", 1, false);
        assertFalse(gate.consume(player, "pp", 1));
    }

    @Test void playersAreIsolatedAndReloadAndLogoutInvalidateReceipts() {
        var gate = new SuccessGate(); var a = UUID.randomUUID(); var b = UUID.randomUUID(); gate.watch(Set.of("pp"));
        gate.complete(a, "pp", 1, true); assertFalse(gate.consume(b, "pp", 1));
        gate.remove(a); assertFalse(gate.consume(a, "pp", 1));
        gate.complete(a, "pp", 1, true); gate.watch(Set.of("pp")); assertFalse(gate.consume(a, "pp", 1));
        gate.complete(a, "unwatched", 1, true); assertFalse(gate.consume(a, "unwatched", 1));
    }
}
