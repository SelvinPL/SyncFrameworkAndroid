package pl.selvin.android.syncframework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import pl.selvin.android.syncframework.ColumnType;

/**
 * FSM save us!!
 *
 * @author Selvin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Column {
    int type() default ColumnType.INTEGER;

    boolean nullable() default false;

    String extras() default EMPTY;

    /*
     *
     */
    String computed() default EMPTY;

    public static final String COLLATE = "COLLATE LOCALIZED";
    public static final String EMPTY = "";
}