import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Vector;

/** Tiny file browser (JSR-75): pick a disk, walk folders, choose a .mid file. */
public class FileBrowser extends List implements CommandListener {
    FruityCanvas owner;
    MIDlet midlet;
    String dir = "";             // "" = list of disks, otherwise e.g. "E:/Sounds/"
    Vector names = new Vector(); // entry names, parallel to the list items
    Command cmdBack;

    public FileBrowser(FruityCanvas owner, MIDlet midlet) {
        super("MIDI", List.IMPLICIT);
        this.owner = owner;
        this.midlet = midlet;
        cmdBack = new Command("\u041d\u0430\u0437\u0430\u0434", Command.BACK, 1);
        addCommand(cmdBack);
        setCommandListener(this);
        fill();
    }

    void add(String n) {
        names.addElement(n);
        append(n, null);
    }

    void fill() {
        deleteAll();
        names.removeAllElements();
        setTitle(dir.length() == 0 ? "MIDI: \u0434\u0438\u0441\u043a" : dir);
        FileConnection fc = null;
        Enumeration e = null;
        String err = null;
        try {
            if (dir.length() == 0) {
                e = FileSystemRegistry.listRoots();
                while (e.hasMoreElements()) {
                    String r = (String) e.nextElement();
                    if (!r.endsWith("/")) r = r + "/";
                    add(r);
                }
            } else {
                add("../");
                fc = (FileConnection) Connector.open("file:///" + dir, Connector.READ);
                e = fc.list();
                while (e.hasMoreElements()) {
                    String n = (String) e.nextElement();
                    String low = n.toLowerCase();
                    if (n.endsWith("/") || low.endsWith(".mid") || low.endsWith(".midi")) add(n);
                }
                fc.close();
            }
        } catch (Throwable t) {
            err = "" + t;
        }
        if (err != null) fail(err);
    }

    void fail(String m) {
        Alert a = new Alert("MobileFruity", m, null, AlertType.ERROR);
        a.setTimeout(3000);
        Display.getDisplay(midlet).setCurrent(a, this);
    }

    void up() {
        if (dir.length() == 0) {
            Display.getDisplay(midlet).setCurrent(owner);
            return;
        }
        String s = dir.substring(0, dir.length() - 1);
        int k = s.lastIndexOf('/');
        dir = k < 0 ? "" : s.substring(0, k + 1);
        fill();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdBack) {
            up();
            return;
        }
        int i = getSelectedIndex();
        if (i < 0 || i >= names.size()) return;
        String n = (String) names.elementAt(i);
        if (n.equals("../")) {
            up();
        } else if (dir.length() == 0) {
            dir = n;
            fill();
        } else if (n.endsWith("/")) {
            dir = dir + n;
            fill();
        } else {
            load(dir + n);
        }
    }

    void load(String path) {
        FileConnection fc = null;
        InputStream is = null;
        byte[] buf = null;
        String err = null;
        try {
            fc = (FileConnection) Connector.open("file:///" + path, Connector.READ);
            int size = (int) fc.fileSize();
            if (size <= 0 || size > 100000) {
                err = "\u0424\u0430\u0439\u043b \u043f\u0443\u0441\u0442\u043e\u0439 \u0438\u043b\u0438 \u0431\u043e\u043b\u044c\u0448\u0435 100 \u041a\u0411";
            } else {
                buf = new byte[size];
                is = fc.openInputStream();
                int off = 0;
                while (off < size) {
                    int n = is.read(buf, off, size - off);
                    if (n < 0) break;
                    off += n;
                }
                is.close();
            }
            fc.close();
        } catch (Throwable t) {
            err = "" + t;
        }
        if (err != null) {
            fail(err);
        } else {
            owner.importMidi(buf);
        }
    }
}
