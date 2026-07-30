/*
 * Copyright (C) 2014-2015 OpenKeeper
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
package toniarts.openkeeper.game.trigger;

import java.util.Map;
import toniarts.openkeeper.tools.convert.map.KwdFile;
import toniarts.openkeeper.tools.convert.map.Trigger;
import toniarts.openkeeper.tools.convert.map.TriggerAction;
import toniarts.openkeeper.tools.convert.map.TriggerGeneric;

/**
 *
 * Loads all the triggers from a KWD file
 *
 * @author ArchDemon
 */
public final class TriggerLoader {

    private final Map<Integer, Trigger> triggers;

    public TriggerLoader(KwdFile kwdFile) {
        this.triggers = kwdFile.getTriggers();
    }

    public TriggerGenericData load(int triggerId) {
        TriggerGenericData root = null;
        if (triggerId != 0) {
            root = new TriggerGenericData();
            parse(root, triggerId);
        }
        return root;
    }

    private void parse(TriggerGenericData parent, int id) {
        while (true) {
            Trigger temp = triggers.get(id);
            TriggerData trigger;

            if (temp instanceof TriggerGeneric triggerGeneric) {
                TriggerGenericData triggerGenericData
                        = new TriggerGenericData(id, temp.getRepeatTimes());
                trigger = triggerGenericData;
                triggerGenericData.setType(triggerGeneric.getType());
                triggerGenericData.setComparison(
                        triggerGeneric.getTargetValueComparison());

                for (String key : temp.getUserDataKeys()) {
                    trigger.setUserData(key, temp.getUserData(key));
                }

                parent.attachChild(trigger);

                if (temp.hasChildren()) {
                    parse(triggerGenericData, temp.getIdChild());
                }
            } else if (temp instanceof TriggerAction triggerAction) {
                TriggerActionData triggerActionData = new TriggerActionData(id);
                trigger = triggerActionData;
                triggerActionData.setType(triggerAction.getType());

                for (String key : temp.getUserDataKeys()) {
                    trigger.setUserData(key, temp.getUserData(key));
                }

                parent.attachChild(trigger);
            } else {
                throw new RuntimeException("Unexpected class " + temp + "!");
            }

            if (temp.hasNext()) {
                id = temp.getIdNext();
            } else {
                break;
            }
        }
    }
}
