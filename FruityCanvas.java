import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.media.*;
import java.io.*;
import java.util.*;

/*
 * MobileFruity - FL Studio parody for J2ME (CLDC 1.0 / MIDP 2.0)
 *
 * PLAYLIST:  arrows move, FIRE put/remove current pattern,
 *            4/6 (or 7/8) choose pattern -/+, 1/2 shrink/stretch the block under cursor,
 *            0 play/stop song, * / # BPM -/+5, 9 export .mid
 *            Touch: drag the right edge of a block to stretch it.
 * PIANO ROLL: arrows move, FIRE (or 5) add/remove note,
 *            1/2 note length -/+ (on a note: resizes that note),
 *            3/9 instrument -/+, 7/8 pattern -/+, 4/6 pattern length -/+4,
 *            * / # octave down/up, 0 play/stop current pattern
 *            Touch: buttons under the title (instrument, note length, pattern).
 * TEMPO:     key 5 selects the BPM number, then just type digits (0-9),
 *            FIRE / arrows confirm, CLEAR = backspace. Tap on the number = input box.
 * Left soft key = Playlist, right soft key = Piano roll. Touch: tap / drag.
 */
public class FruityCanvas extends Canvas implements CommandListener {
    static final int TRACKS = 7, COLS = 32, PATS = 8;
    static final int LOWP = 24, HIGHP = 108; // C1 .. C8
    static final int MAXLEN = 64;

    static final String[] NN = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
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

    // focus: 0 grid, 1 top buttons, 2 bottom tabs, 3 pattern bar (key 5 cycles)
    int focus = 0;
    int topIdx = 0, tabIdx = 0;
    static final int T_EXPORT = 0, T_MINUS = 1, T_PLUS = 2, T_PLAY = 3, T_BPM = 4;
    static final int[] TOP0 = {T_EXPORT, T_MINUS, T_BPM, T_PLUS, T_PLAY};
    static final int[] TOP1 = {T_MINUS, T_BPM, T_PLUS, T_PLAY};
    String bpmEdit = ""; // digits typed so far
    int toolY, toolH, plMX, plLX, plPX, plLW;
    String plLabel = "";
    Command cmdOk, cmdCancel;
    TextBox tbBpm;
    int exX, exW, minusX, bpmX, bpmW, plusX, playX, plW, sW;

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
            labelW = f.stringWidth("C#8") + 4;
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

    int topCount() {
        return mode == 0 ? TOP0.length : TOP1.length;
    }

    int topId(int i) {
        return mode == 0 ? TOP0[i] : TOP1[i];
    }

    int topX(int id) {
        switch (id) {
            case T_EXPORT: return exX;
            case T_MINUS: return minusX;
            case T_PLUS: return plusX;
            case T_BPM: return bpmX;
            default: return playX;
        }
    }

    int topW(int id) {
        switch (id) {
            case T_EXPORT: return exW;
            case T_MINUS: return sW;
            case T_PLUS: return sW;
            case T_BPM: return bpmW;
            default: return plW;
        }
    }

    int bpmIdx() {
        return mode == 0 ? 2 : 1;
    }

    boolean bpmSelected() {
        return focus == 1 && topId(topIdx) == T_BPM;
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

    void paintTop(Graphics g) {
        int bh = tbH - 2;
        if (mode == 0) {
            btn(g, exX, 1, exW, bh, "\u042d\u043a\u0441\u043f\u043e\u0440\u0442", false);
            String t = "\u041f\u043b\u0435\u0439\u043b\u0438\u0441\u0442";
            int tx = exX + exW + 4;
            if (tx + f.stringWidth(t) < minusX) txt(g, t, tx, (tbH - fh) / 2, C_DIM);
        } else {
            btn(g, plMX, 1, sW, bh, "-", false);
            g.setColor(C_TXT);
            g.drawString(plLabel, plLX + plLW / 2, (tbH - fh) / 2, Graphics.TOP | Graphics.HCENTER);
            btn(g, plPX, 1, sW, bh, "+", false);
        }
        btn(g, minusX, 1, sW, bh, "-", false);
        g.setColor(C_LINE);
        g.drawRect(bpmX, 1, bpmW - 1, bh - 1);
        boolean ed = bpmEdit.length() > 0;
        g.setColor(ed ? 0xFFFF00 : C_TXT);
        g.drawString(ed ? bpmEdit : "" + bpm, bpmX + bpmW / 2, (tbH - fh) / 2,
                Graphics.TOP | Graphics.HCENTER);
        btn(g, plusX, 1, sW, bh, "+", false);
        btn(g, playX, 1, plW, bh, playLabel(), playing);
        if (focus == 1) {
            int id = topId(topIdx);
            g.setColor(0xFFFF00);
            g.drawRect(topX(id), 1, topW(id) - 1, bh - 1);
        }
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
        txt(g, "\u041f" + (curPat + 1) + "  Prg " + (pr + 1) + " " + FAM[pr >> 3], 3, tbH + 1, C_DIM);
        paintTool(g);

        g.setClip(0, gridY0, w, gridY1 - gridY0);
        for (int i = 0; i < visRows; i++) {
            int p = viewPitch - i;
            if (p < LOWP) break;
            int y = gridY0 + i * cellH;
            boolean blk = isBlack(p);
            g.setColor(blk ? 0x202020 : 0xE8E8E8);
            g.fillRect(0, y, labelW, cellH);
            txt(g, name(p), 1, y, blk ? 0xCCCCCC : 0x000000);
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
            String nm = name(n[1]);
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

    /** touch buttons: instrument | note length | pattern */
    void paintTool(Graphics g) {
        int gwid = w / 3;
        for (int i = 0; i < 3; i++) {
            int gx = i * gwid;
            btn(g, gx + 1, toolY, sW, toolH, i == 1 ? "-" : "<", false);
            btn(g, gx + gwid - sW - 1, toolY, sW, toolH, i == 1 ? "+" : ">", false);
            String full, shrt;
            if (i == 0) {
                full = "\u0418\u043d\u0441\u0442\u0440 " + (prog[curPat] + 1);
                shrt = "" + (prog[curPat] + 1);
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
        if (focus == 3) {
            g.setColor(0xFFFF00);
            g.drawRect(curPat * cw, gridY1, cw - 2, patH - 2);
        }
    }

    void paintTabs(Graphics g) {
        int y = h - botH;
        btn(g, 0, y, w / 2 - 1, botH, "\u041f\u043b\u0435\u0439\u043b\u0438\u0441\u0442", mode == 0);
        btn(g, w / 2, y, w - w / 2, botH, "\u041f\u0438\u0430\u043d\u043e \u0440\u043e\u043b\u043b", mode == 1);
        if (focus == 2) {
            g.setColor(0xFFFF00);
            if (tabIdx == 0) g.drawRect(1, y + 1, w / 2 - 4, botH - 3);
            else g.drawRect(w / 2 + 1, y + 1, w - w / 2 - 3, botH - 3);
        }
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
     
