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

package pl.selvin.android.listsyncsample.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.RelativeLayout;

import pl.selvin.android.listsyncsample.R;

public class CheckableRelativeLayout extends RelativeLayout implements
        Checkable {

    public CheckableRelativeLayout(Context context) {
        super(context);
    }

    public CheckableRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckableRelativeLayout(Context context, AttributeSet attrs,
                                   int defStyle) {
        super(context, attrs, defStyle);

    }

    boolean isChecked = false;

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    View selector = null;

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        selector = findViewById(R.id.selector);
        if (selector == null)
            throw new IllegalStateException(
                    "CheckableRelativeLayout should have R.id.selector element!");
    }

    @Override
    public void setChecked(boolean checked) {
        isChecked = checked;
        setUp();
    }

    void setUp() {
        selector.setVisibility(isChecked ? View.VISIBLE : View.GONE);
    }

    @Override
    public void toggle() {
        isChecked = !isChecked;
        setUp();

    }

}
