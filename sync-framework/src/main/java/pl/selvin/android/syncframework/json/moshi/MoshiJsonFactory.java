package pl.selvin.android.syncframework.json.moshi;

import okio.BufferedSink;
import okio.BufferedSource;
import pl.selvin.android.syncframework.json.JsonFactory;
import pl.selvin.android.syncframework.json.JsonReader;
import pl.selvin.android.syncframework.json.JsonWriter;

public class MoshiJsonFactory implements JsonFactory {
	@Override
	public JsonReader createReader(BufferedSource source) {
		return new JsonUtf8Reader(source);
	}

	@Override
	public JsonWriter createWriter(BufferedSink sink) {
		return new JsonUtf8Writer(sink);
	}
}
