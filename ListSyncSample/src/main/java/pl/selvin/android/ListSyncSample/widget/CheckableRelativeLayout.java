package pl.selvin.android.listsyncsample.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.RelativeLayout;

import pl.selvin.android.listsyncsample.R;

public class CheckableRelativeLayout extends RelativeLayout implements
        Checkable {

    boolean isChecked = false;
    View selector = null;

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

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        isChecked = checked;
        setUp();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        selector = findViewById(R.id.selector);
        if (selector == null)
            throw new IllegalStateException(
                    "CheckableRelativeLayout should have R.id.selector element!");
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
