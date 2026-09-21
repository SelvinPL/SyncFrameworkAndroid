/*
 Copyright (c) 2026 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */
package pl.selvin.android.syncframework.test;

import java.util.concurrent.atomic.AtomicInteger;

import okio.BufferedSink;
import okio.BufferedSource;
import pl.selvin.android.syncframework.json.JsonFactory;
import pl.selvin.android.syncframework.json.JsonReader;
import pl.selvin.android.syncframework.json.JsonWriter;

/**
 * Delegates to a real JsonFactory while counting what the provider asked it for.
 * <p>
 * Exists to cover the codec seam: BaseContentProvider takes a JsonFactory, so a caller who
 * needs different JSON behaviour - explicit nulls, a different parser - supplies their own
 * rather than patching the bundled one. These counters prove the supplied factory really is
 * what reads responses and writes requests.
 */
public class RecordingJsonFactory implements JsonFactory {

	private final JsonFactory delegate;
	private final AtomicInteger readers = new AtomicInteger();
	private final AtomicInteger writers = new AtomicInteger();

	public RecordingJsonFactory(JsonFactory delegate) {
		this.delegate = delegate;
	}

	public void reset() {
		readers.set(0);
		writers.set(0);
	}

	public int readersCreated() {
		return readers.get();
	}

	public int writersCreated() {
		return writers.get();
	}

	@Override
	public JsonReader createReader(BufferedSource source) {
		readers.incrementAndGet();
		return delegate.createReader(source);
	}

	@Override
	public JsonWriter createWriter(BufferedSink sink) {
		writers.incrementAndGet();
		return delegate.createWriter(sink);
	}
}
