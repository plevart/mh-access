/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package test.example;

/**
 * @author peter
 */
public class Main {

    public static void main(String[] args) {
        Credentials cred = new Credentials("joe", "secret");
        Credentials cred1 = FriendClass1.clone(cred);
        System.out.println(cred1.isPasswordEqual("secret"));
        Credentials cred2 = FriendClass2.clone(cred);
        System.out.println(cred2.isPasswordEqual("secret"));
    }
}
