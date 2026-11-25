import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.media.*;
import java.io.*;
import java.util.*;

/*
 * MobileFruity - FL Studio parody for J2ME (CLDC 1.0 / MIDP 2.0)
 *
 * KEYS (same idea in both screens):
 *   arrows  move cursor,  FIRE  put/remove (note or pattern block)
 *   5       switch between the grid and the top buttons (selected button is bright yellow)
 *           on the buttons: left/right = next button, up/down = next row, FIRE = press
 *   4 / 6   previous / next pattern
 *   7 / 8   pattern length -/+4 steps
 *   0 or #  play / stop
 *   playlist:   1 / 2 shrink / stretch block under cursor, 3 import .mid, 9 export
 *   piano roll: 1 / 2 note length -/+, 3 / 9 instrument -/+ (after 128 comes the drum kit)
 * TEMPO: select the BPM number with 5 + arrows and type digits, FIRE / arrows confirm,
 *        CLEAR = backspace (a first 5 leaves the buttons, any other digit starts typing).
 * Touch: all buttons are tappable, drag the right edge of a playlist block to stretch it,
 *        tap the tempo number for an input box.
 * Left soft key = Playlist, right soft key = Piano roll.
 */
public class FruityCanvas extends Canvas implements CommandListener {
    static final int TRACKS = 8, COLS = 32, PATS = 8;
    static final int LOWP = 24, HIGHP = 108; // C1 .. C8
    static final int MAXLEN = 256; // 16 bars

    static final String[] NN = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    static final int DRUM = 128, DLO = 35, DHI = 81; // prog 128 = drum kit on channel 10
    static final String[] DN = {"Kick2", "Kick", "Stick", "Snare", "Clap", "Snr2", "FTom2",
            "HatC", "FTom", "HatP", "Tom3", "HatO", "Tom2", "Tom1", "Crash", "Tom0",
            "Ride", "China", "RBell", "Tamb", "Splsh", "Cowbl", "Crsh2", "Vibra",
            "Ride2", "BongH", "BongL", "CongM", "CongH", "CongL", "TimbH", "TimbL",
            "AgogH", "AgogL", "Cabas", "Marac", "WhisS", "WhisL", "GuirS", "GuirL",
            "Claves", "WoodH", "WoodL", "CuicM", "CuicO", "TriM", "TriO"};
    int dnW;

    static final String[] FAM = {"Piano", "Chrom.perc", "Organ", "Guitar", "Bass", "Strings",
            "Ensemble", "Brass", "Reed", "Pipe", "Syn lead", "Syn pad", "Syn fx",
            "Ethnic", "Percuss.", "SFX"};
    static final int[] PCOL = {0xE05050, 0xE09040, 0xD0D040, 0x60C060,
            0x40C0C0, 0x5080E0, 0x9060E0, 0xD060B0};

    static final int C_BG = 0x383838, C_TXT = 0xFFFFFF, C_DIM = 0xA0A0A0;
    static final int C_BTN = 0x505050, C_ACT = 0x3C5C9C, C_LINE = 0x202020;
    static final int C_CELL = 0x585858, C_CELL2 = 0x646464;
    static final int C_ROWW = 0x505050, C_ROWB = 0x404040;
    static final int C_LINE2 = 0x383838, C_BEAT = 0x282828, C_BAR = 0x101010, C_OUT = 0x2A2A2A;

    MIDlet midlet;
    Font f;
    int fh;

    // song
    int mode = 0; // 0 playlist, 1 piano roll
    byte[][] pl = new byte[TRACKS][COLS]; // 0 = empty, 1..8 = pattern
    byte[][] pln = new byte[TRACKS][COLS]; // block length in bars (columns)
    Vector[] notes = new Vector[PATS];    // each element: int[]{start, pitch, len}
    int[] plen = new int[PATS];
    int[] prog = new int[PATS];
    int curPat = 0, bpm = 120;

    // playlist cursor/view
    int plR, plC, plViewC;
    // piano roll cursor/view
    int pStep, pPitch = 60, viewStep, viewPitch = 71, noteLen = 4;

    // layout
    int w, h, tbH, patH, botH, gridY0, gridY1, labelW;
    int colW = 24, rowH, visC;
    int cellW = 12, cellH, visRows, visSteps;

    // focus: 0 grid, 1 buttons (key 5 toggles); frow = button row, topIdx = button in the row
    int focus = 0;
    int frow = 0, topIdx = 0;
    static final int T_EXPORT = 0, T_MINUS = 1, T_PLUS = 2, T_PLAY = 3, T_BPM = 4, T_IMPORT = 13;
    static final int B_LENM = 5, B_LENP = 6, B_INSM = 7, B_INSP = 8,
            B_NOTM = 9, B_NOTP = 10, B_PATM = 11, B_PATP = 12;
    static final int[] ROW_PL = {T_EXPORT, T_IMPORT, T_MINUS, T_BPM, T_PLUS, T_PLAY};
    static final int[] ROW_P0 = {B_LENM, B_LENP, T_MINUS, T_BPM, T_PLUS, T_PLAY};
    static final int[] ROW_P1 = {B_INSM, B_INSP, B_NOTM, B_NOTP, B_PATM, B_PATP};
    static final int C_FOC = 0xE0B000;
    String bpmEdit = ""; // digits typed so far
    int toolY, toolH, plMX, plLX, plPX, plLW;
    String plLabel = "";
    Command cmdOk, cmdCancel;
    TextBox tbBpm;
    int exX, exW, imX, imW, minusX, bpmX, bpmW, plusX, playX, plW, sW;
    String exLab = "", imLab = "";

    // playback
    Player player;
    Timer timer;
    boolean playing;
    long t0;
    int endSteps, playOnly = -1;

    // pointer
    boolean pgrid, pmoved, pstretch;
    int pstR, pstS;
    int pdx, pdy, pvc, pvs, pvp;

    public FruityCanvas(MIDlet m) {
        midlet = m;
        f = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        fh = f.getHeight();
        for (int i = 0; i < DN.length; i++) {
            int dw = f.stringWidth(DN[i]);
            if (dw > dnW) dnW = dw;
        }
        for (int i = 0; i < PATS; i++) {
            notes[i] = new Vector();
            plen[i] = 16;
        }
        try {
            setFullScreenMode(true);
        } catch (Throwable t) {
        }
    }

    // ------------------------------------------------------------ layout
    void layout() {
        w = getWidth();
        h = getHeight();
        fh = f.getHeight();
        tbH = fh + 4;
        patH = fh + 4;
        botH = fh + 6;
        gridY1 = h - botH - patH;
        if (mode == 0) {
            gridY0 = tbH + fh + 2;
            labelW = f.stringWidth("7") + 6;
            rowH = (gridY1 - gridY0) / TRACKS;
            if (rowH < 1) rowH = 1;
            visC = (w - labelW) / colW;
            if (visC < 1) visC = 1;
        } else {
            toolH = fh + 4;
            toolY = tbH + fh + 2;
            gridY0 = toolY + toolH + 2;
            labelW = Math.max(f.stringWidth("C#8"), isDrum(curPat) ? dnW : 0) + 4;
            cellH = fh;
            visRows = (gridY1 - gridY0) / cellH;
            if (visRows < 1) visRows = 1;
            visSteps = (w - labelW) / cellW;
            if (visSteps < 1) visSteps = 1;
        }
        exX = 1;
        exW = f.stringWidth("\u042d\u043a\u0441\u043f\u043e\u0440\u0442") + 8;
        plW = f.stringWidth("Stop") + 8;
        sW = f.stringWidth("+") + 12;
        bpmW = f.stringWidth("240") + 6;
        playX = w - plW - 1;
        plusX = playX - sW - 1;
        bpmX = plusX - bpmW;
        minusX = bpmX - sW;
        exLab = "\u042d\u043a\u0441\u043f\u043e\u0440\u0442";
        imLab = "\u0418\u043c\u043f\u043e\u0440\u0442";
        for (int tr = 0; tr < 3; tr++) {
            if (tr == 1) {
                exLab = "\u042d\u043a\u0441\u043f";
                imLab = "\u0418\u043c\u043f";
            } else if (tr == 2) {
                exLab = "\u042d";
                imLab = "\u0418";
            }
            exW = f.stringWidth(exLab) + 8;
            imW = f.stringWidth(imLab) + 8;
            if (exX + exW + 1 + imW <= minusX - 1) break;
        }
        imX = exX + exW + 1;
        // piano roll: pattern length group in the top bar
        plLabel = "\u0414\u043b\u0438\u043d\u0430 " + plen[curPat];
        plLW = f.stringWidth(plLabel) + 6;
        if (1 + sW + plLW + sW > minusX - 2) {
            plLabel = "" + plen[curPat];
            plLW = f.stringWidth(plLabel) + 6;
        }
        plMX = 1;
        plLX = plMX + sW;
        plPX = plLX + plLW;
    }

    // ------------------------------------------------------------ painting
    void txt(Graphics g, String s, int x, int y, int col) {
        g.setColor(col);
        g.drawString(s, x, y, Graphics.TOP | Graphics.LEFT);
    }

    void btn(Graphics g, int x, int y, int bw, int bh, String s, boolean act) {
        g.setColor(act ? C_ACT : C_BTN);
        g.fillRect(x, y, bw, bh);
        g.setColor(C_LINE);
        g.drawRect(x, y, bw - 1, bh - 1);
        g.setColor(C_TXT);
        g.drawString(s, x + bw / 2, y + (bh - fh) / 2, Graphics.TOP | Graphics.HCENTER);
    }

    String playLabel() {
        return playing ? "Stop" : "Play";
    }

    int rowCount() {
        return mode == 0 ? 1 : 2;
    }

    int[] rowItems(int r) {
        if (mode == 0) return ROW_PL;
        return r == 0 ? ROW_P0 : ROW_P1;
    }

    int curId() {
        return rowItems(frow)[topIdx];
    }

    void clampTop() {
        if (frow >= rowCount()) frow = rowCount() - 1;
        if (frow < 0) frow = 0;
        int n = rowItems(frow).length;
        if (topIdx >= n) topIdx = n - 1;
        if (topIdx < 0) topIdx = 0;
    }

    int bx(int id) {
        int gwid = w / 3;
        switch (id) {
            case T_EXPORT: return exX;
            case T_IMPORT: return imX;
            case T_MINUS: return minusX;
            case T_PLUS: return plusX;
            case T_BPM: return bpmX;
            case T_PLAY: return playX;
            case B_LENM: return plMX;
            case B_LENP: return plPX;
            case B_INSM: return 1;
            case B_INSP: return gwid - sW - 1;
            case B_NOTM: return gwid + 1;
            case B_NOTP: return 2 * gwid - sW - 1;
            case B_PATM: return 2 * gwid + 1;
            default: return 3 * gwid - sW - 1;
        }
    }

    int bw(int id) {
        switch (id) {
            case T_EXPORT: return exW;
            case T_IMPORT: return imW;
            case T_BPM: return bpmW;
            case T_PLAY: return plW;
            default: return sW;
        }
    }

    int by(int id) {
        return id >= B_INSM ? toolY : 1;
    }

    int bh(int id) {
        return id >= B_INSM ? toolH : tbH - 2;
    }

    boolean foc(int id) {
        return focus == 1 && curId() == id;
    }

    boolean bpmSelected() {
        return focus == 1 && curId() == T_BPM;
    }

    /** button under the pointer, or -1 */
    int hitButton(int x, int y) {
        for (int r = 0; r < rowCount(); r++) {
            int[] it = rowItems(r);
            for (int i = 0; i < it.length; i++) {
                int id = it[i];
                if (x < bx(id) || x >= bx(id) + bw(id)) continue;
                boolean iny = id >= B_INSM ? (y >= toolY - 1 && y < toolY + toolH + 1) : y < tbH;
                if (iny) return id;
            }
        }
        return -1;
    }

    void press(int id) {
        switch (id) {
            case T_EXPORT: doExport(); break;
            case T_IMPORT: doImport(); break;
            case T_MINUS: bpmChange(-5); break;
            case T_PLUS: bpmChange(5); break;
            case T_PLAY: togglePlay(); break;
            case B_LENM: plenChange(-4); break;
            case B_LENP: plenChange(4); break;
            case B_INSM: progChange(-1); break;
            case B_INSP: progChange(1); break;
            case B_NOTM: lenChange(-1); break;
            case B_NOTP: lenChange(1); break;
            case B_PATM: patChange(-1); break;
            case B_PATP: patChange(1); break;
        }
    }

    void bpmChange(int d) {
        commitBpm();
        if (playing) return;
        bpm += d;
        if (bpm < 40) bpm = 40;
        if (bpm > 240) bpm = 240;
    }

    void commitBpm() {
        if (bpmEdit.length() == 0) return;
        int v = 0;
        try {
            v = Integer.parseInt(bpmEdit);
        } catch (Exception e) {
        }
        bpmEdit = "";
        if (v < 40) v = 40;
        if (v > 240) v = 240;
        bpm = v;
    }

    void bpmDigit(int d) {
        if (bpmEdit.length() >= 3) bpmEdit = "";
        bpmEdit = bpmEdit + d;
        if (bpmEdit.length() >= 3) commitBpm();
    }

    boolean isDrum(int p) {
        return prog[p] == DRUM;
    }

    /** MIDI channel of a pattern: drums always use channel 10 (index 9) */
    int ch(int p) {
        return prog[p] == DRUM ? 9 : p;
    }

    String pname(int pitch) {
        if (isDrum(curPat) && pitch >= DLO && pitch <= DHI) return DN[pitch - DLO];
        return name(pitch);
    }

    /** keep the cursor on a real drum when the pattern is a drum pattern */
    void fixDrumPitch() {
        if (isDrum(curPat) && (pPitch < DLO || pPitch > DHI)) pPitch = 36;
    }

    static boolean isBlack(int p) {
        int n = p % 12;
        return n == 1 || n == 3 || n == 6 || n == 8 || n == 10;
    }

    static String name(int p) {
        return NN[p % 12] + (p / 12 - 1);
    }

    long sx() { // elapsed steps * 1000
        return (System.currentTimeMillis() - t0) * bpm / 15;
    }

    protected void paint(Graphics g) {
        layout();
        g.setClip(0, 0, w, h);
        g.setColor(C_BG);
        g.fillRect(0, 0, w, h);
        g.setFont(f);
        if (mode == 0) paintPlaylist(g);
        else paintPiano(g);
        paintTop(g);
        paintPatBar(g);
        paintTabs(g);
    }

    /** button; the selected one is drawn bright yellow with a thick border */
    void tbtn(Graphics g, int id, String s, boolean act) {
        int x = bx(id), y = by(id), bwi = bw(id), bhi = bh(id);
        if (foc(id)) {
            g.setColor(C_FOC);
            g.fillRect(x, y, bwi, bhi);
            g.setColor(0xFFFF00);
            g.drawRect(x, y, bwi - 1, bhi - 1);
            g.drawRect(x + 1, y + 1, bwi - 3, bhi - 3);
            g.setColor(0x000000);
        } else {
            g.setColor(act ? C_ACT : C_BTN);
            g.fillRect(x, y, bwi, bhi);
            g.setColor(C_LINE);
            g.drawRect(x, y, bwi - 1, bhi - 1);
            g.setColor(C_TXT);
        }
        g.drawString(s, x + bwi / 2, y + (bhi - fh) / 2, Graphics.TOP | Graphics.HCENTER);
    }

    void paintTop(Graphics g) {
        if (mode == 0) {
            tbtn(g, T_EXPORT, exLab, false);
            tbtn(g, T_IMPORT, imLab, false);
            String t = "\u041f\u043b\u0435\u0439\u043b\u0438\u0441\u0442";
            int tx = imX + imW + 4;
            if (tx + f.stringWidth(t) < minusX) txt(g, t, tx, (tbH - fh) / 2, C_DIM);
        } else {
            tbtn(g, B_LENM, "-", false);
            g.setColor(C_TXT);
            g.drawString(plLabel, plLX + plLW / 2, (tbH - fh) / 2, Graphics.TOP | Graphics.HCENTER);
            tbtn(g, B_LENP, "+", false);
        }
        tbtn(g, T_MINUS, "-", false);
        tbtn(g, T_BPM, bpmEdit.length() > 0 ? bpmEdit + "_" : "" + bpm, false);
        tbtn(g, T_PLUS, "+", false);
        tbtn(g, T_PLAY, playLabel(), playing);
    }

    /** default length (bars) of a pattern block = pattern length rounded up to bars */
    int span(int p) {
        return (plen[p] + 15) / 16;
    }

    /** start column of the block covering column c in row r, or -1 */
    int blockAt(int r, int c) {
        for (int k = c; k >= 0; k--) {
            if (pl[r][k] != 0) return (k + pln[r][k] > c) ? k : -1;
        }
        return -1;
    }

    /** how many columns a block starting at s may span before the next block / the end */
    int maxBlockLen(int r, int s) {
        int m = COLS - s;
        for (int k = s + 1; k < COLS; k++) {
            if (pl[r][k] != 0) {
                m = k - s;
                break;
            }
        }
        return m;
    }

    void paintBlocks(Graphics g, int r, int y) {
        int vis = visC;
        if (plViewC + vis > COLS) vis = COLS - plViewC;
        int x0 = labelW;
        int x1 = labelW + vis * colW;
        for (int c = 0; c < COLS; c++) {
            int v = pl[r][c];
            if (v == 0) continue;
            int bx = labelW + (c - plViewC) * colW;
            int bx2 = bx + pln[r][c] * colW;
            if (bx2 <= x0 || bx >= x1) continue;
            int cx = bx < x0 ? x0 : bx;
            int cx2 = bx2 > x1 ? x1 : bx2;
            g.setColor(PCOL[v - 1]);
            g.fillRect(cx + 1, y + 1, cx2 - cx - 1, rowH - 1);
            // marks where the pattern repeats inside a stretched block
            int lw = plen[v - 1] * colW / 16;
            if (lw >= 4) {
                g.setColor((PCOL[v - 1] >> 1) & 0x7F7F7F);
                for (int lx = bx + lw; lx < bx2 - 1; lx += lw) {
                    if (lx > x0 && lx < x1) g.drawLine(lx, y + 1, lx, y + rowH - 1);
                }
            }
            g.setColor(0x000000);
            g.drawString("\u041f" + v, cx + 3, y + (rowH - fh) / 2, Graphics.TOP | Graphics.LEFT);
            // stretch handle on the right edge
            if (bx2 <= x1 && bx2 - bx >= 6) {
                g.setColor(0x202020);
                g.fillRect(bx2 - 4, y + 3, 2, rowH - 5);
            }
        }
    }

    void paintPlaylist(Graphics g) {
        for (int i = 0; i < visC; i++) {
            int c = plViewC + i;
            if (c >= COLS) break;
            txt(g, "" + (c + 1), labelW + i * colW + 2, tbH, C_DIM);
        }
        for (int r = 0; r < TRACKS; r++) {
            int y = gridY0 + r * rowH;
            txt(g, "" + (r + 1), 2, y + (rowH - fh) / 2, C_TXT);
            for (int i = 0; i < visC; i++) {
                int c = plViewC + i;
                if (c >= COLS) break;
                int x = labelW + i * colW;
                g.setColor(C_LINE);
                g.fillRect(x, y, colW, rowH);
                g.setColor((c & 3) == 0 ? C_CELL2 : C_CELL);
                g.fillRect(x + 1, y + 1, colW - 1, rowH - 1);
            }
            paintBlocks(g, r, y);
        }
        // cursor
        int ci = plC - plViewC;
        if (ci >= 0 && ci < visC) {
            g.setColor(focus == 0 ? 0xFFFF00 : 0x909090);
            g.drawRect(labelW + ci * colW, gridY0 + plR * rowH, colW - 1, rowH - 1);
        }
        // playhead
        if (playing && playOnly < 0) {
            int x = labelW + (int) (sx() * colW / 16000) - plViewC * colW;
            if (x >= labelW && x < w) {
                g.setColor(0xFF4040);
                g.drawLine(x, gridY0, x, gridY0 + TRACKS * rowH);
            }
        }
    }

    void paintPiano(Graphics g) {
        int pr = prog[curPat];
        String inf = pr == DRUM ? "\u0414\u0440\u0430\u043c\u044b (\u043a\u0430\u043d\u0430\u043b 10)" : "Prg " + (pr + 1) + " " + FAM[pr >> 3];
        txt(g, "\u041f" + (curPat + 1) + "  " + inf, 3, tbH + 1, C_DIM);
        paintTool(g);

        g.setClip(0, gridY0, w, gridY1 - gridY0);
        for (int i = 0; i < visRows; i++) {
            int p = viewPitch - i;
            if (p < LOWP) break;
            int y = gridY0 + i * cellH;
            boolean blk = !isDrum(curPat) && isBlack(p);
            g.setColor(blk ? 0x202020 : 0xE8E8E8);
            g.fillRect(0, y, labelW, cellH);
            txt(g, pname(p), 1, y, blk ? 0xCCCCCC : 0x000000);
            g.setColor(blk ? C_ROWB : C_ROWW);
            g.fillRect(labelW, y, w - labelW, cellH);
            g.setColor(C_LINE);
            g.drawLine(labelW, y + cellH - 1, w, y + cellH - 1);
        }
        for (int s = 0; s < visSteps; s++) {
            int st = viewStep + s;
            int x = labelW + s * cellW;
            if (st >= plen[curPat]) {
                g.setColor(C_OUT);
                g.fillRect(x, gridY0, cellW, gridY1 - gridY0);
            }
            g.setColor((st & 15) == 0 ? C_BAR : ((st & 3) == 0 ? C_BEAT : C_LINE2));
            g.drawLine(x, gridY0, x, gridY1 - 1);
        }
        Vector nv = notes[curPat];
        for (int i = 0; i < nv.size(); i++) {
            int[] n = (int[]) nv.elementAt(i);
            int row = viewPitch - n[1];
            if (row < 0 || row >= visRows) continue;
            if (n[0] + n[2] <= viewStep || n[0] >= viewStep + visSteps) continue;
            int x = labelW + (n[0] - viewStep) * cellW;
            int y = gridY0 + row * cellH;
            int nw = n[2] * cellW;
            g.setColor(0xB8F050);
            g.fillRect(x, y, nw, cellH - 1);
            g.setColor(0x305010);
            g.drawRect(x, y, nw - 1, cellH - 2);
            String nm = pname(n[1]);
            if (nw >= f.stringWidth(nm) + 4) {
                g.drawString(nm, x + 2, y, Graphics.TOP | Graphics.LEFT);
            }
        }
        // cursor
        int crow = viewPitch - pPitch;
        if (crow >= 0 && crow < visRows) {
            g.setColor(focus == 0 ? 0xFFFF00 : 0x909090);
            g.drawRect(labelW + (pStep - viewStep) * cellW, gridY0 + crow * cellH,
                    noteLen * cellW - 1, cellH - 2);
        }
        // playhead
        if (playing && playOnly >= 0) {
            int x = labelW + (int) (sx() * cellW / 1000) - viewStep * cellW;
            if (x >= labelW && x < w) {
                g.setColor(0xFF4040);
                g.drawLine(x, gridY0, x, gridY1 - 1);
            }
        }
        g.setClip(0, 0, w, h);
    }

    /** second row: instrument | note length | pattern */
    void paintTool(Graphics g) {
        int gwid = w / 3;
        tbtn(g, B_INSM, "<", false);
        tbtn(g, B_INSP, ">", false);
        tbtn(g, B_NOTM, "-", false);
        tbtn(g, B_NOTP, "+", false);
        tbtn(g, B_PATM, "<", false);
        tbtn(g, B_PATP, ">", false);
        for (int i = 0; i < 3; i++) {
            int gx = i * gwid;
            String full, shrt;
            if (i == 0) {
                if (isDrum(curPat)) {
                    full = "\u0414\u0440\u0430\u043c\u044b";
                    shrt = "\u0414\u0440";
                } else {
                    full = "\u0418\u043d\u0441\u0442\u0440 " + (prog[curPat] + 1);
                    shrt = "" + (prog[curPat] + 1);
                }
            } else if (i == 1) {
                full = "\u041d\u043e\u0442\u0430 " + noteLen;
                shrt = "" + noteLen;
            } else {
                full = "\u041f\u0430\u0442\u0442 " + (curPat + 1);
                shrt = "" + (curPat + 1);
            }
            int room = gwid - 2 * sW - 4;
            String t = f.stringWidth(full) <= room ? full : shrt;
            g.setColor(i == 2 ? PCOL[curPat] : C_TXT);
            g.drawString(t, gx + gwid / 2, toolY + (toolH - fh) / 2, Graphics.TOP | Graphics.HCENTER);
        }
    }

    void paintPatBar(Graphics g) {
        int cw = w / PATS;
        for (int i = 0; i < PATS; i++) {
            boolean a = (i == curPat);
            int x = i * cw;
            g.setColor(a ? PCOL[i] : C_BTN);
            g.fillRect(x, gridY1, cw - 1, patH - 1);
            g.setColor(a ? 0x000000 : C_TXT);
            g.drawString("\u041f" + (i + 1), x + cw / 2, gridY1 + (patH - fh) / 2,
                    Graphics.TOP | Graphics.HCENTER);
        }
    }

    void paintTabs(Graphics g) {
        int y = h - botH;
        btn(g, 0, y, w / 2 - 1, botH, "\u041f\u043b\u0435\u0439\u043b\u0438\u0441\u0442", mode == 0);
        btn(g, w / 2, y, w - w / 2, botH, "\u041f\u0438\u0430\u043d\u043e \u0440\u043e\u043b\u043b", mode == 1);
    }

    // ------------------------------------------------------------ note editing
    int findNote(int step, int pitch) {
        Vector nv = notes[curPat];
        for (int i = 0; i < nv.size(); i++) {
            int[] n = (int[]) nv.elementAt(i);
            if (n[1] == pitch && step >= n[0] && step < n[0] + n[2]) return i;
        }
        return -1;
    }

    int maxLen(int start, int pitch, int[] skip) {
        int m = plen[curPat] - start;
        Vector nv = notes[curPat];
        for (int i = 0; i < nv.size(); i++) {
            int[] n = (int[]) nv.elementAt(i);
            if (n != skip && n[1] == pitch && n[0] > start && n[0] - start < m) m = n[0] - start;
        }
        return m;
    }

    void toggleNote() {
        int idx = findNote(pStep, pPitch);
        if (idx >= 0) {
            notes[curPat].removeElementAt(idx);
            return;
        }
        if (pStep >= plen[curPat]) return;
        int l = noteLen;
        int mx = maxLen(pStep, pPitch, null);
        if (l > mx) l = mx;
        if (l < 1) return;
        notes[curPat].addElement(new int[]{pStep, pPitch, l});
        if (isDrum(curPat)) return;
        try {
            Manager.playTone(pPitch, 200, 90);
        } catch (Throwable t) {
        }
    }

    void lenChange(int d) {
        int idx = findNote(pStep, pPitch);
        if (idx >= 0) {
            int[] n = (int[]) notes[curPat].elementAt(idx);
            int nl = n[2] + d;
            int mx = maxLen(n[0], n[1], n);
            if (nl > mx) nl = mx;
            if (nl < 1) nl = 1;
            n[2] = nl;
            noteLen = nl;
        } else {
            noteLen += d;
            if (noteLen < 1) noteLen = 1;
            if (noteLen > 32) noteLen = 32;
        }
    }

    void plenChange(int d) {
        int n = plen[curPat] + d;
        if (n < 4) n = 4;
        if (n > MAXLEN) n = MAXLEN;
        plen[curPat] = n; // notes past the end are kept but not played
        if (pStep >= n) pStep = n - 1;
        ensureVisible();
    }

    void patChange(int d) {
        int n = curPat + d;
        if (n < 0) n = 0;
        if (n >= PATS) n = PATS - 1;
        curPat = n;
        if (pStep >= plen[curPat]) pStep = plen[curPat] - 1;
        fixDrumPitch();
        if (mode == 1) ensureVisible();
    }

    /** instruments 1..128, then the drum kit */
    void progChange(int d) {
        prog[curPat] = (prog[curPat] + d + 129) % 129;
        fixDrumPitch();
        if (mode == 1) ensureVisible();
    }

    /** playlist: shrink / stretch the block under the cursor by whole bars */
    void stretchChange(int d) {
        int st = blockAt(plR, plC);
        if (st < 0) return;
        int nl = pln[plR][st] + d;
        int mx = maxBlockLen(plR, st);
        if (nl > mx) nl = mx;
        if (nl < 1) nl = 1;
        pln[plR][st] = (byte) nl;
    }

    void ensureVisible() {
        layout();
        if (pStep < viewStep) viewStep = pStep;
        if (pStep >= viewStep + visSteps) viewStep = pStep - visSteps + 1;
        if (viewStep < 0) viewStep = 0;
        if (pPitch > viewPitch) viewPitch = pPitch;
        if (pPitch <= viewPitch - visRows) viewPitch = pPitch + visRows - 1;
        if (viewPitch > HIGHP) viewPitch = HIGHP;
    }

    // ------------------------------------------------------------ input
    protected void keyPressed(int k) {
        onKey(k);
        repaint();
    }

    protected void keyRepeated(int k) {
        onKey(k);
        repaint();
    }

    void onKey(int k) {
        if (k == -6 || k == -21) {
            setMode(0);
            return;
        }
        if (k == -7 || k == -22) {
            setMode(1);
            return;
        }
        if (k >= KEY_NUM0 && k <= KEY_NUM9) {
            int d = k - KEY_NUM0;
            if (!playing && bpmSelected() && !(d == 5 && bpmEdit.length() == 0)) bpmDigit(d);
            else digit(d);
            return;
        }
        if (k == -8 && bpmSelected() && bpmEdit.length() > 0) { // CLEAR = backspace
            bpmEdit = bpmEdit.substring(0, bpmEdit.length() - 1);
            return;
        }
        if (k == KEY_STAR) {
            return;
        }
        if (k == KEY_POUND) {
            togglePlay();
            return;
        }
        int ga;
        try {
            ga = getGameAction(k);
        } catch (Exception e) {
            return;
        }
        if (ga == 0) return;
        if (ga == FIRE) {
            if (focus == 0) action();
            else fireFocus();
            return;
        }
        int dx = 0, dy = 0;
        if (ga == LEFT) dx = -1;
        else if (ga == RIGHT) dx = 1;
        else if (ga == UP) dy = -1;
        else if (ga == DOWN) dy = 1;
        if (focus == 0) move(dx, dy);
        else moveFocus(dx, dy);
    }

    void toggleFocus() {
        commitBpm();
        if (focus == 0) {
            focus = 1;
            clampTop();
        } else {
            focus = 0;
        }
    }

    void moveFocus(int dx, int dy) {
        commitBpm();
        if (dy != 0) {
            if (dy > 0) {
                if (frow < rowCount() - 1) {
                    frow++;
                    clampTop();
                } else {
                    focus = 0; // down from the last row: back to the grid
                }
            } else if (frow > 0) {
                frow--;
                clampTop();
            }
            return;
        }
        topIdx += dx;
        clampTop();
    }

    void fireFocus() {
        int id = curId();
        if (id == T_BPM) {
            commitBpm();
            focus = 0;
        } else {
            press(id);
        }
    }

    void setMode(int m) {
        commitBpm();
        mode = m;
        focus = 0;
        frow = 0;
        topIdx = 0;
        layout();
        if (m == 1) ensureVisible();
        repaint();
    }

    void digit(int d) {
        if (d == 5) {
            toggleFocus();
            return;
        }
        if (d == 0) {
            togglePlay();
            return;
        }
        switch (d) {
            case 4: patChange(-1); break;
            case 6: patChange(1); break;
            case 7: plenChange(-4); break;
            case 8: plenChange(4); break;
        }
        if (mode == 0) {
            switch (d) {
                case 1: stretchChange(-1); break;
                case 2: stretchChange(1); break;
                case 3: doImport(); break;
                case 9: doExport(); break;
            }
        } else {
            switch (d) {
                case 1: lenChange(-1); break;
                case 2: lenChange(1); break;
                case 3: progChange(-1); break;
                case 9: progChange(1); break;
            }
        }
    }

    void action() {
        if (mode == 0) {
            int v = curPat + 1;
            int st = blockAt(plR, plC);
            if (st >= 0) {
                if (st == plC && pl[plR][st] != v) pl[plR][st] = (byte) v; // swap pattern, keep length
                else pl[plR][st] = 0;                                      // remove block
            } else {
                int l = span(curPat);
                int mx = maxBlockLen(plR, plC);
                if (l > mx) l = mx;
                pl[plR][plC] = (byte) v;
                pln[plR][plC] = (byte) l;
            }
        } else {
            toggleNote();
        }
    }

    void move(int dx, int dy) {
        if (mode == 0) {
            plC += dx;
            if (plC < 0) plC = 0;
            if (plC >= COLS) plC = COLS - 1;
            plR += dy;
            if (plR < 0) plR = 0;
            if (plR >= TRACKS) plR = TRACKS - 1;
            if (plC < plViewC) plViewC = plC;
            if (plC >= plViewC + visC) plViewC = plC - visC + 1;
        } else {
            pStep += dx;
            if (pStep < 0) pStep = 0;
            if (pStep >= plen[curPat]) pStep = plen[curPat] - 1;
            pPitch -= dy;
            if (pPitch < LOWP) pPitch = LOWP;
            if (pPitch > HIGHP) pPitch = HIGHP;
            ensureVisible();
        }
    }

    // ------------------------------------------------------------ pointer
    protected void pointerPressed(int x, int y) {
        layout();
        commitBpm();
        pgrid = false;
        pmoved = false;
        pstretch = false;
        int id = hitButton(x, y);
        if (id >= 0) {
            if (id == T_BPM) editBpmBox();
            else press(id);
            repaint();
            return;
        }
        if (y >= h - botH) {
            setMode(x < w / 2 ? 0 : 1);
        } else if (y >= gridY1) {
            curPat = x * PATS / w;
            if (curPat >= PATS) curPat = PATS - 1;
            if (pStep >= plen[curPat]) pStep = plen[curPat] - 1;
        } else if (y >= gridY0 && x >= labelW) {
            pgrid = true;
            pdx = x;
            pdy = y;
            pvc = plViewC;
            pvs = viewStep;
            pvp = viewPitch;
            if (mode == 0) { // last column of a block = stretch handle
                int r = (y - gridY0) / rowH;
                int c = plViewC + (x - labelW) / colW;
                if (r >= 0 && r < TRACKS && c >= 0 && c < COLS) {
                    int st = blockAt(r, c);
                    if (st >= 0 && st + pln[r][st] - 1 == c) {
                        pstretch = true;
                        pstR = r;
                        pstS = st;
                    }
                }
            }
        }
        repaint();
    }

    /** touch: tap on the tempo number opens a numeric input box */
    void editBpmBox() {
        if (playing) return;
        try {
            tbBpm = new TextBox("BPM (40-240)", "" + bpm, 3, TextField.NUMERIC);
            cmdOk = new Command("OK", Command.OK, 1);
            cmdCancel = new Command("\u041e\u0442\u043c\u0435\u043d\u0430", Command.CANCEL, 2);
            tbBpm.addCommand(cmdOk);
            tbBpm.addCommand(cmdCancel);
            tbBpm.setCommandListener(this);
            Display.getDisplay(midlet).setCurrent(tbBpm);
        } catch (Throwable t) {
            tbBpm = null;
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdOk && d == tbBpm) {
            bpmEdit = tbBpm.getString().trim();
            commitBpm();
        }
        tbBpm = null;
        Display.getDisplay(midlet).setCurrent(this);
        repaint();
    }

    protected void pointerDragged(int x, int y) {
        if (!pgrid) return;
        if (!pmoved && Math.abs(x - pdx) + Math.abs(y - pdy) < 8) return;
        pmoved = true;
        if (pstretch) {
            int c = plViewC + (x - labelW) / colW;
            int nl = c - pstS + 1;
            int mx = maxBlockLen(pstR, pstS);
            if (nl > mx) nl = mx;
            if (nl < 1) nl = 1;
            pln[pstR][pstS] = (byte) nl;
            plR = pstR;
            plC = pstS;
            repaint();
            return;
        }
        if (mode == 0) {
            int v = pvc - (x - pdx) / colW;
            int mx = COLS - visC;
            if (mx < 0) mx = 0;
            if (v < 0) v = 0;
            if (v > mx) v = mx;
            plViewC = v;
        } else {
            int s = pvs - (x - pdx) / cellW;
            int smx = MAXLEN - visSteps;
            if (smx < 0) smx = 0;
            if (s < 0) s = 0;
            if (s > smx) s = smx;
            viewStep = s;
            int p = pvp + (y - pdy) / cellH;
            int pmin = LOWP + visRows - 1;
            if (pmin > HIGHP) pmin = HIGHP;
            if (p < pmin) p = pmin;
            if (p > HIGHP) p = HIGHP;
            viewPitch = p;
        }
        repaint();
    }

    protected void pointerReleased(int x, int y) {
        if (pgrid && !pmoved) {
            if (mode == 0) {
                int r = (pdy - gridY0) / rowH;
                int c = plViewC + (pdx - labelW) / colW;
                if (r >= 0 && r < TRACKS && c >= 0 && c < COLS) {
                    plR = r;
                    plC = c;
                    action();
                }
            } else {
                int st = viewStep + (pdx - labelW) / cellW;
                int p = viewPitch - (pdy - gridY0) / cellH;
                if (p >= LOWP && p <= HIGHP && st >= 0 && st < plen[curPat]) {
                    pStep = st;
                    pPitch = p;
                    toggleNote();
                }
            }
        }
        pgrid = false;
        pstretch = false;
        repaint();
    }

    // ------------------------------------------------------------ MIDI
    /** adds pattern p at step base, filling blockSteps steps (the pattern loops / is cut) */
    int addPat(Vector[] on, Vector[] off, int p, int base, int blockSteps, int maxSteps) {
        int end = 0;
        int pe = plen[p];
        Vector nv = notes[p];
        for (int rep = 0; rep < blockSteps; rep += pe) {
            for (int i = 0; i < nv.size(); i++) {
                int[] n = (int[]) nv.elementAt(i);
                if (n[0] >= pe) continue;
                int len = n[2];
                if (n[0] + len > pe) len = pe - n[0];
                int s0 = rep + n[0];
                if (s0 >= blockSteps) continue;
                int e0 = s0 + len;
                if (e0 > blockSteps) e0 = blockSteps;
                int s = base + s0;
                int e = base + e0;
                if (e > maxSteps) e = maxSteps;
                if (s >= e) continue;
                if (on[s] == null) on[s] = new Vector();
                on[s].addElement(new int[]{p, n[1]});
                if (off[e] == null) off[e] = new Vector();
                off[e].addElement(new int[]{p, n[1]});
                if (e > end) end = e;
            }
        }
        return end;
    }

    static void vlq(ByteArrayOutputStream o, int v) {
        int b = v & 0x7F;
        while ((v >>= 7) > 0) {
            b <<= 8;
            b |= ((v & 0x7F) | 0x80);
        }
        while (true) {
            o.write(b & 0xFF);
            if ((b & 0x80) != 0) b >>= 8;
            else break;
        }
    }

    /** only < 0: whole song; otherwise just that pattern. Returns null if there are no notes. */
    byte[] build(int only) {
        commitBpm();
        int maxSteps = COLS * 16 + MAXLEN + 1;
        Vector[] on = new Vector[maxSteps + 1];
        Vector[] off = new Vector[maxSteps + 1];
        boolean[] used = new boolean[PATS];
        int end = 0;
        if (only >= 0) {
            int e = addPat(on, off, only, 0, plen[only], maxSteps);
            if (e > end) end = e;
            used[only] = true;
        } else {
            for (int r = 0; r < TRACKS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int v = pl[r][c];
                    if (v != 0) {
                        int e = addPat(on, off, v - 1, c * 16, pln[r][c] * 16, maxSteps);
                        if (e > end) end = e;
                        used[v - 1] = true;
                    }
                }
            }
        }
        if (end == 0) return null;
        endSteps = end;

        ByteArrayOutputStream tr = new ByteArrayOutputStream();
        int us = 60000000 / bpm;
        tr.write(0); tr.write(0xFF); tr.write(0x51); tr.write(3);
        tr.write(us >> 16); tr.write(us >> 8); tr.write(us);
        for (int p = 0; p < PATS; p++) {
            if (used[p] && prog[p] != DRUM) {
                tr.write(0);
                tr.write(0xC0 | p);
                tr.write(prog[p]);
            }
        }
        int last = 0;
        for (int s = 0; s <= end; s++) {
            int tick = s * 24; // 96 PPQ, 1 step = 1/16 note
            boolean first = true;
            Vector v = off[s];
            if (v != null) {
                for (int i = 0; i < v.size(); i++) {
                    int[] e = (int[]) v.elementAt(i);
                    vlq(tr, first ? tick - last : 0);
                    first = false;
                    tr.write(0x80 | ch(e[0])); tr.write(e[1]); tr.write(0);
                }
            }
            v = on[s];
            if (v != null) {
                for (int i = 0; i < v.size(); i++) {
                    int[] e = (int[]) v.elementAt(i);
                    vlq(tr, first ? tick - last : 0);
                    first = false;
                    tr.write(0x90 | ch(e[0])); tr.write(e[1]); tr.write(100);
                }
            }
            if (!first) last = tick;
        }
        vlq(tr, 0);
        tr.write(0xFF); tr.write(0x2F); tr.write(0);

        byte[] td = tr.toByteArray();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write('M'); o.write('T'); o.write('h'); o.write('d');
        o.write(0); o.write(0); o.write(0); o.write(6);
        o.write(0); o.write(0); // format 0
        o.write(0); o.write(1); // 1 track
        o.write(0); o.write(96); // PPQ
        o.write('M'); o.write('T'); o.write('r'); o.write('k');
        o.write(td.length >> 24); o.write(td.length >> 16);
        o.write(td.length >> 8); o.write(td.length);
        o.write(td, 0, td.length);
        return o.toByteArray();
    }

    // ------------------------------------------------------------ playback / export
    void togglePlay() {
        if (playing) stopPlay();
        else startPlay(mode == 0 ? -1 : curPat);
    }

    void startPlay(int only) {
        stopPlay();
        byte[] d = build(only);
        if (d == null) {
            alert("MobileFruity", "\u041d\u0435\u0442 \u043d\u043e\u0442");
            return;
        }
        try {
            player = Manager.createPlayer(new ByteArrayInputStream(d), "audio/midi");
            player.realize();
            player.prefetch();
            player.start();
        } catch (Throwable t) {
            closePlayer();
            alert("Player error", "" + t);
            return;
        }
        playOnly = only;
        t0 = System.currentTimeMillis();
        playing = true;
        timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                tick();
            }
        }, 0, 80);
        repaint();
    }

    void tick() {
        if (!playing) return;
        if (sx() > (long) (endSteps + 4) * 1000) {
            stopPlay();
            return;
        }
        repaint();
    }

    void closePlayer() {
        if (player != null) {
            try {
                player.stop();
            } catch (Throwable t) {
            }
            try {
                player.close();
            } catch (Throwable t) {
            }
            player = null;
        }
    }

    public void stopPlay() {
        playing = false;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        closePlayer();
        repaint();
    }

    void doExport() {
        byte[] d = build(-1);
        if (d == null) {
            alert("MobileFruity", "\u041d\u0435\u0442 \u043d\u043e\u0442");
            return;
        }
        try {
            String r = FileSaver.save(d);
            alert("OK", r);
        } catch (Throwable t) {
            alert("Export error", "" + t);
        }
    }

    void doImport() {
        stopPlay();
        try {
            Display.getDisplay(midlet).setCurrent(new FileBrowser(this, midlet));
        } catch (Throwable t) {
            alert("Import error", "" + t);
        }
    }

    /** called by FileBrowser with the bytes of the chosen .mid file */
    void importMidi(byte[] d) {
        stopPlay();
        String r = MidiImport.run(this, d);
        focus = 0;
        plR = 0;
        plC = 0;
        plViewC = 0;
        curPat = 0;
        pStep = 0;
        viewStep = 0;
        pPitch = 60;
        fixDrumPitch();
        layout();
        ensureVisible();
        alert("MIDI", r);
    }

    void alert(String title, String msg) {
        Alert a = new Alert(title, msg, null, AlertType.INFO);
        a.setTimeout(3000);
        Display.getDisplay(midlet).setCurrent(a, this);
    }
}
