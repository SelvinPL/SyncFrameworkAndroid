package pl.selvin.android.syncframework.json;

import java.io.Closeable;
import java.io.IOException;

public interface JsonWriter extends Closeable {
	JsonWriter beginArray() throws IOException;

	JsonWriter endArray() throws IOException;

	JsonWriter beginObject() throws IOException;

	JsonWriter endObject() throws IOException;

	JsonWriter name(String name) throws IOException;

	JsonWriter value(long value) throws IOException;

	JsonWriter value(double value) throws IOException;

	JsonWriter value(boolean value) throws IOException;

	JsonWriter value(byte[] value) throws IOException;

	JsonWriter value(String value) throws IOException;

	JsonWriter nullValue() throws IOException;
}
