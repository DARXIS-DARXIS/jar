import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

public class MobileFruity extends MIDlet {
    FruityCanvas c;

    protected void startApp() {
        if (c == null) {
            c = new FruityCanvas(this);
        }
        Display.getDisplay(this).setCurrent(c);
    }

    protected void pauseApp() {
        if (c != null) c.stopPlay();
    }

    protected void destroyApp(boolean unconditional) {
        if (c != null) c.stopPlay();
    }
}
