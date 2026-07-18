/*
 * Copyright (C) 2014-2025 OpenKeeper
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
package toniarts.openkeeper.tools.convert;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetProcessor;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.cache.AssetCache;


/**
 * A {@link DesktopAssetManager} subclass that dispatches asset loaders based on
 * the {@link AssetInfo}'s key rather than the original request key.
 * <p>
 * This allows locators (like {@link DK2AssetLocator}) to serve assets in
 * a different format than what was requested. For example, when code requests
 * {@code Models/foo.j3o}, DK2AssetLocator can return {@code .kmf} model data
 * from a WAD archive with a {@code .kmf} key in the AssetInfo, and this
 * AssetManager will dispatch to {@link KmfModelLoader} instead of the default
 * {@code .j3o} loader.
 * <p>
 * Trade-off: for cross-format lookups, the asset is cached under the actual
 * format's key rather than the requested key, so subsequent same-request-key
 * lookups will miss the cache and re-load. This is acceptable since
 * cross-format lookups are the exceptional case (original game files).
 */
public final class OpenKeeperAssetManager extends DesktopAssetManager {

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T loadLocatedAsset(AssetKey<T> key, AssetInfo info,
                                      AssetProcessor proc, AssetCache cache) {
        // Dispatch based on the info's key (actual format found)
        // rather than the original request key's extension.
        return (T) super.loadLocatedAsset(info.getKey(), info, proc, cache);
    }
}
