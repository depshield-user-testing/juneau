// ***************************************************************************************************************************
// * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file *
// * distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file        *
// * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance            *
// * with the License.  You may obtain a copy of the License at                                                              *
// *                                                                                                                         *
// *  http://www.apache.org/licenses/LICENSE-2.0                                                                             *
// *                                                                                                                         *
// * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an  *
// * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the        *
// * specific language governing permissions and limitations under the License.                                              *
// ***************************************************************************************************************************
package org.apache.juneau.http;

/**
 * Represents a parsed <l>If-Modified-Since</l> HTTP request header.
 * <p>
 * Allows a 304 Not Modified to be returned if content is unchanged.
 *
 * <h6 class='figure'>Example</h6>
 * <p class='bcode'>
 * 	If-Modified-Since: Sat, 29 Oct 1994 19:43:31 GMT
 * </p>
 *
 * <h6 class='topic'>RFC2616 Specification</h6>
 *
 * The If-Modified-Since request-header field is used with a method to make it conditional:
 * if the requested variant has not been modified since the time specified in this field, an entity will not be returned
 * from the server; instead, a 304 (not modified) response will be returned without any message-body.
 * <p class='bcode'>
 * 	If-Modified-Since = "If-Modified-Since" ":" HTTP-date
 * </p>
 * <p>
 * An example of the field is:
 * <p class='bcode'>
 * 	If-Modified-Since: Sat, 29 Oct 1994 19:43:31 GMT
 * </p>
 * <p>
 * A GET method with an If-Modified-Since header and no Range header requests that the identified entity be transferred
 * only if it has been modified since the date given by the If-Modified-Since header.
 * The algorithm for determining this includes the following cases:
 * <ol>
 * 	<li>If the request would normally result in anything other than a 200 (OK) status, or if the passed
 * 		If-Modified-Since date is invalid, the response is exactly the same as for a normal GET.
 * 		A date which is later than the server's current time is invalid.
 * 	<li>If the variant has been modified since the If-Modified-Since date, the response is exactly the same as for a
 * 		normal GET.
 * 	<li>If the variant has not been modified since a valid If-Modified-Since date, the server SHOULD return a 304
 * 		(Not Modified) response.
 * </ol>
 * The purpose of this feature is to allow efficient updates of cached information with a minimum amount of transaction
 * overhead.
 * <p>
 * Note: The Range request-header field modifies the meaning of If-Modified-Since; see section 14.35 for full details.
 * <p>
 * Note: If-Modified-Since times are interpreted by the server, whose clock might not be synchronized with the client.
 * <p>
 * Note: When handling an If-Modified-Since header field, some servers will use an exact date comparison function,
 * rather than a less-than function, for deciding whether to send a 304 (Not Modified) response.
 * To get best results when sending an If-Modified-Since header field for cache validation, clients are
 * advised to use the exact date string received in a previous Last-Modified header field whenever possible.
 * <p>
 * Note: If a client uses an arbitrary date in the If-Modified-Since header instead of a date taken from the
 * Last-Modified header for the same request, the client should be aware of the fact that this date is interpreted in
 * the server's understanding of time.
 * The client should consider unsynchronized clocks and rounding problems due to the different encodings of time between
 * the client and server.
 * This includes the possibility of race conditions if the document has changed between the time it was first requested
 * and the If-Modified-Since date of a subsequent request, and the possibility of clock-skew-related problems if the
 * If-Modified-Since date is derived from the client's clock without correction to the server's clock.
 * Corrections for different time bases between client and server are at best approximate due to network latency.
 * The result of a request having both an If-Modified-Since header field and either an If-Match or an
 * If-Unmodified-Since header fields is undefined by this specification.
 */
public final class IfModifiedSince extends HeaderDate {

	/**
	 * Returns a parsed <code>If-Modified-Since</code> header.
	 *
	 * @param value The <code>If-Modified-Since</code> header string.
	 * @return The parsed <code>If-Modified-Since</code> header, or <jk>null</jk> if the string was null.
	 */
	public static IfModifiedSince forString(String value) {
		if (value == null)
			return null;
		return new IfModifiedSince(value);
	}

	private IfModifiedSince(String value) {
		super(value);
	}
}
