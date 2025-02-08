package toniarts.openkeeper.tools.convert;

import com.jme3.asset.ModelKey;

/**
 * Asset key for KMF models
 */
public final class KmfAssetKey extends ModelKey {

    private boolean generateMaterialFile = false;

    public KmfAssetKey(String name) {
        super(name);
    }

    public boolean isGenerateMaterialFile() {
        return generateMaterialFile;
    }

    public void setGenerateMaterialFile(boolean generateMaterialFile) {
        this.generateMaterialFile = generateMaterialFile;
    }
}