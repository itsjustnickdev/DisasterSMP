package org.example;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.block.Block;
import org.bukkit.Bukkit;
import org.bukkit.util.Vector;
import org.bukkit.Material;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.example.NoiseGenerator;
import org.bukkit.block.BlockFace;
import org.bukkit.GameMode;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.ChatColor;
import org.bukkit.block.data.BlockData;

public class DisasterManager {
    private final World world;
    private final Plugin plugin;

    public DisasterManager(World world, Plugin plugin) {
        this.world = world;
        this.plugin = plugin;
    }

    public void triggerMeteorShower(int x, int y, int z, int intensity) {
        Location center = new Location(world, x, y, z);
        Random random = new Random();
        
        // Vergroot het gebied en vertraag de snelheid
        double spreadRadius = 60.0 + (intensity/3.0); // 60-110 blokken radius
        int durationTicks = (20 * 60) + (intensity * 16); // 1200-3600 ticks (1-3 minuten)
        
        AtomicInteger elapsedTicks = new AtomicInteger(0);
        
        Consumer<Location> meteorTask = new Consumer<Location>() {
            @Override
            public void accept(Location loc) {
                if(elapsedTicks.get() >= durationTicks) return;
                
                // Kleinere clusters met meer spreiding
                int clusterSize = 1 + (intensity/60); // 1-3 meteoren per cluster
                for(int i=0; i<clusterSize; i++) {
                    Location meteorLoc = center.clone().add(
                        (random.nextGaussian() * spreadRadius),
                        50 + random.nextInt(40),
                        (random.nextGaussian() * spreadRadius)
                    );
                    createMeteorAnimation(meteorLoc, 6.0f + random.nextFloat() * 5);
                    // Add delay between meteors in same cluster
                    try { Thread.sleep(50); } catch (InterruptedException e) {}
                }
                
                elapsedTicks.getAndAdd(10 + (intensity/50)); // Gelijkmatigere tick progressie
                
                // Grotere interval tussen clusters
                plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, 
                    task -> this.accept(loc), 15 + random.nextInt(15)); // 1.5-3 seconden tussen clusters
            }
        };
        
        plugin.getServer().getGlobalRegionScheduler().run(plugin, 
            task -> meteorTask.accept(center));
    }

    private void createMeteorAnimation(Location impactPoint, float power) {
        Location meteorStart = impactPoint.clone().add(0, 50, 0);
        
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> {
            Consumer<Location> meteorStep = new Consumer<Location>() {
                @Override
                public void accept(Location currentPos) {
                    plugin.getServer().getRegionScheduler().execute(plugin, currentPos, () -> {
                        Block groundBlock = world.getBlockAt(currentPos.clone().subtract(0, 1, 0));
                        if(groundBlock != null && groundBlock.getType().isSolid()) {
                            createMeteorExplosion(currentPos, power);
                            return;
                        }
                        
                        // Vertraagde val snelheid
                        Location newPos = currentPos.clone().add(0, -1, 0); // 1 blok per tick ipv 2
                        
                        if(newPos.getY() <= world.getMinHeight() + 1) {
                            createMeteorExplosion(newPos, power);
                            return;
                        }
                        
                        // Minder intense particles
                        AtomicInteger meteorTicks = new AtomicInteger(0);
                        if(meteorTicks.getAndIncrement() % 2 == 0) {
                            world.spawnParticle(Particle.FLAME, currentPos, 3, 0.1, 0.1, 0.1, 0.01);
                            world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, currentPos, 1, 0.2, 0.2, 0.2, 0.03);
                        }
                        
                        this.accept(newPos);
                    });
                }
            };
            meteorStep.accept(meteorStart);
        }, 1L);
    }

    private void createMeteorExplosion(Location impactPoint, float power) {
        plugin.getServer().getRegionScheduler().execute(plugin, impactPoint, () -> {
            // Add explosion effects and damage
            double radius = 3.0 + (power/3.0);
            
            // 1. Block destruction
            for(int x = -(int)radius; x <= radius; x++) {
                for(int y = -(int)radius; y <= radius; y++) {
                    for(int z = -(int)radius; z <= radius; z++) {
                        if(x*x + y*y + z*z <= radius*radius) {
                            Block b = impactPoint.clone().add(x, y, z).getBlock();
                            if(b.getType().isSolid() && !b.getType().toString().contains("BEDROCK")) {
                                b.setType(Material.AIR);
                            }
                        }
                    }
                }
            }
            
            // 2. Entity damage
            impactPoint.getNearbyEntities(radius*2, radius*2, radius*2).forEach(e -> {
                double distance = e.getLocation().distance(impactPoint);
                double damage = (1 - (distance/(radius*2))) * 10.0;
                e.setFireTicks((int)(damage * 20));
                if(e instanceof Player) {
                    ((Player)e).damage(damage);
                }
            });
            
            // 3. Existing particles
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, impactPoint, 50, 2, 2, 2, 0.2);
            world.createExplosion(impactPoint, (float)radius, true, true);
        });
    }

    public void triggerDisaster(Location loc, DisasterType type) {
        Random rand = new Random();
        int intensity = 30 + rand.nextInt(121); // 30-150 intensity
        
        showWarning(type, loc, intensity);
        
        switch(type) {
            case EARTHQUAKE:
                triggerEarthquake(loc, intensity);
                break;
            case METEOR_SHOWER:
                triggerMeteorShower(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), intensity);
                break;
            case TORNADO:
                triggerTornado(loc, intensity);
                break;
        }
    }

    private void showWarning(DisasterType type, Location loc, int intensity) {
        final String finalMessage;
        switch(type) {
            case EARTHQUAKE:
                double magnitude = 2.0 + (intensity/50.0); // Mw 2.6-5.0
                int mmi = (int)(magnitude*2); // MMI IV-IX
                String intensityDescription;
                if(magnitude < 3.0) {
                    intensityDescription = "Lichte trilling";
                } else if(magnitude < 4.0) {
                    intensityDescription = "Matige aardbeving";
                } else if(magnitude < 5.0) {
                    intensityDescription = "Sterke aardbeving";
                } else {
                    intensityDescription = "Zeer sterke aardbeving";
                }
                finalMessage = "§cAardbeving Mw " + String.format("%.1f", magnitude) +
                    "\n§7" + intensityDescription + " (MMI " + mmi + ")";
                break;
            case METEOR_SHOWER:
                double speedKmps = 11 + (intensity/150.0)*61; // 11-72 km/s (echte range: 11-72 km/s)
                double sizeMeters = 0.1 + (intensity/150.0)*4.9; // 0.1-5m (typische meteoroïden)
                int energy = (int)(Math.pow(sizeMeters,3)*speedKmps/10); // Relatieve kinetische energie
                String meteorType = (intensity > 100) ? "Boliden" : "Meteorieten";
                
                finalMessage = "§6" + meteorType + "storm (" + (int)speedKmps + " km/s)" +
                    "\n§7Gem. grootte: " + String.format("%.1f", sizeMeters) + "m" +
                    "\n§7Energie: ~" + energy + " TNT ton";
                break;
            case TORNADO:
                int efScale = Math.min(5, (intensity - 30) / 24); // EF0-EF5
                int windSpeed = 29 + (int)((intensity - 30) * (61.0/120.0)); // 29-90 m/s
                finalMessage = "§9Tornado EF" + efScale + 
                    "\n§7Windsnelheid: ~" + windSpeed + " m/s";
                break;
            default:
                finalMessage = "§4Onbekende ramp!";
        }
        
        world.getPlayers().forEach(p -> {
            if(p.getLocation().distance(loc) < 100) {
                p.sendTitle("§l⚠ RAMP WAARSCHUWING ⚠", 
                    finalMessage, 
                    10, 70, 20);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.5f);
            }
        });
    }

    private void triggerTornado(Location center, int intensity) {
        int duration = (int)(20 * 300); // 5 minutes base duration
        double speed = 0.3 + (intensity/150.0); // 0.3-1.0 blocks/tick
        double effectRadius = 15 + (intensity/20);
        final double baseSize = 2.0 + (intensity/40.0); // Basis grootte 2-5.75
        final double maxHeight = 35.0 + (intensity/5.0); // Maximale hoogte 35-55

        final class TornadoTask implements Runnable {
            private final int maxDuration;
            private double currentSize;
            private final double movementSpeed;
            private final double[] currentSpeedWrapper;
            private int ticks = 0;
            private final int intensity;
            Random pathRandom = new Random();
            private Location currentCenter;
            private double baseAngle;
            private Vector movementDirection;
            private ScheduledTask globalTask;

            TornadoTask(int duration, double speed, int intensity, Location startCenter) {
                this.maxDuration = duration;
                this.currentSize = baseSize;
                this.movementSpeed = speed;
                this.currentSpeedWrapper = new double[]{speed};
                this.intensity = intensity;
                this.currentCenter = startCenter.clone();
                this.baseAngle = pathRandom.nextDouble() * Math.PI * 2;
                this.movementDirection = new Vector(Math.cos(baseAngle), 0, Math.sin(baseAngle)).normalize();
            }

            public void setScheduledTask(ScheduledTask scheduledTask) {
                this.globalTask = scheduledTask;
            }

            @Override
            public void run() {
                if(ticks >= maxDuration) {
                    plugin.getServer().getRegionScheduler().execute(plugin, currentCenter, () -> {
                        // world.spawnParticle(Particle.EXPLOSION_EMITTER, currentCenter, 5);
                        // world.playSound(currentCenter, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.6f);
                    });
                    if(globalTask != null) globalTask.cancel();
                    return;
                }
                
                // Get precise ground level
                int groundY = world.getHighestBlockYAt(currentCenter) + 5; // Keep 5 blocks above surface
                double maxY = groundY + 10; // Limit height variation

                // Slower movement calculations
                double progress = Math.min(1.0, ticks/800.0);  // Slower progression
                double currentSpeed = Math.min(movementSpeed * (0.5 + progress*0.2), 0.5); // Reduced max speed
                
                // Smoother direction changes
                double angleVariation = (pathRandom.nextDouble() - 0.5) * 0.05; // Reduced from 0.1
                baseAngle += angleVariation;
                
                Vector newDirection = new Vector(Math.cos(baseAngle), 0, Math.sin(baseAngle));
                movementDirection = movementDirection.multiply(0.9).add(newDirection.multiply(0.1)).normalize();
                
                Vector direction = movementDirection.clone().multiply(currentSpeed);
                
                // Update position with constraints
                Location newLocation = currentCenter.clone().add(direction);
                newLocation.setY(Math.min(
                    Math.max(newLocation.getY(), groundY),
                    maxY
                ));

                // Add air resistance simulation
                movementDirection.multiply(0.95); 

                // Ensure we stay within loaded chunks
                if(world.isChunkLoaded(newLocation.getBlockX() >> 4, newLocation.getBlockZ() >> 4)) {
                    currentCenter = newLocation;
                }

                // Execute effects
                destroyBlocksInRadius(currentCenter, currentSize * 2.0);
                affectEntities(currentCenter, currentSize);
                spawnTornadoParticles(currentCenter, currentSize);
                destroyTreesInPath(currentCenter, currentSize);
                
                ticks++;
            }
            
            private void destroyBlocksInRadius(Location center, double radius) {
                int destructionRadius = (int) Math.ceil(radius * 0.9); // Verhoogd van 0.7 naar 0.9
                if(ticks % 2 != 0) return; // Verwerk elke 2 ticks ipv 3
                
                for(int quadrant = 0; quadrant < 4; quadrant++) { // Verhoogd van 3 naar 4 quadranten
                    int minX = (quadrant == 1) ? -destructionRadius : 0;
                    int maxX = (quadrant == 0) ? destructionRadius : 0;
                    int minZ = (quadrant == 2) ? -destructionRadius : 0;
                    int maxZ = destructionRadius;
                    
                    for(int x = minX; x <= maxX; x += 1) { // Check elk blok ipv elke 2
                        for(int z = minZ; z <= maxZ; z += 1) { 
                            if(Math.random() > 0.3) { // 70% kans ipv 50%
                                final int finalX = x;
                                final int finalZ = z;
                                plugin.getServer().getRegionScheduler().execute(plugin, center, () -> {
                                    int groundY = world.getHighestBlockYAt(center.clone().add(finalX, 0, finalZ));
                                    // Verhoogd van 5 naar 8 blokken hoog
                                    for(int y = groundY; y <= groundY + 8; y++) { 
                                        Block b = center.clone().add(finalX, y - center.getY(), finalZ).getBlock();
                                        
                                        if(b.getType().isSolid() && !b.getType().toString().contains("BEDROCK")) {
                                            if(Math.random() > 0.5) { // 50% kans om vallend blok te maken
                                                org.bukkit.entity.FallingBlock fb = world.spawnFallingBlock(
                                                    b.getLocation().add(0.5, 0, 0.5),
                                                    b.getBlockData()
                                                );
                                                fb.setDropItem(false);
                                                
                                                Vector toCenter = center.toVector().subtract(fb.getLocation().toVector());
                                                double distance = toCenter.length();
                                                double distanceFactor = 1 - (distance / radius);
                                                
                                                fb.setVelocity(
                                                    toCenter.normalize().multiply(0.3 * (1 + (1 - distanceFactor)))
                                                    .add(new Vector(-toCenter.getZ(), 0, toCenter.getX()).normalize().multiply(4.0 * (1 - distanceFactor)))
                                                    .add(new Vector(0, Math.min(3.0 + ((y/15.0) * 6.0), 8.0), 0))
                                                    .multiply(0.4)
                                                );
                                                
                                                b.setType(Material.AIR);
                                            }
                                        }
                                    }
                                });
                            }
                        }
                    }
                }
            }
            
            private void spawnTornadoParticles(Location center, double size) {
                // Get precise ground level at current position
                int groundY = world.getHighestBlockYAt(center) + 1; // +1 to prevent particle clipping
                
                // Adjust particle generation parameters
                int particleMultiplier = 8 + (intensity/20);
                double heightStep = 0.3;  // Reduced vertical spacing
                double maxVisibleHeight = 35.0 + (intensity/5.0); // Reduced max height
                
                // Combined smoke parameters
                Particle.DustOptions blackSmoke = new Particle.DustOptions(
                    org.bukkit.Color.fromRGB(30, 30, 30),  // Dark gray
                    1.5f + (intensity/100f)
                );
                
                Particle.DustOptions whiteSmoke = new Particle.DustOptions(
                    org.bukkit.Color.fromRGB(240, 240, 240),  // Off-white
                    1.2f + (intensity/120f)
                );

                // Main particle loop starting from ground level
                for(double yOffset = 0; yOffset < maxVisibleHeight; yOffset += heightStep) {
                    double ratio = yOffset/maxVisibleHeight;
                    double currentRadius = size * (1 + ratio*3.0);
                    
                    // Calculate position relative to ground
                    double angle = yOffset * Math.PI * 0.5 + (ticks * 0.08);
                    Location partLoc = center.clone().add(
                        Math.cos(angle) * currentRadius,
                        groundY + yOffset - center.getY(), // Adjust for ground height
                        Math.sin(angle) * currentRadius
                    );
                    
                    // Ensure particles stay connected to ground
                    if(yOffset < 2.0) { // Force base particles to ground level
                        partLoc.setY(groundY);
                    }
                    
                    // Further reduced white smoke frequency
                    boolean useWhite = Math.sin(yOffset * 0.4 + ticks * 0.08) > 0.2 && ticks % 4 == 0; // Adjusted wave pattern
                    Particle.DustOptions currentDust = useWhite ? whiteSmoke : blackSmoke;
                    
                    // Core particles with reduced white appearance
                    world.spawnParticle(Particle.DUST, partLoc, 
                        (int)(8 * particleMultiplier * (1 - ratio)),
                        0.2, 0.2, 0.2, 0.01, 
                        currentDust);
                    
                    // Additional smoke layers - minimal white clouds
                    if(ticks % 4 == 0) {  // Changed from %3 to %4
                        // Minimal white cloud particles
                        world.spawnParticle(Particle.CLOUD, partLoc,
                            (int)(1.5 * particleMultiplier * (1 - ratio)),  // Reduced from 2
                            0.3, 0.3, 0.3, 0.04);
                        
                        // Maintain black smoke particles
                        world.spawnParticle(Particle.SMOKE, partLoc,
                            (int)(3 * particleMultiplier * (1 - ratio)),
                            0.25, 0.25, 0.25, 0.02);
                    }
                }
                
                // Add dense ground connection effect
                if(ticks % 3 == 0) {
                    world.spawnParticle(Particle.LARGE_SMOKE, center.clone().add(0, groundY, 0),
                        (int)(particleMultiplier * 0.5),
                        1.5, 0.2, 1.5, 0.1);
                }
            }
            
            private void affectEntities(Location center, double size) {
                double effectRadius = Math.max(40, size * 3); // Reduced from 50
                final double tornadoMaxHeight = 50.0; // Lowered from 75
                
                plugin.getServer().getRegionScheduler().execute(plugin, center, () -> {
                    center.getNearbyEntities(effectRadius, 50, effectRadius).forEach(e -> {
                        if (e instanceof Player) {
                            Player p = (Player) e;
                            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                                return;
                            }
                            Location entityLoc = e.getLocation();
                            plugin.getServer().getRegionScheduler().execute(plugin, entityLoc, () -> {
                                Vector toCenter = center.toVector().subtract(entityLoc.toVector());
                                double distance = toCenter.length();
                                double distanceFactor = 1 - (distance / effectRadius);
                                
                                double heightRatio = (entityLoc.getY() - center.getY()) / 50.0;
                                boolean shouldEject = heightRatio > 0.6; // Lower ejection threshold

                                if (e instanceof Player) {
                                    Player player = (Player) e;
                                    
                                    // Reduced forces
                                    Vector inwardForce = toCenter.normalize().multiply(0.6 * (1 + (1 - distanceFactor)));
                                    Vector tangent = new Vector(-toCenter.getZ(), 0, toCenter.getX()).normalize()
                                        .multiply(4.0 * (1 - distanceFactor)); // Reduced from 6.0
                                    Vector vertical = new Vector(0, Math.min(4.0 + (heightRatio * 6.0), 10.0), 0); // Reduced lift
                                    
                                    Vector finalForce = inwardForce.add(tangent).add(vertical).multiply(0.7);
                                    player.setVelocity(player.getVelocity().multiply(0.5).add(finalForce.multiply(0.5)));
                                    
                                    if(shouldEject) {
                                        Vector horizontalEject = toCenter.normalize().multiply(-4.0) // Reduced from -6.0
                                            .rotateAroundY(Math.toRadians(45 * (Math.random() - 0.5)));
                                        Vector verticalEject = new Vector(0, 3.0 + (heightRatio * 3.0), 0);
                                        player.setVelocity(horizontalEject.add(verticalEject));
                                        // Removed particle effect
                                    }
                                    
                                    if(ticks % 10 == 0) { // Reduced damage frequency
                                        player.damage(1.0 + heightRatio);
                                    }
                                } else {
                                    // Enhanced suction for non-player entities
                                    Vector inwardForce = toCenter.normalize().multiply(0.5 * (1 + (1 - distanceFactor))); // Increased from 0.3
                                    Vector tangent = new Vector(-toCenter.getZ(), 0, toCenter.getX()).normalize()
                                        .multiply(5.0 * (1 - distanceFactor)); // Increased from 4.0
                                    Vector vertical = new Vector(0, Math.min(4.0 + (heightRatio * 8.0), 10.0), 0); // Increased lift
                                    
                                    Vector finalForce = inwardForce.add(tangent).add(vertical).multiply(0.7);
                                    e.setVelocity(e.getVelocity().multiply(0.5).add(finalForce.multiply(0.5))); // More force influence
                                    
                                    if(shouldEject) {
                                        Vector horizontalEject = toCenter.normalize().multiply(-3.0) // Reduced from -4.5
                                            .rotateAroundY(Math.toRadians(45 * (Math.random() - 0.5)));
                                        Vector verticalEject = new Vector(0, 2.0 + (heightRatio * 2.0), 0); // Reduced ejection
                                        e.setVelocity(horizontalEject.add(verticalEject));
                                    }
                                }
                            });
                        }
                    });
                });
            }

            private void destroyTreesInPath(Location center, double size) {
                int treeRadius = (int)(size * 3);
                plugin.getServer().getRegionScheduler().execute(plugin, center, () -> {
                    for(int x = -treeRadius; x <= treeRadius; x++) {
                        for(int z = -treeRadius; z <= treeRadius; z++) {
                            Block block = center.clone().add(x, 0, z).getBlock();
                            if(block.getType().toString().contains("LOG")) {
                                block.breakNaturally();
                                // Creëer vallend blok effect
                                world.spawnParticle(Particle.BLOCK, block.getLocation(),
                                    10, 0.4, 0.4, 0.4, 0.1, block.getBlockData());
                            }
                        }
                    }
                });
            }
        }

        TornadoTask task = new TornadoTask(duration, speed, intensity, center);
        ScheduledTask scheduledTask = plugin.getServer().getGlobalRegionScheduler()
            .runAtFixedRate(plugin, t -> {
                task.setScheduledTask(t);
                Location centerCopy = task.currentCenter.clone();
                plugin.getServer().getRegionScheduler().execute(plugin, centerCopy, () -> {
                    task.run();
                });
            }, 1, 1);
    }

    private void triggerEarthquake(Location epicenter, int intensity) {
        Random random = new Random();
        
        // World-based effects instead of player-specific
        world.spawnParticle(Particle.BLOCK_CRUMBLE, epicenter, 100, 25, 0, 25, 0.1, Material.STONE.createBlockData());
        world.spawnParticle(Particle.FALLING_DUST, epicenter, 80, 25, 0, 25, 0.05, Material.DIRT.createBlockData());
        world.playSound(epicenter, Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
        
        // Actual block destruction
        createEarthquakeCracks(epicenter, intensity);
        
        // Affect all nearby players
        epicenter.getNearbyPlayers(100).forEach(p -> {
            // Screen shake parameters
            int shakeDuration = 40 + (intensity * 2); // 40-280 ticks (2-14 seconds)
            float shakeIntensity = 0.08f + (intensity/150f); // 0.08-0.18
            
            // Schedule repeating screen shake
            AtomicInteger shakeCount = new AtomicInteger();
            Consumer<ScheduledTask> shakeTask = t -> {
                plugin.getServer().getRegionScheduler().execute(plugin, p.getLocation(), () -> {
                    if(shakeCount.getAndIncrement() > shakeDuration/5) {
                        t.cancel();
                        return;
                    }
                    
                    // Screen shake effect using damage animation
                    p.playHurtAnimation(0);
                    
                    // Camera movement simulation
                    Vector shakeVector = new Vector(
                        (Math.random() - 0.5) * shakeIntensity,
                        (Math.random() - 0.3) * shakeIntensity/2,
                        (Math.random() - 0.5) * shakeIntensity
                    );
                    p.setVelocity(p.getVelocity().add(shakeVector));
                    
                    // Ground particles at feet
                    p.spawnParticle(Particle.BLOCK_CRUMBLE, 
                        p.getLocation().add(0, 0.1, 0), 5, 
                        0.2, 0, 0.2, 0.05, 
                        Material.DIRT.createBlockData());
                });
            };
            
            plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, shakeTask, 1, 5);

            // Original velocity effects
            p.setVelocity(p.getVelocity().add(new Vector(
                (random.nextDouble() - 0.5) * 0.6,
                0.1,
                (random.nextDouble() - 0.5) * 0.6
            )));
            
            if(intensity > 75) {
                p.damage(2.0);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            }
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                new TextComponent(ChatColor.RED + "⚠️ De grond trilt hevig onder je voeten!"));
        });
    }

    private void createEarthquakeCracks(Location center, int intensity) {
        Random random = new Random();
        int mainFissureLength = intensity * 2;  // Behoud lengte op basis van intensiteit
        double crackWidth = 1.0 + (intensity/100.0);

        // Create only main fissure
        createMainFissure(center, mainFissureLength, crackWidth, random);
    }

    private void createMainFissure(Location start, int length, double width, Random random) {
        Vector direction = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5).normalize();
        
        plugin.getServer().getRegionScheduler().execute(plugin, start, () -> {
            for(int i = 0; i < length; i++) {
                Location segment = start.clone().add(direction.clone().multiply(i));
                
                // Create fissure segment
                for(double dx = -width; dx <= width; dx += 1.0) {
                    for(double dz = -width; dz <= width; dz += 1.0) {
                        if(dx*dx + dz*dz <= width*width) {
                            Location target = segment.clone().add(dx, 0, dz);
                            digFissureColumn(target, random);
                        }
                    }
                }
                
                // Verwijder overhang creatie
            }
        });
    }

    private void digFissureColumn(Location surfaceLoc, Random random) {
        plugin.getServer().getRegionScheduler().execute(plugin, surfaceLoc, () -> {
            int blockX = surfaceLoc.getBlockX();
            int blockZ = surfaceLoc.getBlockZ();
            int surfaceY = world.getHighestBlockYAt(blockX, blockZ);
            
            // Verwijder vegetatie boven het oppervlak
            for(int y = surfaceY + 3; y > surfaceY; y--) {
                Block block = world.getBlockAt(blockX, y, blockZ);
                if(!block.getType().isAir()) block.setType(Material.AIR, true);
            }

            // Realistische vernauwing met meerdere parameters
            int startBreedte = 10;  // 20 blokken breed aan oppervlak
            int minBreedte = 1;     // 2 blokken breed bij bedrock
            int yRange = surfaceY - world.getMinHeight();
            double noiseSeed = random.nextDouble() * 1000;
            
            for(int y = surfaceY; y >= world.getMinHeight(); y--) {
                double progressie = (double)(surfaceY - y) / yRange;
                
                // Combineer verschillende easing functies
                double easedProgress = Math.pow(progressie, 0.5) * 0.7 + 
                                      Math.pow(progressie, 2) * 0.3;
                
                // Voeg Perlin noise toe voor natuurlijke variatie
                double noise = Math.abs(NoiseGenerator.noise(y * 0.1, noiseSeed)) * 1.5;
                
                int basisBreedte = (int) Math.round(startBreedte - (startBreedte - minBreedte) * easedProgress);
                int huidigeBreedte = (int) (basisBreedte * (0.9 + noise * 0.2));
                
                // Creëer onregelmatige randen
                int[][] vorm = {
                    {0,1,1,1,0},
                    {1,1,1,1,1},
                    {1,1,0,1,1},
                    {1,1,1,1,1},
                    {0,1,1,1,0}
                };
                
                for(int dx = -huidigeBreedte; dx <= huidigeBreedte; dx++) {
                    for(int dz = -huidigeBreedte; dz <= huidigeBreedte; dz++) {
                        double afstand = Math.sqrt(dx*dx + dz*dz);
                        double maxAfstand = huidigeBreedte * (0.8 + Math.abs(NoiseGenerator.noise(dx*0.3, dz*0.3)) * 0.4);
                        
                        if(afstand <= maxAfstand) {
                            // 5% kans om een blok te behouden voor ruigere textuur
                            if(random.nextDouble() > 0.05) {
                                Block block = world.getBlockAt(blockX + dx, y, blockZ + dz);
                                if(!block.getType().isAir()) {
                                    if(afstand > maxAfstand * 0.8 && random.nextDouble() < 0.3) {
                                        block.setType(Material.GRAVEL, true);
                                    } else {
                                        block.setType(Material.AIR, true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    enum DisasterType {
        EARTHQUAKE,
        METEOR_SHOWER,
        TORNADO
    }

    private double easeOutQuad(double x) {
        return 1 - (1 - x) * (1 - x);
    }
}