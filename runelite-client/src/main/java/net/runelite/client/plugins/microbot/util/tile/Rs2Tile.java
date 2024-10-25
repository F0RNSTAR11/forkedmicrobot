package net.runelite.client.plugins.microbot.util.tile;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.Direction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.devtools.MovementFlag;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
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

                for (MutablePair<WorldPoint, Integer> dangerousTile : dangerousGraphicsObjectTiles) {
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

        for (WorldPoint walkableTile : getWalkableTilesAroundPlayer(radius)) {
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

            return movementFlags.isEmpty();
        }
        return true;
    }

    public static boolean isWalkable(LocalPoint localPoint) {
        if (localPoint == null)
            return true;

        Client client = Microbot.getClient();
        if (client.getCollisionMaps() != null) {
            int[][] flags = client.getCollisionMaps()[client.getPlane()].getFlags();
            int data = flags[localPoint.getSceneX()][localPoint.getSceneY()];

            Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

            return !movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_FULL)
                    && !movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_FLOOR);
        }
        return true;
    }

    public static List<WorldPoint> getWalkableTilesAroundPlayer(int radius) {
        return getWalkableTilesAroundTile(Rs2Player.getWorldLocation(), radius);
    }

    public static List<WorldPoint> getWalkableTilesAroundTile(WorldPoint point, int radius) {
        List<WorldPoint> worldPoints = new ArrayList<>();
        LocalPoint playerLocalPosition = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), point);

        if (playerLocalPosition == null) return new ArrayList<>();

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

    public static HashMap<WorldPoint, Integer> getReachableTilesFromTile(WorldPoint tile, int distance) {
        var tileDistances = new HashMap<WorldPoint, Integer>();
        tileDistances.put(tile, 0);

        for (int i = 0; i < distance + 1; i++) {
            int dist = i;
            for (var kvp : tileDistances.entrySet().stream().filter(x -> x.getValue() == dist).collect(Collectors.toList())) {
                var point = kvp.getKey();
                LocalPoint localPoint;
                if (Microbot.getClient().isInInstancedRegion()) {
                    var worldPoint = WorldPoint.toLocalInstance(Microbot.getClient(), point).stream().findFirst().get();
                    localPoint = LocalPoint.fromWorld(Microbot.getClient(), worldPoint);
                } else
                    localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), point);

                if (Microbot.getClient().getCollisionMaps() != null && localPoint != null) {
                    int[][] flags = Microbot.getClient().getCollisionMaps()[Microbot.getClient().getPlane()].getFlags();
                    int data = flags[localPoint.getSceneX()][localPoint.getSceneY()];

                    Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

                    if (movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_FULL)
                            || movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_FLOOR)) {
                        tileDistances.remove(point);
                        continue;
                    }

                    if (kvp.getValue() >= distance)
                        continue;

                    if (!movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_EAST))
                        tileDistances.putIfAbsent(point.dx(1), dist + 1);
                    if (!movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_WEST))
                        tileDistances.putIfAbsent(point.dx(-1), dist + 1);
                    if (!movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_NORTH))
                        tileDistances.putIfAbsent(point.dy(1), dist + 1);
                    if (!movementFlags.contains(MovementFlag.BLOCK_MOVEMENT_SOUTH))
                        tileDistances.putIfAbsent(point.dy(-1), dist + 1);
                }
            }
        }

        return tileDistances;
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

    public static boolean isTileReachable(WorldPoint targetPoint) {
        boolean[][] visited = new boolean[104][104];
        int[][] flags = Microbot.getClient().getCollisionMaps()[Microbot.getClient().getPlane()].getFlags();
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        int startX = 0;
        int startY = 0;
        int startPoint = 0;
        if (Microbot.getClient().isInInstancedRegion()) {
            LocalPoint localPoint = Rs2Player.getLocalLocation();
            startX = localPoint.getSceneX();
            startY = localPoint.getSceneY();
            startPoint = (startX << 16) | startY;
        } else {
            startX = playerLoc.getX() - Microbot.getClient().getBaseX();
            startY = playerLoc.getY() - Microbot.getClient().getBaseY();
            startPoint = (startX << 16) | startY;
        }

        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(startPoint);
        visited[startX][startY] = true;

        while (!queue.isEmpty()) {
            int point = queue.poll();
            int x = point >> 16;
            int y = point & 0xFFFF;

            if (isWithinBounds(x, y)) {
                checkAndAddNeighbour(queue, visited, flags, x, y, -1, 0, CollisionDataFlag.BLOCK_MOVEMENT_WEST);
                checkAndAddNeighbour(queue, visited, flags, x, y, 1, 0, CollisionDataFlag.BLOCK_MOVEMENT_EAST);
                checkAndAddNeighbour(queue, visited, flags, x, y, 0, -1, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH);
                checkAndAddNeighbour(queue, visited, flags, x, y, 0, 1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH);
            }
        }

        return isVisited(targetPoint, visited);
    }

    /**
     * Checks if any of the tiles immediately surrounding the given object are walkable.
     * The object is defined by its position (worldPoint) and its size (sizeX x sizeY).
     *
     * @param worldPoint The central point of the object.
     * @param sizeX      The size of the object along the X-axis.
     * @param sizeY      The size of the object along the Y-axis.
     * @return true if any surrounding tile is walkable, false otherwise.
     */
    public static boolean areSurroundingTilesWalkable(WorldPoint worldPoint, int sizeX, int sizeY) {
        int plane = worldPoint.getPlane();

        // Calculate the boundaries of the object
        int minX = worldPoint.getX() - (sizeX - 1) / 2;
        int minY = worldPoint.getY() - (sizeY - 1) / 2;
        int maxX = minX + sizeX - 1;
        int maxY = minY + sizeY - 1;

        // Loop over the tiles surrounding the object
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = minY - 1; y <= maxY + 1; y++) {
                // Skip the tiles that are part of the object itself
                if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
                    continue;
                }

                // Check if the surrounding tile is walkable
                if (isTileReachable(new WorldPoint(x, y, plane))) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isWithinBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < 104 && y < 104;
    }

    private static void checkAndAddNeighbour(ArrayDeque<Integer> queue, boolean[][] visited, int[][] flags, int x, int y, int dx, int dy, int blockMovementFlag) {
        int nx = x + dx;
        int ny = y + dy;

        if (isWithinBounds(nx, ny) && !visited[nx][ny] && (flags[x][y] & blockMovementFlag) == 0 && (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0) {
            queue.add((nx << 16) | ny);
            visited[nx][ny] = true;
        }
    }

    private static boolean isVisited(WorldPoint worldPoint, boolean[][] visited) {
        int baseX = 0;
        int baseY = 0;
        int x = 0;
        int y = 0;
        if (Microbot.getClient().isInInstancedRegion()) {
            LocalPoint localPoint = Rs2Player.getLocalLocation();
            x = localPoint.getSceneX();
            y = localPoint.getSceneY();
        } else {
            baseX = Microbot.getClient().getTopLevelWorldView().getBaseX();
            baseY = Microbot.getClient().getTopLevelWorldView().getBaseY();
            x = worldPoint.getX() - baseX;
            y = worldPoint.getY() - baseY;
        }


        return isWithinBounds(x, y) && visited[x][y];
    }

    /**
     * Gets the neighboring tile in the specified direction from the source tile.
     * <p>
     * This method calculates the neighboring tile based on the given direction
     * (NORTH, SOUTH, EAST, WEST) and returns the corresponding WorldPoint.
     *
     * @param direction The direction in which to find the neighboring tile.
     * @param source    The source tile from which to find the neighboring tile.
     *
     * @return The neighboring tile in the specified direction.
     *
     * @throws IllegalArgumentException if the direction is not one of the expected values.
     */
    private static WorldPoint getNeighbour(Direction direction, WorldPoint source) {
        switch (direction) {
            case NORTH:
                return source.dy(1);
            case SOUTH:
                return source.dy(-1);
            case WEST:
                return source.dx(-1);
            case EAST:
                return source.dx(1);
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * Finds the nearest walkable tile from the given source tile.
     * <p>
     * This method iterates through all possible directions (NORTH, SOUTH, EAST, WEST)
     * from the source tile and checks if the neighboring tile in that direction is walkable.
     * If a walkable tile is found, it is returned.
     *
     * @param source The source tile from which to find the nearest walkable tile.
     *
     * @return The nearest walkable tile, or null if no walkable tile is found.
     */
    public static WorldPoint getNearestWalkableTile(WorldPoint source) {
        for (Direction direction : Direction.values()) {
            WorldPoint neighbour = getNeighbour(direction, source);
            if (neighbour.equals(Rs2Player.getWorldLocation())) continue;
            if (isTileReachable(neighbour)) {
                return neighbour;
            }
        }

        return null;
    }

    public static WorldPoint getNearestWalkableTileWithLineOfSight(WorldPoint source) {
        // check if source is walkable
        if (!tileHasWalls(source)
                && isValidTile(getTile(source.getX(), source.getY()))
                && (isWalkable(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), source.getX(), source.getY())) || isBankBooth(source))) {
            return source;
        }
        //check if neightbours are walkable
        for (Direction direction : Direction.values()) {
            WorldPoint neighbour = getNeighbour(direction, source);
            if (neighbour.equals(Rs2Player.getWorldLocation())) continue;
            if (!tileHasWalls(neighbour)
                    && isValidTile(getTile(neighbour.getX(), neighbour.getY()))
                    && (isWalkable(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), neighbour.getX(), neighbour.getY())) || isBankBooth(neighbour))) {
                return neighbour;
            }
        }

        return null;
    }

    public static boolean tileHasWalls(WorldPoint source) {
        return Rs2GameObject.getWallObjects().stream().filter(x -> x.getWorldLocation().equals(source)).findFirst().orElse(null) != null;
    }

    public static boolean isBankBooth(WorldPoint source) {
        GameObject gameObject = Rs2GameObject.getGameObjects().stream().filter(x -> x.getWorldLocation().equals(source)).findFirst().orElse(null);
        if (gameObject != null) {
            ObjectComposition objectComposition = Rs2GameObject.convertGameObjectToObjectComposition(gameObject);
            return objectComposition != null && objectComposition.getName().equalsIgnoreCase("bank booth");
        }
        return false;
    }

    public static Tile getTile(int x, int y) {
        WorldPoint worldPoint = new WorldPoint(x, y, Microbot.getClient().getPlane());
        if (worldPoint.isInScene(Microbot.getClient())) {
            LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient(), worldPoint);
            if (localPoint == null) return null;
            return Microbot.getClient().getScene().getTiles()[worldPoint.getPlane()][localPoint.getSceneX()][localPoint.getSceneY()];
        }
        return null;
    }

    public static boolean isValidTile(Tile tile) {
        if (tile == null) return false;
        int[][] flags = Microbot.getClient().getCollisionMaps()[Microbot.getClient().getPlane()].getFlags();
        int data = flags[tile.getSceneLocation().getX()][tile.getSceneLocation().getY()];

        Set<MovementFlag> movementFlags = MovementFlag.getSetFlags(data);

        if (movementFlags.isEmpty())
        {
            return true;
        }
        return false;
    }
}
