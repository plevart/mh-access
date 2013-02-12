mh-access
=========

@Friend access via method handles
---------------------------------

This tiny Java library allows Java code to access fields, methods and constructors that would otherwise
be prohibited because of Java accessibility checks. It does this using method handles under the hood.
Method handles are optimized by JIT into machine instructions that execute at about the same speed as instructions
generated for normal field/method/constructor accesses. Measurements indicate that JIT optimized code
executes with no performance penalty when using this library as opposed to making normal method/field/constructor
accesses.

Access to otherwise prohibited members is governed by special *@Friend* annotation which declares
caller classes that are granted access to a particular field method or constructor.
Take for example the following class:

~~~~~ Java
public class Credentials {

    private final String username;

    @Friend(FriendClass1.class)
    private final String password;

    public Credentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    @Friend(FriendClass2.class)
    private String getPassword() {
        return password;
    }

    public boolean isPasswordEqual(String password) {
        return this.password.equals(password);
    }
}
~~~~~

Access on *password* field and *getPassword()* method is normally prohibited to other classes, but
special *@Friend* annotation grants access to particular classes when using method handles:

~~~~~ Java
public class FriendClass1 {

    private static final MethodHandle credentialsPasswordGetter =
        Friendly.getter(Credentials.class, "password");

    public static Credentials clone(Credentials credentials) {
        try {
            return new Credentials(
                credentials.getUsername(),
                (String) credentialsPasswordGetter.invokeExact(credentials)
            );
        }
        catch (Throwable t) {
            throw MHThrows.unchecked(t);
        }
    }
}
~~~~~

Or special dynamically generated proxies:

~~~~~ Java
public class FriendClass2 {

    private interface CredAccess {
        String getPassword(Credentials credentials);
    }

    private static final CredAccess credentialsAccess =
        Friendly.proxy(CredAccess.class);

    public static Credentials clone(Credentials credentials) {
        return new Credentials(
            credentials.getUsername(),
            credentialsAccess.getPassword(credentials)
        );
    }
}
~~~~~

See javadoc of [si.pele.friendly.Friendly](friendly/src/si/pele/friendly/Friendly.java) for more details.

Here are some performance comparisons between normal access and using method handles or dynamically generated proxy:

~~~~~
#
# normal_field_access: run duration:  5,000 ms, #of logical CPUS: 8
#
# Warm up:
           1 threads, Tavg =      7.43 ns/op (σ =   0.00 ns/op)
           1 threads, Tavg =      7.41 ns/op (σ =   0.00 ns/op)
# Measure:
           1 threads, Tavg =      7.33 ns/op (σ =   0.00 ns/op)
           2 threads, Tavg =     14.37 ns/op (σ =   9.26 ns/op)
           3 threads, Tavg =     10.78 ns/op (σ =   9.15 ns/op)
           4 threads, Tavg =     15.67 ns/op (σ =   7.91 ns/op)
           5 threads, Tavg =     13.03 ns/op (σ =   6.85 ns/op)
           6 threads, Tavg =     20.54 ns/op (σ =   5.72 ns/op)
           7 threads, Tavg =     16.18 ns/op (σ =   7.63 ns/op)
           8 threads, Tavg =     20.22 ns/op (σ =   6.06 ns/op)

#
# mh_field_access: run duration:  5,000 ms, #of logical CPUS: 8
#
# Warm up:
           1 threads, Tavg =      7.47 ns/op (σ =   0.00 ns/op)
           1 threads, Tavg =      7.35 ns/op (σ =   0.00 ns/op)
# Measure:
           1 threads, Tavg =      7.36 ns/op (σ =   0.00 ns/op)
           2 threads, Tavg =     14.55 ns/op (σ =   0.92 ns/op)
           3 threads, Tavg =     16.25 ns/op (σ =   6.45 ns/op)
           4 threads, Tavg =     16.22 ns/op (σ =   3.54 ns/op)
           5 threads, Tavg =     10.88 ns/op (σ =   6.75 ns/op)
           6 threads, Tavg =     18.80 ns/op (σ =   5.41 ns/op)
           7 threads, Tavg =     21.58 ns/op (σ =   4.20 ns/op)
           8 threads, Tavg =     22.22 ns/op (σ =   5.37 ns/op)

#
# normal_call: run duration:  5,000 ms, #of logical CPUS: 8
#
# Warm up:
           1 threads, Tavg =      7.40 ns/op (σ =   0.00 ns/op)
           1 threads, Tavg =      7.39 ns/op (σ =   0.00 ns/op)
# Measure:
           1 threads, Tavg =      7.39 ns/op (σ =   0.00 ns/op)
           2 threads, Tavg =     13.40 ns/op (σ =   4.42 ns/op)
           3 threads, Tavg =     16.08 ns/op (σ =   6.46 ns/op)
           4 threads, Tavg =     16.16 ns/op (σ =   5.90 ns/op)
           5 threads, Tavg =     17.04 ns/op (σ =   8.06 ns/op)
           6 threads, Tavg =     21.07 ns/op (σ =   5.47 ns/op)
           7 threads, Tavg =     11.09 ns/op (σ =   1.10 ns/op)
           8 threads, Tavg =     21.68 ns/op (σ =   3.55 ns/op)

#
# mh_call: run duration:  5,000 ms, #of logical CPUS: 8
#
# Warm up:
           1 threads, Tavg =      7.41 ns/op (σ =   0.00 ns/op)
           1 threads, Tavg =      7.41 ns/op (σ =   0.00 ns/op)
# Measure:
           1 threads, Tavg =      7.41 ns/op (σ =   0.00 ns/op)
           2 threads, Tavg =     13.41 ns/op (σ =  13.03 ns/op)
           3 threads, Tavg =     14.78 ns/op (σ =   8.39 ns/op)
           4 threads, Tavg =      7.44 ns/op (σ =   0.04 ns/op)
           5 threads, Tavg =     14.73 ns/op (σ =  10.13 ns/op)
           6 threads, Tavg =     19.35 ns/op (σ =   5.46 ns/op)
           7 threads, Tavg =     11.10 ns/op (σ =   1.51 ns/op)
           8 threads, Tavg =     21.52 ns/op (σ =   6.78 ns/op)

#
# proxy_call: run duration:  5,000 ms, #of logical CPUS: 8
#
# Warm up:
           1 threads, Tavg =      7.43 ns/op (σ =   0.00 ns/op)
           1 threads, Tavg =      7.44 ns/op (σ =   0.00 ns/op)
# Measure:
           1 threads, Tavg =      7.43 ns/op (σ =   0.00 ns/op)
           2 threads, Tavg =     12.26 ns/op (σ =  12.04 ns/op)
           3 threads, Tavg =     17.04 ns/op (σ =   7.25 ns/op)
           4 threads, Tavg =     15.54 ns/op (σ =   7.80 ns/op)
           5 threads, Tavg =     15.62 ns/op (σ =   9.80 ns/op)
           6 threads, Tavg =     18.98 ns/op (σ =   6.89 ns/op)
           7 threads, Tavg =     11.13 ns/op (σ =   1.11 ns/op)
           8 threads, Tavg =     23.96 ns/op (σ =   5.02 ns/op)
~~~~~

The above measurements were taken on a i7 Linux PC using the following micro-benchmark:
[test.perf.MHPerfTest](test/src/test/perf/MHPerfTest.java)
