/*
 Copyright (c) 2017 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.autocontentprovider.test.suite;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import pl.selvin.android.autocontentprovider.test.CascadeDeleteTest;
import pl.selvin.android.autocontentprovider.test.ContentHelperTest;
import pl.selvin.android.autocontentprovider.test.DatabaseLifecycleTest;
import pl.selvin.android.autocontentprovider.test.IndexInfoTest;
import pl.selvin.android.autocontentprovider.test.ProviderCrudTest;
import pl.selvin.android.autocontentprovider.test.ProviderNotificationTest;
import pl.selvin.android.autocontentprovider.test.TableInfoTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({ContentHelperTest.class,
        ProviderCrudTest.class,
        ProviderNotificationTest.class,
        TableInfoTest.class,
        IndexInfoTest.class,
        CascadeDeleteTest.class,
        DatabaseLifecycleTest.class})
public class UnitTestSuite {
}
