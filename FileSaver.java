import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;

/** Saves bytes as MobileFruity.mid to the first writable place. Needs JSR-75. */
public class FileSaver {
    static final String NAME = "MobileFruity.mid";

    /** one attempt: no catch / finally here, keeps the bytecode simple for old verifiers */
    static void write(String url, byte[] data) throws IOException {
        FileConnection fc = (FileConnection) Connector.open(url, Connector.READ_WRITE);
        if (fc.exists()) fc.delete();
        fc.create();
        OutputStream os = fc.openOutputStream();
        os.write(data);
        os.close();
        fc.close();
    }

    public static String save(byte[] data) throws IOException {
        String err = "no file roots";
        Enumeration e = FileSystemRegistry.listRoots();
        while (e.hasMoreElements()) {
            String root = (String) e.nextElement();
            if (!root.endsWith("/")) root = root + "/";
            String[] dirs = {"", "Sounds/", "Others/"};
            for (int i = 0; i < dirs.length; i++) {
                String path = root + dirs[i] + NAME;
                try {
                    write("file:///" + path, data);
                    return path;
                } catch (Throwable t) {
                    err = path + " " + t;
                }
            }
        }
        if (err.length() > 150) err = err.substring(0, 150);
        throw new IOException(err);
    }
}
