/*
  Copyright (c) 2015 Selvin
  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
  by applicable law or agreed to in writing, software distributed under the
  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
  OF ANY KIND, either express or implied. See the License for the specific
  language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.content;

import android.accounts.AuthenticatorException;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface RequestExecutor {
	String SCOPE_PARAMETER = "SCOPE_PARAMETER";
	String REQUEST_TYPE_PARAMETER = "REQUEST_TYPE_PARAMETER";
	String REQUEST_METHOD_PARAMETER = "REQUEST_METHOD_PARAMETER";
	String SYNC_RESULT_PARAMETER = "SYNC_RESULT";
	String UPLOAD = "uploadchanges";
	String DOWNLOAD = "downloadchanges";
	int GET = 1;
	int POST = 2;

	@NonNull
	Result execute(@NonNull Context context, @Nullable BaseContentProvider.ISyncContentProducer syncContentProducer, @NonNull Bundle parameters) throws IOException, AuthenticatorException;

	@Retention(RetentionPolicy.SOURCE)
	@StringDef({UPLOAD, DOWNLOAD})
	@interface RequestType {
	}

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({GET, POST})
	@interface RequestMethod {
	}

	class Result {
		public final int status;
		public final InputStream inputBuffer;
		public final String error;


		protected Result(InputStream inputBuffer, int status, String error) {
			this.inputBuffer = inputBuffer;
			this.status = status;
			this.error = error;
		}

		public void close() {
		}
	}
}