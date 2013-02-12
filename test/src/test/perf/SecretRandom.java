/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test.perf;

import si.pele.friendly.Friend;

/**
 */
public final class SecretRandom {
    public static final long multiplier = 0x5DEECE66DL;
    public static final long addend = 0xBL;
    public static final long mask = (1L << 48) - 1;

    @Friend(MHPerfTest.mh_field_access.class)
    long seed;

    @Friend({MHPerfTest.mh_call.class, MHPerfTest.proxy_call.class})
    int nextInt() {
        long nextseed = (seed * multiplier + addend) & mask;
        seed = nextseed;
        return (int) (nextseed >>> 16);
    }
}
