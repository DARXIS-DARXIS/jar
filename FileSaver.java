import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;

/** Saves bytes to the first writable file root. Needs JSR-75. */
public class FileSaver {
    public static String save(byte[] data) throws Exception {
        Enumeration e = FileSystemRegistry.listRoots();
        Exception last = new IOException("no file roots");
        while (e.hasMoreElements()) {
            String root = (String) e.nextElement();
            if (!root.endsWith("/")) root = root + "/";
            String url = "file:///" + root + "MobileFruity.mid";
            try {
                FileConnection fc = (FileConnection) Connector.open(url, Connector.READ_WRITE);
                if (fc.exists()) fc.delete();
                fc.create();
                OutputStream os = fc.openOutputStream();
                os.write(data);
                os.close();
                fc.close();
                return root + "MobileFruity.mid";
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw last;
    }
}
