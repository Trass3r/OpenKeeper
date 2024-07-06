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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author ArchDemon
 */
public abstract class TriggerData {
    
    private static final Logger logger = System.getLogger(TriggerData.class.getName());

    /**
     * This TriggerActionData's id.
     */
    protected final int id;
    /**
     * TriggerActionData's parent, or null if it has none.
     */
    protected transient TriggerGenericData parent;
    protected Map<String, Number> userData = null;

    public TriggerData() {
        id = 0; // FIXME
    }

    public TriggerData(int id) {
        this.id = id;
    }

    public TriggerData(int id, TriggerGenericData parent) {
        this(id);
        this.parent = parent;
    }

    /**
     * Returns the id of this TriggerData.
     *
     * @return This TriggerActionData's id.
     */
    public int getId() {
        return id;
    }

    /**
     * <code>getParent</code> retrieves this node's parent. If the parent is
     * null this is the root node.
     *
     * @return the parent of this node.
     */
    public TriggerGenericData getParent() {
        return parent;
    }

    /**
     * Called by {@link TriggerGenericData#attachChildAt(TriggerActionData)} and
     * * {@link TriggerGenericData#detachChild(TriggerActionData)} - don't call
     * directly. <code>setParent</code> sets the parent of this
     * TriggerActionData.
     *
     * @param parent the parent of this TriggerActionData.
     */
    protected void setParent(TriggerGenericData parent) {
        this.parent = parent;
    }

    /**
     * determines if the provided TriggerGenericData is the parent, or parent's
     * parent, etc. of this TriggerActionData.
     *
     * @param ancestor the ancestor object to look for.
     * @return true if the ancestor is found, false otherwise.
     */
    public boolean hasAncestor(TriggerGenericData ancestor) {
        if (parent == null) {
            return false;
        } else if (parent.equals(ancestor)) {
            return true;
        } else {
            return parent.hasAncestor(ancestor);
        }
    }

    public void setUserData(String key, Number data) {
        if (userData == null) {
            userData = new HashMap<>();
        }

        if (data == null) {
            userData.remove(key);
        } else if (data instanceof Number) {
            userData.put(key, (Number) data);
        } else {
            logger.log(Level.ERROR, "Unexpected class {0}!", data);
            throw new RuntimeException("Unexpected class");
        }
    }

    public <T extends Number> T getUserData(String key) {
        if (userData == null) {
            return null;
        }

        Number s = userData.get(key);
        return (T) s;
    }

    public <T extends Number> T getUserData(String key, Class<T> dataClass) {
        return (T) getUserData(key);
    }

    public Collection<String> getUserDataKeys() {
        if (userData != null) {
            return userData.keySet();
        }

        return Collections.emptySet();
    }

    public boolean hasUserDataKey(String key) {
        if (userData != null) {
            return userData.containsKey(key);
        }

        return false;
    }

    @Override
    public String toString() {
        return id + " (" + this.getClass().getSimpleName() + ')';
    }
}
