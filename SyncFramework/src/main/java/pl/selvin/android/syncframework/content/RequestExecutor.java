/**
 * Copyright (c) 2015 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.content;

import java.io.IOException;
import java.io.InputStream;

public interface RequestExecutor {

    int HTTP_GET = 1;
    int HTTP_POST = 2;

    Result execute(int requestMethod, String serviceRequestUrl, final BaseContentProvider.ISyncContentProducer syncContentProducer) throws IOException;


    class Result {
        public final InputStream inputBuffer;
        public final int status;
        public final String error;


        public Result(InputStream inputBuffer, int status, String error) {
            this.inputBuffer = inputBuffer;
            this.status = status;
            this.error = error;
        }

        public void close() {
        }

        public String getError() {
            return error;
        }
    }
}
