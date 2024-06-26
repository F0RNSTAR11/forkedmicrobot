package net.runelite.client.plugins.microbot.util.tile;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.devtools.MovementFlag;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Rs2Tile {

    @Getter
    public static List<MutablePair<WorldPoint, Integer>> dangerousGraphicsObjectTiles = new ArrayList<>();

    private static ScheduledExecutorService tileExecutor;


    public static void init() {
        if (tileExecutor == null) {
            tileExecutor = Executors.newSingleThreadScheduledExecutor();
            tileExecutor.scheduleWithFixedDelay(() -> {
                if (dangerousGraphicsObjectTiles.isEmpty()) return;

                for (MutablePair<WorldPoint, Integer> dangerousTile: dangerousGraphicsObjectTiles) {
                    dangerousTile.setValue(dangerousTile.getValue() - 600);
                }
                dangerousGraphicsObjectTiles = dangerousGraphicsObjectTiles.stream().filter(x -> x.getValue() > 0).collect(Collectors.toList());
            }, 0, 600, TimeUnit.MILLISECONDS);
        }
    }


    public static void addDangerousGraphicsObjectTile(GraphicsObject graphicsObject, int time) {
        WorldPoint worldPoint;

        if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
            worldPoint = WorldPoint.fromLocalInstance(Microbot.getClient(), graphicsObject.getLocation());
        } else {
            worldPoint = WorldPoint.fromLocal(Microbot.getClient(), graphicsObject.getLocation());
        }

        if (worldPoint == null) return;

        final MutablePair<WorldPoint, Integer> dangerousTile = MutablePair.of(worldPoint, time);

        dangerousGraphicsObjectTiles.add(dangerousTile);

        if (Rs2Player.getWorldLocation().equals(worldPoint)) {
            Microbot.getClientThread().runOnSeperateThread(() -> {
                final WorldPoint safeTile = Rs2Tile.getSafeTile();
                System.out.println(safeTile);
                Rs2Walker.walkFastCanvas(safeTile);
                return true;
            });
        }
    }

    /**
     * Returns a safe tile based on dangerous tiles
     *
     * @return list of safe tile, sorted on the closest tile to the player
     */
    public static List<WorldPoint> getSafeTiles(int radius) {
        List<WorldPoint> safeTiles = new ArrayList<>();

        for (WorldPoint walkableTile: getWalkableTilesAroundPlayer(radius)) {
            boolean isDangerousTile = dangerousGraphicsObjectTiles.stream().anyMatch(x -> x.getKey().equals(walkableTile));
            if (isDangerousTile) continue;
            safeTiles.add(walkableTile);
        }
        return safeTiles;
    }

    public static WorldPoint getSafeTile() {

        List<WorldPoint> safeTiles = getSafeTiles(4)
                .stream()
                .sorted(Comparator.comparingInt(value -> value.distanceTo(Rs2Player.getWorldLocation()))).collect(Collectors.toList());
        if (safeTiles.isEmpty()) return null;

        return safeTiles.get(0);
    }

    public static boolean isWalkable(Tile tile) {
        Client client = Microbot.getClient();
        if (client.getCollisionMaps() != null) {
            int[][] flags = client.getCollisionMaps()[client.getPlane()].getFlags();
            int data = flags[tile.getSceneLocation().getX()][tile.getSceneLocation().getY()];

            Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

            if (movementFlags.isEmpty()) {
                return true;
            }
            return false;
        }
        return true;
    }

    public static boolean isWalkable(LocalPoint localPoint) {
        Client client = Microbot.getClient();
        if (client.getCollisionMaps() != null) {
            int[][] flags = client.getCollisionMaps()[client.getPlane()].getFlags();
            int data = flags[localPoint.getSceneX()][localPoint.getSceneY()];

            Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

            if (movementFlags.isEmpty()) {
                return true;
            }
            return false;
        }
        return true;
    }

    public static List<WorldPoint> getWalkableTilesAroundPlayer(int radius) {
        List<WorldPoint> worldPoints = new ArrayList<>();
        LocalPoint playerLocalPosition = Rs2Player.getLocalLocation();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx == 0 && dy == 0) continue; // Skip the player's current position
                LocalPoint localPoint;
                WorldPoint worldPoint;
                localPoint = new LocalPoint(playerLocalPosition.getX() + (dx * 128), playerLocalPosition.getY() + (dy * 128), -1);
                if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
                    worldPoint = WorldPoint.fromLocalInstance(Microbot.getClient(), localPoint);
                } else {
                    worldPoint = WorldPoint.fromLocal(Microbot.getClient(), localPoint);
                }
                if (!isWalkable(localPoint)) continue;

                worldPoints.add(worldPoint);
            }
        }
        return worldPoints;
    }

    public static List<LocalPoint> getTilesAroundPlayer(int radius) {
        List<LocalPoint> localPoints = new ArrayList<>();
        LocalPoint playerLocalPosition = Rs2Player.getLocalLocation();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx == 0 && dy == 0) continue; // Skip the player's current position
                LocalPoint localPoint;
                WorldPoint worldPoint;
                localPoint = new LocalPoint(playerLocalPosition.getX() + (dx * 128), playerLocalPosition.getY() + (dy * 128), -1);
                localPoints.add(localPoint);
            }
        }
        return localPoints;
    }
}
