package org.example;

import java.util.Random;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;

public class DisasterManager {

    private final World world;
    private final int radius = 5000;
    private final Location spawnLocation;
    private final Plugin plugin;

    public DisasterManager(World world, Plugin plugin) {
        this.plugin = plugin;
        this.world = world;
        
        // Temporary location, will be set properly
        this.spawnLocation = new Location(world, 14, 0, -2);
        
        // Get the chunk location
        Location chunkLocation = new Location(world, 14, 0, -2);
        
        // Use region scheduler for the specific location
        plugin.getServer().getRegionScheduler().execute(plugin, chunkLocation, () -> {
            if (chunkLocation.getChunk().isLoaded() || chunkLocation.getChunk().load(true)) {
                int y = world.getHighestBlockYAt(14, -2);
                this.spawnLocation.setY(y);
                Bukkit.getLogger().info("Spawn location initialized at Y: " + y);
            } else {
                Bukkit.getLogger().warning("Failed to load chunk for spawn location initialization!");
            }
        });
    }

    public void triggerDisaster(Location commandLocation) {
        // Define different disasters
        String[] disasters = {
                "Aardbeving",
                "Meteoor",
                "Storm"
        };

        // Pick a random disaster
        String disaster = disasters[new Random().nextInt(disasters.length)];

        // Use command sender's location
        int x = commandLocation.getBlockX();
        int z = commandLocation.getBlockZ();
        
        // Get Y coordinate safely using region scheduler
        plugin.getServer().getRegionScheduler().execute(plugin, commandLocation, () -> {
            int y = world.getHighestBlockYAt(x, z);
            
            // Log coordinates
            Bukkit.getLogger().info("Disaster triggered: " + disaster + " at coordinates: (" + x + ", " + y + ", " + z + ")");
            
            // Show warning and schedule effect
            showWarning(disaster, x, y, z);
            
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> {
                triggerDisasterEffect(disaster, x, y, z);
            }, 20 * 10);
        });
    }

    private void showWarning(String disaster, int x, int y, int z) {
        String warningMessage = getDisasterWarningMessage(disaster);

        // Send a warning to all players 1 minute before the disaster, above the hotbar
        for (Player player : world.getPlayers()) {
            // Get the distance between the player and the spawn
            double distance = player.getLocation().distance(spawnLocation);

            // Adjust intensity based on the distance (closer = stronger effect)
            int intensity = (int) Math.max(0, 100 - (distance / 50));  // Example: Stronger effect closer to the spawn

            // Send warning above hotbar with intensity level (red color)
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(warningMessage + " ⚠️ Intensiteit: " + intensity));
        }
    }

    private String getDisasterWarningMessage(String disaster) {
        switch (disaster) {
            case "Aardbeving":
                return "⚠️ De grond begint te beven...";
            case "Meteoor":
                return "☄️ Iets nadert vanuit de lucht...";
            case "Storm":
                return "🌪️ Er hangt iets gevaarlijks in de lucht...";
            default:
                return "Een ramp is op komst!";
        }
    }

    private void triggerDisasterEffect(String disaster, int x, int y, int z) {
        // Loop through all players and apply disaster effects based on proximity to spawn
        for (Player player : world.getPlayers()) {
            double distance = player.getLocation().distance(spawnLocation);
            int intensity = (int) Math.max(0, 100 - (distance / 50));

            switch (disaster) {
                case "Aardbeving":
                    triggerEarthquake(player, x, y, z, intensity);
                    break;
                case "Meteoor":
                    triggerMeteor(player, x, y, z, intensity);
                    break;
                case "Storm":
                    triggerStorm(player, x, y, z, intensity);
                    break;
            }
        }
    }

    private void triggerEarthquake(Player player, int x, int y, int z, int intensity) {
        Location epicenter = new Location(world, x, y, z);
        Location playerLoc = player.getLocation();
        double distance = playerLoc.distance(epicenter);

        // Bereken volume op basis van afstand (tot max 100 blocks afstand)
        float volume = (float) Math.max(0, 1 - (distance / 100));

        // Blokpuin
        BlockData stoneData = Material.STONE.createBlockData();
        player.spawnParticle(Particle.BLOCK_CRUMBLE, playerLoc, 100, 1.5, 1.5, 1.5, 0.1, stoneData);

        // Stof
        BlockData dirtData = Material.DIRT.createBlockData();
        player.spawnParticle(Particle.FALLING_DUST, playerLoc, 80, 1.5, 1.5, 1.5, 0.05, dirtData);

        // Kleine schokken
        player.spawnParticle(Particle.EXPLOSION, playerLoc, 20, 1, 0.5, 1, 0.01);

        // Grote explosie bij hoge intensiteit
        if (intensity > 60) {
            player.spawnParticle(Particle.EXPLOSION_EMITTER, playerLoc, 3);
        }

        // Geluid met afstandsdemping
        player.playSound(playerLoc, Sound.BLOCK_STONE_BREAK, volume, 0.8f);
        player.playSound(playerLoc, Sound.ENTITY_GENERIC_EXPLODE, volume * 0.6f, 1.2f);

        // Beweging: schud de speler
        Vector shake = new Vector(
                (Math.random() - 0.5) * 0.6,
                0.1,
                (Math.random() - 0.5) * 0.6
        );
        player.setVelocity(player.getVelocity().add(shake));

        // Schade of verwarring bij zeer hoge intensiteit (optioneel)
        if (intensity > 75) {
            plugin.getServer().getRegionScheduler().execute(plugin, player.getLocation(), () -> {
                player.damage(2.0);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            });
        }

        // Actie boven de hotbar (i.p.v. chat)
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "⚠️ De grond trilt hevig onder je voeten!"));

        // Scheuren in de grond (alleen als dichtbij)
        if (distance < 40) {
            createEarthquakeCracks(epicenter, intensity);
        }
    }

    private void createEarthquakeCracks(Location center, int intensity) {
        Random random = new Random();
        int mainFissureLength = intensity * 2;
        double crackWidth = 3.0 + (intensity/20.0);

        // Create main fissure
        createMainFissure(center, mainFissureLength, crackWidth, random);

        // Create secondary cracks
        for(int i = 0; i < 8; i++) {
            Location branchPoint = center.clone().add(
                random.nextInt(mainFissureLength*2) - mainFissureLength,
                0,
                random.nextInt(mainFissureLength*2) - mainFissureLength
            );
            createFissureBranch(branchPoint, random);
        }
    }

    private void createMainFissure(Location start, int length, double width, Random random) {
        Vector direction = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5).normalize();
        
        plugin.getServer().getRegionScheduler().execute(plugin, start, () -> {
            for(int i = 0; i < length; i++) {
                Location segment = start.clone().add(direction.clone().multiply(i));
                
                // Create fissure segment
                for(double dx = -width; dx <= width; dx += 0.5) {
                    for(double dz = -width; dz <= width; dz += 0.5) {
                        if(dx*dx + dz*dz <= width*width) {
                            Location target = segment.clone().add(dx, 0, dz);
                            digFissureColumn(target, random);
                        }
                    }
                }
                
                // Add overhangs
                if(random.nextDouble() < 0.3) {
                    createOverhang(segment.clone().add(0, -1, 0), random);
                }
            }
        });
    }

    private void digFissureColumn(Location surfaceLoc, Random random) {
        plugin.getServer().getRegionScheduler().execute(plugin, surfaceLoc, () -> {
            int surfaceY = world.getHighestBlockYAt(surfaceLoc) - 1;
            for(int y = surfaceY; y >= world.getMinHeight(); y--) {
                Location target = new Location(world, surfaceLoc.getX(), y, surfaceLoc.getZ());
                Block block = target.getBlock();
                
                // Remove all blocks including liquids without exceptions
                block.setType(Material.AIR, false);
            }
        });
    }

    private void createFissureBranch(Location start, Random random) {
        Vector direction = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5).normalize();
        int branchLength = 15 + random.nextInt(20);
        double branchWidth = 1.5 + random.nextDouble() * 2;

        for(int i = 0; i < branchLength; i++) {
            Location segment = start.clone().add(direction.clone().multiply(i));
            
            plugin.getServer().getRegionScheduler().execute(plugin, segment, () -> {
                for(double dx = -branchWidth; dx <= branchWidth; dx += 0.5) {
                    for(double dz = -branchWidth; dz <= branchWidth; dz += 0.5) {
                        if(dx*dx + dz*dz <= branchWidth*branchWidth) {
                            Location target = segment.clone().add(dx, 0, dz);
                            digFissureColumn(target, random);
                        }
                    }
                }
            });
        }
    }

    private void createOverhang(Location start, Random random) {
        for(int i = 0; i < 3 + random.nextInt(3); i++) {
            Location overhangLoc = start.clone().add(
                random.nextGaussian() * 1.5,
                -i,
                random.nextGaussian() * 1.5
            );
            
            plugin.getServer().getRegionScheduler().execute(plugin, overhangLoc, () -> {
                overhangLoc.getBlock().setType(Material.AIR, false);
            });
        }
    }

    private Material getMaterialForDepth(int y, Random random) {
        double depth = (double)y / world.getMaxHeight();
        if(depth < 0.1) return Material.BEDROCK;
        if(depth < 0.3) return random.nextDouble() < 0.3 ? Material.BASALT : Material.BLACKSTONE;
        if(depth < 0.5) return random.nextDouble() < 0.4 ? Material.DEEPSLATE : Material.TUFF;
        return random.nextDouble() < 0.2 ? Material.STONE : Material.AIR;
    }

    private void triggerMeteor(Player player, int x, int y, int z, int intensity) {
        Location impactPoint = new Location(world, x, y, z);
        Location meteorStart = impactPoint.clone().add(0, 50, 0);
        
        // Folia-compatible meteor animation
        player.getScheduler().execute(plugin, () -> {
            Consumer<Location> meteorStep = new Consumer<Location>() {
                @Override
                public void accept(Location currentPos) {
                    if(currentPos.getY() <= impactPoint.getY()) {
                        createMeteorExplosion(impactPoint);
                        return;
                    }
                    
                    // Visual effects
                    player.spawnParticle(Particle.FLAME, currentPos, 8, 0.2, 0.2, 0.2, 0.02);
                    player.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, currentPos, 3, 0.1, 0.1, 0.1, 0.01);
                    player.playSound(currentPos, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.5f, 0.3f);
                    
                    // Schedule next position
                    Location newPos = currentPos.clone().add(0, -2.5, 0);
                    plugin.getServer().getRegionScheduler().execute(plugin, newPos, () -> this.accept(newPos));
                }
            };
            
            // Start the animation
            meteorStep.accept(meteorStart);
        }, null, 1L);
    }

    private void createMeteorExplosion(Location impactPoint) {
        plugin.getServer().getRegionScheduler().execute(plugin, impactPoint, () -> {
            world.createExplosion(
                impactPoint.getX(),
                impactPoint.getY(),
                impactPoint.getZ(),
                20f,
                true,
                true
            );
            
            // Shockwave effect
            world.getPlayers().forEach(p -> {
                if(p.getLocation().distance(impactPoint) < 50) {
                    p.playSound(impactPoint, Sound.ENTITY_ENDER_DRAGON_DEATH, 2.0f, 0.3f);
                    p.spawnParticle(Particle.EXPLOSION, impactPoint, 100);
                }
            });
        });
    }

    private void triggerStorm(Player player, int x, int y, int z, int intensity) {
        Location center = new Location(world, x, y, z);
        double distance = player.getLocation().distance(center);

        if (distance < intensity) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("§b🌪️ EEN HEVIGE STORM RAAST DOOR HET GEBIED!"));

            // Laat bliksem inslaan in de buurt
            plugin.getServer().getRegionScheduler().execute(plugin, center, () -> {
                world.strikeLightningEffect(center);
            });

            // Geluid en regenachtige sfeer
            player.playSound(player.getLocation(), Sound.WEATHER_RAIN, 1f, 1f);
            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);

            // Voeg particles toe voor drama
            player.spawnParticle(Particle.CLOUD, player.getLocation(), 20, 1, 1, 1, 0.1);

            // Simuleer het effect van de tornado door blokken weg te blazen
            blowAwayNearbyBlocks(center, intensity);
        }
    }

    private void blowAwayNearbyBlocks(Location center, int intensity) {
        int radius = intensity / 10;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location columnLoc = center.clone().add(x, 0, z);
                plugin.getServer().getRegionScheduler().execute(plugin, columnLoc, () -> {
                    int surfaceY = world.getHighestBlockYAt(columnLoc) - 1;
                    for(int y = surfaceY; y >= world.getMinHeight(); y--) {
                        Location target = new Location(world, columnLoc.getX(), y, columnLoc.getZ());
                        target.getBlock().setType(Material.AIR, false);
                    }
                });
            }
        }
    }
}