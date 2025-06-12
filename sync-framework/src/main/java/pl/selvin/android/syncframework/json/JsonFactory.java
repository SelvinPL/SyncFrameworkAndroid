package pl.selvin.android.syncframework.json;

import okio.BufferedSink;
import okio.BufferedSource;

public interface JsonFactory {
	JsonReader createReader(BufferedSource source);
	JsonWriter createWriter(BufferedSink sink);
}
