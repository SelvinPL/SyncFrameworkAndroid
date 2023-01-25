/*
  Copyright (c) 2014 Selvin
  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
  by applicable law or agreed to in writing, software distributed under the
  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
  OF ANY KIND, either express or implied. See the License for the specific
  language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.content;

public interface BlobsTable {
	//why such name? ... to make sure that Your table will not have the same name as blob table
	String NAME = "pl_selvin_android_sync_framework_blobs";
	String C_NAME = "name";
	String C_VALUE = "value";
	String C_DATE = "date";
	String C_STATE = "state";
}
