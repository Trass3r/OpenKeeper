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
package toniarts.openkeeper.tools.convert;

import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.MorphControl;
import com.jme3.anim.MorphTrack;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.MaterialKey;
import com.jme3.asset.ModelKey;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.material.plugin.export.material.J3MExporter;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.AssetLinkNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.MorphTarget;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import com.jme3.util.mikktspace.MikktspaceTangentGenerator;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import toniarts.openkeeper.tools.convert.kmf.Anim;
import toniarts.openkeeper.tools.convert.kmf.Grop;
import toniarts.openkeeper.tools.convert.kmf.KmfFile;
import toniarts.openkeeper.tools.convert.kmf.Triangle;
import toniarts.openkeeper.tools.convert.kmf.Uv;
import toniarts.openkeeper.tools.convert.kmf.Material.MaterialFlag;
import toniarts.openkeeper.tools.modelviewer.ModelViewer;
import toniarts.openkeeper.utils.AssetUtils;
import toniarts.openkeeper.utils.PathUtils;

/**
 * Loads up and converts a Dungeon Keeper II model to JME model<br>
 * The coordinate system is a bit different, so switching Z & Y is intentional,
 * JME uses right-handed coordinate system
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class KmfModelLoader implements AssetLoader {

    // there's 1 kmf per animation so we only use 1 dummy JME animation clip
    public static final String DUMMY_ANIM_CLIP_NAME = "dummyAnimClipName";
    private static final Logger logger = System.getLogger(KmfModelLoader.class.getName());

    /* Some textures are broken */
    private final static Map<String, String> textureFixes = Map.of("Goblinbak", "GoblinBack", "Goblin2", "GoblinFront");
    private static String dkIIFolder;

    /**
     * If the material has multiple texture options, the material is named
     * &lt;material&gt;&lt;this suffix&gt;&lt;texture index&gt;. Texture index
     * is 0-based
     */
    public static final String MATERIAL_ALTERNATIVE_TEXTURE_SUFFIX_SEPARATOR = "_";
    /**
     * If this user meta data is found from the geometry, it has alternative
     * material possibilities
     */
    public static final String MATERIAL_ALTERNATIVE_TEXTURES = "AlternativeTextures";
    public static final String FRAME_FACTOR_FUNCTION = "FrameFactorFunction";
    /* Already saved materials are stored here */
    private static final Map<toniarts.openkeeper.tools.convert.kmf.Material, String> materialCache = new HashMap<>();
    private static final TextureSorter TEXTURE_SORTER = new TextureSorter();

    public static void main(final String[] args) throws IOException {

        //Take Dungeon Keeper 2 root folder as parameter
        if (args.length != 2 || !Files.exists(Paths.get(args[1]))) {
            dkIIFolder = PathUtils.getDKIIFolder();
            if (dkIIFolder == null) {
                throw new RuntimeException("Please provide file path to the model as a first parameter! Second parameter is the Dungeon Keeper II main folder (optional)");
            }
        } else {
            dkIIFolder = PathUtils.fixFilePath(args[1]);
        }

        ModelViewer app = new ModelViewer(Paths.get(args[0]), dkIIFolder);
        app.start();
    }

    @Override
    public Object load(AssetInfo assetInfo) throws IOException {

        KmfFile kmfFile;
        boolean generateMaterialFile = false;
        if (assetInfo instanceof KmfAssetInfo) {
            kmfFile = ((KmfAssetInfo) assetInfo).getKmfFile();
            generateMaterialFile = ((KmfAssetInfo) assetInfo).isGenerateMaterialFile();
        } else {
            kmfFile = new KmfFile(assetInfo.openStream());
        }

        // root node is needed cause AnimationLoader adds start and end anims
        var root = new Node("Root");
        if (kmfFile.getType() == KmfFile.Type.MESH || kmfFile.getType() == KmfFile.Type.ANIM) {

            // Get the materials first
            Map<Integer, List<Material>> materials = getMaterials(kmfFile, generateMaterialFile, assetInfo);

            if (kmfFile.getType() == KmfFile.Type.MESH)
                root.attachChild(handleMesh(kmfFile.getMesh(), materials));
            else if (kmfFile.getType() == KmfFile.Type.ANIM)
                root.attachChild(handleAnim(kmfFile.getAnim(), materials));
        } else if (kmfFile.getType() == KmfFile.Type.GROP) {
            root.attachChild(createGroup(kmfFile));
        }

        return root;
    }

    /**
     * Creates a grop, a.k.a. a group, links existing models to one scene?
     *
     * @param root root node
     * @param kmfFile the KMF file
     */
    private Node createGroup(KmfFile kmfFile) {

        var groupNode = new Node("MeshGroup");

        //Go trough the models and add them
        for (Grop grop : kmfFile.getGrops()) {
            String key = AssetsConverter.MODELS_FOLDER + File.separator + grop.getName() + ".j3o";
            AssetLinkNode modelLink = new AssetLinkNode(key, new ModelKey(key));
            modelLink.setLocalTranslation(new Vector3f(grop.getPos().x, -grop.getPos().z, grop.getPos().y));
            groupNode.attachChild(modelLink);
        }
        return groupNode;
    }

    /**
     * Handle mesh creation
     *
     * @param sourceMesh the mesh
     * @param materials materials map
     * @param root the root node
     */
    private Node handleMesh(toniarts.openkeeper.tools.convert.kmf.Mesh sourceMesh, Map<Integer, List<Material>> materials) {

        var node = new Node(sourceMesh.getName());
        node.setLocalTranslation(new Vector3f(sourceMesh.getPos().x, -sourceMesh.getPos().z, sourceMesh.getPos().y));

        int index = 0;
        for (var subMesh : sourceMesh.getSprites()) {

            if (subMesh.getTriangles().get(0).isEmpty())
                continue; // FIXME: LODs are broken so we only take L0

            //Each sprite represents a geometry (+ mesh) since they each have their own material
            Mesh mesh = new Mesh();

            //Vertices, UV (texture coordinates), normals
            final int numVertices = subMesh.getVertices().size();
            var vertices = new Vector3f[numVertices];
            var texCoord = new Vector2f[numVertices];
            var normals  = new Vector3f[numVertices];
            int i = 0;
            for (var meshVertex : subMesh.getVertices()) {

                //Vertice
                javax.vecmath.Vector3f v = sourceMesh.getGeometries().get(meshVertex.getGeomIndex());
                vertices[i] = new Vector3f(v.x, -v.z, v.y);

                //Texture coordinate
                Uv uv = meshVertex.getUv();
                texCoord[i] = new Vector2f(uv.getUv()[0] / 32768f, uv.getUv()[1] / 32768f);

                //Normals
                v = meshVertex.getNormal();
                normals[i] = new Vector3f(v.x, -v.z, v.y);

                i++;
            }

            // Create LOD levels
            // FIXME: LODs are broken so we only take L0
            var lodLevels = createIndices(subMesh.getTriangles().subList(0, 1));
            mesh.setBuffer(lodLevels[0]);
            // mesh.setLodLevels(lodLevels); // needs to include L0!

            mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
            mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(texCoord));
            mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
            mesh.setStatic();

            // Create geometry
            Geometry geom = createGeometry(index, sourceMesh.getName(), mesh, materials, subMesh.getMaterialIndex());

            //Attach the geometry to the node
            node.attachChild(geom);
            index++;
        }

        return node;
    }

    /**
     * Handle mesh creation
     *
     * @param anim the anim
     * @param materials materials map
     * @param root the root node
     */
    private Node handleAnim(Anim anim, Map<Integer, List<Material>> materials) {

        var node = new Node(anim.getName());
        node.setUserData(FRAME_FACTOR_FUNCTION, anim.getFrameFactorFunction().name());
        node.setLocalTranslation(new Vector3f(anim.getPos().x, -anim.getPos().z, anim.getPos().y));

        // Create one track per submesh
        List<MorphTrack> animTracks = new ArrayList<>(anim.getSprites().size());

        // Create times (same for all tracks)
        // we subsample the frames to reduce the size
        // always take the last frame
        final int frameSubdiv = 2;
        final int lastFrame = anim.getFrames() - 1;
        assert lastFrame > 0;
        final int numFrames = (lastFrame - 1) / frameSubdiv + 1 + 1; // ceiling division + 1
        float[] times = new float[numFrames];
        for (int i = 0; i < numFrames-1; ++i)
            times[i] = (frameSubdiv * i) / 30f;
        times[numFrames-1] = (lastFrame) / 30f;

        int subMeshIndex = 0;
        for (var subMesh : anim.getSprites()) {

            if (subMesh.getTriangles().get(0).isEmpty())
                continue; // FIXME: LODs are broken so we only take L0

            //Each submesh is its own geometry (+ mesh) since they each have their own material
            var mesh = new Mesh();

            // Base Pose vertices, uvs, normals
            final int numVertices = subMesh.getVertices().size();
            var baseVertices = new Vector3f[numVertices];
            var vertices = new Vector3f[numVertices];
            var uvs      = new Vector2f[numVertices];
            var normals  = new Vector3f[numVertices];

            // first the UVs and normals since they are frame-independent
            int i = 0;
            for (var animVertex : subMesh.getVertices()) {
                var uv = animVertex.getUv();
                uvs[i] = new Vector2f(uv.getUv()[0] / 32768f, uv.getUv()[1] / 32768f);

                var v = animVertex.getNormal();
                normals[i] = new Vector3f(v.x, -v.z, v.y);

                ++i;
            }

            // now get the vertices for each frame, make sure we pick the last frame too
            for (int frame = 0; frame < anim.getFrames(); frame += Math.max(1, Math.min(frameSubdiv, anim.getFrames() - frame - 1)))
            {
                i = 0;
                for (var animVertex : subMesh.getVertices())
                {
                    // the split into base + offset is just a file size optimization
                    int geomBase = anim.getItab()[frame >> 7][animVertex.getItabIndex()];
                    short geomOffset = anim.getOffsets()[animVertex.getItabIndex()][frame];
                    int geomIndex = geomBase + geomOffset;

                    var animGeometries  = anim.getGeometries();
                    var coord           = animGeometries.get(geomIndex).getGeometry();
                    short frameBase     = animGeometries.get(geomIndex).getFrameBase();
                    var nextCoord       = animGeometries.get(geomIndex + 1).getGeometry();
                    short nextFrameBase = animGeometries.get(geomIndex + 1).getFrameBase();
                    float geomFactor = (float) ((frame & 0x7f) - frameBase) / (float) (nextFrameBase - frameBase);

                    // interpolate and convert to Y-up
                    vertices[i] = new Vector3f(
                        coord.x + (nextCoord.x - coord.x) * geomFactor,
                      -(coord.z + (nextCoord.z - coord.z) * geomFactor),
                        coord.y + (nextCoord.y - coord.y) * geomFactor);
                    ++i;
                }

                if (frame == 0) {
                    // we need a valid position buffer for BVH generation etc.
                    mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
                    for (i = 0; i < vertices.length; ++i)
                        baseVertices[i] = new Vector3f(vertices[i]);
                }
                // create a relative morph target
                var morphTarget = new MorphTarget("submesh " + subMeshIndex + " frame " + frame);
                for (i = 0; i < vertices.length; ++i)
                    vertices[i].subtractLocal(baseVertices[i]);
                morphTarget.setBuffer(Type.Position, BufferUtils.createFloatBuffer(vertices));
                mesh.addMorphTarget(morphTarget);
            }

            // Create LOD levels
            // FIXME: LODs are broken so we only take L0
            var lodLevels = createIndices(subMesh.getTriangles().subList(0, 1));
            mesh.setBuffer(lodLevels[0]);
            //mesh.setLodLevels(lodLevels); // needs to include L0!

            mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(uvs));
            mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(normals));
            mesh.setStatic();

            // Create geometry
            Geometry geom = createGeometry(subMeshIndex, anim.getName(), mesh, materials, subMesh.getMaterialIndex());

            // Create a morph track for this mesh
            var weights = new float[numFrames * numFrames];
            // set up weights as identity matrix
            for (i = 1; i < numFrames; ++i) {
                weights[i * numFrames + i] = 1;
            }
            var morphTrack = new MorphTrack(geom, times, weights, numFrames);
            animTracks.add(morphTrack);

            //Attach the geometry to the node
            node.attachChild(geom);
            ++subMeshIndex;
        }

        // Create the animation itself and attach the animation
        var animClip = new AnimClip(DUMMY_ANIM_CLIP_NAME);
        animClip.setTracks(animTracks.toArray(new MorphTrack[0]));
        var composer = new AnimComposer();
        composer.addAnimClip(animClip);
        node.addControl(composer);
        node.addControl(new MorphControl());
        // we could also do setCurrentAction(DUMMY_ANIM_CLIP_NAME) here but it wouldn't get serialized

        return node;
    }

    private VertexBuffer[] createIndices(final List<List<Triangle>> trianglesList) {

        // Triangles are not in order, sometimes they are very random, many missing etc.
        // For JME 3.0 this was somehow ok, but JME 3.1 doesn't do some automatic organizing etc.
        // Assume that if first LOD level is null, there are no LOD levels, instead the mesh should be just point mesh
        // I tried ordering them etc. but it went worse (3DFE_GemHolder.kmf is a good example)
        //
        // Ultimately all failed, now just instead off completely empty buffers, put one 0 there, seems to have done the trick
        //
        // Triangles, we have LODs here

        // replace runs of empty LODs with just a single empty level
        // LodControl would just skip them anyway
        for (int i = trianglesList.size() - 1; i > 0; --i)
            if (trianglesList.get(i).isEmpty() && trianglesList.get(i - 1).isEmpty())
                trianglesList.remove(i);

        var lodLevels = new VertexBuffer[trianglesList.size()];
        int lod = 0;
        for (var triangles : trianglesList) {
            // in case of an empty buffer, put one 0 there to prevent exception in LwjglRender.checkLimit
            var indexes = new byte[Math.max(1, 3 * triangles.size())];
            int x = 0;
            for (Triangle triangle : triangles) {
                indexes[x * 3] = triangle.getTriangle()[2];
                indexes[x * 3 + 1] = triangle.getTriangle()[1];
                indexes[x * 3 + 2] = triangle.getTriangle()[0];
                ++x;
            }
            var buf = new VertexBuffer(Type.Index);
            buf.setName("IndexBuffer L" + lod);
            buf.setupData(VertexBuffer.Usage.Static, 3, VertexBuffer.Format.UnsignedByte, BufferUtils.createByteBuffer(indexes));
            lodLevels[lod] = buf;
            ++lod;
        }
        return lodLevels;
    }

    /**
     * Creates a geometry from the given mesh, applies material and LOD control
     * to it
     *
     * @param subMeshIndex mesh index (just for naming)
     * @param meshName the mesh name, just for logging
     * @param mesh the mesh
     * @param materials list of materials
     * @param materialIndex the material index
     * @return
     */
    private Geometry createGeometry(int subMeshIndex, String meshName, Mesh mesh, Map<Integer, List<Material>> materials, int materialIndex) {

        //Create geometry
        var geom = new Geometry(meshName + '_' + subMeshIndex, mesh);

        // LODs are broken
        //var lc = new LodControl();
        //geom.addControl(lc);

        // Material, set the first
        geom.setMaterial(materials.get(materialIndex).get(0));
        if (geom.getMaterial().isTransparent()) {
            geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        }

        // The receive shadows flag is used to turn the shadows completely off
        if (!geom.getMaterial().isReceivesShadows()) {
            geom.setShadowMode(RenderQueue.ShadowMode.Off);
        }

        // If we have multiple materials to choose from, tag them to the geometry
        List<Material> materialsList = materials.get(materialIndex);
        if (materialsList.size() > 1) {
            List<String> textureNames = new ArrayList<>(materialsList.size());
            for (Material material : materialsList) {
                textureNames.add(material.getTextureParam("DiffuseMap").getTextureValue().getKey().getName());
            }
            
            // The textures seem to be in random order (sometimes), I don't know if the real idea is to figure the purpose out from the file names..
            // But a quick fix is to sort them... So they go in somewhat logical order
            Collections.sort(textureNames, TEXTURE_SORTER);
            
            geom.setUserData(MATERIAL_ALTERNATIVE_TEXTURES, textureNames);
        }

        // Update bounds
        geom.updateModelBound();

        // Try to generate tangents
        try {
            MikktspaceTangentGenerator.generate(geom);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to generate tangents for " + meshName + "! ", e);
        }

        return geom;
    }

    /**
     * Set some flags on the material that do not get saved
     *
     * @param material material to modify
     * @param kmfMaterial the KMF material entry
     */
    private void setMaterialFlags(Material material, toniarts.openkeeper.tools.convert.kmf.Material kmfMaterial) {

        // Read the flags & stuff
        if (kmfMaterial.getFlag().contains(MaterialFlag.HAS_ALPHA)) {
            material.setTransparent(true);
            material.setFloat("AlphaDiscardThreshold", 0.1f);
            material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        }
        if (kmfMaterial.getFlag().contains(MaterialFlag.ALPHA_ADDITIVE)) {
            material.setTransparent(true);
            material.setFloat("AlphaDiscardThreshold", 0.1f);
            material.getAdditionalRenderState().setDepthWrite(false);
            material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
        }

        if (kmfMaterial.getFlag().contains(MaterialFlag.DOUBLE_SIDED))
            material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);

        // Shadows thing is just a guess, like, they seem to be small light sources
        material.setReceivesShadows(!kmfMaterial.getFlag().contains(MaterialFlag.ALPHA_ADDITIVE));
    }

    /**
     * <i>Extracts</i> the materials from the KMF file
     *
     * @param kmfFile the KMF file
     * @param generateMaterialFile should we create J3M material file (in total
     * conversion always yes)
     * @param assetInfo the asset info
     * @param engineTextureFile instance of engine textures file
     * @return returns materials by the material index
     * @throws IOException may fail
     */
    private Map<Integer, List<Material>> getMaterials(KmfFile kmfFile, boolean generateMaterialFile, AssetInfo assetInfo) throws IOException {

        //
        // Create the materials
        //
        Map<Integer, List<Material>> materials = HashMap.newHashMap(kmfFile.getMaterials().size());
        int i = 0;
        for (toniarts.openkeeper.tools.convert.kmf.Material mat : kmfFile.getMaterials()) {
            Material material = null;

            // Get the texture, the first one
            // There is a list of possible alternative textures
            String texture = mat.getTextures().get(0);
            if (textureFixes.containsKey(texture)) {

                //Fix the texture entry
                texture = textureFixes.get(texture);
            }

            // See if the material is found already on the cache
            String materialLocation = null;
            String materialKey = null;
            String fileName;
            if (generateMaterialFile) {
                materialKey = materialCache.get(mat);
                if (materialKey != null) {
                    material = assetInfo.getManager().loadMaterial(materialKey);
                    setMaterialFlags(material, mat);
                    List<Material> materialList = new ArrayList<>(mat.getTextures().size());
                    materialList.add(material);

                    // Multiple textures
                    addAlternativeTextures(mat, assetInfo, material, materialList);

                    materials.put(i, materialList);
                    i++;
                    continue;
                } else {

                    // Ok, it it not in the cache yet, but maybe it has been already generated, so use it and update the defaults in it
                    fileName = PathUtils.stripFileName(mat.getName());

                    // If there are multiple texture options, add a suffix to the material file name
                    if (mat.getTextures().size() > 1) {
                        fileName = fileName.concat(MATERIAL_ALTERNATIVE_TEXTURE_SUFFIX_SEPARATOR).concat("0");
                    }

                    materialKey = AssetsConverter.MATERIALS_FOLDER.concat("/").concat(fileName).concat(".j3m");
                    materialLocation = AssetsConverter.getAssetsFolder().concat(AssetsConverter.MATERIALS_FOLDER.concat(File.separator).concat(fileName).concat(".j3m"));

                    // See if it exists
                    Path file = Paths.get(materialLocation);
                    if (Files.exists(file)) {
                        file = file.toRealPath();
                        if (!file.getFileName().toString().equals(fileName.concat(".j3m"))) {

                            // Case sensitivity issue
                            materialKey = AssetsConverter.MATERIALS_FOLDER.concat("/").concat(file.getFileName().toString());
                            materialLocation = AssetsConverter.getAssetsFolder().concat(AssetsConverter.MATERIALS_FOLDER.concat(File.separator).concat(file.getFileName().toString()));
                        }
                        material = assetInfo.getManager().loadMaterial(materialKey);
                    }
                }
            }

            // Create the material
            if (material == null) {
                material = new Material(assetInfo.getManager(), "Common/MatDefs/Light/Lighting.j3md");
                material.setName(mat.getName());
            }

            //Load up the texture and create the material
            Texture tex = loadTexture(texture, assetInfo);
            material.setTexture("DiffuseMap", tex);
            material.setColor("Specular", ColorRGBA.Orange); // Dungeons are lit only with fire...? Experimental
            material.setColor("Diffuse", ColorRGBA.White); // Experimental
            material.setFloat("Shininess", mat.getShininess());

            // Set some flags
            setMaterialFlags(material, mat);

            // Add material to list and create the possible alternatives
            List<Material> materialList = new ArrayList<>(mat.getTextures().size());
            materialList.add(material);
            addAlternativeTextures(mat, assetInfo, material, materialList);

            // See if we should save the materials
            if (generateMaterialFile) {
                for (int k = 0; k < materialList.size(); k++) {

                    Material m = materialList.get(k);

                    // If there are multiple textures / material options, alter the key and location
                    if (materialList.size() > 1) {
                        materialKey = materialKey.substring(0, materialKey.lastIndexOf(MATERIAL_ALTERNATIVE_TEXTURE_SUFFIX_SEPARATOR) + 1).concat(k + "").concat(materialKey.substring(materialKey.lastIndexOf(".")));
                        materialLocation = materialLocation.substring(0, materialLocation.lastIndexOf(MATERIAL_ALTERNATIVE_TEXTURE_SUFFIX_SEPARATOR) + 1).concat(k + "").concat(materialLocation.substring(materialLocation.lastIndexOf(".")));
                    }

                    // Set the material so that it realizes that it is a J3M file
                    m.setKey(new MaterialKey(materialKey));

                    // Save
                    J3MExporter exporter = new J3MExporter();
                    try (OutputStream out = Files.newOutputStream(Paths.get(materialLocation));
                            BufferedOutputStream bout = new BufferedOutputStream(out)) {
                        exporter.save(m, bout);
                    }

                    // Put the first one to the cache
                    if (k == 0) {
                        materialCache.put(mat, materialKey);
                    }
                }
            }

            materials.put(i, materialList);
            i++;
        }
        return materials;
    }

    private void addAlternativeTextures(toniarts.openkeeper.tools.convert.kmf.Material mat, AssetInfo assetInfo, Material material, List<Material> materialList) {
        for (int k = 1; k < mat.getTextures().size(); k++) {
            
            // Get the texture
            String alternativeTexture = mat.getTextures().get(k);
            if (textureFixes.containsKey(alternativeTexture)) {
                
                //Fix the texture entry
                alternativeTexture = textureFixes.get(alternativeTexture);
            }
            Texture alternativeTex = loadTexture(alternativeTexture, assetInfo);
            
            // Clone the original material, set texture and add to list
            Material alternativeMaterial = material.clone();
            alternativeMaterial.setTexture("DiffuseMap", alternativeTex);
            materialList.add(alternativeMaterial);
        }
    }

    /**
     * Loads a JME texture of the texture name
     *
     * @param texture the texture name
     * @param assetInfo the assetInfo
     * @return texture file
     */
    private Texture loadTexture(String texture, AssetInfo assetInfo) {

        // Load the texture
        TextureKey textureKey = new TextureKey(AssetUtils.getCanonicalAssetKey(AssetsConverter.TEXTURES_FOLDER.concat("/").concat(texture).concat(".png")), false);
        Texture tex = assetInfo.getManager().loadTexture(textureKey);
        return tex;
    }

    private static final class TextureSorter implements Comparator<String> {
        
        private static final Pattern PATTERN = Pattern.compile("\\D+(?<number>\\d+)\\.png");

        @Override
        public int compare(String o1, String o2) {
            Integer o1Numbering = null;
            Integer o2Numbering = null;
            Matcher m = PATTERN.matcher(o1);
            if(m.matches()) {
                o1Numbering = Integer.parseInt(m.group("number"));
            }
             m = PATTERN.matcher(o2);
            if(m.matches()) {
                o2Numbering = Integer.parseInt(m.group("number"));
            }

            int result = 0;
            if (o1Numbering != null && o2Numbering != null) {
                result = Integer.compare(o1Numbering, o2Numbering);
            }

            if (result == 0) {
                result = String.CASE_INSENSITIVE_ORDER.compare(o1, o2);
            }

            return result;
        }
    }

    /**
     * A frame info identifies an vertex transition, it has the start and the
     * end pose frame indexes (key frames) which will identify it
     */
    private final static class FrameInfo implements Comparable<KmfModelLoader.FrameInfo> {

        private final int previousPoseFrame;
        private final int nextPoseFrame;
        private final float weight;

        public FrameInfo(int previousPoseFrame, int nextPoseFrame, float weight) {
            this.previousPoseFrame = previousPoseFrame;
            this.nextPoseFrame = nextPoseFrame;
            this.weight = weight;
        }

        public int getPreviousPoseFrame() {
            return previousPoseFrame;
        }

        public int getNextPoseFrame() {
            return nextPoseFrame;
        }

        public float getWeight() {
            return weight;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 79 * hash + this.previousPoseFrame;
            hash = 79 * hash + this.nextPoseFrame;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final var other = (KmfModelLoader.FrameInfo) obj;
            if (this.previousPoseFrame != other.previousPoseFrame) {
                return false;
            }
            if (this.nextPoseFrame != other.nextPoseFrame) {
                return false;
            }
            return true;
        }

        @Override
        public int compareTo(KmfModelLoader.FrameInfo o) {
            int result = Integer.compare(previousPoseFrame, o.previousPoseFrame);
            if (result == 0) {

                // It is up to the second key then
                result = Integer.compare(nextPoseFrame, o.nextPoseFrame);
            }
            return result;
        }
    }
}
