package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.DisasterManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Sound;

public class DisasterPlugin extends JavaPlugin implements Listener {

    private DisasterManager disasterManager;

    @Override
    public void onEnable() {
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        World world = Bukkit.getWorlds().get(0);
        
        // Initialize disaster manager with safe delayed setup
        getServer().getGlobalRegionScheduler().runDelayed(this, task -> {
            disasterManager = new DisasterManager(world, this);
            getLogger().info("✅ DisasterSMP Plugin succesvol ingeschakeld.");
        }, 1); // 1 tick delay to ensure world is fully loaded
    }
    @Override
    public void onDisable() {
        getLogger().info("DisasterPlugin disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("disaster")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Alleen spelers kunnen dit commando gebruiken!");
                return true;
            }
            
            Player player = (Player) sender;
            if(!player.hasPermission("disaster.trigger")) {
                player.sendMessage(ChatColor.RED + "§cAlleen admins kunnen dit commando gebruiken!");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return true;
            }
            
            new DisasterMenu(player, disasterManager).open();
            return true;
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof DisasterMenu) {
            event.setCancelled(true);
            
            if (event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();
                ItemStack clicked = event.getCurrentItem();
                
                DisasterMenu menu = (DisasterMenu) event.getInventory().getHolder();
                if (menu.handleClick(clicked)) {
                    player.sendMessage(ChatColor.YELLOW + "Disaster will occur in a random nearby area!");
                    player.closeInventory();
                }
            }
        }
    }
}
