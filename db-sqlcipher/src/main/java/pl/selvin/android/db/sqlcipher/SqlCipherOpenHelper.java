/*
 Copyright (C) 2016 The Android Open Source Project
 Copyright (c) 2014-2016 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.db.sqlcipher;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.content.Context;
import net.sqlcipher.DatabaseErrorHandler;
import net.sqlcipher.database.SQLiteDatabase;
import android.os.Build;
import android.support.annotation.RequiresApi;

import net.sqlcipher.database.SQLiteOpenHelper;

class SqlCipherOpenHelper implements SupportSQLiteOpenHelper {
    private final OpenHelper mDelegate;
    private final String mName;

    SqlCipherOpenHelper(Context context, String name,
                        Callback callback, String password) {
        mName = name;
        mDelegate = createDelegate(context, name, callback, password);
    }

    private OpenHelper createDelegate(Context context, String name, Callback callback, String password) {
        final SqlCipherDatabase[] dbRef = new SqlCipherDatabase[1];
        return new OpenHelper(context, name, dbRef, callback, password);
    }

    @Override
    public String getDatabaseName() {
        return mName;
    }

    @Override
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void setWriteAheadLoggingEnabled(boolean enabled) {
        throw  new UnsupportedOperationException();
        //mDelegate.setWriteAheadLoggingEnabled(enabled);
    }

    @Override
    public SupportSQLiteDatabase getWritableDatabase() {
        return mDelegate.getWritableSupportDatabase();
    }

    @Override
    public SupportSQLiteDatabase getReadableDatabase() {
        return mDelegate.getReadableSupportDatabase();
    }

    @Override
    public void close() {
        mDelegate.close();
    }

    static class OpenHelper extends SQLiteOpenHelper {
        /**
         * This is used as an Object reference so that we can access the wrapped database inside
         * the constructor. SQLiteOpenHelper requires the error handler to be passed in the
         * constructor.
         */
        final SqlCipherDatabase[] mDbRef;
        final Callback mCallback;
        final String mPassword;

        OpenHelper(Context context, String name, final SqlCipherDatabase[] dbRef,
                   final Callback callback, String password) {
            super(context, name, null, callback.version, null,
                    new DatabaseErrorHandler() {
                        @Override
                        public void onCorruption(SQLiteDatabase dbObj) {
                            SqlCipherDatabase db = dbRef[0];
                            if (db != null) {
                                callback.onCorruption(db);
                            }
                        }
                    });
            mPassword = password;
            mCallback = callback;
            mDbRef = dbRef;
        }

        SupportSQLiteDatabase getWritableSupportDatabase() {
            SQLiteDatabase db = super.getWritableDatabase(mPassword);
            return getWrappedDb(db);
        }

        SupportSQLiteDatabase getReadableSupportDatabase() {
            SQLiteDatabase db = super.getReadableDatabase(mPassword);
            return getWrappedDb(db);
        }

        SqlCipherDatabase getWrappedDb(SQLiteDatabase sqLiteDatabase) {
            SqlCipherDatabase dbRef = mDbRef[0];
            if (dbRef == null) {
                dbRef = new SqlCipherDatabase(sqLiteDatabase);
                mDbRef[0] = dbRef;
            }
            return mDbRef[0];
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            mCallback.onCreate(getWrappedDb(sqLiteDatabase));
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
            mCallback.onUpgrade(getWrappedDb(sqLiteDatabase), oldVersion, newVersion);
        }

        //@Override
        public void onConfigure(SQLiteDatabase db) {
            mCallback.onConfigure(getWrappedDb(db));
        }

        //@Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            mCallback.onDowngrade(getWrappedDb(db), oldVersion, newVersion);
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            mCallback.onOpen(getWrappedDb(db));
        }

        @Override
        public synchronized void close() {
            super.close();
            mDbRef[0] = null;
        }
    }
}
