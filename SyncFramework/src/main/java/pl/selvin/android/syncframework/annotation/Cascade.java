package pl.selvin.android.syncframework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * FSM save us!!
 *
 * @author Selvin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Cascade {
    String table();

    String[] pk();

    String[] fk();
}