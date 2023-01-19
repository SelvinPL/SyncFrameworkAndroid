/*
 Copyright (c) 2014-2016 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.listsyncsample;

public interface Constants {
	String ACCOUNT_TYPE = BuildConfig.APPLICATION_ID;
	String AUTHORITY = BuildConfig.APPLICATION_ID;
	String AUTH_TOKEN_TYPE = BuildConfig.APPLICATION_ID;
	String SERVICE_URI = "http://selvin.pl/listservice/";

	interface StringUtil {
		String EMPTY = "";
	}
}