package test.proxy;

import si.pele.friendly.Friendly;

/**
 */
public class MyFriend {
    public static final SecretRandomAccess ACCESS = Friendly.proxy(SecretRandomAccess.class);
}
