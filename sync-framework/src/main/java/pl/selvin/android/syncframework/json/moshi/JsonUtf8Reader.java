/*
 * Copyright (C) 2010 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.selvin.android.syncframework.json.moshi;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import pl.selvin.android.syncframework.json.JsonDataException;
import pl.selvin.android.syncframework.json.JsonEncodingException;
import pl.selvin.android.syncframework.json.JsonReader;

class JsonUtf8Reader implements JsonReader, Closeable {
	private final static int PEEKED_NONE = 0;
	private final static int PEEKED_BEGIN_OBJECT = 1;
	private final static int PEEKED_END_OBJECT = 2;
	private final static int PEEKED_BEGIN_ARRAY = 3;
	private final static int PEEKED_END_ARRAY = 4;
	private final static int PEEKED_TRUE = 5;
	private final static int PEEKED_FALSE = 6;
	private final static int PEEKED_NULL = 7;
	private final static int PEEKED_SINGLE_QUOTED = 8;
	private final static int PEEKED_DOUBLE_QUOTED = 9;
	private final static int PEEKED_UNQUOTED = 10;
	/**
	 * When this is returned, the string value is stored in peekedString.
	 */
	private final static int PEEKED_BUFFERED = 11;
	private final static int PEEKED_SINGLE_QUOTED_NAME = 12;
	private final static int PEEKED_DOUBLE_QUOTED_NAME = 13;
	private final static int PEEKED_UNQUOTED_NAME = 14;
	private final static int PEEKED_BUFFERED_NAME = 15;
	/**
	 * When this is returned, the integer value is stored in peekedLong.
	 */
	private final static int PEEKED_LONG = 16;
	private final static int PEEKED_NUMBER = 17;
	private final static int PEEKED_EOF = 18;
	/* State machine when parsing numbers */
	private final static int NUMBER_CHAR_NONE = 0;
	private final static int NUMBER_CHAR_SIGN = 1;
	private final static int NUMBER_CHAR_DIGIT = 2;
	private final static int NUMBER_CHAR_DECIMAL = 3;
	private final static int NUMBER_CHAR_FRACTION_DIGIT = 4;
	private final static int NUMBER_CHAR_EXP_E = 5;
	private final static int NUMBER_CHAR_EXP_SIGN = 6;
	private final static int NUMBER_CHAR_EXP_DIGIT = 7;
	private final static long MIN_INCOMPLETE_INTEGER = Long.MIN_VALUE / 10;
	private final static Charset UTF8_CHARSET = StandardCharsets.UTF_8;
	private final static ByteString SINGLE_QUOTE_OR_SLASH = new ByteString("'\\".getBytes(UTF8_CHARSET));
	private final static ByteString DOUBLE_QUOTE_OR_SLASH = new ByteString("\"\\".getBytes(UTF8_CHARSET));
	private final static ByteString UNQUOTED_STRING_TERMINALS = new ByteString("{}[]:, \n\t\r\u000C/\\;#=".getBytes(UTF8_CHARSET));
	private final static ByteString LINEFEED_OR_CARRIAGE_RETURN = new ByteString("\n\r".getBytes(UTF8_CHARSET));
	private final static ByteString CLOSING_BLOCK_COMMENT = new ByteString("*/".getBytes(UTF8_CHARSET));
	private static final String[] names = new String[]{
			"BEGIN_OBJECT",
			"END_OBJECT",
			"BEGIN_ARRAY",
			"END_ARRAY",
			"NAME",
			"STRING",
			"NUMBER_INTEGER",
			"NUMBER_REAL",
			"BOOLEAN",
			"NULL",
			"END_DOCUMENT"
	};
	/**
	 * The input JSON.
	 */
	private final BufferedSource source;
	private final Buffer buffer;
	private int peeked = PEEKED_NONE;
	private int stackSize = 0;
	private int[] scopes;
	private String[] pathNames;
	private int[] pathIndices;
	/**
	 * A peeked value that was composed entirely of digits with an optional leading dash. Positive
	 * values may not have a leading 0.
	 */
	private long peekedLong = 0L;
	/**
	 * The number of characters in a peeked number literal.
	 */
	private int peekedNumberLength = 0;
	/**
	 * A peeked string that should be parsed on the next double, long or string. This is populated
	 * before a numeric value is parsed and used if that parsing fails.
	 */
	private String peekedString = null;

	public JsonUtf8Reader(BufferedSource source) {
		this.source = source;
		this.buffer = source.getBuffer();
		scopes = new int[32];
		pathNames = new String[32];
		pathIndices = new int[32];
		pushScope(JsonScope.EMPTY_DOCUMENT);
	}

	private void pushScope(int newTop) {
		if (stackSize == scopes.length) {
			if (stackSize == 256) {
				throw new JsonDataException("Nesting too deep at " + path());
			}
			scopes = Arrays.copyOf(scopes, scopes.length * 2);
			pathNames = Arrays.copyOf(pathNames, pathNames.length * 2);
			pathIndices = Arrays.copyOf(pathIndices, pathIndices.length * 2);
		}
		scopes[stackSize++] = newTop;
	}

	@Override
	public void beginArray() throws IOException {
		final int p = peekIfNone();
		if (p == PEEKED_BEGIN_ARRAY) {
			pushScope(JsonScope.EMPTY_ARRAY);
			pathIndices[stackSize - 1] = 0;
			peeked = PEEKED_NONE;
		} else {
			throw new JsonDataException("Expected BEGIN_ARRAY but was " + peekName() + " at path " + path());
		}
	}

	@Override
	public void endArray() throws IOException {
		final int p = peekIfNone();
		if (p == PEEKED_END_ARRAY) {
			stackSize--;
			pathIndices[stackSize - 1]++;
			peeked = PEEKED_NONE;
		} else {
			throw new JsonDataException("Expected END_ARRAY but was " + peekName() + " at path " + path());
		}
	}

	@Override
	public void beginObject() throws IOException {
		final int p = peekIfNone();
		if (p == PEEKED_BEGIN_OBJECT) {
			pushScope(JsonScope.EMPTY_OBJECT);
			peeked = PEEKED_NONE;
		} else {
			throw new JsonDataException("Expected BEGIN_OBJECT but was " + peekName() + " at path " + path());
		}
	}

	@Override
	public void endObject() throws IOException {
		final int p = peekIfNone();
		if (p == PEEKED_END_OBJECT) {
			stackSize--;
			pathNames[stackSize] =
					null; // Free the last path name so that it can be garbage collected!
			pathIndices[stackSize - 1]++;
			peeked = PEEKED_NONE;
		} else {
			throw new JsonDataException("Expected END_OBJECT but was " + peekName() + " at path " + path());
		}
	}

	@Override
	public boolean hasNext() throws IOException {
		final int p = peekIfNone();
		return p != PEEKED_END_OBJECT && p != PEEKED_END_ARRAY && p != PEEKED_EOF;
	}

	@Override
	public int peek() throws IOException {
		switch (peekIfNone()) {
			case PEEKED_BEGIN_OBJECT:
				return JsonReader.BEGIN_OBJECT;
			case PEEKED_END_OBJECT:
				return JsonReader.END_OBJECT;
			case PEEKED_BEGIN_ARRAY:
				return JsonReader.BEGIN_ARRAY;
			case PEEKED_END_ARRAY:
				return JsonReader.END_ARRAY;
			case PEEKED_SINGLE_QUOTED_NAME:
			case PEEKED_DOUBLE_QUOTED_NAME:
			case PEEKED_UNQUOTED_NAME:
			case PEEKED_BUFFERED_NAME:
				return JsonReader.NAME;
			case PEEKED_TRUE:
			case PEEKED_FALSE:
				return JsonReader.BOOLEAN;
			case PEEKED_NULL:
				return JsonReader.NULL;
			case PEEKED_SINGLE_QUOTED:
			case PEEKED_DOUBLE_QUOTED:
			case PEEKED_UNQUOTED:
			case PEEKED_BUFFERED:
				return JsonReader.STRING;
			case PEEKED_LONG:
				return JsonReader.NUMBER_INTEGER;
			case PEEKED_NUMBER:
				return JsonReader.NUMBER_REAL;
			case PEEKED_EOF:
				return JsonReader.END_DOCUMENT;
			default:
				throw new AssertionError();
		}
	}

	private int doPeek() throws IOException {
		final int peekStack = scopes[stackSize - 1];
		switch (peekStack) {
			case JsonScope.EMPTY_ARRAY:
				scopes[stackSize - 1] = JsonScope.NONEMPTY_ARRAY;
				break;
			case JsonScope.NONEMPTY_ARRAY:
				// Look for a comma before the next element.
				final char ca = (char) nextNonWhitespace(true);
				buffer.readByte(); // consume ']' or ','.
				switch (ca) {
					case ']':
						return setPeeked(PEEKED_END_ARRAY);
					case ';':
						/*no op*/
					case ',':
						break;
					default:
						throw new JsonEncodingException("Unterminated array");
				}
				break;
			case JsonScope.EMPTY_OBJECT:
			case JsonScope.NONEMPTY_OBJECT: {
				scopes[stackSize - 1] = JsonScope.DANGLING_NAME;
				// Look for a comma before the next element.
				if (peekStack == JsonScope.NONEMPTY_OBJECT) {
					final char co = (char) nextNonWhitespace(true);
					buffer.readByte(); // Consume '}' or ','.
					switch (co) {
						case '}':
							return setPeeked(PEEKED_END_OBJECT);
						/*no op*/
						case ',':
						case ';':
							break;
						default:
							throw new JsonEncodingException("Unterminated object");
					}
				}
				final char co2 = (char) nextNonWhitespace(true);
				final int next;
				switch (co2) {
					case '"':
						buffer.readByte(); // consume the '\"'.
						next = PEEKED_DOUBLE_QUOTED_NAME;
						break;

					case '\'':
						buffer.readByte(); // consume the '\''.
						next = PEEKED_SINGLE_QUOTED_NAME;
						break;

					case '}':
						if (peekStack != JsonScope.NONEMPTY_OBJECT) {
							buffer.readByte(); // consume the '}'.
							next = PEEKED_END_OBJECT;
						} else {
							throw new JsonEncodingException("Expected name");
						}
						break;
					default:
						if (isLiteral(co2)) {
							next = PEEKED_UNQUOTED_NAME;
						} else {
							throw new JsonEncodingException("Expected name");
						}
						break;
				}
				peeked = next;
				return next;
			}
			case JsonScope.DANGLING_NAME:
				scopes[stackSize - 1] = JsonScope.NONEMPTY_OBJECT;
				// Look for a colon before the value.
				final char c = (char) nextNonWhitespace(true);
				buffer.readByte(); // Consume ':'.
				switch (c) {
					/*no op*/
					case ':':
						break;
					case '=':
						if (source.request(1) && buffer.getByte(0) == '>') {
							buffer.readByte(); // Consume '>'.
						}
						break;
					default:
						throw new JsonEncodingException("Expected ':'");
				}
				break;

			case JsonScope.EMPTY_DOCUMENT:
				scopes[stackSize - 1] = JsonScope.NONEMPTY_DOCUMENT;
				break;
			case JsonScope.NONEMPTY_DOCUMENT:
				if (nextNonWhitespace(false) == -1) {
					return setPeeked(PEEKED_EOF);
				}
				break;

			case JsonScope.STREAMING_VALUE:
				stackSize--;
				return doPeek();
			default:
				if (peekStack == JsonScope.CLOSED) {
					throw new IllegalStateException("JsonReader is closed");
				}
				break;
		}
		// "fallthrough" from previous `when`
		switch ((char) nextNonWhitespace(true)) {
			case ']':
				switch (peekStack) {
					case JsonScope.EMPTY_ARRAY:
						buffer.readByte(); // Consume ']'.
						return setPeeked(PEEKED_END_ARRAY);
					case JsonScope.NONEMPTY_ARRAY:
						// In lenient mode, a 0-length literal in an array means 'null'.
						return setPeeked(PEEKED_NULL);
					default:
						throw new JsonEncodingException("Unexpected value");
				}
				// In lenient mode, a 0-length literal in an array means 'null'.
			case ';':
			case ',':
				switch (peekStack) {
					case JsonScope.EMPTY_ARRAY:
					case JsonScope.NONEMPTY_ARRAY:
						return setPeeked(PEEKED_NULL);
					default:
						throw new JsonEncodingException("Unexpected value");
				}

			case '\'':
				buffer.readByte(); // Consume '\''.
				return setPeeked(PEEKED_SINGLE_QUOTED);

			case '"':
				buffer.readByte(); // Consume '\"'.
				return setPeeked(PEEKED_DOUBLE_QUOTED);

			case '[':
				buffer.readByte(); // Consume '['.
				return setPeeked(PEEKED_BEGIN_ARRAY);

			case '{':
				buffer.readByte(); // Consume '{'.
				return setPeeked(PEEKED_BEGIN_OBJECT);
		}
		int result = peekKeyword();
		if (result != PEEKED_NONE) {
			return result;
		}
		result = peekNumber();
		if (result != PEEKED_NONE) {
			return result;
		}
		if (!isLiteral((char) buffer.getByte(0))) {
			throw new JsonEncodingException("Expected value");
		}
		return setPeeked(PEEKED_UNQUOTED);
	}

	private int peekKeyword() throws IOException {
		// Figure out which keyword we're matching against by its first character.
		char c = (char) buffer.getByte(0);
		final String keyword;
		final String keywordUpper;
		final int peeking;
		switch (c) {
			case 't':
			case 'T':
				keyword = "true";
				keywordUpper = "TRUE";
				peeking = PEEKED_TRUE;
				break;
			case 'f':
			case 'F':
				keyword = "false";
				keywordUpper = "FALSE";
				peeking = PEEKED_FALSE;
				break;

			case 'n':
			case 'N':
				keyword = "null";
				keywordUpper = "NULL";
				peeking = PEEKED_NULL;
				break;
			default:
				return PEEKED_NONE;
		}

		// Confirm that chars [1..length) match the keyword.
		final int length = keyword.length();
		for (int i = 1; i < length; i++) {
			if (!source.request(i + 1)) {
				return PEEKED_NONE;
			}
			c = (char) buffer.getByte(i);
			if (c != keyword.charAt(i) && c != keywordUpper.charAt(i)) {
				return PEEKED_NONE;
			}
		}
		if (source.request(length + 1) && isLiteral((char) buffer.getByte(length))) {
			return PEEKED_NONE; // Don't match trues, falsely or nullsoft!
		}

		// We've found the keyword followed either by EOF or by a non-literal character.
		buffer.skip(length);
		return setPeeked(peeking);
	}

	private int peekNumber() throws IOException {
		long value = 0L; // Negative to accommodate Long.MIN_VALUE more easily.
		boolean negative = false;
		boolean fitsInLong = true;
		int last = NUMBER_CHAR_NONE;
		long i = 0L;
		loop:
		while (source.request(i + 1)) {
			final char c = (char) buffer.getByte(i);
			switch (c) {
				case '-':
					switch (last) {
						case NUMBER_CHAR_NONE:
							negative = true;
							last = NUMBER_CHAR_SIGN;
							i++;
							continue;
						case NUMBER_CHAR_EXP_E:
							last = NUMBER_CHAR_EXP_SIGN;
							i++;
							continue;
					}
					return PEEKED_NONE;

				case '+':
					if (last == NUMBER_CHAR_EXP_E) {
						last = NUMBER_CHAR_EXP_SIGN;
						i++;
						continue;
					}
					return PEEKED_NONE;

				case 'e':
				case 'E':
					if (last == NUMBER_CHAR_DIGIT || last == NUMBER_CHAR_FRACTION_DIGIT) {
						last = NUMBER_CHAR_EXP_E;
						i++;
						continue;
					}
					return PEEKED_NONE;

				case '.':
					if (last == NUMBER_CHAR_DIGIT) {
						last = NUMBER_CHAR_DECIMAL;
						i++;
						continue;
					}
					return PEEKED_NONE;
				default:
					if (c < '0' || c > '9') {
						if (!isLiteral(c))
							break loop;
						return PEEKED_NONE;
					}
					switch (last) {
						case NUMBER_CHAR_SIGN:
						case NUMBER_CHAR_NONE:
							value = -(c - '0');
							last = NUMBER_CHAR_DIGIT;
							break;

						case NUMBER_CHAR_DIGIT:
							if (value == 0L) {
								return PEEKED_NONE; // Leading '0' prefix is not allowed (since it could be octal).
							}
							final long newValue = value * 10 - (c - '0');
							fitsInLong = fitsInLong &&
									(value > MIN_INCOMPLETE_INTEGER
											|| value == MIN_INCOMPLETE_INTEGER
											&& newValue < value);
							value = newValue;
							break;
						case NUMBER_CHAR_DECIMAL:
							last = NUMBER_CHAR_FRACTION_DIGIT;
							break;

						case NUMBER_CHAR_EXP_E:
						case NUMBER_CHAR_EXP_SIGN:
							last = NUMBER_CHAR_EXP_DIGIT;
							break;
					}
			}
			i++;
		}

		// We've read a complete number. Decide if it's a PEEKED_LONG or a PEEKED_NUMBER.
		if (last == NUMBER_CHAR_DIGIT &&
				fitsInLong &&
				(value != Long.MIN_VALUE || negative) &&
				(value != 0L || !negative)) {
			peekedLong = negative ? value : -value;
			buffer.skip(i);
			return setPeeked(PEEKED_LONG);
		} else if (
				last == NUMBER_CHAR_DIGIT ||
						last == NUMBER_CHAR_FRACTION_DIGIT ||
						last == NUMBER_CHAR_EXP_DIGIT) {
			peekedNumberLength = (int) i;
			return setPeeked(PEEKED_NUMBER);
		}

		return PEEKED_NONE;
	}

	private boolean isLiteral(char c) {
		switch (c) {
			case '/':
			case '\\':
			case ';':
			case '#':
			case '=':
				return false;

			// 0x000C = \f
			case '{':
			case '}':
			case '[':
			case ']':
			case ':':
			case ',':
			case ' ':
			case '\t':
			case '\u000C':
			case '\r':
			case '\n':
				return false;
			default:
				return true;
		}
	}

	@Override
	public String nextName() throws IOException {
		final String result;
		switch (peekIfNone()) {
			case PEEKED_UNQUOTED_NAME:
				result = nextUnquotedValue();
				break;
			case PEEKED_DOUBLE_QUOTED_NAME:
				result = nextQuotedValue(DOUBLE_QUOTE_OR_SLASH);
				break;
			case PEEKED_SINGLE_QUOTED_NAME:
				result = nextQuotedValue(SINGLE_QUOTE_OR_SLASH);
				break;
			case PEEKED_BUFFERED_NAME:
				result = peekedString;
				peekedString = null;
				break;
			default:
				throw new JsonDataException("Expected a name but was " + peekName() + " at path " + path());
		}
		peeked = PEEKED_NONE;
		pathNames[stackSize - 1] = result;
		return result;
	}

	@Override
	public String nextString() throws IOException {
		final String result;
		switch (peekIfNone()) {
			case PEEKED_UNQUOTED:
				result = nextUnquotedValue();
				break;
			case PEEKED_DOUBLE_QUOTED:
				result = nextQuotedValue(DOUBLE_QUOTE_OR_SLASH);
				break;
			case PEEKED_SINGLE_QUOTED:
				result = nextQuotedValue(SINGLE_QUOTE_OR_SLASH);
				break;
			case PEEKED_BUFFERED:
				result = peekedString;
				peekedString = null;
				break;

			case PEEKED_LONG:
				result = Long.toString(peekedLong);
				break;
			case PEEKED_NUMBER:
				result = buffer.readUtf8(peekedNumberLength);
				break;
			default:
				throw new JsonDataException("Expected a string but was " + peekName() + " at path " + path());
		}
		peeked = PEEKED_NONE;
		pathIndices[stackSize - 1]++;
		return result;
	}

	private String peekName() throws IOException {
		return names[peek()];
	}

	@Override
	public boolean nextBoolean() throws IOException {
		switch (peekIfNone()) {
			case PEEKED_TRUE:
				peeked = PEEKED_NONE;
				pathIndices[stackSize - 1]++;
				return true;
			case PEEKED_FALSE:
				peeked = PEEKED_NONE;
				pathIndices[stackSize - 1]++;
				return false;
			default:
				throw new JsonDataException("Expected a boolean but was " + peekName() + " at path " + path());
		}
	}

	@Override
	public <T> T nextNull() throws IOException {
		final int p = peekIfNone();
		if (p == PEEKED_NULL) {
			peeked = PEEKED_NONE;
			pathIndices[stackSize - 1]++;
			return null;
		}
		throw new JsonDataException("Expected null but was " + peekName() + " at path " + path());
	}

	@Override
	public double nextDouble() throws IOException {
		final int p = peekIfNone();
		if (p == PEEKED_LONG) {
			peeked = PEEKED_NONE;
			pathIndices[stackSize - 1]++;
			return (double) peekedLong;
		}
		final String next;
		switch (p) {
			case PEEKED_NUMBER:
				peekedString = next = buffer.readUtf8(peekedNumberLength);
				break;
			case PEEKED_DOUBLE_QUOTED:
				peekedString = next = nextQuotedValue(DOUBLE_QUOTE_OR_SLASH);
				break;
			case PEEKED_SINGLE_QUOTED:
				peekedString = next = nextQuotedValue(SINGLE_QUOTE_OR_SLASH);
				break;
			case PEEKED_UNQUOTED:
				peekedString = next = nextUnquotedValue();
				break;
			case PEEKED_BUFFERED:
				// PEEKED_BUFFERED means the value's been stored in peekedString
				next = peekedString;
				break;
			default:
				throw new JsonDataException("Expected a double but was " + peek() + " at path " + path());
		}
		peeked = PEEKED_BUFFERED;
		final double result;
		try {
			result = Double.parseDouble(next);
		} catch (NumberFormatException e) {
			throw new JsonDataException("Expected a double but was " + next + " at path " + path());
		}
		peekedString = null;
		peeked = PEEKED_NONE;
		pathIndices[stackSize - 1]++;
		return result;
	}

	@Override
	public long nextLong() throws IOException {
		final int p = peekIfNone();
		if (p == PEEKED_LONG) {
			peeked = PEEKED_NONE;
			pathIndices[stackSize - 1]++;
			return peekedLong;
		}
		if (p == PEEKED_NUMBER) {
			peekedString = buffer.readUtf8(peekedNumberLength);
		} else if (p == PEEKED_DOUBLE_QUOTED || p == PEEKED_SINGLE_QUOTED) {
			peekedString = p == PEEKED_DOUBLE_QUOTED ? nextQuotedValue(DOUBLE_QUOTE_OR_SLASH) :
					nextQuotedValue(SINGLE_QUOTE_OR_SLASH);
			try {
				final long result = Long.parseLong(peekedString);
				peeked = PEEKED_NONE;
				pathIndices[stackSize - 1]++;
				return result;
			} catch (NumberFormatException ignored) {
				// Fall back to parse as a BigDecimal below.
			}
		} else if (p != PEEKED_BUFFERED) {
			throw new JsonDataException("Expected a long but was " + peek() + " at path " + path());
		}
		peeked = PEEKED_BUFFERED;
		final long result;
		try {
			final BigDecimal asDecimal = new BigDecimal(peekedString);
			result = asDecimal.longValueExact();
		} catch (NumberFormatException | ArithmeticException e) {
			throw new JsonDataException("Expected a long but was " + peekedString + " at path " + path());
		}
		peekedString = null;
		peeked = PEEKED_NONE;
		pathIndices[stackSize - 1]++;
		return result;
	}

	/**
	 * Returns the string up to but not including `quote`, unescaping any character escape
	 * sequences encountered along the way. The opening quote should have already been read. This
	 * consumes the closing quote, but does not include it in the returned string.
	 *
	 * @throws IOException if any unicode escape sequences are malformed.
	 */
	private String nextQuotedValue(ByteString runTerminator) throws IOException {
		StringBuilder builder = null;
		while (true) {
			final long index = source.indexOfElement(runTerminator);
			if (index == -1L) throw new JsonEncodingException("Unterminated string");

			// If we've got an escape character, we're going to need a string builder.
			if (buffer.getByte(index) == '\\') {
				if (builder == null) builder = new StringBuilder();
				builder.append(buffer.readUtf8(index));
				buffer.readByte(); // '\'
				builder.append(readEscapeCharacter());
				continue;
			}

			// If it isn't the escape character, it's the quote. Return the string.

			if (builder == null) {
				final String result = buffer.readUtf8(index);
				buffer.readByte(); // Consume the quote character.
				return result;
			} else {
				builder.append(buffer.readUtf8(index));
				buffer.readByte(); // Consume the quote character.
				return builder.toString();
			}
		}
	}

	/**
	 * Returns an unquoted value as a string.
	 */
	private String nextUnquotedValue() throws IOException {
		final long i = source.indexOfElement(UNQUOTED_STRING_TERMINALS);
		return (i != -1L) ? buffer.readUtf8(i) : buffer.readUtf8();
	}

	private void skipQuotedValue(final ByteString runTerminator) throws IOException {
		while (true) {
			final long index = source.indexOfElement(runTerminator);
			if (index == -1L) throw new JsonEncodingException("Unterminated string");
			final char terminator = (char) buffer.getByte(index);
			buffer.skip(index + 1);
			if (terminator == '\\') {
				readEscapeCharacter();
			} else {
				return;
			}
		}
	}

	private void skipUnquotedValue() throws IOException {
		final long i = source.indexOfElement(UNQUOTED_STRING_TERMINALS);
		buffer.skip((i != -1L) ? i : buffer.size());
	}

	@Override
	public void close() throws IOException {
		peeked = PEEKED_NONE;
		scopes[0] = JsonScope.CLOSED;
		stackSize = 1;
		buffer.clear();
		source.close();
	}

	@Override
	public void skipValue() throws IOException {
		int count = 0;
		do {
			switch (peekIfNone()) {
				case PEEKED_BEGIN_ARRAY:
					pushScope(JsonScope.EMPTY_ARRAY);
					count++;
					break;
				case PEEKED_BEGIN_OBJECT:
					pushScope(JsonScope.EMPTY_OBJECT);
					count++;
					break;
				case PEEKED_END_ARRAY:
				case PEEKED_END_OBJECT:
					count--;
					if (count < 0) {
						throw new JsonDataException("Expected a value but was " + peekName() + " at path " + path());
					}
					stackSize--;
					break;
				case PEEKED_UNQUOTED_NAME:
				case PEEKED_UNQUOTED:
					skipUnquotedValue();
					break;
				case PEEKED_DOUBLE_QUOTED:
				case PEEKED_DOUBLE_QUOTED_NAME:
					skipQuotedValue(DOUBLE_QUOTE_OR_SLASH);
					break;
				case PEEKED_SINGLE_QUOTED:
				case PEEKED_SINGLE_QUOTED_NAME:
					skipQuotedValue(SINGLE_QUOTE_OR_SLASH);
					break;
				case PEEKED_NUMBER:
					buffer.skip(peekedNumberLength);
					break;
				case PEEKED_EOF:
					throw new JsonDataException("Expected a value but was " + peekName() + " at path " + path());
			}
			peeked = PEEKED_NONE;
		} while (count != 0);
		pathIndices[stackSize - 1]++;
		pathNames[stackSize - 1] = "null";
	}

	/**
	 * Returns the next character in the stream that is neither whitespace nor a part of a comment.
	 * When this returns, the returned character is always at `buffer.getByte(0)`.
	 */
	private int nextNonWhitespace(boolean throwOnEof) throws IOException {
		/*
		 * This code uses ugly local variable 'p' to represent the 'pos' field.
		 * Using locals rather than fields saves a few field reads for each
		 * whitespace character in a pretty-printed document, resulting in a
		 * 5% speedup. We need to flush 'p' to its field before any
		 * (potentially indirect) call to fillBuffer() and reread 'p' after
		 * any (potentially indirect) call to the same method.
		 */
		long p = 0L;
		while (source.request(p + 1)) {
			final char c = (char) buffer.getByte(p++);
			if (c == '\n' || c == ' ' || c == '\r' || c == '\t') {
				continue;
			}
			buffer.skip(p - 1);
			switch (c) {
				case '/':
					if (!source.request(2)) {
						return c;
					}
					final byte peek = buffer.getByte(1);
					switch ((char) peek) {
						case '*':
							// skip a /* c-style comment */
							buffer.readByte(); // '/'
							buffer.readByte(); // '*'
							if (!skipToEndOfBlockComment()) {
								throw new JsonDataException("Unterminated comment");
							}
							p = 0;
							continue;
						case '/':
							// skip a // end-of-line comment
							buffer.readByte(); // '/'
							buffer.readByte(); // '/'
							skipToEndOfLine();
							p = 0;
							continue;
						default:
							return c;
					}
				case '#':
					// Skip a # hash end-of-line comment. The JSON RFC doesn't specify this behaviour, but it's
					// required to parse existing documents.
					skipToEndOfLine();
					p = 0;
					break;
				default:
					return c;
			}
		}
		if (throwOnEof) {
			throw new EOFException("End of input");
		}
		return -1;
	}

	/**
	 * Advances the position until after the next newline character. If the line is terminated by
	 * "\r\n", the '\n' must be consumed as whitespace by the caller.
	 */
	private void skipToEndOfLine() throws IOException {
		final long index = source.indexOfElement(LINEFEED_OR_CARRIAGE_RETURN);
		buffer.skip((index != -1L) ? index + 1 : buffer.size());
	}

	/**
	 * Skips through the next closing block comment.
	 */
	private boolean skipToEndOfBlockComment() throws IOException {
		final long index = source.indexOf(CLOSING_BLOCK_COMMENT);
		boolean found = index != -1L;
		buffer.skip(found ? index + CLOSING_BLOCK_COMMENT.size() : buffer.size());
		return found;
	}

	/**
	 * Unescapes the character identified by the character or characters that immediately follow a
	 * backslash. The backslash '\' should have already been read. This supports both unicode escapes
	 * "u000A" and two-character escapes "\n".
	 *
	 * @throws IOException if any unicode escape sequences are malformed.
	 */
	private char readEscapeCharacter() throws IOException {
		if (!source.request(1)) {
			throw new JsonDataException("Unterminated escape sequence");
		}
		final char escaped = (char) buffer.readByte();
		switch (escaped) {
			case 'u': {
				if (!source.request(4)) {
					throw new EOFException("Unterminated escape sequence at path " + path());
				}
				// Equivalent to Integer.parseInt(stringPool.get(buffer, pos, 4), 16);
				int result = 0;
				for (int i = 0; i < 4; i++) {
					result <<= 4;
					final char c = (char) buffer.getByte(i);
					if (c >= '0' && c <= '9') {
						result += (c - '0');
					} else if (c >= 'a' && c <= 'f') {
						result += (c - 'a' + 10);
					} else if (c >= 'A' && c <= 'F') {
						result += (c - 'A' + 10);
					} else {
						throw new JsonDataException("\\u" + buffer.readUtf8(4));
					}
				}
				buffer.skip(4);
				return (char) result;
			}
			case 't':
				return '\t';
			case 'b':
				return '\b';
			case 'n':
				return '\n';
			case 'r':
				return '\r';
			case 'f':
				return '\f';
			case '\n':
			case '\'':
			case '"':
			case '\\':
			case '/':
				return escaped;

		}
		return escaped;
	}

	/**
	 * Returns a [JsonPath](http://goessner.net/articles/JsonPath/) to the current location
	 * in the JSON value.
	 */
	private String path() {
		return JsonScope.getPath(stackSize, scopes, pathNames, pathIndices);
	}

	private int peekIfNone() throws IOException {
		final int p = peeked;
		return (p == PEEKED_NONE) ? doPeek() : p;
	}

	private int setPeeked(int peekedType) {
		peeked = peekedType;
		return peekedType;
	}
}