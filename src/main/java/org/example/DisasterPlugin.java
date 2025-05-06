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
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DisasterPlugin extends JavaPlugin implements Listener {

    private DisasterManager disasterManager;
    private final HashMap<UUID, Long> lastUsedTimes = new HashMap<>();
    private static final long COOLDOWN_MILLIS = TimeUnit.HOURS.toMillis(1);

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
            
            // Check permissions and cooldown
            if(!player.hasPermission("disaster.trigger")) {
                player.sendMessage(ChatColor.RED + "§cJe hebt geen toestemming voor dit commando!");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return true;
            }
            
            // Check cooldown unless player has bypass permission
            if(!player.hasPermission("disaster.bypasscooldown")) {
                UUID playerId = player.getUniqueId();
                long currentTime = System.currentTimeMillis();
                
                if(lastUsedTimes.containsKey(playerId)) {
                    long timeDiff = currentTime - lastUsedTimes.get(playerId);
                    if(timeDiff < COOLDOWN_MILLIS) {
                        long remaining = COOLDOWN_MILLIS - timeDiff;
                        String timeLeft = formatMilliseconds(remaining);
                        player.sendMessage(ChatColor.RED + "§cJe moet " + timeLeft + " wachten voordat je dit opnieuw kunt gebruiken!");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                        return true;
                    }
                }
                
                // Update last used time
                lastUsedTimes.put(playerId, currentTime);
            }
            
            new DisasterMenu(player, disasterManager, this).open();
            return true;
        }
        return false;
    }

    private String formatMilliseconds(long millis) {
        return String.format("%02dh %02dm %02ds",
            TimeUnit.MILLISECONDS.toHours(millis),
            TimeUnit.MILLISECONDS.toMinutes(millis) % 60,
            TimeUnit.MILLISECONDS.toSeconds(millis) % 60);
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
                    player.sendMessage(ChatColor.YELLOW + "Ramp vindt plaats in een willekeurig gebied!");
                    player.closeInventory();
                }
            }
        }
    }
}
