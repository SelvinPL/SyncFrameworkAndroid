package pl.selvin.android.syncframework.json.moshi;

import java.util.Collections;
import java.util.LinkedHashSet;

import okio.BufferedSink;
import okio.BufferedSource;
import pl.selvin.android.autocontentprovider.db.ColumnInfo;
import pl.selvin.android.syncframework.content.SYNC;
import pl.selvin.android.syncframework.content.SyncContentHelper;
import pl.selvin.android.syncframework.content.SyncTableInfo;
import pl.selvin.android.syncframework.json.JsonFactory;
import pl.selvin.android.syncframework.json.JsonReader;
import pl.selvin.android.syncframework.json.JsonWriter;

public class MoshiJsonFactory implements JsonFactory {

	private final NameCache names;

	public MoshiJsonFactory(SyncContentHelper helper) {
		this.names = new NameCache(collectNames(helper));
	}

	private static String[] collectNames(SyncContentHelper helper) {
		final LinkedHashSet<String> s = new LinkedHashSet<>();
		Collections.addAll(s,
				SYNC.d, SYNC.__sync, SYNC.serverBlob, SYNC.moreChangesAvailable,
				SYNC.resolveConflicts, SYNC.results, SYNC.__metadata, SYNC.uri, SYNC.type,
				SYNC.isDeleted, SYNC.tempId, SYNC.__syncConflict, SYNC.isResolved,
				SYNC.conflictResolution, SYNC.conflictingChange, SYNC.__syncError,
				SYNC.errorDescription, SYNC.changeInError);
		for (SyncTableInfo t : helper.getAllTables())
			for (ColumnInfo c : t.columns)
				s.add(c.name);
		return s.toArray(new String[0]);
	}

	@Override
	public JsonReader createReader(BufferedSource source) {
		return new JsonUtf8Reader(source, names);
	}

	@Override
	public JsonWriter createWriter(BufferedSink sink) {
		return new JsonUtf8Writer(sink);
	}
}