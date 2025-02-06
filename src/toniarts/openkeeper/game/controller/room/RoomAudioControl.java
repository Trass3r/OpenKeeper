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
package toniarts.openkeeper.game.controller.room;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.audio.AudioSource;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.control.AbstractControl;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import toniarts.openkeeper.tools.convert.map.Room;
import toniarts.openkeeper.tools.convert.sound.BankMapFile;
import toniarts.openkeeper.tools.convert.sound.BankMapFileEntry;
import toniarts.openkeeper.tools.convert.sound.SdtFile;
import toniarts.openkeeper.tools.convert.sound.SdtFileEntry;
import toniarts.openkeeper.tools.convert.sound.sfx.SfxMapFile;
import toniarts.openkeeper.utils.PathUtils;

/**
 * Keeps the tunes playing in a room
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class RoomAudioControl extends AbstractControl {

    private final Room room;
    private final BankMapFile bankMapFile;
    private final SfxMapFile sfxMapFile;
    private final List<SdtFile> sdtFiles;
    private AudioNode audioNode;
    private final AssetManager assetManager;
    private int index = 0;

    public RoomAudioControl(Room room, AssetManager assetManager) {
        this.room = room;
        this.assetManager = assetManager;

        if (room.getSoundCategory() != null && !room.getSoundCategory().isEmpty()) {
            String soundFolder = PathUtils.getDKIIFolder().concat(PathUtils.DKII_SFX_FOLDER);
            String mapFolder = soundFolder.concat("Global").concat(File.separator).concat(room.getSoundCategory().toLowerCase());
            bankMapFile = new BankMapFile(Path.of(mapFolder.concat("BANK.map")));
			sfxMapFile = new SfxMapFile(Path.of(mapFolder.concat("SFX.map")));

            // Open the SDTs ready too
            sdtFiles = new ArrayList<>(bankMapFile.getEntries().length);
            for (BankMapFileEntry entry : bankMapFile.getEntries()) {
                sdtFiles.add(new SdtFile(Path.of(soundFolder.concat(entry.getName()).concat("HD.sdt"))));
            }
        } else {
            bankMapFile = null;
            sfxMapFile = null;
            sdtFiles = null;
        }
    }

    @Override
    protected void controlUpdate(float tpf) {
        if (audioNode == null || audioNode.getStatus() == AudioSource.Status.Stopped) {

            // Play next
            if (sfxMapFile.getEntries()[0].getGroups()[0].getEntries().length > index) {
                var entry = sfxMapFile.getEntries()[0].getGroups()[0].getEntries()[index].getSounds()[0];
				SdtFileEntry fileEntry = sdtFiles.get(entry.getArchiveId() - 1).getEntries()[entry.getIndex() - 1];
				try {
                String soundPath = "Sounds/" + bankMapFile.getEntries()[0].getName().replace('\\', '/') + "HD/" + SdtFile.fixFileExtension(fileEntry);
                audioNode = new AudioNode(assetManager, soundPath, AudioData.DataType.Buffer);
                audioNode.setPositional(true);
                audioNode.setDirectional(true);
                audioNode.setRefDistance(sfxMapFile.getEntries()[0].getMinDistance());
                audioNode.setMaxDistance(sfxMapFile.getEntries()[0].getMaxDistance());
                audioNode.play();
                Logger.getLogger("RoomAudioControl").log(Level.INFO, "Playing " + soundPath);
				} catch (AssetNotFoundException ex) {
				Logger.getLogger("RoomAudioControl").log(Level.SEVERE, "Sound file not found: {0}", fileEntry.getName());
				}
                index++;
            } else {
                index = 0; // Rewind
            }
        }
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {

    }
}