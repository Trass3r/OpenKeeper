/*
 * Copyright (C) 2014-2019 OpenKeeper
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
package toniarts.openkeeper.game.logic;

import com.jme3.util.SafeArrayList;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import java.util.Collections;
import java.util.Set;
import toniarts.openkeeper.game.component.Decay;
import toniarts.openkeeper.game.component.Health;
import toniarts.openkeeper.game.component.Position;
import toniarts.openkeeper.game.controller.entity.EntityController;
import toniarts.openkeeper.utils.GameTimeCounter;

/**
 * Handles entity decaying
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class DecaySystem extends GameTimeCounter {

    private final EntitySet decayEntities;
    private final EntityData entityData;
    private final SafeArrayList<EntityId> entityIds;

    public DecaySystem(EntityData entityData) {
        this.entityData = entityData;
        entityIds = new SafeArrayList<>(EntityId.class);

        decayEntities = entityData.getEntities(Decay.class, Position.class);
        processAddedEntities(decayEntities);
    }

    @Override
    public void processTick(float tpf) {
        super.processTick(tpf);

        if (decayEntities.applyChanges()) {
            processDeletedEntities(decayEntities.getRemovedEntities());

            processAddedEntities(decayEntities.getAddedEntities());
        }

        // Decay stuff
        for (Entity entity : decayEntities) {
            Decay decay = entity.get(Decay.class);
            if (timeElapsed - decay.startTime >= decay.duration) {
                // Decay
                entityData.removeComponent(entity.getId(), Decay.class);
                decay(entity.getId());
            }
        }
    }

    private void decay(EntityId entityId) {
        Health health = entityData.getComponent(entityId, Health.class);
        if (health != null) {
            EntityController.setDamage(entityData, entityId, health.maxHealth * 2);
        } else {

            // Hmm, just outright remove it
            entityData.removeEntity(entityId);
        }
    }

    private void processAddedEntities(Set<Entity> entities) {
        for (Entity entity : entities) {
            int index = Collections.binarySearch(entityIds, entity.getId());
            entityIds.add(~index, entity.getId());
        }
    }

    private void processDeletedEntities(Set<Entity> entities) {
        for (Entity entity : entities) {
            int index = Collections.binarySearch(entityIds, entity.getId());
            entityIds.remove(index);
        }
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
        decayEntities.release();
        entityIds.clear();
    }

}
