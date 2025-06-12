package pl.selvin.android.syncframework.json;

import java.io.Closeable;
import java.io.IOException;

public interface JsonReader extends Closeable {
	int BEGIN_OBJECT = 0;
	int END_OBJECT = 1;
	int BEGIN_ARRAY = 2;
	int END_ARRAY = 3;
	int NAME = 4;
	int STRING = 5;
	int NUMBER_INTEGER = 6;
	int NUMBER_REAL = 7;
	int BOOLEAN = 8;
	int NULL = 9;
	int END_DOCUMENT = 10;

	void beginObject() throws IOException;

	void endObject() throws IOException;

	void beginArray() throws IOException;

	void endArray() throws IOException;

	boolean hasNext() throws IOException;

	String nextName() throws IOException;

	String nextString() throws IOException;

	boolean nextBoolean() throws IOException;

	<T> T nextNull() throws IOException;

	double nextDouble() throws IOException;

	long nextLong() throws IOException;

	void skipValue() throws IOException;

	int peek() throws IOException;

}
