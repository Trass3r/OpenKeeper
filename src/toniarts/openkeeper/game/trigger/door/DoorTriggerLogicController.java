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
package toniarts.openkeeper.game.trigger.door;

import com.simsilica.es.EntityData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import toniarts.openkeeper.game.component.DoorComponent;
import toniarts.openkeeper.game.component.Trigger;
import toniarts.openkeeper.game.controller.ICreaturesController;
import toniarts.openkeeper.game.controller.IDoorsController;
import toniarts.openkeeper.game.controller.IGameController;
import toniarts.openkeeper.game.controller.IGameTimer;
import toniarts.openkeeper.game.controller.ILevelInfo;
import toniarts.openkeeper.game.controller.IMapController;
import toniarts.openkeeper.game.controller.door.IDoorController;
import toniarts.openkeeper.game.state.session.PlayerService;
import toniarts.openkeeper.game.trigger.AbstractThingTriggerControl;
import toniarts.openkeeper.game.trigger.AbstractThingTriggerLogicController;
import toniarts.openkeeper.tools.convert.map.Thing;

/**
 * A state for handling door triggers
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class DoorTriggerLogicController extends AbstractThingTriggerLogicController<IDoorController> {

    public DoorTriggerLogicController(final IGameController gameController, final ILevelInfo levelInfo, final IGameTimer gameTimer, final IMapController mapController,
            final ICreaturesController creaturesController, final PlayerService playerService, final EntityData entityData,
            final IDoorsController doorsController) {
        super(initTriggers(levelInfo.getLevelData().getThings(Thing.Door.class), gameController, levelInfo, gameTimer, mapController,
                creaturesController, playerService),
                entityData.getEntities(DoorComponent.class, Trigger.class),
                doorsController);
    }

    private static Map<Integer, AbstractThingTriggerControl<IDoorController>> initTriggers(List<Thing.Door> things, final IGameController gameController, final ILevelInfo levelInfo, final IGameTimer gameTimer, final IMapController mapController,
            final ICreaturesController creaturesController, final PlayerService playerService) {
        Map<Integer, AbstractThingTriggerControl<IDoorController>> doorTriggers = new HashMap<>();
        for (Thing.Door door : things) {
            if (door.getTriggerId() != 0) {
                doorTriggers.put(door.getTriggerId(), new DoorTriggerControl(gameController, levelInfo, gameTimer, mapController,
                        creaturesController, door.getTriggerId(), door.getPlayerId(), playerService));
            }
        }
        return doorTriggers;
    }

}
