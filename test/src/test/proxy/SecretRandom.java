package test.proxy;

import si.pele.friendly.Friend;
import test.MHPerfTest;

/**
 */
public class SecretRandom {
    public static final long multiplier = 0x5DEECE66DL;
    public static final long addend = 0xBL;
    public static final long mask = (1L << 48) - 1;

    private long seed;

    @Friend({MHPerfTest.mh_proxy_call.class})
    private final int nextInt() {
        long nextseed = (seed * multiplier + addend) & mask;
        seed = nextseed;
        return (int) (nextseed >>> 16);
    }

    public interface Access {
        int nextInt(SecretRandom tc);
    }

    public static final Access ACCESS = new Access() {
        @Override
        public int nextInt(SecretRandom tc) {
            return tc.nextInt();
        }
    };
}
