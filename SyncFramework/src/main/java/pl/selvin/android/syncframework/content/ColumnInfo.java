/**
 * Copyright (c) 2014 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.content;

import pl.selvin.android.syncframework.annotation.Column;

final class ColumnInfo {
    public final String name;
    public final int type;
    public final String extras;
    public final String computed;
    public final String scope;
    public final boolean nullable;

    public ColumnInfo(final String scope, final String name, final Column column) {
        this.scope = scope;
        this.name = name;
        type = column.type();
        nullable = column.nullable();
        extras = column.extras();
        computed = column.computed();
    }
}