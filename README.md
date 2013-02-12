mh-access
=========

@Friend access via method handles
---------------------------------

This Java library allows Java code to access fields, methods and constructors that would otherwise
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
special *@Friend* annotations allow access to particular classes when using method handles:

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
