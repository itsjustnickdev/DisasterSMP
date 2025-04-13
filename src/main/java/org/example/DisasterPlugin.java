package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.DisasterManager;

public class DisasterPlugin extends JavaPlugin {

    private DisasterManager disasterManager;

    @Override
    public void onEnable() {
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
            if (sender instanceof Player) {
                Player player = (Player) sender;
                disasterManager.triggerDisaster(player.getLocation());
                sender.sendMessage("Een ramp zal binnenkort plaatsvinden...");
                return true;
            } else {
                sender.sendMessage("Alleen spelers kunnen dit commando uitvoeren.");
            }
        }
        return false;
    }
}
