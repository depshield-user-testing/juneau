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
package org.apache.juneau.httppart;

import org.apache.juneau.*;
import org.apache.juneau.parser.*;

/**
 * OpenAPI part parser.
 *
 * <h5 class='section'>See Also:</h5>
 * <ul>
 * 	<li class='link'><a class="doclink" href="../../../../overview-summary.html#juneau-marshall.OpenApiDetails.Parsers">Overview &gt; juneau-marshall &gt; OpenAPI Details &gt; OpenAPI Parsers</a>
 * </ul>
 */
public class OpenApiPartParser extends UonPartParser {

	//-------------------------------------------------------------------------------------------------------------------
	// Configurable properties
	//-------------------------------------------------------------------------------------------------------------------

	private static final String PREFIX = "OpenApiPartParser.";

	/**
	 * Configuration property:  OpenAPI schema description.
	 *
	 * <h5 class='section'>Property:</h5>
	 * <ul>
	 * 	<li><b>Name:</b>  <js>"OpenApiPartParser.schema"</js>
	 * 	<li><b>Data type:</b>  <code>HttpPartSchema</code>
	 * 	<li><b>Default:</b>  <jk>false</jk>
	 * 	<li><b>Session property:</b>  <jk>false</jk>
	 * 	<li><b>Methods:</b>
	 * 		<ul>
	 * 			<li class='jm'>{@link OpenApiPartParserBuilder#schema(HttpPartSchema)}
	 * 		</ul>
	 * </ul>
	 *
	 * <h5 class='section'>Description:</h5>
	 * <p>
	 * Defines the OpenAPI schema for this part parser.
	 */
	public static final String OAPI_schema = PREFIX + "schema.o";

	//-------------------------------------------------------------------------------------------------------------------
	// Predefined instances
	//-------------------------------------------------------------------------------------------------------------------

	/** Reusable instance of {@link OpenApiPartParser}. */
	public static final OpenApiPartParser DEFAULT = new OpenApiPartParser(PropertyStore.DEFAULT);

	// Cache these for faster lookup
	private static final HttpPartSchema DEFAULT_SCHEMA = HttpPartSchema.DEFAULT;

	//-------------------------------------------------------------------------------------------------------------------
	// Instance
	//-------------------------------------------------------------------------------------------------------------------

	private final HttpPartSchema schema;

	/**
	 * Constructor.
	 *
	 * @param ps The property store containing all the settings for this object.
	 */
	public OpenApiPartParser(PropertyStore ps) {
		super(
			ps.builder().build()
		);
		this.schema = getProperty(OAPI_schema, HttpPartSchema.class, DEFAULT_SCHEMA);
	}

	@Override /* Context */
	public UonPartParserBuilder builder() {
		return new UonPartParserBuilder(getPropertyStore());
	}

	/**
	 * Instantiates a new clean-slate {@link UonPartParserBuilder} object.
	 *
	 * <p>
	 * This is equivalent to simply calling <code><jk>new</jk> UonPartParserBuilder()</code>.
	 *
	 * <p>
	 * Note that this method creates a builder initialized to all default settings, whereas {@link #builder()} copies
	 * the settings of the object called on.
	 *
	 * @return A new {@link UonPartParserBuilder} object.
	 */
	public static UonPartParserBuilder create() {
		return new UonPartParserBuilder();
	}

	//-------------------------------------------------------------------------------------------------------------------
	// Entry point methods
	//-------------------------------------------------------------------------------------------------------------------

	@Override
	public OpenApiPartParserSession createSession() {
		return new OpenApiPartParserSession(this, ParserSessionArgs.DEFAULT);
	}

	@Override
	public OpenApiPartParserSession createSession(ParserSessionArgs args) {
		return new OpenApiPartParserSession(this, args);
	}

	//-----------------------------------------------------------------------------------------------------------------
	// Properties
	//-----------------------------------------------------------------------------------------------------------------

	/**
	 * Configuration property:  OpenAPI schema description.
	 *
	 * @see #OAPI_schema
	 * @return
	 * 	The default part schema on this serializer, or <jk>null</jk> if none is defined.
	 */
	protected final HttpPartSchema getSchema() {
		return schema;
	}
}
