/***
 Copyright (c) 2014 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * FSM save us!!
 *
 * @author Selvin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Table {
    /**
     * Sync scope. Needed to synchronization with Microsoft Sync Framework
     * Toolkit
     */
    String scope();

    /**
     * Array containing primary key column names
     */
    String[] primaryKeys();

    /**
     * Tells if Table is read only. If value is true, dynamic ContentProvider
     * will throw Exception if you will try to do update/insert/delete operation
     * with Uri base on this table
     */
    boolean readonly() default false;

    /**
     * Used to provide information about cascade deleting {@link Cascade}
     */
    Cascade[] delete() default {};

    /**
     * Used to provide information about cascade deleting {@link Cascade}
     */
    String[] notifyUris() default {};

    String rowIdAlias() default "ROWID";
}