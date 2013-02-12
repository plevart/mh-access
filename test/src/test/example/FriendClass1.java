/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test.example;

import si.pele.friendly.Friendly;
import si.pele.friendly.MHThrows;

import java.lang.invoke.MethodHandle;

/**
* @author peter
*/
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
