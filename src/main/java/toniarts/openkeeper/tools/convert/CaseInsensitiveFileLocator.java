package toniarts.openkeeper.tools.convert;

import com.jme3.asset.*;
import com.jme3.asset.plugins.FileLocator;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.*;

/**
 * A cached, case-insensitive extension of {@link FileLocator}.
 * Indexes the directory structure at startup and uses reflection to preserve asset key subclasses.
 */
public final class CaseInsensitiveFileLocator extends FileLocator {

    private File localRoot;
    private final Map<String, String> pathCache = new HashMap<>();
    private static Field nameField;

    static {
        try {
            // cache the reflection field reference
            nameField = AssetKey.class.getDeclaredField("name");
            nameField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to initialize AssetKey reflection field injection", e);
        }
    }

    @Override
    public void setRootPath(String rootPath) {
        super.setRootPath(rootPath);
        if (rootPath != null) {
            try {
                this.localRoot = new File(rootPath).getCanonicalFile();
                this.pathCache.clear();
                buildCache(this.localRoot, "");
            } catch (IOException ex) {
                throw new AssetLoadException("Failed to resolve canonical path for root", ex);
            }
        }
    }

    private void buildCache(File dir, String currentRelativePath) {
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            String childRelativePath = currentRelativePath.isEmpty() 
                    ? child.getName() 
                    : currentRelativePath + '/' + child.getName();

            String unifiedPath = childRelativePath.replace('\\', '/');
            pathCache.put(unifiedPath.toLowerCase(Locale.ROOT), unifiedPath);

            if (child.isDirectory()) {
                buildCache(child, childRelativePath);
            }
        }
    }

    @Override
    public AssetInfo locate(AssetManager manager, AssetKey key) {
        if (localRoot == null)
            return null;

        String requestedName = key.getName().replace('\\', '/');
        String lookupKey = requestedName.toLowerCase(Locale.ROOT);

        // query our instant memory cache
        String exactCaseRelativePath = pathCache.get(lookupKey);
        if (exactCaseRelativePath == null) {
            // fallback: esp. for conversion case
            exactCaseRelativePath = findFile(requestedName, key);
            if (exactCaseRelativePath != null)
                pathCache.put(lookupKey, exactCaseRelativePath);
            else
                return null;
        }

        // inject the correct case relative path back into the immutable name field
        try {
            nameField.set(key, exactCaseRelativePath);
        } catch (IllegalAccessException e) {
            throw new AssetLoadException("Reflection failure injecting case-insensitive asset name", e);
        }

        return super.locate(manager, key);
    }

    private String findFile(String requestedName, AssetKey key) {
        String name = key.getName();
        String[] parts = name.split("[/\\\\]");
        File current = localRoot;

        // Resolve each directory/file segment ignoring case
        for (String part : parts) {
            if (part.isEmpty() || part.equals("."))
                continue;
            if (part.equals("..")) {
                current = current.getParentFile();
                if (current == null)
                    return null;
                continue;
            }

            File found = findFileIgnoreCase(current, part);
            if (found == null) {
                return null;
            }
            current = found;
        }
        if (current.exists() && current.isFile())
            return Paths.get(localRoot.toString()).relativize(Paths.get(current.getAbsolutePath())).toString().replace('\\', '/');
        return null;
    }

    private static File findFileIgnoreCase(File dir, String name) {
        File[] children = dir.listFiles();
        if (children == null)
            return null;

        for (File child : children) {
            if (child.getName().equalsIgnoreCase(name)) {
                return child;
            }
        }
        return null;
    }
}