package android.content.res;
import java.io.IOException;
import java.io.InputStream;
/** JVM test seam only. */
public abstract class AssetManager { public abstract InputStream open(String name) throws IOException; }
