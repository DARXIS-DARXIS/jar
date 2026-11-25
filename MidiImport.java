import java.util.Vector;

/**
 * Standard MIDI file (format 0 / 1) -> MobileFruity patterns.
 * One pattern per channel (the 8 busiest channels, drums on channel 10 get a drum pattern).
 * Long channels are cut into several patterns when there are free ones.
 * Time is rounded to 16th notes. Replaces the current project.
 */
public class MidiImport {
    static byte[] b;
    static int p;
    static Vector[] ev;
    static int[] cnt;
    static int total;
    static final int MAXTOTAL = 6000;

    static int u8() {
        return b[p++] & 0xFF;
    }

    static int u16() {
        int hi = u8();
        return (hi << 8) | u8();
    }

    static int u32() {
        int a = u8();
        int c = u8();
        int d = u8();
        int e = u8();
        return (a << 24) | (c << 16) | (d << 8) | e;
    }

    static int vlq() {
        int v = 0;
        for (int i = 0; i < 4; i++) {
            int c = u8();
            v = (v << 7) | (c & 0x7F);
            if ((c & 0x80) == 0) break;
        }
        return v;
    }

    static void closeNote(int ch, int n, int st, int en) {
        if (total >= MAXTOTAL) return;
        if (ev[ch] == null) ev[ch] = new Vector();
        ev[ch].addElement(new int[]{st, n, en});
        cnt[ch]++;
        total++;
    }

    static int step(int tick, int div) {
        return (tick * 4 + div / 2) / div;
    }

    public static String run(FruityCanvas c, byte[] d) {
        try {
            return parse(c, d);
        } catch (Throwable t) {
            return "\u041e\u0448\u0438\u0431\u043a\u0430 MIDI: " + t;
        }
    }

    static String parse(FruityCanvas c, byte[] d) {
        b = d;
        p = 0;
        ev = new Vector[16];
        cnt = new int[16];
        total = 0;
        if (d.length < 14 || d[0] != 'M' || d[1] != 'T' || d[2] != 'h' || d[3] != 'd') {
            return "\u042d\u0442\u043e \u043d\u0435 MIDI \u0444\u0430\u0439\u043b";
        }
        p = 4;
        u32();
        u16();
        int ntr = u16();
        int div = u16();
        if (div <= 0 || (div & 0x8000) != 0) return "\u0422\u0430\u043a\u043e\u0439 \u0444\u043e\u0440\u043c\u0430\u0442 \u0432\u0440\u0435\u043c\u0435\u043d\u0438 \u043d\u0435 \u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044f";

        int[] prog = new int[16];
        boolean[] progSet = new boolean[16];
        int[] startT = new int[2048];
        for (int i = 0; i < 2048; i++) startT[i] = -1;
        int tempo = 0;

        for (int tr = 0; tr < ntr && p + 8 <= d.length; tr++) {
            boolean isTrk = d[p] == 'M' && d[p + 1] == 'T' && d[p + 2] == 'r' && d[p + 3] == 'k';
            p += 4;
            int len = u32();
            int tend = p + len;
            if (len < 0 || tend > d.length || tend < p) tend = d.length;
            if (!isTrk) {
                p = tend;
                continue;
            }
            int tick = 0;
            int status = 0;
            while (p < tend) {
                tick += vlq();
                int s = d[p] & 0xFF;
                if (s >= 0x80) {
                    p++;
                    if (s < 0xF0) status = s;
                } else {
                    s = status; // running status
                }
                if (s == 0xFF) {
                    int type = u8();
                    int ml = vlq();
                    if (type == 0x51 && ml == 3 && tempo == 0) {
                        int t1 = u8();
                        int t2 = u8();
                        int t3 = u8();
                        tempo = (t1 << 16) | (t2 << 8) | t3;
                    } else {
                        p += ml;
                    }
                    if (type == 0x2F) break;
                } else if (s == 0xF0 || s == 0xF7) {
                    int sl = vlq();
                    p += sl;
                } else if (s >= 0x80) {
                    int ch = s & 15;
                    int kind = s & 0xF0;
                    if (kind == 0x80 || kind == 0x90) {
                        int n = u8();
                        int v = u8();
                        int key = ch * 128 + n;
                        if (kind == 0x90 && v > 0) {
                            if (startT[key] >= 0) closeNote(ch, n, startT[key], tick);
                            startT[key] = tick;
                        } else if (startT[key] >= 0) {
                            closeNote(ch, n, startT[key], tick);
                            startT[key] = -1;
                        }
                    } else if (kind == 0xC0) {
                        int pr = u8();
                        if (!progSet[ch]) {
                            prog[ch] = pr;
                            progSet[ch] = true;
                        }
                    } else if (kind == 0xD0) {
                        u8();
                    } else {
                        u8();
                        u8();
                    }
                } else {
                    break; // garbage
                }
            }
            for (int i = 0; i < 2048; i++) {
                if (startT[i] >= 0) {
                    closeNote(i >> 7, i & 127, startT[i], tick);
                    startT[i] = -1;
                }
            }
            p = tend;
        }

        // choose up to 8 channels: drums first, then the busiest
        boolean[] pick = new boolean[16];
        int npick = 0;
        if (cnt[9] > 0) {
            pick[9] = true;
            npick = 1;
        }
        while (npick < 8) {
            int best = -1;
            for (int ch = 0; ch < 16; ch++) {
                if (!pick[ch] && cnt[ch] > 0 && (best < 0 || cnt[ch] > cnt[best])) best = ch;
            }
            if (best < 0) break;
            pick[best] = true;
            npick++;
        }
        if (npick == 0) return "\u0412 \u0444\u0430\u0439\u043b\u0435 \u043d\u0435\u0442 \u043d\u043e\u0442";

        int chunk = FruityCanvas.MAXLEN;
        int per = FruityCanvas.PATS / npick; // patterns per channel
        int maxChunks = FruityCanvas.COLS * 16 / chunk;
        if (per > maxChunks) per = maxChunks;
        if (per < 1) per = 1;

        // clear the project
        for (int i = 0; i < FruityCanvas.PATS; i++) {
            c.notes[i].removeAllElements();
            c.plen[i] = 16;
            c.prog[i] = 0;
        }
        for (int r = 0; r < FruityCanvas.TRACKS; r++) {
            for (int col = 0; col < FruityCanvas.COLS; col++) {
                c.pl[r][col] = 0;
                c.pln[r][col] = 0;
            }
        }

        int pat = 0;
        int row = 0;
        int kept = 0;
        boolean cut = total >= MAXTOTAL;
        for (int ch = 0; ch < 16; ch++) {
            if (!pick[ch]) continue;
            Vector v = ev[ch];
            int last = 0;
            for (int i = 0; i < v.size(); i++) {
                int[] a = (int[]) v.elementAt(i);
                a[0] = step(a[0], div);
                a[2] = step(a[2], div);
                if (a[2] <= a[0]) a[2] = a[0] + 1;
                if (a[2] > last) last = a[2];
            }
            int need = (last + chunk - 1) / chunk;
            int use = need < per ? need : per;
            if (use < 1) use = 1;
            if (need > use) cut = true;
            int pr = (ch == 9) ? FruityCanvas.DRUM : (prog[ch] & 127);
            for (int j = 0; j < use; j++) {
                int lo = j * chunk;
                int plen = chunk;
                if (j == use - 1 && last - lo < chunk) {
                    plen = ((last - lo + 15) / 16) * 16;
                    if (plen < 16) plen = 16;
                }
                c.plen[pat] = plen;
                c.prog[pat] = pr;
                Vector dst = c.notes[pat];
                for (int i = 0; i < v.size(); i++) {
                    int[] a = (int[]) v.elementAt(i);
                    if (a == null) continue;
                    int st = a[0] - lo;
                    if (st < 0 || st >= plen) continue;
                    v.setElementAt(null, i);
                    if (a[1] < FruityCanvas.LOWP || a[1] > FruityCanvas.HIGHP) continue;
                    int l = a[2] - a[0];
                    if (l > plen - st) l = plen - st;
                    if (l > 64) l = 64;
                    a[0] = st;
                    a[2] = l;
                    dst.addElement(a);
                    kept++;
                }
                int col = j * (chunk / 16);
                c.pl[row][col] = (byte) (pat + 1);
                c.pln[row][col] = (byte) (plen / 16);
                pat++;
            }
            row++;
        }

        int bpm = tempo > 0 ? 60000000 / tempo : 120;
        if (bpm < 40) bpm = 40;
        if (bpm > 240) bpm = 240;
        c.bpm = bpm;

        String r = "\u041a\u0430\u043d\u0430\u043b\u043e\u0432: " + npick + ", \u043d\u043e\u0442: " + kept + ", BPM: " + bpm;
        if (cut) r = r + " (\u0434\u043b\u0438\u043d\u043d\u043e\u0435 \u043e\u0431\u0440\u0435\u0437\u0430\u043d\u043e)";
        return r;
    }
}
