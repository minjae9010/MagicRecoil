package dev.magicrecoil.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Single-use receipts for the latest completed attempt, valid in its server tick only. */
public final class SuccessGate {
    private record Receipt(String spell, int tick) {}
    private final Map<UUID, Receipt> receipts = new HashMap<>();
    private final Map<UUID, Integer> stoppedTicks = new HashMap<>();
    private Set<String> watched = Set.of();

    public void watch(Set<String> spells) { watched = Set.copyOf(spells); clear(); }
    public void attempt(UUID player, String spell) {
        if (watched.contains(spell)) receipts.remove(player);
    }
    public void complete(UUID player, String spell, int tick, boolean success) {
        if (!watched.contains(spell)) return;
        receipts.remove(player);
        if (success && !isBlocked(player, tick)) receipts.put(player, new Receipt(spell, tick));
    }
    public boolean consume(UUID player, String spell, int tick) {
        Receipt receipt = receipts.remove(player);
        return !isBlocked(player, tick) && receipt != null && receipt.tick() == tick && receipt.spell().equals(spell);
    }
    public void block(UUID player, int tick) { receipts.remove(player); stoppedTicks.put(player, tick); }
    public boolean isBlocked(UUID player, int tick) { return Integer.valueOf(tick).equals(stoppedTicks.get(player)); }
    public void remove(UUID player) { receipts.remove(player); stoppedTicks.remove(player); }
    public void clear() { receipts.clear(); stoppedTicks.clear(); }
}
