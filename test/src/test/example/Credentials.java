/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test.example;

import si.pele.friendly.Friend;

/**
 * @author peter
 */
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
