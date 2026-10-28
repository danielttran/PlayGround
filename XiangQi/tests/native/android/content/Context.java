package android.content;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import java.io.File;
/** JVM test seam only; never part of the Android source sets. */
public abstract class Context {
    public abstract ApplicationInfo getApplicationInfo();
    public abstract File getFilesDir();
    public abstract AssetManager getAssets();
}
