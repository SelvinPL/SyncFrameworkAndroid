/**
 * Copyright (c) 2014-2018 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.autocontentprovider.db;


import pl.selvin.android.autocontentprovider.annotation.IndexColumn;

public final class IndexColumnInfo {
    public final String name;
    public final String collate;
    public final String order;

    public IndexColumnInfo(final IndexColumn indexColumn) {
        name = indexColumn.name();
        order = indexColumn.order();
        collate = indexColumn.collate();
    }
}