package dev.magicrecoil;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.events.*;
import dev.magicrecoil.integration.SuccessGate;
import dev.magicrecoil.packet.v1_21_11.Paper12111RotationAdapter;
import dev.magicrecoil.recoil.RecoilManager;
import dev.magicrecoil.spells.RecoilSpell;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class MagicRecoilPlugin extends JavaPlugin implements Listener {
    private static MagicRecoilPlugin instance;
    private final SuccessGate successGate = new SuccessGate();
    private RecoilManager manager;
    private final Set<String> stopSpells = new HashSet<>();
    private final Set<String> stopBuffs = new HashSet<>();

    @Override
    public void onLoad() {
        instance = this;
        // All onLoad calls precede plugin enabling. MagicSpells needs our classes during its onEnable.
        registerSpellLoader();
    }

    private void registerSpellLoader() {
        if (!MagicSpells.getClassLoaders().contains(getClassLoader())) MagicSpells.getClassLoaders().add(getClassLoader());
    }

    @Override
    public void onEnable() {
        if (!Bukkit.getMinecraftVersion().equals("1.21.11")) {
            getLogger().severe("Only Paper 1.21.11 is supported. A separate adapter and validation are required for other versions.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!MagicSpells.getInstance().getPluginMeta().getVersion().equals("4.0-Beta-18")) {
            getLogger().severe("MagicSpells 4.0-Beta-18 is required; other releases have not been validated.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        manager = new RecoilManager(this, new Paper12111RotationAdapter());
        getServer().getPluginManager().registerEvents(this, this);
        refreshWatches();
        getLogger().info("Enabled relative rotation for Paper 1.21.11 / MagicSpells 4.0-Beta-18.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.clear();
        successGate.clear();
        MagicSpells.getClassLoaders().remove(getClassLoader());
        instance = null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void loading(MagicSpellsLoadingEvent event) {
        manager.clear();
        successGate.watch(Set.of());
        // unload() clears MagicSpells' class loader list on every /ms reload.
        registerSpellLoader();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void loaded(MagicSpellsLoadedEvent event) { refreshWatches(); }

    private void refreshWatches() {
        stopSpells.clear();
        stopBuffs.clear();
        Set<String> watched = new HashSet<>();
        for (Spell spell : MagicSpells.getSpells().values()) {
            if (!(spell instanceof RecoilSpell recoil)) continue;
            String source = recoil.settings().requireSuccessOf();
            if (!source.isEmpty()) { watched.add(source); checkReference(spell, source); }
            recoil.settings().stopOnSpells().forEach(name -> checkReference(spell, name));
            recoil.settings().stopOnBuffEnd().forEach(name -> checkReference(spell, name));
            stopSpells.addAll(recoil.settings().stopOnSpells());
            stopBuffs.addAll(recoil.settings().stopOnBuffEnd());
            if (source.equalsIgnoreCase(spell.getInternalName())) getLogger().warning(spell.getInternalName() + " requires itself; it will never recoil.");
        }
        successGate.watch(watched);
    }

    private void checkReference(Spell owner, String name) {
        if (MagicSpells.getSpellByInternalName(name) == null) getLogger().warning(owner.getInternalName() + ": referenced spell does not exist: " + name);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void attempting(SpellCastEvent event) {
        if (!(event.getCaster() instanceof Player player) || !Bukkit.isPrimaryThread()) return;
        String spell = normalized(event.getSpell());
        successGate.attempt(player.getUniqueId(), spell);
        if (stopSpells.contains(spell)) successGate.block(player.getUniqueId(), Bukkit.getCurrentTick());
        manager.stopForSpell(player.getUniqueId(), spell, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void casted(SpellCastedEvent event) {
        if (!(event.getCaster() instanceof Player player) || !Bukkit.isPrimaryThread()) return;
        boolean success = event.getSpellCastState() == Spell.SpellCastState.NORMAL
                && event.getPostCastAction() != Spell.PostCastAction.ALREADY_HANDLED
                && event.getPostCastAction() != Spell.PostCastAction.DELAYED;
        successGate.complete(player.getUniqueId(), normalized(event.getSpell()), Bukkit.getCurrentTick(), success);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void buffEnded(BuffEndEvent event) {
        if (event.getTarget() instanceof Player player && Bukkit.isPrimaryThread()) {
            if (stopBuffs.contains(normalized(event.getBuffSpell()))) successGate.block(player.getUniqueId(), Bukkit.getCurrentTick());
            manager.stopForSpell(player.getUniqueId(), normalized(event.getBuffSpell()), true);
        }
    }

    private static String normalized(Spell spell) { return spell.getInternalName().toLowerCase(Locale.ROOT); }

    public void reset(UUID player) { manager.stop(player); successGate.remove(player); }

    @EventHandler public void quit(PlayerQuitEvent event) { reset(event.getPlayer().getUniqueId()); }
    @EventHandler public void death(PlayerDeathEvent event) { reset(event.getEntity().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void held(PlayerItemHeldEvent event) { reset(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent event) { reset(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) { reset(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) { reset(event.getPlayer().getUniqueId()); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { reset(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void inventoryClick(InventoryClickEvent event) { reset(event.getWhoClicked().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void inventoryDrag(InventoryDragEvent event) { reset(event.getWhoClicked().getUniqueId()); }
    @EventHandler public void disabled(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("MagicSpells")) { manager.clear(); successGate.clear(); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("magicrecoil.admin")) { sender.sendMessage("You do not have permission."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("MagicRecoil " + getPluginMeta().getVersion() + " | " + manager.adapterName()
                    + " | active players: " + manager.activePlayers() + " | rotation packets: " + manager.packetsSent());
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            Player player = Bukkit.getPlayerExact(args[1]);
            if (player == null) sender.sendMessage("Player is not online.");
            else { reset(player.getUniqueId()); sender.sendMessage("Recoil cleared for " + player.getName() + '.'); }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("magicrecoil.admin")) return List.of();
        List<String> choices = args.length == 1 ? List.of("status", "reset")
                : args.length == 2 && args[0].equalsIgnoreCase("reset") ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList() : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    public static MagicRecoilPlugin instance() { return instance; }
    public RecoilManager manager() { return manager; }
    public SuccessGate successGate() { return successGate; }
}
