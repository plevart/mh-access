/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test.perf;

import org.openjdk.jmh.annotations.BenchmarkType;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
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
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class MHPerfTestJMH {
    private final SecretRandom sr = new SecretRandom();

    @GenerateMicroBenchmark(BenchmarkType.AverageTimePerOp)
    public int normal_field_access() {
        long oldseed = sr.seed;
        long nextseed = (oldseed * multiplier + addend) & mask;
        sr.seed = nextseed;
        return (int) (nextseed >>> 16);
    }

    private static final MethodHandle seedGetter = Friendly.getter(SecretRandom.class, "seed");
    private static final MethodHandle seedSetter = Friendly.setter(SecretRandom.class, "seed");

    @GenerateMicroBenchmark(BenchmarkType.AverageTimePerOp)
    public int mh_field_access() {
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

    @GenerateMicroBenchmark(BenchmarkType.AverageTimePerOp)
    public int normal_call() {
        return sr.nextInt();
    }

    private static final MethodHandle nextIntMH = Friendly.method(SecretRandom.class, "nextInt");

    @GenerateMicroBenchmark(BenchmarkType.AverageTimePerOp)
    public int mh_call() {
        try {
            return (int) nextIntMH.invokeExact(sr);
        }
        catch (Throwable t) {
            throw unchecked(t);
        }
    }

    interface SRA {
        int nextInt(SecretRandom tc);
    }

    private static final SRA sra = Friendly.proxy(SRA.class);

    @GenerateMicroBenchmark(BenchmarkType.AverageTimePerOp)
    public int proxy_call() {
        return sra.nextInt(sr);
    }
}
