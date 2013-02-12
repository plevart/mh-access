/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test.example;

import si.pele.friendly.Friendly;

/**
* @author peter
*/
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
