SyncFrameworkAndroid
====================

Implemetation of Android client for Microsoft Sync Framework Toolkit

#### Using auto content provider:
For setup auto-content-library you need class which describes your database
```java
interface Database {
    @Table(primaryKeys={Status.ID})
    interface Status {
        @TableName
        String TABLE_NAME = "Status";
        
        @Column
        String ID = "ID";
        
        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
        String NAME = "Name";
    }
}
```

with such classes we may build ContentHelper

```java
public final static String AUTHORITY = "pl.selvin.android.autocontentprovider.test";
public final static ContentHelper CONTENT_HELPER = 
    new ContentHelper(Database.class, AUTHORITY, new DefaultDatabaseInfoFactory(), "test_db", 2);
```

this class is used to obtain uris for given table like:

```java
final Uri statusDirUri = CONTENT_HELPER.getDirUri(Database.Status.TABLE_NAME);
```

should generate Uri like `"content://pl.selvin.android.autocontentprovider.test/Status"`

which can be easily used with CursorLoaders;
  
```java
new CursorLoader(getContext(), statusDirUri, new String[] { Status.ID, Status.NAME }, null, null, null);
```

full setup should looks like this
```java
public class TestProvider extends AutoContentProvider {

    public final static String AUTHORITY = "pl.selvin.android.autocontentprovider.test";
    public final static ContentHelper CONTENT_HELPER = new ContentHelper(Database.class, AUTHORITY, new DefaultDatabaseInfoFactory(), "test_db", 2);

    public TestProvider() {
        super(CONTENT_HELPER, Logger.EmptyLogger.INSTANCE,
                new SupportSQLiteOpenHelperFactoryProvider() {
                    @Override
                    public SupportSQLiteOpenHelper.Factory createFactory(Context context) {
                        return new FrameworkSQLiteOpenHelperFactory();
                    }
                });
    }
}
```

But it's only auto content function **without synchronization**
 
To add synchronization code you have to add `@SyncScope` annotation to the table
and make use of `SyncContentHelper` and `BaseContentProvider`

```java

interface Database {
    @SyncScope("DefaultScope")
    @Table(primaryKeys={Status.ID})
    interface Status {
        @TableName
        String TABLE_NAME = "Status";
        
        @Column
        String ID = "ID";
        
        @Column(type = ColumnType.VARCHAR, extras = Column.COLLATE_NO_CASE)
        String NAME = "Name";
    }
}
```

and then setup out provider with code:
```java
public class SyncProvider extends BaseContentProvider {

    public final static String AUTHORITY = "pl.selvin.android.autocontentprovider.test";
    public final static SyncContentHelper CONTENT_HELPER = SyncContentHelper.getInstance(Database.class, AUTHORITY, "test_db", 2, "http://example.com/service/path");

    public TestProvider() {
        super(CONTENT_HELPER, Logger.EmptyLogger.INSTANCE,
                new SupportSQLiteOpenHelperFactoryProvider() {
                    @Override
                    public SupportSQLiteOpenHelper.Factory createFactory(Context context) {
                        return new FrameworkSQLiteOpenHelperFactory();
                    }
                }, executor);
    }
}
```

then we can initialize sync with 
```java
Uri syncUri = CONTENT_HELPER.getSyncUri("DefaultScopeSyncService", "defaultscope");
String parameters = null;
getContentResolver().update(uri, null, parameters, null);
```
or by using call (on api >= `JELLY_BEAN_MR1`)
```java
Stats stats = new Stats(); 
Bundle syncParams = new Bundle();
syncParams.putParcelable(ListProvider.SYNC_PARAM_IN_SYNC_STATS, stats);
syncParams = getContentResolver().call(uri.toString(), parameters, syncParams);
stats = syncParams.getParcelable(ListProvider.SYNC_PARAM_IN_SYNC_STATS);
```



###### License: 
 >Copyright (c) 2014-2017 Selvin
 >Licensed under the Apache License, Version 2.0 (the "License"); 
 >you may not use this file except in compliance with the License. 
 >You may obtain a copy of the License at 
 >
 >   http://www.apache.org/licenses/LICENSE-2.0
 >
 >Unless required by applicable law or agreed to in writing, software 
 >distributed under the License is distributed on an "AS IS" BASIS, 
 >WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 >See the License for the specific language governing permissions and 
 >limitations under the License.

