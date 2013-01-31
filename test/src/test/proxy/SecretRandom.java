package test.proxy;

import si.pele.friendly.Friend;

import static si.pele.friendly.MHThrows.unchecked;

/**
 */
public class SecretRandom {
    public static final long multiplier = 0x5DEECE66DL;
    public static final long addend = 0xBL;
    public static final long mask = (1L << 48) - 1;

    private long seed;

    @Friend(MyFriend.class)
    private int nextInt() {
        try {
            long nextseed = (seed * multiplier + addend) & mask;
            seed = nextseed;
            return (int) (nextseed >>> 16);
        }
        catch (Throwable t) {
            throw unchecked(t);
        }
    }

    public static final SecretRandomAccess ACCESS = new SecretRandomAccess() {
        @Override
        public int nextInt(SecretRandom tc) {
            return tc.nextInt();
        }
    };
}
