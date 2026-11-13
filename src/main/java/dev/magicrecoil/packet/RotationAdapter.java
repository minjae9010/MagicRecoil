package dev.magicrecoil.packet;

import dev.magicrecoil.recoil.RotationDelta;
import org.bukkit.entity.Player;

/** Version boundary. Implementations must change rotation only, on the main thread. */
public interface RotationAdapter {
    RotationDelta rotate(Player player, RotationDelta requested);
    String name();
}
