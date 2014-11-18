package pl.selvin.android.ListSyncSample.ui;

import pl.selvin.android.ListSyncSample.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.widget.Toast;

public class ConfirmDeleteDialog extends DialogFragment {

    static final String URI = "uri";
    static final String TITLE = "title";
	public static ConfirmDeleteDialog newInstance(int title, Uri uri) {
		ConfirmDeleteDialog frag = new ConfirmDeleteDialog();
		Bundle args = new Bundle();
		args.putParcelable(URI, uri);
		args.putInt(TITLE, title);
		frag.setArguments(args);
		return frag;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		int title = getArguments().getInt(TITLE);
		final Uri uri = getArguments().getParcelable(URI);
		return new AlertDialog.Builder(getActivity())
				.setTitle(title)
				.setMessage(R.string.ui_alert_confirm)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								getActivity().getContentResolver().delete(uri,
										null, null);
								Toast.makeText(getActivity(), "Deleted!",
										Toast.LENGTH_SHORT).show();
							}
						})
				.setNegativeButton(android.R.string.cancel,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								Toast.makeText(getActivity(),
										"Deletion canceled!",
										Toast.LENGTH_SHORT).show();
							}
						}).create();
	}
}