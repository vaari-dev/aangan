package dev.vaari.aangan;

import java.nio.charset.StandardCharsets;

/**
 * A minimal QR code encoder: byte mode, error correction level M, versions 1 to 6. That covers
 * up to 106 bytes, enough for a LAN URL with a token, without pulling in a library.
 */
final class QrCode {

    // Level M, versions 1-6. Every block within a version is the same size up to version 6.
    private static final int[] BLOCKS = {1, 1, 1, 2, 2, 4};
    private static final int[] DATA_PER_BLOCK = {16, 28, 44, 32, 43, 27};
    private static final int[] EC_PER_BLOCK = {10, 16, 26, 18, 24, 16};

    private static final int[] EXP = new int[255];
    private static final int[] LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if (x >= 256) x ^= 0x11D;
        }
    }

    private QrCode() {
    }

    /**
     * Encodes text (UTF-8, byte mode, error correction level M) in the smallest version 1-6 that
     * fits. Throws IllegalArgumentException if it's longer than 106 bytes.
     */
    static boolean[][] encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int version = 1;
        while (version <= 6 && bytes.length > capacity(version)) version++;
        if (version > 6) {
            throw new IllegalArgumentException(bytes.length + " bytes; QR capacity is " + capacity(6));
        }
        int v = version - 1;
        byte[] data = dataCodewords(bytes, BLOCKS[v] * DATA_PER_BLOCK[v]);
        byte[] codewords = interleave(data, BLOCKS[v], DATA_PER_BLOCK[v], EC_PER_BLOCK[v]);

        int size = 17 + 4 * version;
        boolean[][] modules = new boolean[size][size];
        boolean[][] function = new boolean[size][size];
        drawFunctionPatterns(modules, function, version);
        drawCodewords(modules, function, codewords);

        // Each of the eight masks is tried, and the one with the lowest penalty score kept.
        boolean[][] best = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            boolean[][] candidate = masked(modules, function, m);
            int penalty = penalty(candidate);
            if (penalty < bestPenalty) {
                best = candidate;
                bestPenalty = penalty;
            }
        }
        return best;
    }

    /** Mode (4 bits) and an 8-bit count leave this many whole bytes for data. */
    private static int capacity(int version) {
        int v = version - 1;
        return (BLOCKS[v] * DATA_PER_BLOCK[v] * 8 - 12) / 8;
    }

    private static byte[] dataCodewords(byte[] bytes, int total) {
        byte[] out = new byte[total];
        int bit = put(out, put(out, 0, 0x4, 4), bytes.length, 8);
        for (byte b : bytes) bit = put(out, bit, b & 0xFF, 8);
        // The terminator, then zero bits up to a byte boundary.
        bit += Math.min(4, total * 8 - bit);
        for (int i = (bit + 7) / 8, pad = 0xEC; i < total; i++, pad ^= 0xEC ^ 0x11) {
            out[i] = (byte) pad;
        }
        return out;
    }

    private static int put(byte[] out, int bit, int value, int length) {
        for (int i = length - 1; i >= 0; i--, bit++) {
            if (((value >>> i) & 1) != 0) out[bit >>> 3] |= (byte) (0x80 >>> (bit & 7));
        }
        return bit;
    }

    private static byte[] interleave(byte[] data, int blocks, int dataLen, int ecLen) {
        int[] gen = generator(ecLen);
        int[][] ec = new int[blocks][];
        for (int b = 0; b < blocks; b++) ec[b] = remainder(data, b * dataLen, dataLen, gen);
        byte[] out = new byte[blocks * (dataLen + ecLen)];
        int k = 0;
        for (int i = 0; i < dataLen; i++) {
            for (int b = 0; b < blocks; b++) out[k++] = data[b * dataLen + i];
        }
        for (int i = 0; i < ecLen; i++) {
            for (int b = 0; b < blocks; b++) out[k++] = (byte) ec[b][i];
        }
        return out;
    }

    private static int mul(int a, int b) {
        return a == 0 || b == 0 ? 0 : EXP[(LOG[a] + LOG[b]) % 255];
    }

    /** Coefficients of (x - a^0)(x - a^1)...(x - a^(degree-1)), highest first, leading 1 dropped. */
    private static int[] generator(int degree) {
        int[] g = new int[degree];
        g[degree - 1] = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                g[j] = mul(g[j], EXP[i]) ^ (j + 1 < degree ? g[j + 1] : 0);
            }
        }
        return g;
    }

    private static int[] remainder(byte[] data, int offset, int length, int[] gen) {
        int[] rem = new int[gen.length];
        for (int i = 0; i < length; i++) {
            int factor = (data[offset + i] & 0xFF) ^ rem[0];
            System.arraycopy(rem, 1, rem, 0, rem.length - 1);
            rem[rem.length - 1] = 0;
            for (int j = 0; j < rem.length; j++) rem[j] ^= mul(gen[j], factor);
        }
        return rem;
    }

    private static void drawFunctionPatterns(boolean[][] m, boolean[][] f, int version) {
        int size = m.length;
        for (int i = 0; i < size; i++) {
            set(m, f, 6, i, i % 2 == 0);
            set(m, f, i, 6, i % 2 == 0);
        }
        drawFinder(m, f, 3, 3);
        drawFinder(m, f, size - 4, 3);
        drawFinder(m, f, 3, size - 4);
        if (version > 1) {
            // From version 2 to 6 the only alignment centre clear of the finders is (c, c).
            int c = 4 * version + 10;
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    set(m, f, c + dx, c + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                }
            }
        }
        // Reserves the format areas; they are rewritten once the mask is known.
        drawFormat(m, f, 0);
        set(m, f, 8, size - 8, true);
    }

    /** Finder plus its light separator: concentric squares at Chebyshev distance 0-4. */
    private static void drawFinder(boolean[][] m, boolean[][] f, int cx, int cy) {
        int size = m.length;
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x < 0 || y < 0 || x >= size || y >= size) continue;
                int d = Math.max(Math.abs(dx), Math.abs(dy));
                set(m, f, x, y, d != 2 && d != 4);
            }
        }
    }

    private static void drawFormat(boolean[][] m, boolean[][] f, int mask) {
        // Level M is 0b00 in the top two bits.
        int data = mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ (((rem >>> 9) & 1) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;
        int size = m.length;
        for (int i = 0; i <= 5; i++) set(m, f, 8, i, bit(bits, i));
        set(m, f, 8, 7, bit(bits, 6));
        set(m, f, 8, 8, bit(bits, 7));
        set(m, f, 7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(m, f, 14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(m, f, size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(m, f, 8, size - 15 + i, bit(bits, i));
    }

    private static boolean bit(int value, int i) {
        return ((value >>> i) & 1) != 0;
    }

    private static void set(boolean[][] m, boolean[][] f, int x, int y, boolean dark) {
        m[y][x] = dark;
        f[y][x] = true;
    }

    /** Zigzags up and down two-module columns from the right, skipping the vertical timing line. */
    private static void drawCodewords(boolean[][] m, boolean[][] f, byte[] codewords) {
        int size = m.length;
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            boolean upward = ((right + 1) & 2) == 0;
            for (int vert = 0; vert < size; vert++) {
                int y = upward ? size - 1 - vert : vert;
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    if (f[y][x] || i >= codewords.length * 8) continue;
                    m[y][x] = bit(codewords[i >>> 3], 7 - (i & 7));
                    i++;
                }
            }
        }
    }

    private static boolean[][] masked(boolean[][] modules, boolean[][] function, int mask) {
        int size = modules.length;
        boolean[][] m = new boolean[size][];
        for (int y = 0; y < size; y++) m[y] = modules[y].clone();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!function[y][x] && flips(mask, x, y)) m[y][x] = !m[y][x];
            }
        }
        drawFormat(m, new boolean[size][size], mask);
        return m;
    }

    private static boolean flips(int mask, int x, int y) {
        switch (mask) {
            case 0: return (x + y) % 2 == 0;
            case 1: return y % 2 == 0;
            case 2: return x % 3 == 0;
            case 3: return (x + y) % 3 == 0;
            case 4: return (x / 3 + y / 2) % 2 == 0;
            case 5: return x * y % 2 + x * y % 3 == 0;
            case 6: return (x * y % 2 + x * y % 3) % 2 == 0;
            default: return ((x + y) % 2 + x * y % 3) % 2 == 0;
        }
    }

    private static int penalty(boolean[][] m) {
        int size = m.length;
        int score = 0, dark = 0;
        for (int a = 0; a < size; a++) {
            int rowRun = 0, colRun = 0, rowBits = 0, colBits = 0;
            for (int b = 0; b < size; b++) {
                if (m[a][b]) dark++;
                rowRun = b > 0 && m[a][b] == m[a][b - 1] ? rowRun + 1 : 1;
                colRun = b > 0 && m[b][a] == m[b - 1][a] ? colRun + 1 : 1;
                if (rowRun == 5) score += 3; else if (rowRun > 5) score++;
                if (colRun == 5) score += 3; else if (colRun > 5) score++;
                if (a > 0 && b > 0 && m[a][b] == m[a - 1][b] && m[a][b] == m[a][b - 1]
                        && m[a][b] == m[a - 1][b - 1]) {
                    score += 3;
                }
                // Last 11 modules as bits: 1:1:3:1:1 with four light modules on either side.
                rowBits = ((rowBits << 1) | (m[a][b] ? 1 : 0)) & 0x7FF;
                colBits = ((colBits << 1) | (m[b][a] ? 1 : 0)) & 0x7FF;
                if (b >= 10) {
                    if (rowBits == 0x5D0 || rowBits == 0x05D) score += 40;
                    if (colBits == 0x5D0 || colBits == 0x05D) score += 40;
                }
            }
        }
        score += Math.abs(dark * 20 - size * size * 10) / (size * size) * 10;
        return score;
    }
}
