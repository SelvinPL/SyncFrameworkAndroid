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

/**
 * Builds the server payloads the sync loop expects, so tests read as protocol rather than
 * as string concatenation. Shape is documented at the top of SYNC.
 */
public final class SyncJson {

	private SyncJson() {
	}

	/** {"d":{"__sync":{...},"results":[...]}} */
	public static String response(String serverBlob, boolean moreChanges, String... results) {
		return "{\"d\":{\"__sync\":{\"serverBlob\":" + quote(serverBlob)
				+ ",\"moreChangesAvailable\":" + moreChanges + "},\"results\":["
				+ String.join(",", results) + "]}}";
	}

	/**
	 * The same envelope carrying the optional __sync.resolveConflicts flag. SYNC documents the
	 * field and the parser reads it, but the reference service never emits it - nothing in
	 * SyncWinRT writes that name.
	 */
	public static String responseResolvingConflicts(String serverBlob, boolean moreChanges,
	                                                String... results) {
		return "{\"d\":{\"__sync\":{\"serverBlob\":" + quote(serverBlob)
				+ ",\"moreChangesAvailable\":" + moreChanges + ",\"resolveConflicts\":true},"
				+ "\"results\":[" + String.join(",", results) + "]}}";
	}

	/** A row the server is sending down, with no tempId - it did not come from us. */
	public static String row(String type, String uri, String fields) {
		return row(type, uri, null, fields);
	}

	/**
	 * A row echoed back after an upload: tempId ties it to the row we inserted locally, uri
	 * is the identity the server assigned it.
	 */
	public static String row(String type, String uri, String tempId, String fields) {
		final StringBuilder sb = new StringBuilder("{\"__metadata\":{\"uri\":").append(quote(uri))
				.append(",\"type\":").append(quote(type));
		if (tempId != null)
			sb.append(",\"tempId\":").append(quote(tempId));
		sb.append("}");
		if (fields != null && !fields.isEmpty())
			sb.append(",").append(fields);
		return sb.append("}").toString();
	}

	/**
	 * Splices a __syncConflict member on to a row: the resolution the server applied, and the
	 * whole entity that lost it. The reference ODataJsonWriter adds it as the entry's last
	 * member, which is where this puts it too.
	 */
	public static String withConflict(String row, String resolution, String losingRow) {
		return withConflict(row, null, resolution, losingRow);
	}

	/**
	 * As above, with isResolved. SYNC documents that field, but the reference writer only ever
	 * emits conflictResolution and conflictingChange - pass null to leave it out.
	 */
	public static String withConflict(String row, Boolean isResolved, String resolution,
	                                  String losingRow) {
		return row.substring(0, row.length() - 1) + ",\"__syncConflict\":{"
				+ (isResolved == null ? "" : "\"isResolved\":" + isResolved + ",")
				+ "\"conflictResolution\":" + quote(resolution)
				+ ",\"conflictingChange\":" + losingRow + "}}";
	}

	/** A tombstone coming down from the server. */
	public static String deletedRow(String type, String uri) {
		return "{\"__metadata\":{\"uri\":" + quote(uri) + ",\"type\":" + quote(type)
				+ ",\"isDeleted\":true}}";
	}

	/** Fields of an Item row, as the server would send them. */
	public static String itemFields(String id, String name) {
		return "\"" + SyncDatabase.Item.ID + "\":" + quote(id) + ",\""
				+ SyncDatabase.Item.NAME + "\":" + quote(name);
	}

	/** The MS Sync Service date format: /Date(millis)/ */
	public static String msDate(long millis) {
		return "\"/Date(" + millis + ")/\"";
	}

	private static String quote(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}
}
