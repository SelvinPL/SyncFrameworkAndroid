/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.selvin.android.syncframework.json.moshi;

import java.io.IOException;
import java.util.Arrays;

import okio.BufferedSink;
import pl.selvin.android.syncframework.json.JsonDataException;
import pl.selvin.android.syncframework.json.JsonWriter;

public class JsonUtf8Writer implements JsonWriter {

	private static final String[] REPLACEMENT_CHARS = new String[128];
	private static final int MASK_6_BITS = 0x3f;
	private static final int BYTES_PER_UNENCODED_BLOCK = 3;
	private static final byte[] STANDARD_ENCODE_TABLE = new byte[]{
			'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
			'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
			'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
			'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
	};

	static {
		for (int i = 0; i <= 0x1f; i++) {
			REPLACEMENT_CHARS[i] = String.format("\\u%04x", i);
		}
		REPLACEMENT_CHARS['"'] = "\\\"";
		REPLACEMENT_CHARS['\\'] = "\\\\";
		REPLACEMENT_CHARS['\t'] = "\\t";
		REPLACEMENT_CHARS['\b'] = "\\b";
		REPLACEMENT_CHARS['\n'] = "\\n";
		REPLACEMENT_CHARS['\r'] = "\\r";
		REPLACEMENT_CHARS['\f'] = "\\f";
	}

	private final BufferedSink sink;
	private final int[] longToStringBuffer = new int[20];
	/**
	 * Sets whether object members are serialized when their value is null. This has no impact on
	 * array elements. The default is false.
	 * <p>
	 * Returns true if object members are serialized when their value is null. This has no impact on
	 * array elements. The default is false.
	 */
	private static final boolean serializeNulls = false;
	private  int[] pathIndices = new int[32];
	private String deferredName = null;
	/**
	 * The nesting stack. Using a manual array rather than an ArrayList saves 20%. This stack will
	 * grow itself up to 256 levels of nesting including the top-level document. Deeper nesting is
	 * prone to trigger StackOverflowErrors.
	 */
	private int stackSize = 0;
	private int[] scopes = new int[32];
	private String[] pathNames = new String[32];
	private int flattenStackSize = -1;

	public JsonUtf8Writer(BufferedSink sink) {
		this.sink = sink;
		pushScope(JsonScope.EMPTY_DOCUMENT);
	}

	private void string(String value) throws IOException {
		sink.writeByte('"');
		int last = 0;
		final int length = value.length();
		for (int i = 0; i < length; i++) {
			final char c = value.charAt(i);
			String replacement;
			if (c < 128) {
				replacement = REPLACEMENT_CHARS[c];
				if (replacement == null) {
					continue;
				}
			} else if (c == '\u2028') {
				replacement = "\\u2028";
			} else if (c == '\u2029') {
				replacement = "\\u2029";
			} else {
				continue;
			}
			if (last < i) {
				sink.writeUtf8(value, last, i);
			}
			sink.writeUtf8(replacement);
			last = i + 1;
		}
		if (last < length) {
			sink.writeUtf8(value, last, length);
		}
		sink.writeByte('"');
	}

	@Override
	public JsonWriter beginArray() throws IOException {
		writeDeferredName();
		return open(JsonScope.EMPTY_ARRAY, JsonScope.NONEMPTY_ARRAY, '[');
	}

	@Override
	public JsonWriter endArray() throws IOException {
		return close(JsonScope.EMPTY_ARRAY, JsonScope.NONEMPTY_ARRAY, ']');
	}

	@Override
	public JsonWriter beginObject() throws IOException {
		writeDeferredName();
		return open(JsonScope.EMPTY_OBJECT, JsonScope.NONEMPTY_OBJECT, '{');
	}

	@Override
	public JsonWriter endObject() throws IOException {
		return close(JsonScope.EMPTY_OBJECT, JsonScope.NONEMPTY_OBJECT, '}');
	}

	@Override
	public JsonWriter name(String name) throws IOException {
		if (stackSize == 0) {
			throw new IllegalStateException("JsonWriter is closed.");
		}
		final int context = peekScope();
		final boolean isWritingObject = !(
				context != JsonScope.EMPTY_OBJECT &&
						context != JsonScope.NONEMPTY_OBJECT ||
						deferredName != null
		);
		if (!isWritingObject) {
			throw new IllegalStateException("Nesting problem.");
		}
		deferredName = name;
		pathNames[stackSize - 1] = name;
		return this;
	}

	@Override
	public JsonWriter value(String value) throws IOException {
		if (value == null) {
			return nullValue();
		}
		writeDeferredName();
		beforeValue();
		string(value);
		pathIndices[stackSize - 1]++;
		return this;
	}

	@Override
	public JsonWriter nullValue() throws IOException {
		if (deferredName != null) {
			if (serializeNulls) {
				writeDeferredName();
			} else {
				deferredName = null;
				return this; // skip the name and the value
			}
		}
		beforeValue();
		sink.writeUtf8("null");
		pathIndices[stackSize - 1]++;
		return this;
	}

	@Override
	public JsonWriter value(boolean value) throws IOException {
		writeDeferredName();
		beforeValue();
		sink.writeUtf8(value ? "true" : "false");
		pathIndices[stackSize - 1]++;
		return this;
	}

	/*
	base64 encoding from https://github.com/apache/commons-codec/blob/master/src/main/java/org/apache/commons/codec/binary/Base64.java
	allows us to not allocate interim string
	 */
	@Override
	public JsonWriter value(byte[] value) throws IOException {
		if (value == null) {
			return nullValue();
		}
		writeDeferredName();
		beforeValue();
		sink.writeByte('"');
		int modulus = 0;
		int ibitWorkArea = 0;
		for (byte item : value) {
			modulus = (modulus + 1) % BYTES_PER_UNENCODED_BLOCK;
			int b = item;
			if (b < 0) {
				b += 256;
			}
			ibitWorkArea = (ibitWorkArea << 8) + b; // BITS_PER_BYTE
			if (0 == modulus) { // 3 bytes = 24 bits = 4 * 6 bits to extract
				sink.writeByte(STANDARD_ENCODE_TABLE[ibitWorkArea >> 18 & MASK_6_BITS]);
				sink.writeByte(STANDARD_ENCODE_TABLE[ibitWorkArea >> 12 & MASK_6_BITS]);
				sink.writeByte(STANDARD_ENCODE_TABLE[ibitWorkArea >> 6 & MASK_6_BITS]);
				sink.writeByte(STANDARD_ENCODE_TABLE[ibitWorkArea & MASK_6_BITS]);
			}
		}
		if (modulus == 1) {
			// top 6 bits:
			sink.writeByte(STANDARD_ENCODE_TABLE[ibitWorkArea >> 2 & MASK_6_BITS]);
			// remaining 2:
			sink.writeByte(STANDARD_ENCODE_TABLE[ibitWorkArea << 4 & MASK_6_BITS]);
			sink.writeByte('=');
			sink.writeByte('=');
		} else if (modulus == 2) {
			sink.writeByte(STANDARD_ENCODE_TABLE[ibitWorkArea >> 10 & MASK_6_BITS]);
			sink.writeByte(STANDARD_ENCODE_TABLE[ibitWorkArea >> 4 & MASK_6_BITS]);
			sink.writeByte(STANDARD_ENCODE_TABLE[ibitWorkArea << 2 & MASK_6_BITS]);
			sink.writeByte('=');
		}
		sink.writeByte('"');
		pathIndices[stackSize - 1]++;
		return this;
	}

	@Override
	public JsonWriter value(double value) throws IOException {
		writeDeferredName();
		beforeValue();
		//TODO: does using Ryu would take less memory - prolly, does it worth it ?
		sink.writeUtf8(Double.toString(value));
		pathIndices[stackSize - 1]++;
		return this;
	}

	@Override
	public JsonWriter value(long value) throws IOException {
		writeDeferredName();
		beforeValue();
		if (value != 0) {
			if (value < 0) {
				sink.writeByte('-');
				value = -value;
			}
			int pos = 0;
			for (int i = 0; i < longToStringBuffer.length; i++) {
				longToStringBuffer[pos] = (int) (value % 10);
				value /= 10;
				if (value == 0)
					break;
				pos++;
			}

			while (pos >= 0) {
				sink.writeByte(longToStringBuffer[pos] + '0');
				pos--;
			}
		} else {
			sink.writeByte('0');
		}
		pathIndices[stackSize - 1]++;
		return this;
	}

	/**
	 * Flushes and closes this writer and the underlying [Sink].
	 *
	 * @throws JsonDataException if the JSON document is incomplete.
	 */
	@Override
	public void close() throws IOException {
		sink.close();
		final int size = stackSize;
		if (size > 1 || size == 1 && scopes[0] != JsonScope.NONEMPTY_DOCUMENT) {
			throw new IOException("Incomplete document");
		}
		stackSize = 0;
	}

	/**
	 * Closes the current scope by appending any necessary whitespace and the given bracket.
	 */
	private JsonWriter close(int empty, int nonempty, char closeBracket) throws IOException {
		final int context = peekScope();
		if (context != nonempty && context != empty) {
			throw new IllegalStateException("Nesting problem.");
		}
		if (deferredName != null) {
			throw new IllegalStateException("Dangling name: " + deferredName);
		}
		if (stackSize == ~flattenStackSize) {
			// Cancel this close. Restore the flattenStackSize so we're ready to flatten again!
			flattenStackSize = ~flattenStackSize;
			return this;
		}
		stackSize--;
		pathNames[stackSize] = null; // Free the last path name so that it can be garbage collected!
		pathIndices[stackSize - 1]++;
		sink.writeByte(closeBracket);
		return this;
	}

	/**
	 * Enters a new scope by appending any necessary whitespace and the given bracket.
	 */
	private JsonWriter open(int empty, int nonempty, char openBracket) throws IOException {
		final boolean shouldCancelOpen = stackSize == flattenStackSize &&
				(scopes[stackSize - 1] == empty || scopes[stackSize - 1] == nonempty);
		if (shouldCancelOpen) {
			// Cancel this open. Invert the flatten stack size until this is closed.
			flattenStackSize = ~flattenStackSize;
			return this;
		}
		beforeValue();
		checkStack();
		pushScope(empty);
		pathIndices[stackSize - 1] = 0;
		sink.writeByte(openBracket);
		return this;
	}

	/**
	 * Inserts any necessary separators and whitespace before a literal value, inline array, or inline
	 * object. Also adjusts the stack to expect either a closing bracket or another element.
	 */
	private void beforeValue() throws IOException {
		final int nextTop;
		switch (peekScope()) {
			case JsonScope.NONEMPTY_DOCUMENT:
			case JsonScope.EMPTY_DOCUMENT:
				nextTop = JsonScope.NONEMPTY_DOCUMENT;
				break;

			case JsonScope.NONEMPTY_ARRAY:
				sink.writeByte(',');
				nextTop = JsonScope.NONEMPTY_ARRAY;
				break;

			case JsonScope.EMPTY_ARRAY:
				nextTop = JsonScope.NONEMPTY_ARRAY;
				break;

			case JsonScope.DANGLING_NAME:
				nextTop = JsonScope.NONEMPTY_OBJECT;
				sink.writeByte(':');
				break;
			case JsonScope.STREAMING_VALUE:
				throw new IllegalStateException("Sink from valueSink() was not closed");
			default:
				throw new IllegalStateException("Nesting problem.");
		}
		replaceTop(nextTop);
	}

	private void writeDeferredName() throws IOException {
		if (deferredName != null) {
			beforeName();
			string(deferredName);
			deferredName = null;
		}
	}

	/**
	 * Inserts any necessary separators and whitespace before a name. Also adjusts the stack to expect
	 * the name's value.
	 */
	private void beforeName() throws IOException {
		final int context = peekScope();
		if (context == JsonScope.NONEMPTY_OBJECT) { // first in object
			sink.writeByte(',');
		} else {
			if (context != JsonScope.EMPTY_OBJECT) {
				// not in an object!
				throw new IllegalStateException("Nesting problem.");
			}
		}
		replaceTop(JsonScope.DANGLING_NAME);
	}

	/**
	 * Returns a [JsonPath](http://goessner.net/articles/JsonPath/) to the current location
	 * in the JSON value.
	 */
	private String path() {
		return JsonScope.getPath(stackSize, scopes, pathNames, pathIndices);
	}

	/**
	 * Returns the scope on the top of the stack.
	 */
	private int peekScope() {
		if (stackSize == 0) throw new IllegalStateException("JsonWriter is closed.");
		return scopes[stackSize - 1];
	}

	/**
	 * Before pushing a value on the stack this confirms that the stack has capacity.
	 */
	private void checkStack() {
		if (stackSize != scopes.length)
			return;
		if (stackSize == 256) {
			throw new JsonDataException("Nesting too deep at " + path() + ": circular reference?");
		}
		scopes = Arrays.copyOf(scopes, scopes.length * 2);
		pathNames = Arrays.copyOf(pathNames, pathNames.length * 2);
		pathIndices = Arrays.copyOf(pathIndices, pathIndices.length * 2);
	}

	private void pushScope(int newTop) {
		scopes[stackSize++] = newTop;
	}

	/**
	 * Replace the value on the top of the stack with the given value.
	 */
	private void replaceTop(int topOfStack) {
		scopes[stackSize - 1] = topOfStack;
	}
}
