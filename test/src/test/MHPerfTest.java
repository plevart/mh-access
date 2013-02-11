/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test;

import si.pele.friendly.Friend;
import si.pele.friendly.Friendly;
import si.pele.microbench.TestRunner;
import test.proxy.SecretRandom;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.ThreadLocalRandom;

import static si.pele.friendly.MHThrows.unchecked;

/**
 * Tests method handle performance compared against direct method calls or field accesses
 */
public class MHPerfTest extends TestRunner {

    public static final long multiplier = 0x5DEECE66DL;
    public static final long addend = 0xBL;
    public static final long mask = (1L << 48) - 1;

    @Friend(mh_field_access.class)
    long seed;

    @Friend(mh_method_call.class)
    int testMethod() {
        return ThreadLocalRandom.current().nextInt();
    }

    public static class method_call extends Test {
        private final MHPerfTest mpt = new MHPerfTest();

        @Override
        protected void doOp() {
            try {
                consume(mpt.testMethod());
                consume(mpt.testMethod());
                consume(mpt.testMethod());
                consume(mpt.testMethod());
                consume(mpt.testMethod());
            }
            catch (Throwable t) {
                throw unchecked(t);
            }
        }
    }

    public static class mh_method_call extends Test {
        private static final MethodHandle testMethodMH = Friendly.method(MHPerfTest.class, "testMethod");
        private final MHPerfTest mpt = new MHPerfTest();

        @Override
        protected void doOp() {
            try {
                consume((int) testMethodMH.invokeExact(mpt));
                consume((int) testMethodMH.invokeExact(mpt));
                consume((int) testMethodMH.invokeExact(mpt));
                consume((int) testMethodMH.invokeExact(mpt));
                consume((int) testMethodMH.invokeExact(mpt));
            }
            catch (Throwable t) {
                throw unchecked(t);
            }
        }
    }

    public static class field_access extends Test {
        private final MHPerfTest mpt = new MHPerfTest();

        private int nextInt() {
            try {
                long oldseed = mpt.seed;
                long nextseed = (oldseed * multiplier + addend) & mask;
                mpt.seed = nextseed;
                return (int) (nextseed >>> 16);
            }
            catch (Throwable t) {
                throw unchecked(t);
            }
        }

        @Override
        protected void doOp() {
            consume(nextInt());
            consume(nextInt());
            consume(nextInt());
            consume(nextInt());
            consume(nextInt());
        }
    }

    public static class mh_field_access extends Test {
        private static final MethodHandle seedGetter = Friendly.getter(MHPerfTest.class, "seed");
        private static final MethodHandle seedSetter = Friendly.setter(MHPerfTest.class, "seed");
        private final MHPerfTest mpt = new MHPerfTest();

        private int nextInt() {
            try {
                long oldseed = (long) seedGetter.invokeExact(mpt);
                long nextseed = (oldseed * multiplier + addend) & mask;
                seedSetter.invokeExact(mpt, nextseed);
                return (int) (nextseed >>> 16);
            }
            catch (Throwable t) {
                throw unchecked(t);
            }
        }

        @Override
        protected void doOp() {
            consume(nextInt());
            consume(nextInt());
            consume(nextInt());
            consume(nextInt());
            consume(nextInt());
        }
    }

    public static class proxy_call extends Test {
        private static final SecretRandom.Access sra = SecretRandom.ACCESS;
        private final SecretRandom sr = new SecretRandom();

        @Override
        protected void doOp() {
            consume(sra.nextInt(sr));
            consume(sra.nextInt(sr));
            consume(sra.nextInt(sr));
            consume(sra.nextInt(sr));
            consume(sra.nextInt(sr));
        }
    }

    public static class mh_proxy_call extends Test {
        interface SRA {
            int nextInt(SecretRandom tc);
        }

        private static final SRA sra = Friendly.proxy(SRA.class);
        private final SecretRandom sr = new SecretRandom();

        @Override
        protected void doOp() {
            consume(sra.nextInt(sr));
            consume(sra.nextInt(sr));
            consume(sra.nextInt(sr));
            consume(sra.nextInt(sr));
            consume(sra.nextInt(sr));
        }
    }

    public static void main(String[] args) throws Throwable {
        doTest(mh_proxy_call.class, 5000L, 1, 8, 1);
        doTest(proxy_call.class, 5000L, 1, 8, 1);
    }
}
