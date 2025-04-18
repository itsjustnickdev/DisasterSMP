package org.example;

public class NoiseGenerator {
    public static double noise(double x, double y) {
        // Eenvoudige 2D pseudo-random noise implementatie
        int n = (int)(x * 57 + y * 131);
        n = (n << 13) ^ n;
        return (1.0 - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0);    
    }
} 