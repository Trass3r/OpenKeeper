/*
 * Copyright (C) 2014-2024 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenKeeper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenKeeper.  If not, see <http://www.gnu.org/licenses/>.
 */
package toniarts.openkeeper.view.map;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import toniarts.openkeeper.game.component.Health;
import toniarts.openkeeper.game.component.MapTile;
import toniarts.openkeeper.game.component.Owner;
import toniarts.openkeeper.game.listener.MapListener;
import toniarts.openkeeper.game.listener.MapTileChange;
import toniarts.openkeeper.game.listener.MapChangeType;
import toniarts.openkeeper.game.map.AbstractMapTileInformation;
import toniarts.openkeeper.game.map.IMapDataInformation;
import toniarts.openkeeper.game.map.IMapTileInformation;
import toniarts.openkeeper.tools.convert.map.KwdFile;
import toniarts.openkeeper.utils.Point;

/**
 * Contains the map tiles
 */
public abstract class MapTileContainer extends EntityContainer<IMapTileInformation> implements IMapDataInformation<IMapTileInformation> {

    private static final System.Logger logger = System.getLogger(MapTileContainer.class.getName());

    private final int width;
    private final int height;
    private final IMapTileInformation[][] tiles;
    private final List<MapListener> mapListeners;
    private int tilesAdded = 0;
    
    // Fine-grained component listeners for different change types
    private final EntitySet ownerChanges;
    private final EntitySet healthChanges;
    // Note: Gold/Mana changes are intentionally NOT tracked to avoid performance issues
    // from mining operations that don't affect visual appearance

    protected MapTileContainer(EntityData entityData, KwdFile kwdFile) {
        // Only watch MapTile component for structural changes (terrain, room changes)
        super(entityData, MapTile.class);

        this.mapListeners = new ArrayList<>();
        width = kwdFile.getMap().getWidth();
        height = kwdFile.getMap().getHeight();

        // Duplicate the map
        this.tiles = new IMapTileInformation[width][height];
        
        // Set up fine-grained component change tracking
        // Owner changes affect visual ownership overlay
        this.ownerChanges = entityData.getEntities(MapTile.class, Owner.class);
        
        // Health changes affect visual damage appearance  
        this.healthChanges = entityData.getEntities(MapTile.class, Health.class);
    }

    @Override
    protected IMapTileInformation addObject(Entity e) {
        logger.log(Level.TRACE, "MapTileContainer.addObject({0})", e);
        IMapTileInformation result = new MapTileInformation(e);
        Point p = result.getLocation();
        this.tiles[p.x][p.y] = result;

        // Naive completion checker
        tilesAdded++;
        if (tilesAdded == getSize()) {
            onLoadComplete();
        }

        return result;
    }
    
    @Override
    public void start() {
        super.start();
        
        // Initialize component-specific change tracking
        ownerChanges.applyChanges();
        healthChanges.applyChanges();
    }
    
    @Override
    public void stop() {
        // Clean up component change tracking
        ownerChanges.release();
        healthChanges.release();
        
        super.stop();
    }
    
    @Override
    public boolean update() {
        boolean hasChanges = super.update(); // Handle MapTile component changes
        
        // Check for fine-grained component changes
        List<MapTileChange> changes = new ArrayList<>();
        
        // Handle ownership changes (affects visual overlay)
        if (ownerChanges.applyChanges()) {
            for (Entity entity : ownerChanges.getChangedEntities()) {
                IMapTileInformation tile = getObject(entity.getId());
                if (tile != null) {
                    Point location = tile.getLocation();
                    changes.add(new MapTileChange(location, MapChangeType.OWNERSHIP));
                }
            }
            hasChanges = true;
        }
        
        // Handle health changes (affects visual damage appearance)
        if (healthChanges.applyChanges()) {
            for (Entity entity : healthChanges.getChangedEntities()) {
                IMapTileInformation tile = getObject(entity.getId());
                if (tile != null) {
                    Point location = tile.getLocation();
                    changes.add(new MapTileChange(location, MapChangeType.HEALTH));
                }
            }
            hasChanges = true;
        }
        
        // Send fine-grained notifications for component-specific changes
        if (!changes.isEmpty()) {
            notifyTileChanges(changes);
        }
        
        return hasChanges;
    }

    @Override
    protected void updateObjects(Set<Entity> set) {
        if (set.isEmpty()) {
            return;
        }

        logger.log(System.Logger.Level.TRACE, "MapTileContainer.updateObjects({0}) - MapTile structural changes", set.size());

        // This method only handles MapTile component changes (structural: terrain, room changes)
        // Owner and Health changes are handled separately via fine-grained EntitySet tracking
        List<MapTileChange> changes = new ArrayList<>();
        
        for (Entity e : set) {
            IMapTileInformation object = getObject(e.getId());
            if (object == null) {
                logger.log(Level.WARNING, "Update: No matching object for entity:{0}", e);
                continue;
            }
            
            Point location = object.getLocation();
            
            // MapTile changes are structural (terrain, room modifications)
            MapTile mapTile = e.get(MapTile.class);
            MapChangeType changeType;
            if (mapTile != null) {
                // Check if this is a selection/flashing change (frequent, visual-only)
                if ((mapTile.selection != null && !mapTile.selection.isEmpty()) ||
                    (mapTile.flashing != null && !mapTile.flashing.isEmpty())) {
                    changeType = MapChangeType.SELECTION;
                } else if (mapTile.room != null) {
                    changeType = MapChangeType.ROOM_STRUCTURE;
                } else {
                    changeType = MapChangeType.TERRAIN;
                }
            } else {
                changeType = MapChangeType.UNKNOWN;
            }
            
            changes.add(new MapTileChange(location, changeType));
        }

        // Send notifications for structural changes
        if (!changes.isEmpty()) {
            notifyTileChanges(changes);
        }
    }

    private void notifyTileChanges(List<MapTileChange> changes) {
        for (MapListener mapListener : mapListeners) {
            mapListener.onTilesChanged(changes);
        }
    }

    /**
     * Add a listener for tile changes
     *
     * @param listener the listener to add
     */
    public void addMapListener(MapListener listener) {
        if (!mapListeners.contains(listener)) {
            mapListeners.add(listener);
        }
    }

    /**
     * Remove a listener for tile changes
     *
     * @param listener the listener to remove
     */
    public void removeMapListener(MapListener listener) {
        mapListeners.remove(listener);
    }

    @Override
    protected void updateObject(IMapTileInformation object, Entity e) {
        throw new UnsupportedOperationException("Should use the batch method.");
    }

    @Override
    protected void removeObject(IMapTileInformation object, Entity e) {
        logger.log(Level.TRACE, "MapTileContainer.removeObject({0})", e);
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public IMapTileInformation getTile(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return null;
        }

        return this.tiles[x][y];
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public void setTiles(List<IMapTileInformation> mapTiles) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    protected abstract void onLoadComplete();

    /**
     * Single map tile that taps into the entity information
     */
    private static class MapTileInformation extends AbstractMapTileInformation {

        private final Entity entity;

        public MapTileInformation(Entity entity) {
            super(entity.getId());

            this.entity = entity;
        }

        @Override
        protected <T extends EntityComponent> T getEntityComponent(Class<T> type) {
            return entity.get(type);
        }

    }

}
