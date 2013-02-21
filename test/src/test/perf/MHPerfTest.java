/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test.perf;

import si.pele.friendly.Friendly;
import si.pele.friendly.MHThrows;
import si.pele.microbench.TestRunner;

import java.lang.invoke.MethodHandle;

import static si.pele.friendly.MHThrows.unchecked;
import static test.perf.SecretRandom.addend;
import static test.perf.SecretRandom.mask;
import static test.perf.SecretRandom.multiplier;

/**
 * Tests method handle performance compared against direct method calls or field accesses
 */
public class MHPerfTest extends TestRunner {

    public static final class normal_field_access extends Test {
        private final SecretRandom sr = new SecretRandom();

        @Override
        protected void doLoop(Loop loop, DevNull devNull1, DevNull devNull2, DevNull devNull3, DevNull devNull4, DevNull devNull5) {
            while (loop.nextIteration()) {
                long oldseed = sr.seed;
                long nextseed = (oldseed * multiplier + addend) & mask;
                sr.seed = nextseed;
                devNull1.yield((int) (nextseed >>> 16));
            }
        }
    }

    public static final class mh_field_access extends Test {
        private static final MethodHandle seedGetter = Friendly.getter(SecretRandom.class, "seed");
        private static final MethodHandle seedSetter = Friendly.setter(SecretRandom.class, "seed");
        private final SecretRandom sr = new SecretRandom();

        @Override
        protected void doLoop(Loop loop, DevNull devNull1, DevNull devNull2, DevNull devNull3, DevNull devNull4, DevNull devNull5) {
            try {
                while (loop.nextIteration()) {
                    long oldseed = (long) seedGetter.invokeExact(sr);
                    long nextseed = (oldseed * multiplier + addend) & mask;
                    seedSetter.invokeExact(sr, nextseed);
                    devNull1.yield((int) (nextseed >>> 16));
                }
            }
            catch (Throwable t) {
                throw unchecked(t);
            }
        }
    }

    public static final class normal_call extends Test {
        private final SecretRandom sr = new SecretRandom();

        @Override
        protected void doLoop(Loop loop, DevNull devNull1, DevNull devNull2, DevNull devNull3, DevNull devNull4, DevNull devNull5) {
            while (loop.nextIteration()) {
                devNull1.yield(sr.nextInt());
            }
        }
    }

    public static final class mh_call extends Test {
        private static final MethodHandle nextIntMH = Friendly.method(SecretRandom.class, "nextInt");
        private final SecretRandom sr = new SecretRandom();

        @Override
        protected void doLoop(Loop loop, DevNull devNull1, DevNull devNull2, DevNull devNull3, DevNull devNull4, DevNull devNull5) {
            try {
                while (loop.nextIteration()) {
                    devNull1.yield((int) nextIntMH.invokeExact(sr));
                }
            }
            catch (Throwable t) {
                throw MHThrows.unchecked(t);
            }
        }
    }

    public static final class proxy_call extends Test {
        interface SRA {
            int nextInt(SecretRandom tc);
        }

        private static final SRA sra = Friendly.proxy(SRA.class);
        private final SecretRandom sr = new SecretRandom();

        @Override
        protected void doLoop(Loop loop, DevNull devNull1, DevNull devNull2, DevNull devNull3, DevNull devNull4, DevNull devNull5) {
            while (loop.nextIteration()) {
                devNull1.yield(sra.nextInt(sr));
            }
        }
    }

    public static void main(String[] args) throws Throwable {
        doTest(normal_call.class, 5000L, 1, 8, 1);
        doTest(normal_field_access.class, 5000L, 1, 8, 1);
        doTest(mh_call.class, 5000L, 1, 8, 1);
        doTest(mh_field_access.class, 5000L, 1, 8, 1);
        doTest(proxy_call.class, 5000L, 1, 8, 1);
    }
}
