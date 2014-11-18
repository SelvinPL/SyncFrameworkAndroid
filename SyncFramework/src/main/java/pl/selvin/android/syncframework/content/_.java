/***
 Copyright (c) 2014 Selvin
 Licensed under the Apache License, Version 2.0 (the "License"); you may not
 use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 by applicable law or agreed to in writing, software distributed under the
 License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.
 */

package pl.selvin.android.syncframework.content;

/*- ok i think its all:
 * {"d":
 * 		"__sync"	:{	//req
 *			"serverBlob"			: "blob",
 *			"moreChangesAvailable"	: boolean,
 * 			"resolveConflicts"		: boolean //opt 
 * 		},
 * 		"results"	:[
 * 			"__metadata"	:{ //req
 * 				"uri"		: "http://example.com/service.svc/scope/type(id)",
 *				"type"		: "scope.type",
 *				"isDeleted" : boolean, // optional can be only true not exits if false
 *				"tempId"	: "tempid"
 * 			},
 * 			"__syncConflict"	:{ //opt
 * 				"isResolved"			: boolean,
 * 				"conflictResolution"	: "ClientWins|ServerWins|Merge", 
 * 						//names taken from doc ... i'm not sure about 'em
 * 				"conflictingChange"		: { 
 * 					"restOfFields":"goes here" //... i think doc sucks
 * 				}
 * 			},
 * 			"__syncError"		:{ //opt
 * 				"errorDescription"	:	"self explained field",
 * 				"changeInError"		:	{//... have no idea what we can get here }
 * 			},
 * 			"restOfFields"		:"goes here"
 * 		]
 */

public interface _ {
    public final static String d = "d";
    public final static String P = "=?";
    public final static String __sync = "__sync";
    public final static String serverBlob = "serverBlob";
    public final static String moreChangesAvailable = "moreChangesAvailable";
    public final static String resolveConflicts = "resolveConflicts";
    public final static String results = "results";
    public final static String __metadata = "__metadata";
    public final static String uri = "uri";
    public final static String uriP = uri + P;
    public final static String isDeleted = "isDeleted";
    public final static String isDeletedP = isDeleted + P;
    public final static String isDirty = "isDirty";
    public final static String isDirtyP = isDirty + P;
    public final static String tempId = "tempId";
    public final static String tempIdP = tempId + P;
    public final static String type = "type";
    public final static String __syncConflict = "__syncConflict";
    public final static String isResolved = "isResolved";
    public final static String conflictResolution = "conflictResolution";
    public final static String conflictingChange = "conflictingChange";
    public final static String __syncError = "__syncError";
    public final static String errorDescription = "errorDescription";
    public final static String changeInError = "changeInError";
}
