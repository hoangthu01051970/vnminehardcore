package com.vnmine.hardcore.managers;

import java.util.regex.Pattern;

public record PortalDefinition(
    String id,
    String name,
    String worldName,
    int minX,
    int minY,
    int minZ,
    int maxX,
    int maxY,
    int maxZ,
    Destination destination,
    String linkedPortalId,
    boolean enabled
) {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{1,31}");

    public PortalDefinition {
        if (!isValidId(id)) throw new IllegalArgumentException("Invalid portal ID.");
        if (name == null || name.isBlank() || name.length() > 64) {
            throw new IllegalArgumentException("Portal name must be 1-64 characters.");
        }
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("Portal world name is required.");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Portal bounds are inverted.");
        }
        if (linkedPortalId != null && !isValidId(linkedPortalId)) {
            throw new IllegalArgumentException("Invalid linked portal ID.");
        }
    }

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    public boolean contains(String candidateWorld, int x, int y, int z) {
        return worldName.equalsIgnoreCase(candidateWorld)
            && x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public long volume() {
        long x = (long) maxX - minX + 1;
        long y = (long) maxY - minY + 1;
        long z = (long) maxZ - minZ + 1;
        if (x > Long.MAX_VALUE / y) return Long.MAX_VALUE;
        long xy = x * y;
        return xy > Long.MAX_VALUE / z ? Long.MAX_VALUE : xy * z;
    }

    public PortalDefinition withDestination(Destination newDestination) {
        return new PortalDefinition(id, name, worldName, minX, minY, minZ, maxX, maxY, maxZ,
            newDestination, linkedPortalId, enabled);
    }

    public PortalDefinition withLink(String newLinkedPortalId) {
        return new PortalDefinition(id, name, worldName, minX, minY, minZ, maxX, maxY, maxZ,
            destination, newLinkedPortalId, enabled);
    }

    public PortalDefinition withEnabled(boolean newEnabled) {
        return new PortalDefinition(id, name, worldName, minX, minY, minZ, maxX, maxY, maxZ,
            destination, linkedPortalId, newEnabled);
    }

    public record Destination(String worldName, double x, double y, double z, float yaw, float pitch) {
        public Destination {
            if (worldName == null || worldName.isBlank()
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new IllegalArgumentException("Invalid portal destination.");
            }
        }
    }
}
