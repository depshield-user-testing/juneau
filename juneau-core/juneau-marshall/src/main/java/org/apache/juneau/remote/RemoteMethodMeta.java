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
package org.apache.juneau.remote;

import static org.apache.juneau.internal.StringUtils.*;
import static org.apache.juneau.httppart.HttpPartType.*;

import java.lang.reflect.*;
import java.util.*;

import org.apache.juneau.*;
import org.apache.juneau.http.annotation.*;
import org.apache.juneau.httppart.*;
import org.apache.juneau.httppart.bean.*;
import org.apache.juneau.internal.*;

/**
 * Contains the meta-data about a Java method on a remote class.
 *
 * <p>
 * Captures the information in {@link RemoteMethod @RemoteMethod} annotations for caching and reuse.
 *
 * <h5 class='section'>See Also:</h5>
 * <ul class='doctree'>
 * 	<li class='link'>{@doc juneau-rest-client.RemoteResources}
 * </ul>
 */
public class RemoteMethodMeta {

	private final String httpMethod;
	private final String url, path;
	private final RemoteMethodArg[] pathArgs, queryArgs, headerArgs, formDataArgs, otherArgs;
	private final RemoteMethodBeanArg[] requestArgs;
	private final RemoteMethodArg bodyArg;
	private final RemoteMethodReturn methodReturn;
	private final Method method;

	/**
	 * Constructor.
	 *
	 * @param restUrl The absolute URL of the REST interface backing the interface proxy.
	 * @param m The Java method.
	 * @param useMethodSignatures If <jk>true</jk> then the default path for the method should be the full method signature.
	 * @param defaultMethod The default HTTP method if not specified through annotation.
	 */
	public RemoteMethodMeta(final String restUrl, Method m, boolean useMethodSignatures, String defaultMethod) {
		Builder b = new Builder(restUrl, m, useMethodSignatures, defaultMethod);
		this.method = m;
		this.httpMethod = b.httpMethod;
		this.path = b.path;
		this.url = b.url;
		this.pathArgs = b.pathArgs.toArray(new RemoteMethodArg[b.pathArgs.size()]);
		this.queryArgs = b.queryArgs.toArray(new RemoteMethodArg[b.queryArgs.size()]);
		this.formDataArgs = b.formDataArgs.toArray(new RemoteMethodArg[b.formDataArgs.size()]);
		this.headerArgs = b.headerArgs.toArray(new RemoteMethodArg[b.headerArgs.size()]);
		this.requestArgs = b.requestArgs.toArray(new RemoteMethodBeanArg[b.requestArgs.size()]);
		this.otherArgs = b.otherArgs.toArray(new RemoteMethodArg[b.otherArgs.size()]);
		this.bodyArg = b.bodyArg;
		this.methodReturn = b.methodReturn;
	}

	private static final class Builder {
		String httpMethod, url, path;
		List<RemoteMethodArg>
			pathArgs = new LinkedList<>(),
			queryArgs = new LinkedList<>(),
			headerArgs = new LinkedList<>(),
			formDataArgs = new LinkedList<>(),
			otherArgs = new LinkedList<>();
		List<RemoteMethodBeanArg>
			requestArgs = new LinkedList<>();
		RemoteMethodArg bodyArg;
		RemoteMethodReturn methodReturn;

		Builder(String restUrl, Method m, boolean useMethodSignatures, String defaultMethod) {

			RemoteMethod rm = m.getAnnotation(RemoteMethod.class);

			httpMethod = rm == null ? "" : rm.httpMethod();
			path = rm == null ? "" : rm.path();

			if (path.isEmpty()) {
				path = HttpUtils.detectHttpPath(m, ! useMethodSignatures);
				if (useMethodSignatures)
					path += HttpUtils.getMethodArgsSignature(m, true);
			}
			if (httpMethod.isEmpty())
				httpMethod = HttpUtils.detectHttpMethod(m, ! useMethodSignatures, defaultMethod);

			if (path.startsWith("/"))
				path = path.substring(1);

			if (! isOneOf(httpMethod, "DELETE", "GET", "POST", "PUT", "OPTIONS", "HEAD", "CONNECT", "TRACE", "PATCH"))
				throw new RemoteMetadataException(m,
					"Invalid value specified for @RemoteMethod.httpMethod() annotation.  Valid values are [DELTE,GET,POST,PUT].");

			RemoteReturn rv = m.getReturnType() == void.class ? RemoteReturn.NONE : rm == null ? RemoteReturn.BODY : rm.returns();

			methodReturn = new RemoteMethodReturn(m, rv);

			url = trimSlashes(restUrl) + '/' + urlEncode(path);

			for (int i = 0; i < m.getParameterTypes().length; i++) {
				RemoteMethodArg rma = RemoteMethodArg.create(m, i);
				boolean annotated = false;
				if (rma != null) {
					annotated = true;
					HttpPartType pt = rma.getPartType();
					if (pt == HEADER)
						headerArgs.add(rma);
					else if (pt == QUERY)
						queryArgs.add(rma);
					else if (pt == FORMDATA)
						formDataArgs.add(rma);
					else if (pt == PATH)
						pathArgs.add(rma);
					else if (pt == BODY)
						bodyArg = rma;
					else
						annotated = false;
				}
				RequestBeanMeta rmba = RequestBeanMeta.create(m, i, PropertyStore.DEFAULT);
				if (rmba != null) {
					annotated = true;
					requestArgs.add(new RemoteMethodBeanArg(i, null, rmba));
				}
				if (! annotated) {
					otherArgs.add(new RemoteMethodArg(i, BODY, null));
				}
			}
		}
	}

	/**
	 * Returns the value of the {@link RemoteMethod#httpMethod() @RemoteMethod.httpMethod()} annotation on this Java method.
	 *
	 * @return The value of the annotation, never <jk>null</jk>.
	 */
	public String getHttpMethod() {
		return httpMethod;
	}

	/**
	 * Returns the absolute URL of the REST interface invoked by this Java method.
	 *
	 * @return The absolute URL of the REST interface, never <jk>null</jk>.
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * Returns the {@link Path @Path} annotated arguments on this Java method.
	 *
	 * @return A map of {@link Path#value() @Path.value()} names to zero-indexed argument indices.
	 */
	public RemoteMethodArg[] getPathArgs() {
		return pathArgs;
	}

	/**
	 * Returns the {@link Query @Query} annotated arguments on this Java method.
	 *
	 * @return A map of {@link Query#value() @Query.value()} names to zero-indexed argument indices.
	 */
	public RemoteMethodArg[] getQueryArgs() {
		return queryArgs;
	}

	/**
	 * Returns the {@link FormData @FormData} annotated arguments on this Java method.
	 *
	 * @return A map of {@link FormData#value() @FormData.value()} names to zero-indexed argument indices.
	 */
	public RemoteMethodArg[] getFormDataArgs() {
		return formDataArgs;
	}

	/**
	 * Returns the {@link Header @Header} annotated arguments on this Java method.
	 *
	 * @return A map of {@link Header#value() @Header.value()} names to zero-indexed argument indices.
	 */
	public RemoteMethodArg[] getHeaderArgs() {
		return headerArgs;
	}

	/**
	 * Returns the {@link Request @Request} annotated arguments on this Java method.
	 *
	 * @return A list of zero-indexed argument indices.
	 */
	public RemoteMethodBeanArg[] getRequestArgs() {
		return requestArgs;
	}

	/**
	 * Returns the remaining non-annotated arguments on this Java method.
	 *
	 * @return A list of zero-indexed argument indices.
	 */
	public RemoteMethodArg[] getOtherArgs() {
		return otherArgs;
	}

	/**
	 * Returns the argument annotated with {@link Body @Body}.
	 *
	 * @return A index of the argument with the {@link Body @Body} annotation, or <jk>null</jk> if no argument exists.
	 */
	public RemoteMethodArg getBodyArg() {
		return bodyArg;
	}

	/**
	 * Returns whether the method returns the HTTP response body or status code.
	 *
	 * @return Whether the method returns the HTTP response body or status code.
	 */
	public RemoteMethodReturn getReturns() {
		return methodReturn;
	}

	/**
	 * Returns the HTTP path of this method.
	 *
	 * @return
	 * 	The HTTP path of this method relative to the parent interface.
	 * 	<br>Never <jk>null</jk>.
	 * 	<br>Never has leading or trailing slashes.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Returns the underlying Java method that this metadata is about.
	 *
	 * @return
	 * 	The underlying Java method that this metadata is about.
	 * 	<br>Never <jk>null</jk>.
	 */
	public Method getJavaMethod() {
		return method;
	}
}