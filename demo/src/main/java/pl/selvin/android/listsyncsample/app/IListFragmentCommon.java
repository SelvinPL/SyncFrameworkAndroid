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

package pl.selvin.android.listsyncsample.app;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import androidx.fragment.app.Fragment;

public interface IListFragmentCommon {
	String LIST_FRAGMENT_SUPPORTS_EDIT = "LIST_FRAGMENT_SUPPORTS_EDIT";
	String LIST_FRAGMENT_SUPPORTS_PICK = "LIST_FRAGMENT_SUPPORTS_PICK";

	Class<? extends Fragment> getDetailsClass();

	void setArguments(Bundle fragmentArgs);

	/**
	 * @noinspection BooleanMethodIsAlwaysInverted, unused
	 */
	default boolean onItemClick(AdapterView<?> parent, View view, int position, long id, Uri currentUri, boolean editable, boolean longClick) {
		return false;
	}
}