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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import okio.Buffer;
import pl.selvin.android.syncframework.content.BaseContentProvider;
import pl.selvin.android.syncframework.content.RequestExecutor;

/**
 * Stands in for the network: hands back canned responses and records what the provider
 * tried to send, so the sync loop can be driven without a server.
 * <p>
 * The uploaded body is produced by calling the provider's own SyncContentProducer, exactly
 * as a real executor would when writing the request body - which is also what applies the
 * "sent" bookkeeping (isDirty cleared, tombstones dropped) to the database.
 */
public class FakeRequestExecutor implements RequestExecutor {

	private final ArrayDeque<CannedResponse> responses = new ArrayDeque<>();
	private final ArrayList<RecordedRequest> requests = new ArrayList<>();

	/** A 200 with this body. */
	public void enqueue(String body) {
		responses.add(new CannedResponse(200, body, null));
	}

	/** A non-200, whose error text drives httpBadStatusHandling. */
	public void enqueueError(int status, String error) {
		responses.add(new CannedResponse(status, "", error));
	}

	public void reset() {
		responses.clear();
		requests.clear();
	}

	public List<RecordedRequest> requests() {
		return requests;
	}

	public RecordedRequest lastRequest() {
		if (requests.isEmpty())
			throw new AssertionError("no request was made");
		return requests.get(requests.size() - 1);
	}

	public int requestCount() {
		return requests.size();
	}

	public boolean hasQueuedResponses() {
		return !responses.isEmpty();
	}

	@NonNull
	@Override
	public Result execute(@NonNull Context context,
	                      @Nullable BaseContentProvider.SyncContentProducer syncContentProducer,
	                      @NonNull Bundle parameters) throws IOException {
		String uploaded = null;
		if (syncContentProducer != null) {
			final Buffer body = new Buffer();
			syncContentProducer.writeTo(body);
			uploaded = body.readUtf8();
		}
		requests.add(new RecordedRequest(
				parameters.getString(SCOPE_PARAMETER),
				parameters.getString(REQUEST_TYPE_PARAMETER),
				parameters.getInt(REQUEST_METHOD_PARAMETER),
				uploaded));

		final CannedResponse response = responses.poll();
		if (response == null)
			throw new IOException("FakeRequestExecutor ran out of responses after "
					+ requests.size() + " request(s)");
		return new Result(new Buffer().writeUtf8(response.body), response.status, response.error);
	}

	public static class RecordedRequest {
		public final String scope;
		@RequestType
		public final String type;
		@RequestMethod
		public final int method;
		/** Request body, or null for a GET, which sends nothing. */
		public final String body;

		RecordedRequest(String scope, String type, int method, String body) {
			this.scope = scope;
			this.type = type;
			this.method = method;
			this.body = body;
		}
	}

	private static class CannedResponse {
		final int status;
		final String body;
		final String error;

		CannedResponse(int status, String body, String error) {
			this.status = status;
			this.body = body;
			this.error = error;
		}
	}
}
