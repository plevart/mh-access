/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test.perf;

import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.logic.BlackHole;
import si.pele.friendly.Friendly;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

import static si.pele.friendly.MHThrows.unchecked;
import static test.perf.SecretRandom.addend;
import static test.perf.SecretRandom.mask;
import static test.perf.SecretRandom.multiplier;

/**
 * @author peter
 */
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class MHPerfTestJMH {
    private final SecretRandom sr = new SecretRandom();

    private int nextIntNormal() {
        long oldseed = sr.seed;
        long nextseed = (oldseed * multiplier + addend) & mask;
        sr.seed = nextseed;
        return (int) (nextseed >>> 16);
    }

    @GenerateMicroBenchmark
    public void normal_field_access(BlackHole bh1, BlackHole bh2, BlackHole bh3, BlackHole bh4, BlackHole bh5) {
        bh1.consume(nextIntNormal());
        bh2.consume(nextIntNormal());
        bh3.consume(nextIntNormal());
        bh4.consume(nextIntNormal());
        bh5.consume(nextIntNormal());
    }

    private static final MethodHandle seedGetter = Friendly.getter(SecretRandom.class, "seed");
    private static final MethodHandle seedSetter = Friendly.setter(SecretRandom.class, "seed");

    private int nextIntMh() {
        try {
            long oldseed = (long) seedGetter.invokeExact(sr);
            long nextseed = (oldseed * multiplier + addend) & mask;
            seedSetter.invokeExact(sr, nextseed);
            return (int) (nextseed >>> 16);
        }
        catch (Throwable t) {
            throw unchecked(t);
        }
    }

    @GenerateMicroBenchmark
    public void mh_field_access(BlackHole bh1, BlackHole bh2, BlackHole bh3, BlackHole bh4, BlackHole bh5) {
        bh1.consume(nextIntMh());
        bh2.consume(nextIntMh());
        bh3.consume(nextIntMh());
        bh4.consume(nextIntMh());
        bh5.consume(nextIntMh());
    }

    @GenerateMicroBenchmark
    public void normal_call(BlackHole bh1, BlackHole bh2, BlackHole bh3, BlackHole bh4, BlackHole bh5) {
        bh1.consume(sr.nextInt());
        bh2.consume(sr.nextInt());
        bh3.consume(sr.nextInt());
        bh4.consume(sr.nextInt());
        bh5.consume(sr.nextInt());
    }

    private static final MethodHandle nextIntMH = Friendly.method(SecretRandom.class, "nextInt");

    @GenerateMicroBenchmark
    public void mh_call(BlackHole bh1, BlackHole bh2, BlackHole bh3, BlackHole bh4, BlackHole bh5) {
        try {
            bh1.consume((int) nextIntMH.invokeExact(sr));
            bh2.consume((int) nextIntMH.invokeExact(sr));
            bh3.consume((int) nextIntMH.invokeExact(sr));
            bh4.consume((int) nextIntMH.invokeExact(sr));
            bh5.consume((int) nextIntMH.invokeExact(sr));
        }
        catch (Throwable t) {
            throw unchecked(t);
        }
    }


    interface SRA {
        int nextInt(SecretRandom tc);
    }

    private static final SRA sra = Friendly.proxy(SRA.class);

    @GenerateMicroBenchmark
    public void proxy_call(BlackHole bh1, BlackHole bh2, BlackHole bh3, BlackHole bh4, BlackHole bh5) {
        bh1.consume(sra.nextInt(sr));
        bh2.consume(sra.nextInt(sr));
        bh3.consume(sra.nextInt(sr));
        bh4.consume(sra.nextInt(sr));
        bh5.consume(sra.nextInt(sr));
    }
}
