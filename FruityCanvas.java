import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.media.*;
import java.io.*;
import java.util.*;
 
/*
 * MobileFruity - FL Studio parody for J2ME (CLDC 1.0 / MIDP 2.0)
 *
 * PLAYLIST:  arrows move, FIRE put/remove current pattern, 1-8 choose pattern,
 *            0 play/stop song, * / # BPM -/+5, 9 export .mid
 * PIANO ROLL: arrows move, FIRE (or 5) add/remove note,
 *            1/3 note length -/+ (on a note: resizes that note),
 *            4/6 pattern length -/+4, 7/9 instrument -/+, 2/8 octave up/down,
 *            * / # previous/next pattern, 0 play/stop current pattern
 * Left soft key = Playlist, right soft key = Piano roll. Touch: tap / drag.
 */
public class FruityCanvas extends Canvas {
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
    static final int T_EXPORT = 0, T_MINUS = 1, T_PLUS = 2, T_PLAY = 3;
    int exX, exW, minusX, bpmX, bpmW, plusX, playX, plW, sW;
 
    // playback
    Player player;
    Timer timer;
    boolean playing;
    long t0;
    int endSteps, playOnly = -1;
 
    // pointer
    boolean pgrid, pmoved;
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
            gridY0 = tbH + fh + 3;
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
        return mode == 0 ? 4 : 3; // piano roll has no Export button
    }
 
    int topId(int i) {
        return mode == 0 ? i : i + 1;
    }
 
    int topX(int id) {
        switch (id) {
            case T_EXPORT: return exX;
            case T_MINUS: return minusX;
            case T_PLUS: return plusX;
            default: return playX;
        }
    }
 
    int topW(int id) {
        switch (id) {
            case T_EXPORT: return exW;
            case T_MINUS: return sW;
            case T_PLUS: return sW;
            default: return plW;
        }
    }
 
    void bpmChange(int d) {
        if (playing) return;
        bpm += d;
        if (bpm < 40) bpm = 40;
        if (bpm > 240) bpm = 240;
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
        }
        btn(g, minusX, 1, sW, bh, "-", false);
        g.setColor(C_TXT);
        g.drawString("" + bpm, bpmX + bpmW / 2, (tbH - fh) / 2, Graphics.TOP | Graphics.HCENTER);
        btn(g, plusX, 1, sW, bh, "+", false);
        btn(g, playX, 1, plW, bh, playLabel(), playing);
        if (focus == 1) {
            int id = topId(topIdx);
            g.setColor(0xFFFF00);
            g.drawRect(topX(id), 1, topW(id) - 1, bh - 1);
        }
    }
 
    /** how many playlist columns (16 steps each) a pattern occupies */
    int span(int p) {
        return (plen[p] + 15) / 16;
    }
 
    /** start column of an earlier block that covers column c in row r, or -1 */
    int coverStart(int r, int c) {
        for (int k = c - 1; k >= 0 && k >= c - 4; k--) {
            int v = pl[r][k];
            if (v != 0 && k + span(v - 1) > c) return k;
        }
        return -1;
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
            int bx2 = bx + span(v - 1) * colW;
            if (bx2 <= x0 || bx >= x1) continue;
            int cx = bx < x0 ? x0 : bx;
            int cx2 = bx2 > x1 ? x1 : bx2;
            g.setColor(PCOL[v - 1]);
            g.fillRect(cx + 1, y + 1, cx2 - cx - 1, rowH - 1);
            g.setColor(0x000000);
            g.drawString("\u041f" + v, cx + 3, y + (rowH - fh) / 2, Graphics.TOP | Graphics.LEFT);
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
        txt(g, "\u041d\u043e\u0442\u0430 " + noteLen + "  \u041f\u0430\u0442\u0442 " + plen[curPat],
                3, (tbH - fh) / 2, C_TXT);
        int pr = prog[curPat];
        txt(g, "\u041f" + (curPat + 1) + "  Prg " + (pr + 1) + " " + FAM[pr >> 3], 3, tbH + 1, C_DIM);
 
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
            digit(k - KEY_NUM0);
            return;
        }
        if (k == KEY_STAR) {
            star(-1);
            return;
        }
        if (k == KEY_POUND) {
            star(1);
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
 
    void cycleFocus() {
        focus = (focus + 1) % 4;
        if (focus == 1) topIdx = 0;
        if (focus == 2) tabIdx = mode;
    }
 
    void moveFocus(int dx, int dy) {
        if (dy != 0) {
            focus = 0; // up/down: back to the grid
            return;
        }
        if (dx == 0) return;
        if (focus == 1) {
            topIdx += dx;
            if (topIdx < 0) topIdx = 0;
            if (topIdx >= topCount()) topIdx = topCount() - 1;
        } else if (focus == 2) {
            tabIdx = dx < 0 ? 0 : 1;
        } else if (focus == 3) {
            curPat += dx;
            if (curPat < 0) curPat = 0;
            if (curPat >= PATS) curPat = PATS - 1;
            if (pStep >= plen[curPat]) pStep = plen[curPat] - 1;
        }
    }
 
    void fireFocus() {
        if (focus == 1) {
            int id = topId(topIdx);
            if (id == T_EXPORT) doExport();
            else if (id == T_MINUS) bpmChange(-5);
            else if (id == T_PLUS) bpmChange(5);
            else togglePlay();
        } else if (focus == 2) {
            setMode(tabIdx);
        } else {
            focus = 0;
        }
    }
 
    void setMode(int m) {
        mode = m;
        focus = 0;
        topIdx = 0;
        layout();
        if (m == 1) ensureVisible();
        repaint();
    }
 
    void digit(int d) {
        if (d == 5) {
            cycleFocus();
            return;
        }
        if (d == 0) {
            togglePlay();
            return;
        }
        if (mode == 0) {
            if (d >= 1 && d <= 8) curPat = d - 1;
            else if (d == 9) doExport();
        } else {
            switch (d) {
                case 1: lenChange(-1); break;
                case 3: lenChange(1); break;
                case 4: plenChange(-4); break;
                case 6: plenChange(4); break;
                case 7: prog[curPat] = (prog[curPat] + 127) % 128; break;
                case 9: prog[curPat] = (prog[curPat] + 1) % 128; break;
                case 2: pPitch += 12; if (pPitch > HIGHP) pPitch = HIGHP; ensureVisible(); break;
                case 8: pPitch -= 12; if (pPitch < LOWP) pPitch = LOWP; ensureVisible(); break;
            }
        }
    }
 
    void star(int dir) {
        if (mode == 0) {
            if (playing) return;
            bpm += dir * 5;
            if (bpm < 40) bpm = 40;
            if (bpm > 240) bpm = 240;
        } else {
            curPat = (curPat + dir + PATS) % PATS;
            if (pStep >= plen[curPat]) pStep = plen[curPat] - 1;
            ensureVisible();
        }
    }
 
    void action() {
        if (mode == 0) {
            int v = curPat + 1;
            int cur = pl[plR][plC];
            if (cur != 0) {
                pl[plR][plC] = (byte) (cur == v ? 0 : v);
            } else {
                int k = coverStart(plR, plC);
                if (k >= 0) pl[plR][k] = 0;
                else pl[plR][plC] = (byte) v;
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
        pgrid = false;
        pmoved = false;
        if (y < tbH) {
            if (x >= playX) togglePlay();
            else if (x >= plusX) bpmChange(5);
            else if (x >= bpmX) { /* tempo number, no action */ }
            else if (x >= minusX) bpmChange(-5);
            else if (mode == 0 && x < exX + exW + 1) doExport();
        } else if (y >= h - botH) {
            setMode(x < w / 2 ? 0 : 1);
        } else if (y >= gridY1) {
            curPat = x * PATS / w;
            if (curPat >= PATS) curPat = PATS - 1;
        } else if (y >= gridY0 && x >= labelW) {
            pgrid = true;
            pdx = x;
            pdy = y;
            pvc = plViewC;
            pvs = viewStep;
            pvp = viewPitch;
        }
        repaint();
    }
 
    protected void pointerDragged(int x, int y) {
        if (!pgrid) return;
        if (!pmoved && Math.abs(x - pdx) + Math.abs(y - pdy) < 8) return;
        pmoved = true;
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
        repaint();
    }
 
    // ------------------------------------------------------------ MIDI
    int addPat(Vector[] on, Vector[] off, int p, int base, int maxSteps) {
        int end = 0;
        Vector nv = notes[p];
        for (int i = 0; i < nv.size(); i++) {
            int[] n = (int[]) nv.elementAt(i);
            if (n[0] >= plen[p]) continue;
            int len = n[2];
            if (n[0] + len > plen[p]) len = plen[p] - n[0];
            int s = base + n[0];
            int e = s + len;
            if (e > maxSteps) e = maxSteps;
            if (s >= e) continue;
            if (on[s] == null) on[s] = new Vector();
            on[s].addElement(new int[]{p, n[1]});
            if (off[e] == null) off[e] = new Vector();
            off[e].addElement(new int[]{p, n[1]});
            if (e > end) end = e;
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
        int maxSteps = COLS * 16 + MAXLEN + 1;
        Vector[] on = new Vector[maxSteps + 1];
        Vector[] off = new Vector[maxSteps + 1];
        boolean[] used = new boolean[PATS];
        int end = 0;
        if (only >= 0) {
            int e = addPat(on, off, only, 0, maxSteps);
            if (e > end) end = e;
            used[only] = true;
        } else {
            for (int r = 0; r < TRACKS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int v = pl[r][c];
                    if (v != 0) {
                        int e = addPat(on, off, v - 1, c * 16, maxSteps);
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
            if (used[p]) {
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
                    tr.write(0x80 | e[0]); tr.write(e[1]); tr.write(0);
                }
            }
            v = on[s];
            if (v != null) {
                for (int i = 0; i < v.size(); i++) {
                    int[] e = (int[]) v.elementAt(i);
                    vlq(tr, first ? tick - last : 0);
                    first = false;
                    tr.write(0x90 | e[0]); tr.write(e[1]); tr.write(100);
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
 
    void alert(String title, String msg) {
        Alert a = new Alert(title, msg, null, AlertType.INFO);
        a.setTimeout(3000);
        Display.getDisplay(midlet).setCurrent(a, this);
    }
}
 
