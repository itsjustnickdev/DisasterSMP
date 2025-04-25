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
        
        int aantalMeteoren = 3 + (intensity/25); // 3-9 bij 30-150
        AtomicInteger count = new AtomicInteger(0);
        
        Consumer<Location> meteorTask = new Consumer<Location>() {
            @Override
            public void accept(Location loc) {
                if(count.getAndIncrement() >= aantalMeteoren) return;
                
                // Maak finale kopie voor de scheduler
                final Location scheduledLoc = loc.clone();
                
                // Genereer nieuwe meteor locatie
                Location meteorLoc = center.clone().add(
                    random.nextGaussian() * 25, 
                    50 + random.nextInt(30), 
                    random.nextGaussian() * 25
                );
                
                // Start meteor animatie voor alle spelers
                createMeteorAnimation(meteorLoc, 6.0f + random.nextFloat() * 2);
                
                // Plan volgende meteor met Folia scheduler
                plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, 
                    task -> this.accept(scheduledLoc), 15);
            }
        };
        
        // Start de eerste meteor
        plugin.getServer().getGlobalRegionScheduler().run(plugin, 
            task -> meteorTask.accept(center));
    }

    private void createMeteorAnimation(Location impactPoint, float power) {
        Location meteorStart = impactPoint.clone().add(0, 50, 0);
        
        // Gebruik globale scheduler in plaats van player-specifieke
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> {
            Consumer<Location> meteorStep = new Consumer<Location>() {
                @Override
                public void accept(Location currentPos) {
                    // Verplaats block check naar de region scheduler
                    plugin.getServer().getRegionScheduler().execute(plugin, currentPos, () -> {
                        Block groundBlock = world.getBlockAt(currentPos.clone().subtract(0, 1, 0));
                        if(groundBlock != null && groundBlock.getType().isSolid()) {
                            createMeteorExplosion(currentPos, power);
                        return;
                    }
                    
                        // Toon particles aan alle spelers in de buurt
                        world.spawnParticle(Particle.FLAME, currentPos, 8, 0.2, 0.2, 0.2, 0.02);
                        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, currentPos, 4, 0.3, 0.3, 0.3, 0.05);
                        world.playSound(currentPos, Sound.ENTITY_FIREWORK_ROCKET_SHOOT, 1.0f, 1.5f);
                        
                        Location newPos = currentPos.clone().add(0, -2, 0);
                        
                        // Stop als we bedrock niveau bereiken
                        if(newPos.getY() <= world.getMinHeight() + 1) {
                            createMeteorExplosion(newPos, power);
                            return;
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
            world.createExplosion(
                impactPoint.getX(),
                impactPoint.getY(),
                impactPoint.getZ(),
                power,
                true,
                true
            );
        });
    }

    public void triggerDisaster(Location loc) {
        Random rand = new Random();
        DisasterType type = DisasterType.values()[rand.nextInt(DisasterType.values().length)];
        int intensity = 30 + rand.nextInt(121); // Genereer intensiteit 30-150
        
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
                finalMessage = "§cDe grond begint te trillen...";
                break;
            case METEOR_SHOWER:
                finalMessage = "§6Vurige objecten naderen de atmosfeer!";
                break;
            case TORNADO:
                int category = Math.min(5, 1 + (intensity/30));
                finalMessage = "§9Categorie " + category + " tornado nadert!";
                break;
            default:
                finalMessage = "§4Onbekende ramp!";
        }
        
            world.getPlayers().forEach(p -> {
            if(p.getLocation().distance(loc) < 100) {
                p.sendTitle("§l⚠ WAARSCHUWING ⚠", finalMessage, 10, 70, 20);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.5f);
            }
        });
    }

    private void triggerTornado(Location center, int intensity) {
        int category = Math.min(5, 1 + (intensity/30));
        int duration = (int)(20 * (30 + (category - 1) * 22.5)); // Category 1:30s - Category 5:120s
        double speed = (30.0 + (intensity - 30) * (50.0/120.0)) / 200.0; // 30-80 blocks per 10 seconds
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

            TornadoTask(int duration, double speed, int intensity) {
                this.maxDuration = duration;
                this.currentSize = baseSize;
                this.movementSpeed = speed;
                this.currentSpeedWrapper = new double[]{speed};
                this.intensity = intensity;
            }

            @Override
            public void run() {
                // Progressie van 0-1 over de eerste 20 seconden
                double progress = Math.min(1.0, ticks/400.0); 
                currentSize = baseSize + (baseSize * 1.5) * progress;
                
                // Snelheid opvoeren naarmate de tornado ouder wordt
                double currentSpeed = movementSpeed * (0.8 + progress*0.4);
                currentSpeedWrapper[0] = currentSpeed;
                
                // Bewegingspatroon met random variatie
                double angle = pathRandom.nextDouble() * Math.PI * 2;
                angle += (pathRandom.nextDouble() - 0.5) * 0.5; // Minder abrupte richtingsverandering
                
                Vector direction = new Vector(Math.cos(angle), 0, Math.sin(angle))
                    .multiply(currentSpeed);
                
                // Hoogtevariatie + geleidelijke stijging
                double verticalMovement = Math.min(ticks/100.0, 1.0) * 0.3;
                Location newCenter = center.clone().add(direction)
                    .add(0, pathRandom.nextDouble() * 2.0 - 1.0 + verticalMovement, 0);
                
                // Effect radius vergroten
                destroyBlocksInRadius(newCenter, currentSize * 2.0);
                affectEntities(newCenter, currentSize);
                spawnTornadoParticles(newCenter, currentSize);
                destroyTreesInPath(newCenter, currentSize);
                
                ticks++;
                if(ticks > maxDuration) return;
            }
            
            private void destroyBlocksInRadius(Location center, double radius) {
                for(int x = (int)-radius; x <= radius; x++) {
                    for(int z = (int)-radius; z <= radius; z++) {
                        if(x*x + z*z < radius*radius) {
                            final int finalX = x;
                            final int finalZ = z;
                            plugin.getServer().getRegionScheduler().execute(plugin, center, () -> {
                                for(int y = 0; y < 15; y++) {
                                    Block b = center.clone().add(finalX, y, finalZ).getBlock();
                                    if(b.getType().isSolid() && !b.getType().toString().contains("BEDROCK")) {
                                        if(Math.random() < 0.3 - (y*0.02)) {
                                            // Create falling block with tornado attraction
                                            org.bukkit.entity.FallingBlock fb = world.spawnFallingBlock(
                                                b.getLocation().add(0.5, 0, 0.5),
                                                b.getBlockData()
                                            );
                                            fb.setDropItem(false);
                                            
                                            // Set velocity towards center with spiral
                                            Vector toCenter = center.toVector().subtract(fb.getLocation().toVector());
                                            double distance = toCenter.length();
                                            double distanceFactor = 1 - (distance / (radius));
                                            fb.setVelocity(
                                                toCenter.normalize().multiply(0.3 * (1 + (1 - distanceFactor)))
                                                .add(new Vector(-toCenter.getZ(), 0, toCenter.getX()).normalize().multiply(4.0 * (1 - distanceFactor)))
                                                .add(new Vector(0, Math.min(3.0 + ((y/15.0) * 6.0), 8.0), 0))
                                                .multiply(0.6 * (0.5 + Math.random() * 0.5))
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
            
            private void spawnTornadoParticles(Location center, double size) {
                int particleMultiplier = 1 + (intensity/20);
                double heightStep = 0.2;
                
                // Modified sound logic with cooldown
                if(ticks % 40 == 0 && ticks < maxDuration) {
                    world.playSound(
                        center, 
                        "csmp.tornado", 
                        2.0f, 
                        0.8f + (float)Math.random()*0.4f
                    );
                }
                
                for(double y = 0; y < maxHeight; y += heightStep) {
                    double ratio = y/maxHeight;
                    double currentRadius = size * (1 + ratio*3.0);
                    
                    // Dynamische rotatie gebaseerd op ticks
                    double angle = y * Math.PI * 0.8 + (ticks * 0.2);
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    
                    Location partLoc = center.clone().add(x, y, z);
                    // Intensere kleurverloop
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, partLoc, 
                        (int)(5 * particleMultiplier * (1 - ratio)), 0.2, 0.2, 0.2, 0,
                        new Particle.DustTransition(
                            org.bukkit.Color.fromRGB(30, 30, 30), 
                            org.bukkit.Color.fromRGB(150, 150, 150), 
                            3.0f));
                    
                    // Draaiende puinwolk
                    if(ticks % 3 == 0) {
                        world.spawnParticle(Particle.CLOUD, partLoc, 
                            (int)(3 * particleMultiplier), 0.6, 0.6, 0.6, 0.15);
                    }
                }
                
                // Basis explosie-effecten
                world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 
                    2 + (intensity/30), 3.0, 3.0, 3.0, 0.5);
            }
            
            private void affectEntities(Location center, double size) {
                double effectRadius = size * 4;
                final double tornadoMaxHeight = maxHeight;
                plugin.getServer().getRegionScheduler().execute(plugin, center, () -> {
                    center.getNearbyEntities(effectRadius, 20, effectRadius).forEach(e -> {
                        Location entityLoc = e.getLocation();
                        plugin.getServer().getRegionScheduler().execute(plugin, entityLoc, () -> {
                            Vector toCenter = center.toVector().subtract(entityLoc.toVector());
                            double distance = toCenter.length();
                            double distanceFactor = 1 - (distance / effectRadius);
                            
                            // Hoogte t.o.v. tornado basis
                            double heightAboveBase = entityLoc.getY() - center.getY();
                            double heightRatio = heightAboveBase / tornadoMaxHeight;
                            boolean shouldEject = heightRatio > 0.5; // Eject bij 50% hoogte

                            if (e instanceof Player) {
                                Player p = (Player) e;
                                
                                // Apply similar forces to non-player entities
                                Vector inwardForce = toCenter.normalize().multiply(0.3 * (1 + (1 - distanceFactor)));
                                Vector tangent = new Vector(-toCenter.getZ(), 0, toCenter.getX()).normalize().multiply(4.0 * (1 - distanceFactor));
                                Vector vertical = new Vector(0, Math.min(3.0 + (heightRatio * 6.0), 8.0), 0);
                                
                                Vector finalForce = inwardForce.add(tangent).add(vertical).multiply(0.6);
                                p.setVelocity(p.getVelocity().multiply(0.8).add(finalForce.multiply(0.2)));
                                
                                if(shouldEject) {
                                    // Krachtige ejectie met curve
                                    Vector horizontalEject = toCenter.normalize().multiply(-4.5)
                                        .rotateAroundY(Math.toRadians(45 * (Math.random() - 0.5)));
                                    Vector verticalEject = new Vector(0, 3.0 + (heightRatio * 3.0), 0);
                                    p.setVelocity(horizontalEject.add(verticalEject));
                                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.3f);
                                    p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation(), 3);
                                }
                                
                                // Schade aanpassen aan hoogte
                                if(ticks % 20 == 0 && heightRatio < 0.4) {
                                    p.damage(1.0); // Meer schade onderin
                                }
                            } else {
                                // Apply similar forces to non-player entities
                                Vector inwardForce = toCenter.normalize().multiply(0.3 * (1 + (1 - distanceFactor)));
                                Vector tangent = new Vector(-toCenter.getZ(), 0, toCenter.getX()).normalize().multiply(4.0 * (1 - distanceFactor));
                                Vector vertical = new Vector(0, Math.min(3.0 + (heightRatio * 6.0), 8.0), 0);
                                
                                Vector finalForce = inwardForce.add(tangent).add(vertical).multiply(0.6);
                                e.setVelocity(e.getVelocity().multiply(0.8).add(finalForce.multiply(0.2)));
                                
                                if(shouldEject) {
                                    Vector horizontalEject = toCenter.normalize().multiply(-4.5)
                                        .rotateAroundY(Math.toRadians(45 * (Math.random() - 0.5)));
                                    Vector verticalEject = new Vector(0, 3.0 + (heightRatio * 3.0), 0);
                                    e.setVelocity(horizontalEject.add(verticalEject));
                                }
                            }
                        });
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

        TornadoTask task = new TornadoTask(duration, speed, intensity);
        ScheduledTask scheduledTask = plugin.getServer().getGlobalRegionScheduler()
            .runAtFixedRate(plugin, t -> {
                task.run();
                if(task.ticks > task.maxDuration) t.cancel();
            }, 1, 1);
    }

    private void triggerEarthquake(Location epicenter, int intensity) {
        AtomicInteger executionCount = new AtomicInteger(0);
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            int count = executionCount.getAndIncrement();
            int radius = 15 + (count * 2);
            
            // Verplaats speleracties naar de juiste region schedulers
            for(Player p : Bukkit.getOnlinePlayers()) {
                if(p.getLocation().distanceSquared(epicenter) > radius*radius) continue;
                
                // Schedule voor elke speler apart
                plugin.getServer().getRegionScheduler().execute(plugin, p.getLocation(), () -> {
                    p.playHurtAnimation(0.5f);
                    p.setVelocity(new Vector(
                        (Math.random()-0.5)*0.3,
                        0.2,
                        (Math.random()-0.5)*0.3
                    ));
                });
            }
            
            // Bestaande blokbrekende code blijft hetzelfde
            for(int i = 0; i < 20; i++) {
                double angle = Math.toRadians(i * 18);
                int x = (int)(Math.cos(angle) * radius);
                int z = (int)(Math.sin(angle) * radius);
                
                // Directe blokmanipulatie ZONDER scheduler voor direct effect
                Location loc = epicenter.clone().add(x, 0, z);
                createEarthquakeCracks(loc, intensity);
            }
            
            if(radius > 35) task.cancel();
        }, 10, 10);
    }

    private void createEarthquakeCracks(Location center, int intensity) {
        Random random = new Random();
        double baseSize = 1.5 + (intensity/40.0); // Kleinere basisbreedte
        int maxDepth = 2 + (intensity/30); // Minder diepe scheuren

        for(int i = 0; i < 10 + (intensity/15); i++) {
            Vector dir = new Vector(random.nextDouble()-0.5, 0, random.nextDouble()-0.5).normalize();
            Location segment = center.clone().add(dir.multiply(i));
            
            // Natuurlijke scheurvorming met noise
            double noise = NoiseGenerator.noise(segment.getX(), segment.getZ());
            double crackWidth = baseSize * (0.7 + noise*0.6); // Meer variatie
            
            for(int dx = -(int)crackWidth; dx <= crackWidth; dx++) {
                for(int dz = -(int)crackWidth; dz <= crackWidth; dz++) {
                    if(dx*dx + dz*dz > crackWidth*crackWidth) continue;
                    
                    Location targetLoc = segment.clone().add(dx, 0, dz);
                    final int finalDx = dx;
                    final int finalDz = dz;
                    
                    plugin.getServer().getRegionScheduler().execute(plugin, targetLoc, () -> {
                        try {
                            int surfaceY = world.getHighestBlockYAt(targetLoc);
                            Block surfaceBlock = world.getBlockAt(targetLoc.getBlockX(), surfaceY, targetLoc.getBlockZ());
                            
                            // Alleen breken als het een solide blok is
                            if(surfaceBlock.getType().isSolid() && !surfaceBlock.getType().name().contains("BEDROCK")) {
                                // Breek 1-3 blokken diep
                                for(int y = surfaceY; y > surfaceY - maxDepth && y > world.getMinHeight(); y--) {
                                    Block target = world.getBlockAt(targetLoc.getBlockX(), y, targetLoc.getBlockZ());
                                    if(target.getType().isSolid()) {
                                        target.setType(Material.AIR, false);
                                        
                                        // 50% kans op scheur in de grond
                                        if(y == surfaceY && random.nextBoolean()) {
                                            world.getBlockAt(targetLoc.getBlockX(), y-1, targetLoc.getBlockZ())
                                                .setType(Material.CRACKED_STONE_BRICKS, false);
                                        }
                                    }
                                }
                                
                                // Voeg scheurparticles toe
                                world.spawnParticle(Particle.BLOCK, surfaceBlock.getLocation().add(0.5, 0.1, 0.5), 
                                    10, 0.3, 0.1, 0.3, 0.05, surfaceBlock.getBlockData());
                            }
                            
                            // Update water en lava flow
                            Block liquidCheck = surfaceBlock.getRelative(BlockFace.DOWN);
                            if(liquidCheck.isLiquid()) {
                                liquidCheck.getState().update(true, false);
                            }
                            
                            // Vernietig planten en bloemen
                            Block vegetation = surfaceBlock.getRelative(BlockFace.UP);
                            if(vegetation.getType().isAir() || vegetation.getType().toString().contains("FLOWER")) {
                                vegetation.breakNaturally();
                            }
                        } catch(Exception e) {
                            // Negeer ongeladen chunks
                        }
                    });
                }
            }
        }
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