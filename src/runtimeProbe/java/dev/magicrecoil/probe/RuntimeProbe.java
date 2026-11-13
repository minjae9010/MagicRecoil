package dev.magicrecoil.probe;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.events.TrackerMoveEvent;
import com.nisovin.magicspells.util.trackers.ParticleProjectileTracker;
import com.nisovin.magicspells.util.trackers.Tracker;
import dev.magicrecoil.MagicRecoilPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Development fixture only: install on an isolated server with MR_PP/check-plugins. */
public final class RuntimeProbe extends JavaPlugin implements Listener {
    private final Map<UUID, Vector> launchDirections = new HashMap<>();
    private final Map<Tracker, Boolean> observed = new WeakHashMap<>();

    @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void cast(SpellCastEvent event) {
        if (event.getCaster() instanceof Player player && event.getSpell().getInternalName().equalsIgnoreCase("MR_PP")) {
            launchDirections.put(player.getUniqueId(), player.getEyeLocation().getDirection());
            getLogger().info("LAUNCH yaw=" + player.getYaw() + " pitch=" + player.getPitch());
        }
    }

    @EventHandler
    public void moved(TrackerMoveEvent event) {
        if (!(event.getTracker() instanceof ParticleProjectileTracker tracker) || observed.put(tracker, true) != null) return;
        var expected = launchDirections.get(tracker.getCaster().getUniqueId());
        if (expected == null) return;
        var direction = tracker.getCurrentVelocity().clone().normalize();
        getLogger().info("PROJECTILE direction=" + direction + " expected=" + expected
                + " distance=" + direction.distance(expected));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("magicrecoil.probe") || args.length != 2) return false;
        var player = Bukkit.getPlayerExact(args[1]);
        if (player == null) return false;
        if (args[0].equals("api")) player.setRotation(player.getYaw(), player.getPitch() - 3);
        if (args[0].equals("protocol")) {
            if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
                sender.sendMessage("ProtocolLib is not enabled.");
                return true;
            }
            var protocol = ProtocolLibrary.getProtocolManager();
            var packet = protocol.createPacket(PacketType.Play.Server.PLAYER_ROTATION);
            packet.getFloat().write(0, 0f).write(1, -3f);
            packet.getBooleans().write(0, true).write(1, true);
            protocol.sendServerPacket(player, packet);
            // Intentionally raw-send only: snapshot demonstrates missing immediate server update.
        }
        var location = player.getLocation();
        sender.sendMessage("PROBE position=" + location.toVector() + " yaw=" + location.getYaw() + " pitch=" + location.getPitch()
                + " active=" + MagicRecoilPlugin.instance().manager().activePlayers());
        return true;
    }
}
