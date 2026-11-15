package dev.magicrecoil.probe;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.events.TrackerMoveEvent;
import com.nisovin.magicspells.util.trackers.ParticleProjectileTracker;
import com.nisovin.magicspells.util.trackers.Tracker;
import dev.magicrecoil.MagicRecoilPlugin;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
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
    @EventHandler(priority = EventPriority.LOWEST)
    public void input(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == org.bukkit.Material.BLAZE_ROD) {
            getLogger().info("INPUT tick=" + Bukkit.getCurrentTick() + " action=" + event.getAction() + " hand=" + event.getHand()
                    + " item=" + event.useItemInHand() + " block=" + event.useInteractedBlock());
        }
    }

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
        if (args[0].equals("aim")) {
            var plugin = MagicRecoilPlugin.instance();
            var ammo = com.nisovin.magicspells.MagicSpells.getVariableManager().getVariable("Paimon");
            var slow = player.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
            sender.sendMessage("AIM player=" + player.getName()
                    + " speed=" + player.getAttribute(Attribute.MOVEMENT_SPEED).getValue()
                    + " baseSpeed=" + player.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue()
                    + " serverWalkSpeed=" + player.getWalkSpeed() / 2
                    + " multiplier=" + plugin.aimManager().recoilMultiplier(player, "ADS_Recoil")
                    + " states=" + plugin.aimManager().activePlayers()
                    + " firing=" + plugin.autoFireManager().activePlayers()
                    + " using=" + (player.hasActiveItem() ? 1 : 0)
                    + " prepared=" + (dev.magicrecoil.firing.UseItemSupport.marked(player.getInventory().getItemInMainHand()) ? 1 : 0)
                    + " itemHash=" + player.getInventory().getItemInMainHand().hashCode()
                    + " slowness=" + (slow == null ? -1 : slow.getAmplifier())
                    + " slownessTicks=" + (slow == null ? 0 : slow.getDuration())
                    + " ammo=" + (ammo == null ? -1 : ammo.getValue(player))
                    + " cobblestone=" + java.util.Arrays.stream(player.getInventory().getContents())
                            .filter(item -> item != null && item.getType() == org.bukkit.Material.COBBLESTONE)
                            .mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum()
                    + " yaw=" + player.getYaw() + " pitch=" + player.getPitch());
            return true;
        }
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
