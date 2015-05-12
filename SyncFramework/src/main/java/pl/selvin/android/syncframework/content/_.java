/**
 * Copyright (c) 2014 Selvin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
 * by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
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

@SuppressWarnings("unused")
public interface _ {
    String d = "d";
    String P = "=?";
    String __sync = "__sync";
    String serverBlob = "serverBlob";
    String moreChangesAvailable = "moreChangesAvailable";
    String resolveConflicts = "resolveConflicts";
    String results = "results";
    String __metadata = "__metadata";
    String uri = "uri";
    String uriP = uri + P;
    String isDeleted = "isDeleted";
    String isDeletedP = isDeleted + P;
    String isDirty = "isDirty";
    String isDirtyP = isDirty + P;
    String tempId = "tempId";
    String tempIdP = tempId + P;
    String type = "type";
    String __syncConflict = "__syncConflict";
    String isResolved = "isResolved";
    String conflictResolution = "conflictResolution";
    String conflictingChange = "conflictingChange";
    String __syncError = "__syncError";
    String errorDescription = "errorDescription";
    String changeInError = "changeInError";
}
