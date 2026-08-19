package io.tony.zonespawner.zone;

/**
 * A per-species rule for a zone: how many of that species are allowed to
 * live in the zone at once, how often (in seconds) the zone should attempt
 * to spawn a replacement when it is under that cap, and how far (in blocks,
 * horizontally) an individual animal is allowed to wander from the spot it
 * spawned at before it gets nudged back. A leash radius of 0 means
 * unlimited wandering (no containment).
 */
public final class ZoneRule {

    private int amount;
    private int respawnRateSeconds;
    private double leashRadius;

    public ZoneRule(int amount, int respawnRateSeconds, double leashRadius) {
        this.amount = amount;
        this.respawnRateSeconds = respawnRateSeconds;
        this.leashRadius = leashRadius;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getRespawnRateSeconds() {
        return respawnRateSeconds;
    }

    public void setRespawnRateSeconds(int respawnRateSeconds) {
        this.respawnRateSeconds = respawnRateSeconds;
    }

    public double getLeashRadius() {
        return leashRadius;
    }

    public void setLeashRadius(double leashRadius) {
        this.leashRadius = leashRadius;
    }
}
