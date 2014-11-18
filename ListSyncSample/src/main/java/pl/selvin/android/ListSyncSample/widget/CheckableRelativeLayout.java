package pl.selvin.android.ListSyncSample.widget;

import pl.selvin.android.ListSyncSample.R;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;
import android.widget.RelativeLayout;

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
