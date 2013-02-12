/*
 * Written by Peter Levart <peter.levart@gmail.com>
 * and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package si.pele.friendly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation, that when attached to a field, method or constructor, declares
 * a set of "friend" classes - classes that are allowed access to the otherwise prohibited
 * field, method or constructor using method handles or proxies obtained via {@link Friendly}
 * static methods.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
public @interface Friend {
    Class<?>[] value();
}
