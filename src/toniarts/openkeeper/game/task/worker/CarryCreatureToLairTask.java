/*
 * Copyright (C) 2014-2016 OpenKeeper
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
package toniarts.openkeeper.game.task.worker;

import com.jme3.math.Vector2f;
import com.simsilica.es.EntityId;
import toniarts.openkeeper.game.controller.IMapController;
import toniarts.openkeeper.game.controller.creature.ICreatureController;
import toniarts.openkeeper.game.navigation.INavigationService;
import toniarts.openkeeper.game.task.AbstractTileTask;
import toniarts.openkeeper.game.task.TaskType;
import toniarts.openkeeper.utils.WorldUtils;

/**
 * A task for creatures to rescue a fellow comrade from the harsh world
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class CarryCreatureToLairTask extends AbstractTileTask {

    private final ICreatureController creature;
    private boolean executed = false;

    public CarryCreatureToLairTask(final INavigationService navigationService, final IMapController mapController, ICreatureController creature, short playerId) {
        super(navigationService, mapController, creature.getLairLocation(), playerId);
        this.creature = creature;
    }

    @Override
    public Vector2f getTarget(ICreatureController creature) {
        return WorldUtils.pointToVector2f(getTaskLocation()); // FIXME 0.5f not needed?
    }

    @Override
    public boolean isValid(ICreatureController creature) {
        return !executed && this.creature.hasLair() && this.creature.isDragged() && !this.creature.isDead();
    }

    @Override
    public String toString() {
        return "Carrying creature to its lair at " + getTaskLocation();
    }

    @Override
    public void executeTask(ICreatureController creature, float executionDuration) {
        this.creature.sleep();
        executed = true;

        // Set the dragged state
        this.creature.setHaulable(null);
    }

    @Override
    public void unassign(ICreatureController creature) {
        super.unassign(creature);

        // Set the dragged state
        this.creature.setHaulable(null);
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.CARRY_CREATURE_TO_LAIR;
    }

    @Override
    public EntityId getTaskTarget() {
        return creature.getEntityId();
    }

}
