package dev.magicrecoil.probe;

import com.google.gson.Gson;
import com.nisovin.magicspells.events.SpellApplyDamageEvent;
import dev.magicrecoil.hit.HitGeometry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.Map;

/** Live Player geometry/damage fixture, only included in the optional diagnostic JAR. */
final class PvpHitProbe implements Listener {
    private static final Gson JSON = new Gson();
    private final JavaPlugin plugin;
    private final Map<String, Integer> hooks = new LinkedHashMap<>();
    private final Map<String, Object> damage = new LinkedHashMap<>();
    private String caseId;
    private boolean pvp = true;

    PvpHitProbe(JavaPlugin plugin) { this.plugin = plugin; }

    boolean command(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        switch (args[1]) {
            case "hook" -> {
                hooks.merge(args[2], 1, Integer::sum);
                if (args[2].equals("hit")) damage.put("immediateHealth", player("Target").getHealth());
                return true;
            }
            case "policy" -> {
                pvp = Boolean.parseBoolean(args[2]);
                player("Target").getWorld().setPVP(pvp);
                emit(sender, "policy", Map.of("pvp", pvp));
                return true;
            }
            case "setup" -> {
                if (args.length != 7) return false;
                caseId = args[2];
                hooks.clear(); damage.clear();
                Player target = player("Target");
                Player attacker = player("Attacker");
                String pose = args[3], region = args[4], side = args[5];
                float yaw = Float.parseFloat(args[6]);
                var world = target.getWorld();
                world.setPVP(pvp);
                world.setGameRule(GameRule.NATURAL_REGENERATION, false);
                world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                for (int x = -2; x <= 2; x++) for (int y = 69; y <= 73; y++) for (int z = -2; z <= 2; z++) {
                    world.getBlockAt(x, y, z).setType(pose.equals("swimming") ? Material.WATER : Material.AIR, false);
                }
                attacker.setGameMode(GameMode.CREATIVE);
                attacker.setFlying(true);
                target.setGameMode(GameMode.SURVIVAL);
                target.setGliding(false); target.setSwimming(false); target.setSprinting(false);
                target.setGravity(false);
                target.getInventory().clear();
                target.getActivePotionEffects().forEach(effect -> target.removePotionEffect(effect.getType()));
                target.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
                target.setAbsorptionAmount(0); target.setHealth(20); target.setNoDamageTicks(0);
                target.setFireTicks(0); target.setRemainingAir(300); target.setFoodLevel(10); target.setSaturation(0);
                target.teleport(new Location(world, 0.5, 70, 0.5, yaw, 0));
                target.setVelocity(new Vector());
                if (pose.equals("gliding")) {
                    target.getInventory().setChestplate(new ItemStack(Material.ELYTRA));
                    target.setGliding(true);
                } else if (pose.equals("swimming")) {
                    target.setSprinting(true);
                    target.setSwimming(true);
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    var box = target.getBoundingBox();
                    double y = switch (region) {
                        case "upper" -> box.getMaxY() - box.getHeight() * 0.125;
                        case "body" -> box.getMinY() + box.getHeight() * 0.35;
                        case "eye" -> target.getEyeLocation().getY();
                        default -> throw new IllegalArgumentException("Unknown region " + region);
                    };
                    double angle = Math.toRadians(yaw);
                    Vector front = new Vector(-Math.sin(angle), 0, Math.cos(angle));
                    Vector right = new Vector(-Math.cos(angle), 0, -Math.sin(angle));
                    Vector normal = switch (side) {
                        case "front", "above", "below" -> front;
                        case "back" -> front.clone().multiply(-1);
                        case "left" -> right.clone().multiply(-1);
                        case "right" -> right;
                        default -> throw new IllegalArgumentException("Unknown side " + side);
                    };
                    Vector point = box.getCenter().setY(y).add(normal.clone().multiply(box.getWidthX() / 2));
                    Vector offset = normal.clone().multiply(side.equals("above") || side.equals("below") ? 4 : 6);
                    if (side.equals("above")) offset.setY(6);
                    if (side.equals("below")) offset.setY(-6);
                    Location origin = point.clone().add(offset).toLocation(world);
                    Location feet = origin.clone().subtract(0, attacker.getEyeHeight(), 0);
                    feet.setDirection(point.clone().subtract(origin.toVector()));
                    attacker.teleport(feet);
                    attacker.setFlying(true);
                    var data = snapshot(target);
                    data.put("intendedRegion", region); data.put("intendedSide", side);
                    data.put("aimPoint", vec(point)); data.put("shooterEyes", vec(attacker.getEyeLocation().toVector()));
                    data.put("upperHeadMinY", box.getMaxY() - box.getHeight() * 0.25);
                    data.put("headMinY", HitGeometry.headMinimumY(target.getType(), target.getPose(), box,
                            target.getEyeLocation().getY(), 0.25));
                    data.put("requestedPose", pose);
                    emit(sender, "setup", data);
                }, 8);
                return true;
            }
            case "fire" -> {
                Player target = player("Target");
                target.setNoDamageTicks(0);
                damage.clear(); hooks.clear();
                damage.put("before", target.getHealth());
                damage.put("beforePose", target.getPose().name());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ms cast as MR_PVP_Attacker "
                        + (args[2].equals("particle") ? "MR_PVP_Projectile" : "MR_PVP_Hit"));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    var data = snapshot(target);
                    data.put("hooks", new LinkedHashMap<>(hooks));
                    data.put("damage", new LinkedHashMap<>(damage));
                    data.put("path", args[2]);
                    emit(sender, "result", data);
                }, 12);
                return true;
            }
            default -> { return false; }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void damage(SpellApplyDamageEvent event) {
        if (event.getTarget() instanceof Player target && target.getName().equals("MR_PVP_Target")
                && event.getSpell().getInternalName().equalsIgnoreCase("MR_PVP_Hit")) {
            damage.put("requested", event.getFinalDamage());
            damage.put("attacker", event.getCaster().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void applied(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player target && target.getName().equals("MR_PVP_Target")) {
            damage.put("bukkitDamage", event.getDamage());
            damage.put("bukkitFinalDamage", event.getFinalDamage());
            damage.put("cancelled", event.isCancelled());
        }
    }

    private Player player(String role) {
        Player player = Bukkit.getPlayerExact("MR_PVP_" + role);
        if (player == null) throw new IllegalStateException("Connect both PvP clients first");
        return player;
    }

    private Map<String, Object> snapshot(Player target) {
        var result = new LinkedHashMap<String, Object>();
        var box = target.getBoundingBox();
        result.put("id", caseId); result.put("tick", Bukkit.getCurrentTick());
        result.put("pose", target.getPose().name());
        result.put("swimming", target.isSwimming()); result.put("gliding", target.isGliding());
        result.put("inWater", target.isInWater()); result.put("pvp", target.getWorld().getPVP());
        result.put("naturalRegeneration", target.getWorld().getGameRuleValue(GameRule.NATURAL_REGENERATION));
        result.put("health", target.getHealth()); result.put("eyeY", target.getEyeLocation().getY());
        result.put("yaw", target.getYaw());
        result.put("boxMin", vec(box.getMin())); result.put("boxMax", vec(box.getMax()));
        result.put("boxHeight", box.getHeight());
        return result;
    }

    private double[] vec(Vector vector) { return new double[]{vector.getX(), vector.getY(), vector.getZ()}; }

    private void emit(CommandSender sender, String phase, Map<String, Object> data) {
        var result = new LinkedHashMap<>(data);
        result.put("phase", phase);
        sender.sendMessage("PVP_PROBE " + JSON.toJson(result));
    }
}
