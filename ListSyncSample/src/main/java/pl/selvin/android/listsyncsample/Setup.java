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

package pl.selvin.android.listsyncsample;

import pl.selvin.android.listsyncsample.provider.Database;
import pl.selvin.android.syncframework.SetupInterface;

public class Setup implements SetupInterface {

    @Override
    public String getServiceUrl() {
        return Constants.SERVICE_URI;
    }

    @Override
    public String getAuthority() {
        return Constants.AUTHORITY;
    }

    @Override
    public Class<?> getDatabaseClass() {
        return Database.class;
    }

    @Override
    public String getDatabaseName() {
        return "listdb";
    }

    @Override
    public int getDatabaseVersion() {
        return 17;
    }
}