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
package org.apache.juneau.rest.client;

import static org.apache.juneau.internal.StringUtils.*;
import static org.apache.juneau.json.JsonSerializer.*;
import static org.apache.juneau.parser.Parser.*;
import static org.apache.juneau.uon.UonSerializer.*;
import static org.apache.juneau.rest.client.RestClient.*;

import java.lang.reflect.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import javax.net.ssl.*;

import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.*;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.*;
import org.apache.http.client.entity.*;
import org.apache.http.config.*;
import org.apache.http.conn.*;
import org.apache.http.conn.routing.*;
import org.apache.http.conn.socket.*;
import org.apache.http.conn.ssl.*;
import org.apache.http.conn.util.*;
import org.apache.http.cookie.*;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.*;
import org.apache.http.protocol.*;
import org.apache.juneau.*;
import org.apache.juneau.http.*;
import org.apache.juneau.httppart.*;
import org.apache.juneau.parser.*;
import org.apache.juneau.serializer.*;
import org.apache.juneau.uon.*;

/**
 * Builder class for the {@link RestClient} class.
 * 
 * <p>
 * Instances of this class are created by the following methods:
 * <ul>
 * 	<li>{@link RestClient#create()} - Create from scratch.
 * 	<li>{@link RestClient#create(Serializer,Parser)} - Create from scratch using specified serializer/parser.
 * 	<li>{@link RestClient#create(Class,Class)} - Create from scratch using specified serializer/parser classes.
 * 	<li>{@link RestClient#builder()} - Copy settings from an existing client.
 * </ul>
 */
public class RestClientBuilder extends BeanContextBuilder {

	private HttpClientConnectionManager httpClientConnectionManager;
	private HttpClientBuilder httpClientBuilder;
	private CloseableHttpClient httpClient;
	private SSLOpts sslOpts;
	private boolean pooled;

	/**
	 * Constructor.
	 */
	RestClientBuilder(PropertyStore ps, HttpClientBuilder httpClientBuilder) {
		super(ps);
		this.httpClientBuilder = httpClientBuilder != null ? httpClientBuilder : createHttpClientBuilder();
	}

	@SuppressWarnings("resource")
	@Override /* ContextBuilder */
	public RestClient build() {
		try {
			CloseableHttpClient c = httpClient != null ? httpClient : createHttpClient();
			PropertyStore ps = psb.build();

			return new RestClient(ps, httpClientBuilder, c);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates an instance of an {@link HttpClient} to be used to handle all HTTP communications with the target server.
	 *
	 * <p>
	 * This HTTP client is used when the HTTP client is not specified through one of the constructors or the
	 * {@link #httpClient(CloseableHttpClient, boolean)} method.
	 *
	 * <p>
	 * Subclasses can override this method to provide specially-configured HTTP clients to handle stuff such as
	 * SSL/TLS certificate handling, authentication, etc.
	 *
	 * <p>
	 * The default implementation returns an instance of {@link HttpClient} using the client builder returned by
	 * {@link #createHttpClientBuilder()}.
	 *
	 * @return The HTTP client to use.
	 * @throws Exception
	 */
	protected CloseableHttpClient createHttpClient() throws Exception {
		// Don't call createConnectionManager() if RestClient.setConnectionManager() was called.
		if (httpClientConnectionManager == null)
			httpClientBuilder.setConnectionManager(createConnectionManager());
		return httpClientBuilder.build();
	}

	/**
	 * Creates an instance of an {@link HttpClientBuilder} to be used to create the {@link HttpClient}.
	 *
	 * <p>
	 * Subclasses can override this method to provide their own client builder.
	 *
	 * <p>
	 * The predefined method returns an {@link HttpClientBuilder} with the following settings:
	 * <ul>
	 * 	<li>Lax redirect strategy.
	 * 	<li>The connection manager returned by {@link #createConnectionManager()}.
	 * </ul>
	 *
	 * @return The HTTP client builder to use to create the HTTP client.
	 */
	protected HttpClientBuilder createHttpClientBuilder() {
		HttpClientBuilder b = HttpClientBuilder.create();
		b.setRedirectStrategy(new AllowAllRedirects());
		return b;
	}

	/**
	 * Creates the {@link HttpClientConnectionManager} returned by {@link #createConnectionManager()}.
	 *
	 * <p>
	 * Subclasses can override this method to provide their own connection manager.
	 *
	 * <p>
	 * The default implementation returns an instance of a {@link PoolingHttpClientConnectionManager}.
	 *
	 * @return The HTTP client builder to use to create the HTTP client.
	 */
	@SuppressWarnings("resource")
	protected HttpClientConnectionManager createConnectionManager() {
		if (sslOpts != null) {
			HostnameVerifier hv = null;
			switch (sslOpts.getHostVerify()) {
				case LAX: hv = new NoopHostnameVerifier(); break;
				case DEFAULT: hv = new DefaultHostnameVerifier(); break;
				default: throw new RuntimeException("Programmer error");
			}

			for (String p : split(sslOpts.getProtocols())) {
				try {
					TrustManager tm = new SimpleX509TrustManager(sslOpts.getCertValidate() == SSLOpts.CertValidate.LAX);

					SSLContext ctx = SSLContext.getInstance(p);
					ctx.init(null, new TrustManager[] { tm }, null);

					// Create a socket to ensure this algorithm is acceptable.
					// This will correctly disallow certain configurations (such as SSL_TLS under FIPS)
					ctx.getSocketFactory().createSocket().close();
					SSLConnectionSocketFactory sf = new SSLConnectionSocketFactory(ctx, hv);
					setSSLSocketFactory(sf);

					Registry<ConnectionSocketFactory> r = RegistryBuilder.<ConnectionSocketFactory> create().register("https", sf).build();

					return (pooled ? new PoolingHttpClientConnectionManager(r) : new BasicHttpClientConnectionManager(r));
				} catch (Throwable t) {}
			}
		}

			// Using pooling connection so that this client is threadsafe.
		return (pooled ? new PoolingHttpClientConnectionManager() : new BasicHttpClientConnectionManager());
	}

	/**
	 * Set a root URL for this client.
	 *
	 * <p>
	 * When set, URL strings passed in through the various rest call methods (e.g. {@link RestClient#doGet(Object)}
	 * will be prefixed with the specified root.
	 * This root URL is ignored on those methods if you pass in a {@link URL}, {@link URI}, or an absolute URL string.
	 *
	 * @param rootUrl
	 * 	The root URL to prefix to relative URL strings.
	 * 	Trailing slashes are trimmed.
	 * 	Usually a <code>String</code> but you can also pass in <code>URI</code> and <code>URL</code> objects as well.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder rootUrl(Object rootUrl) {
		set(RESTCLIENT_rootUri, rootUrl);
		return this;
	}

	/**
	 * Enable SSL support on this client.
	 *
	 * @param opts
	 * 	The SSL configuration options.
	 * 	See {@link SSLOpts} for details.
	 * 	This method is a no-op if <code>sslConfig</code> is <jk>null</jk>.
	 * @return This object (for method chaining).
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 */
	public RestClientBuilder enableSSL(SSLOpts opts) throws KeyStoreException, NoSuchAlgorithmException {
		this.sslOpts = opts;
		return this;
	}

	/**
	 * Enable LAX SSL support.
	 *
	 * <p>
	 * Certificate chain validation and hostname verification is disabled.
	 *
	 * @return This object (for method chaining).
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 */
	public RestClientBuilder enableLaxSSL() throws KeyStoreException, NoSuchAlgorithmException {
		return enableSSL(SSLOpts.LAX);
	}

	/**
	 * Sets the client version by setting the value for the <js>"X-Client-Version"</js> header.
	 *
	 * @param version The version string (e.g. <js>"1.2.3"</js>)
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder clientVersion(String version) {
		return header("X-Client-Version", version);
	}

	/**
	 * Adds an interceptor that gets called immediately after a connection is made.
	 *
	 * @param interceptor The interceptor.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder interceptor(RestCallInterceptor interceptor) {
		return addTo(RESTCLIENT_interceptors, interceptor);
	}

	/**
	 * Adds a {@link RestCallLogger} to the list of interceptors on this class.
	 *
	 * @param level The log level to log messages at.
	 * @param log The logger to log messages to.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder logTo(Level level, Logger log) {
		return interceptor(new RestCallLogger(level, log));
	}

	/**
	 * Make HTTP calls retryable if an error response (>=400) is received.
	 *
	 * @param retries The number of retries to attempt.
	 * @param interval The time in milliseconds between attempts.
	 * @param retryOn
	 * 	Optional object used for determining whether a retry should be attempted.
	 * 	If <jk>null</jk>, uses {@link RetryOn#DEFAULT}.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder retryable(int retries, int interval, RetryOn retryOn) {
		set(RESTCLIENT_retries, retries);
		set(RESTCLIENT_retryInterval, interval);
		set(RESTCLIENT_retryOn, retryOn);
		return this;
	}

	/**
	 * When called, the {@link #createConnectionManager()} method will return a {@link PoolingHttpClientConnectionManager}
	 * instead of a {@link BasicHttpClientConnectionManager}.
	 *
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder pooled() {
		this.pooled = true;
		return this;
	}

	/**
	 * Sets the serializer used for serializing POJOs to the HTTP request message body.
	 *
	 * @param serializer The serializer.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder serializer(Serializer serializer) {
		return set(RESTCLIENT_serializer, serializer);
	}

	/**
	 * Same as {@link #serializer(Serializer)}, except takes in a serializer class that will be instantiated through a
	 * no-arg constructor.
	 *
	 * @param serializerClass The serializer class.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder serializer(Class<? extends Serializer> serializerClass) {
		return set(RESTCLIENT_serializer, serializerClass);
	}

	/**
	 * Sets the parser used for parsing POJOs from the HTTP response message body.
	 *
	 * @param parser The parser.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder parser(Parser parser) {		
		set(RESTCLIENT_parser, parser);
		return this;
	}

	/**
	 * Same as {@link #parser(Parser)}, except takes in a parser class that will be instantiated through a no-arg
	 * constructor.
	 *
	 * @param parserClass The parser class.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder parser(Class<? extends Parser> parserClass) {
		return set(RESTCLIENT_parser, parserClass);
	}

	/**
	 * Sets the part serializer to use for converting POJOs to headers, query parameters, form-data parameters, and
	 * path variables.
	 *
	 * @param partSerializer The part serializer instance.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder partSerializer(HttpPartSerializer partSerializer) {
		return set(RESTCLIENT_partSerializer, partSerializer);
	}

	/**
	 * Sets the part formatter to use for converting POJOs to headers, query parameters, form-data parameters, and
	 * path variables.
	 *
	 * @param partSerializerClass
	 * 	The part serializer class.
	 * 	The class must have a no-arg constructor.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder partSerializer(Class<? extends HttpPartSerializer> partSerializerClass) {
		return set(RESTCLIENT_partSerializer, partSerializerClass);
	}

	/**
	 * Set up this client to use BASIC auth.
	 *
	 * @param host The auth scope hostname.
	 * @param port The auth scope port.
	 * @param user The username.
	 * @param pw The password.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder basicAuth(String host, int port, String user, String pw) {
		AuthScope scope = new AuthScope(host, port);
		Credentials up = new UsernamePasswordCredentials(user, pw);
		CredentialsProvider p = new BasicCredentialsProvider();
		p.setCredentials(scope, up);
		setDefaultCredentialsProvider(p);
		return this;
	}

	/**
	 * Sets the internal {@link HttpClient} to use for handling HTTP communications.
	 *
	 * @param httpClient The HTTP client.
	 * @param keepHttpClientOpen Don't close this client when the {@link RestClient#close()} method is called.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder httpClient(CloseableHttpClient httpClient, boolean keepHttpClientOpen) {
		this.httpClient = httpClient;
		set(RESTCLIENT_keepHttpClientOpen, keepHttpClientOpen);
		return this;
	}

	/**
	 * Defines the executor service to use when calling future methods on the {@link RestCall} class.
	 *
	 * <p>
	 * This executor service is used to create {@link Future} objects on the following methods:
	 * <ul>
	 * 	<li>{@link RestCall#runFuture()}
	 * 	<li>{@link RestCall#getResponseFuture(Class)}
	 * 	<li>{@link RestCall#getResponseFuture(Type,Type...)}
	 * 	<li>{@link RestCall#getResponseAsString()}
	 * </ul>
	 *
	 * <p>
	 * The default executor service is a single-threaded {@link ThreadPoolExecutor} with a 30 second timeout
	 * and a queue size of 10.
	 *
	 * @param executorService The executor service.
	 * @param shutdownOnClose Call {@link ExecutorService#shutdown()} when {@link RestClient#close()} is called.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder executorService(ExecutorService executorService, boolean shutdownOnClose) {
		set(RESTCLIENT_executorService, executorService);
		set(RESTCLIENT_executorServiceShutdownOnClose, shutdownOnClose);
		return this;
	}


	//--------------------------------------------------------------------------------
	// HTTP headers
	//--------------------------------------------------------------------------------

	/**
	 * Sets the value for the <code>Accept</code> request header.
	 *
	 * <p>
	 * This overrides the media type specified on the parser, but is overridden by calling
	 * <code>header(<js>"Accept"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder accept(Object value) {
		return header("Accept", value);
	}

	/**
	 * Sets the value for the <code>Accept-Charset</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Accept-Charset"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder acceptCharset(Object value) {
		return header("Accept-Charset", value);
	}

	/**
	 * Sets the value for the <code>Accept-Encoding</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Accept-Encoding"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder acceptEncoding(Object value) {
		return header("Accept-Encoding", value);
	}

	/**
	 * Sets the value for the <code>Accept-Language</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Accept-Language"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder acceptLanguage(Object value) {
		return header("Accept-Language", value);
	}

	/**
	 * Sets the value for the <code>Authorization</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Authorization"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder authorization(Object value) {
		return header("Authorization", value);
	}

	/**
	 * Sets the value for the <code>Cache-Control</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Cache-Control"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder cacheControl(Object value) {
		return header("Cache-Control", value);
	}

	/**
	 * Sets the value for the <code>Connection</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Connection"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder connection(Object value) {
		return header("Connection", value);
	}

	/**
	 * Sets the value for the <code>Content-Length</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Content-Length"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder contentLength(Object value) {
		return header("Content-Length", value);
	}

	/**
	 * Sets the value for the <code>Content-Type</code> request header.
	 *
	 * <p>
	 * This overrides the media type specified on the serializer, but is overridden by calling
	 * <code>header(<js>"Content-Type"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder contentType(Object value) {
		return header("Content-Type", value);
	}

	/**
	 * Sets the value for the <code>Date</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Date"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder date(Object value) {
		return header("Date", value);
	}

	/**
	 * Sets the value for the <code>Expect</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Expect"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder expect(Object value) {
		return header("Expect", value);
	}

	/**
	 * Sets the value for the <code>Forwarded</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Forwarded"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder forwarded(Object value) {
		return header("Forwarded", value);
	}

	/**
	 * Sets the value for the <code>From</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"From"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder from(Object value) {
		return header("From", value);
	}

	/**
	 * Sets the value for the <code>Host</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Host"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder host(Object value) {
		return header("Host", value);
	}

	/**
	 * Sets the value for the <code>If-Match</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"If-Match"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder ifMatch(Object value) {
		return header("If-Match", value);
	}

	/**
	 * Sets the value for the <code>If-Modified-Since</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"If-Modified-Since"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder ifModifiedSince(Object value) {
		return header("If-Modified-Since", value);
	}

	/**
	 * Sets the value for the <code>If-None-Match</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"If-None-Match"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder ifNoneMatch(Object value) {
		return header("If-None-Match", value);
	}

	/**
	 * Sets the value for the <code>If-Range</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"If-Range"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder ifRange(Object value) {
		return header("If-Range", value);
	}

	/**
	 * Sets the value for the <code>If-Unmodified-Since</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"If-Unmodified-Since"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder ifUnmodifiedSince(Object value) {
		return header("If-Unmodified-Since", value);
	}

	/**
	 * Sets the value for the <code>Max-Forwards</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Max-Forwards"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder maxForwards(Object value) {
		return header("If-Unmodified-Since", value);
	}

	/**
	 * Sets the value for the <code>Origin</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Origin"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder origin(Object value) {
		return header("If-Unmodified-Since", value);
	}

	/**
	 * Sets the value for the <code>Pragma</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Pragma"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder pragma(Object value) {
		return header("Pragma", value);
	}

	/**
	 * Sets the value for the <code>Proxy-Authorization</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Proxy-Authorization"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder proxyAuthorization(Object value) {
		return header("Proxy-Authorization", value);
	}

	/**
	 * Sets the value for the <code>Range</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Range"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder range(Object value) {
		return header("Range", value);
	}

	/**
	 * Sets the value for the <code>Referer</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Referer"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder referer(Object value) {
		return header("Referer", value);
	}

	/**
	 * Sets the value for the <code>TE</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"TE"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder te(Object value) {
		return header("TE", value);
	}

	/**
	 * Sets the value for the <code>User-Agent</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"User-Agent"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder userAgent(Object value) {
		return header("User-Agent", value);
	}

	/**
	 * Sets the value for the <code>Upgrade</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Upgrade"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder upgrade(Object value) {
		return header("Upgrade", value);
	}

	/**
	 * Sets the value for the <code>Via</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Via"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder via(Object value) {
		return header("Via", value);
	}

	/**
	 * Sets the value for the <code>Warning</code> request header.
	 *
	 * <p>
	 * This is a shortcut for calling <code>header(<js>"Warning"</js>, value);</code>
	 *
	 * @param value The new header value.
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder warning(Object value) {
		return header("Warning", value);
	}


	//--------------------------------------------------------------------------------
	// Properties
	//--------------------------------------------------------------------------------

	/**
	 * Sets the {@link Serializer#SERIALIZER_abridged} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_abridged
	 */
	public RestClientBuilder abridged(boolean value) {
		return set(SERIALIZER_abridged, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_addBeanTypeProperties} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_addBeanTypeProperties
	 */
	public RestClientBuilder addBeanTypeProperties(boolean value) {
		return set(SERIALIZER_addBeanTypeProperties, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_detectRecursions} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_detectRecursions
	 */
	public RestClientBuilder detectRecursions(boolean value) {
		return set(SERIALIZER_detectRecursions, value);
	}

	/**
	 * Sets the {@link Parser#PARSER_fileCharset} property on all parsers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Parser#PARSER_fileCharset
	 */
	public RestClientBuilder fileCharset(String value) {
		return set(PARSER_fileCharset, value);
	}

	/**
	 * Configuration property:  Request headers.
	 *
	 * @param key The header name.
	 * @param value The header value.
	 * @return This object (for method chaining).
	 * @see RestClient#RESTCLIENT_headers
	 */
	public RestClientBuilder header(String key, Object value) {
		return addTo(RESTCLIENT_headers, key, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_ignoreRecursions} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_ignoreRecursions
	 */
	public RestClientBuilder ignoreRecursions(boolean value) {
		return set(SERIALIZER_ignoreRecursions, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_initialDepth} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_initialDepth
	 */
	public RestClientBuilder initialDepth(int value) {
		return set(SERIALIZER_initialDepth, value);
	}

	/**
	 * Sets the {@link Parser#PARSER_inputStreamCharset} property on all parsers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Parser#PARSER_inputStreamCharset
	 */
	public RestClientBuilder inputStreamCharset(String value) {
		return set(PARSER_inputStreamCharset, value);
	}

	/**
	 * Configuration property:  Keep HttpClient open.
	 *
	 * <p>
	 * Don't close this client when the {@link RestClient#close()} method is called.
	 *
	 * <h5 class='section'>Notes:</h5>
	 * <ul>
	 * 	<li>This is equivalent to calling <code>set(<jsf>RESTCLIENT_keepHttpClientOpen</jsf>, value)</code>.
	 * </ul>
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see RestClient#RESTCLIENT_keepHttpClientOpen
	 */
	public RestClientBuilder keepHttpClientOpen(boolean value) {
		return set(RESTCLIENT_keepHttpClientOpen, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_listener} and {@link Parser#PARSER_listener} property on all
	 * 	serializers and parsers in this group.
	 *
	 * @param sl The new serializer listener.
	 * @param pl The new parser listener.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_abridged
	 */
	public RestClientBuilder listeners(Class<? extends SerializerListener> sl, Class<? extends ParserListener> pl) {
		set(SERIALIZER_listener, sl);
		set(PARSER_listener, pl);
		return this;
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_maxDepth} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_maxDepth
	 */
	public RestClientBuilder maxDepth(int value) {
		return set(SERIALIZER_maxDepth, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_maxIndent} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_maxIndent
	 */
	public RestClientBuilder maxIndent(boolean value) {
		return set(SERIALIZER_maxIndent, value);
	}

	/**
	 * When called, <code>No-Trace: true</code> is added to requests.
	 *
	 * <p>
	 * This gives the opportunity for the servlet to not log errors on invalid requests.
	 * This is useful for testing purposes when you don't want your log file to show lots of errors that are simply the
	 * results of testing.
	 *
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder noTrace() {
		return header("No-Trace", true);
	}

	/**
	 * Sets the {@link UonSerializer#UON_paramFormat} property on the URL-encoding serializers in this group.
	 *
	 * <p>
	 * This overrides the behavior of the URL-encoding serializer to quote and escape characters in query names and
	 * values that may be confused for UON notation (e.g. <js>"'(foo=123)'"</js>, <js>"'@(1,2,3)'"</js>).
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see UonSerializer#UON_paramFormat
	 */
	public RestClientBuilder paramFormat(String value) {
		super.set(UON_paramFormat, value);
		return this;
	}

	/**
	 * Shortcut for calling <code>paramFormat(<js>"PLAINTEXT"</js>)</code>.
	 *
	 * <p>
	 * The default behavior is to serialize part values (query parameters, form data, headers, path variables) in UON
	 * notation.
	 * Calling this method forces plain-text to be used instead.
	 *
	 * <p>
	 * Specifically, UON notation has the following effects:
	 * <ul>
	 * 	<li>Boolean strings (<js>"true"</js>/<js>"false"</js>) and numeric values (<js>"123"</js>) will be
	 * 			quoted (<js>"'true'"</js>, <js>"'false'"</js>, <js>"'123'"</js>.
	 * 		<br>This allows them to be differentiated from actual boolean and numeric values.
	 * 	<li>String such as <js>"(foo='bar')"</js> that mimic UON structures will be quoted and escaped to
	 * 		<js>"'(foo=bar~'baz~')'"</js>.
	 * </ul>
	 * <p>
	 * The down-side to using plain text part serialization is that you cannot serialize arbitrary POJOs.
	 *
	 * @return This object (for method chaining).
	 */
	public RestClientBuilder plainTextParts() {
		super.set(UON_paramFormat, "PLAINTEXT");
		return this;
	}

	/**
	 * Configuration property:  Request query parameters.
	 *
	 * @param key The query parameter name.
	 * @param value The query parameter value value.
	 * @return This object (for method chaining).
	 * @see RestClient#RESTCLIENT_query
	 */
	public RestClientBuilder query(String key, Object value) {
		return addTo(RESTCLIENT_query, key, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_quoteChar} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_quoteChar
	 */
	public RestClientBuilder quoteChar(char value) {
		return set(SERIALIZER_quoteChar, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_sortCollections} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_sortCollections
	 */
	public RestClientBuilder sortCollections(boolean value) {
		return set(SERIALIZER_sortCollections, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_sortMaps} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_sortMaps
	 */
	public RestClientBuilder sortMaps(boolean value) {
		return set(SERIALIZER_sortMaps, value);
	}

	/**
	 * Sets the {@link Parser#PARSER_strict} property on all parsers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Parser#PARSER_strict
	 */
	public RestClientBuilder strict(boolean value) {
		return set(PARSER_strict, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_trimEmptyCollections} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_trimEmptyCollections
	 */
	public RestClientBuilder trimEmptyCollections(boolean value) {
		return set(SERIALIZER_trimEmptyCollections, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_trimEmptyMaps} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_trimEmptyMaps
	 */
	public RestClientBuilder trimEmptyMaps(boolean value) {
		return set(SERIALIZER_trimEmptyMaps, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_trimNullProperties} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_trimNullProperties
	 */
	public RestClientBuilder trimNullProperties(boolean value) {
		return set(SERIALIZER_trimNullProperties, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_trimStrings} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_trimStrings
	 */
	public RestClientBuilder trimStrings(boolean value) {
		return set(SERIALIZER_trimStrings, value);
	}

	/**
	 * Sets the {@link Parser#PARSER_trimStrings} property on all parsers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Parser#PARSER_trimStrings
	 */
	public RestClientBuilder trimStringsP(boolean value) {
		return set(PARSER_trimStrings, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_uriContext} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_uriContext
	 */
	public RestClientBuilder uriContext(UriContext value) {
		return set(SERIALIZER_uriContext, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_uriRelativity} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_uriRelativity
	 */
	public RestClientBuilder uriRelativity(UriRelativity value) {
		return set(SERIALIZER_uriRelativity, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_uriResolution} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_uriResolution
	 */
	public RestClientBuilder uriResolution(UriResolution value) {
		return set(SERIALIZER_uriResolution, value);
	}

	/**
	 * Sets the {@link Serializer#SERIALIZER_useWhitespace} property on all serializers in this group.
	 *
	 * @param value The new value for this property.
	 * @return This object (for method chaining).
	 * @see Serializer#SERIALIZER_useWhitespace
	 */
	public RestClientBuilder useWhitespace(boolean value) {
		return set(SERIALIZER_useWhitespace, value);
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beanClassVisibility(Visibility value) {
		super.beanClassVisibility(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beanConstructorVisibility(Visibility value) {
		super.beanConstructorVisibility(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beanDictionary(boolean append, Object...values) {
		super.beanDictionary(append, values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beanDictionary(Class<?>...values) {
		super.beanDictionary(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beanDictionary(Object...values) {
		super.beanDictionary(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beanDictionaryRemove(Object...values) {
		super.beanDictionaryRemove(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beanFieldVisibility(Visibility value) {
		super.beanFieldVisibility(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beanFilters(boolean append, Object...values) {
		super.beanFilters(append, values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beanFilters(Object...values) {
		super.beanFilters(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beanFiltersRemove(Object...values) {
		super.beanFiltersRemove(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beanMapPutReturnsOldValue(boolean value) {
		super.beanMapPutReturnsOldValue(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beansRequireDefaultConstructor(boolean value) {
		super.beansRequireDefaultConstructor(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beansRequireSerializable(boolean value) {
		super.beansRequireSerializable(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beansRequireSettersForGetters(boolean value) {
		super.beansRequireSettersForGetters(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beansRequireSomeProperties(boolean value) {
		super.beansRequireSomeProperties(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder beanTypePropertyName(String value) {
		super.beanTypePropertyName(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder debug() {
		super.debug();
		set(RESTCLIENT_debug, true);
		header("Debug", true);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder excludeProperties(Class<?> beanClass, String properties) {
		super.excludeProperties(beanClass, properties);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder excludeProperties(Map<String,String> values) {
		super.excludeProperties(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder excludeProperties(String beanClassName, String properties) {
		super.excludeProperties(beanClassName, properties);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder ignoreInvocationExceptionsOnGetters(boolean value) {
		super.ignoreInvocationExceptionsOnGetters(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder ignoreInvocationExceptionsOnSetters(boolean value) {
		super.ignoreInvocationExceptionsOnSetters(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder ignorePropertiesWithoutSetters(boolean value) {
		super.ignorePropertiesWithoutSetters(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder ignoreUnknownBeanProperties(boolean value) {
		super.ignoreUnknownBeanProperties(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder ignoreUnknownNullBeanProperties(boolean value) {
		super.ignoreUnknownNullBeanProperties(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public <T> RestClientBuilder implClass(Class<T> interfaceClass, Class<? extends T> implClass) {
		super.implClass(interfaceClass, implClass);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder implClasses(Map<String,Class<?>> values) {
		super.implClasses(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder includeProperties(Class<?> beanClass, String properties) {
		super.includeProperties(beanClass, properties);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder includeProperties(Map<String,String> values) {
		super.includeProperties(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder includeProperties(String beanClassName, String properties) {
		super.includeProperties(beanClassName, properties);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder locale(Locale value) {
		super.locale(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder mediaType(MediaType value) {
		super.mediaType(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder methodVisibility(Visibility value) {
		super.methodVisibility(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder notBeanClasses(boolean append, Object...values) {
		super.notBeanClasses(append, values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder notBeanClasses(Class<?>...values) {
		super.notBeanClasses(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder notBeanClasses(Object...values) {
		super.notBeanClasses(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder notBeanClassesRemove(Object...values) {
		super.notBeanClassesRemove(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder notBeanPackages(boolean append, Object...values) {
		super.notBeanPackages(append, values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder notBeanPackages(Object...values) {
		super.notBeanPackages(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder notBeanPackages(String...values) {
		super.notBeanPackages(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder notBeanPackagesRemove(Object...values) {
		super.notBeanPackagesRemove(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder pojoSwaps(boolean append, Object...values) {
		super.pojoSwaps(append, values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder pojoSwaps(Class<?>...values) {
		super.pojoSwaps(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder pojoSwaps(Object...values) {
		super.pojoSwaps(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder pojoSwapsRemove(Object...values) {
		super.pojoSwapsRemove(values);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder sortProperties(boolean value) {
		super.sortProperties(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder timeZone(TimeZone value) {
		super.timeZone(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder useInterfaceProxies(boolean value) {
		super.useInterfaceProxies(value);
		return this;
	}

	@Override /* BeanContextBuilder */
	public RestClientBuilder useJavaBeanIntrospector(boolean value) {
		super.useJavaBeanIntrospector(value);
		return this;
	}

	@Override /* ContextBuilder */
	public RestClientBuilder set(String name, Object value) {
		super.set(name, value);
		return this;
	}

	@Override /* ContextBuilder */
	public RestClientBuilder set(boolean append, String name, Object value) {
		super.set(append, name, value);
		return this;
	}

	@Override /* ContextBuilder */
	public RestClientBuilder set(Map<String,Object> properties) {
		super.set(properties);
		return this;
	}

	@Override /* ContextBuilder */
	public RestClientBuilder add(Map<String,Object> properties) {
		super.add(properties);
		return this;
	}

	@Override /* ContextBuilder */
	public RestClientBuilder addTo(String name, Object value) {
		super.addTo(name, value);
		return this;
	}

	@Override /* ContextBuilder */
	public RestClientBuilder addTo(String name, String key, Object value) {
		super.addTo(name, key, value);
		return this;
	}

	@Override /* ContextBuilder */
	public RestClientBuilder removeFrom(String name, Object value) {
		super.removeFrom(name, value);
		return this;
	}


	//------------------------------------------------------------------------------------------------
	// Passthrough methods for HttpClientBuilder.
	//------------------------------------------------------------------------------------------------

	/**
	 * @param redirectStrategy
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setRedirectStrategy(RedirectStrategy)
	 */
	public RestClientBuilder setRedirectStrategy(RedirectStrategy redirectStrategy) {
		httpClientBuilder.setRedirectStrategy(redirectStrategy);
		return this;
	}

	/**
	 * @param cookieSpecRegistry
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setDefaultCookieSpecRegistry(Lookup)
	 */
	public RestClientBuilder setDefaultCookieSpecRegistry(Lookup<CookieSpecProvider> cookieSpecRegistry) {
		httpClientBuilder.setDefaultCookieSpecRegistry(cookieSpecRegistry);
		return this;
	}

	/**
	 * @param requestExec
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setRequestExecutor(HttpRequestExecutor)
	 */
	public RestClientBuilder setRequestExecutor(HttpRequestExecutor requestExec) {
		httpClientBuilder.setRequestExecutor(requestExec);
		return this;
	}

	/**
	 * @param hostnameVerifier
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setSSLHostnameVerifier(HostnameVerifier)
	 */
	public RestClientBuilder setSSLHostnameVerifier(HostnameVerifier hostnameVerifier) {
		httpClientBuilder.setSSLHostnameVerifier(hostnameVerifier);
		return this;
	}

	/**
	 * @param publicSuffixMatcher
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setPublicSuffixMatcher(PublicSuffixMatcher)
	 */
	public RestClientBuilder setPublicSuffixMatcher(PublicSuffixMatcher publicSuffixMatcher) {
		httpClientBuilder.setPublicSuffixMatcher(publicSuffixMatcher);
		return this;
	}

	/**
	 * @param sslContext
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setSSLContext(SSLContext)
	 */
	public RestClientBuilder setSSLContext(SSLContext sslContext) {
		httpClientBuilder.setSSLContext(sslContext);
		return this;
	}

	/**
	 * @param sslSocketFactory
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setSSLSocketFactory(LayeredConnectionSocketFactory)
	 */
	public RestClientBuilder setSSLSocketFactory(LayeredConnectionSocketFactory sslSocketFactory) {
		httpClientBuilder.setSSLSocketFactory(sslSocketFactory);
		return this;
	}

	/**
	 * @param maxConnTotal
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setMaxConnTotal(int)
	 */
	public RestClientBuilder setMaxConnTotal(int maxConnTotal) {
		httpClientBuilder.setMaxConnTotal(maxConnTotal);
		return this;
	}

	/**
	 * @param maxConnPerRoute
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setMaxConnPerRoute(int)
	 */
	public RestClientBuilder setMaxConnPerRoute(int maxConnPerRoute) {
		httpClientBuilder.setMaxConnPerRoute(maxConnPerRoute);
		return this;
	}

	/**
	 * @param config
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setDefaultSocketConfig(SocketConfig)
	 */
	public RestClientBuilder setDefaultSocketConfig(SocketConfig config) {
		httpClientBuilder.setDefaultSocketConfig(config);
		return this;
	}

	/**
	 * @param config
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setDefaultConnectionConfig(ConnectionConfig)
	 */
	public RestClientBuilder setDefaultConnectionConfig(ConnectionConfig config) {
		httpClientBuilder.setDefaultConnectionConfig(config);
		return this;
	}

	/**
	 * @param connTimeToLive
	 * @param connTimeToLiveTimeUnit
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setConnectionTimeToLive(long,TimeUnit)
	 */
	public RestClientBuilder setConnectionTimeToLive(long connTimeToLive, TimeUnit connTimeToLiveTimeUnit) {
		httpClientBuilder.setConnectionTimeToLive(connTimeToLive, connTimeToLiveTimeUnit);
		return this;
	}

	/**
	 * @param connManager
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setConnectionManager(HttpClientConnectionManager)
	 */
	public RestClientBuilder setConnectionManager(HttpClientConnectionManager connManager) {
		this.httpClientConnectionManager = connManager;
		httpClientBuilder.setConnectionManager(connManager);
		return this;
	}

	/**
	 * @param shared
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setConnectionManagerShared(boolean)
	 */
	public RestClientBuilder setConnectionManagerShared(boolean shared) {
		httpClientBuilder.setConnectionManagerShared(shared);
		return this;
	}

	/**
	 * @param reuseStrategy
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setConnectionReuseStrategy(ConnectionReuseStrategy)
	 */
	public RestClientBuilder setConnectionReuseStrategy(ConnectionReuseStrategy reuseStrategy) {
		httpClientBuilder.setConnectionReuseStrategy(reuseStrategy);
		return this;
	}

	/**
	 * @param keepAliveStrategy
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setKeepAliveStrategy(ConnectionKeepAliveStrategy)
	 */
	public RestClientBuilder setKeepAliveStrategy(ConnectionKeepAliveStrategy keepAliveStrategy) {
		httpClientBuilder.setKeepAliveStrategy(keepAliveStrategy);
		return this;
	}

	/**
	 * @param targetAuthStrategy
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setTargetAuthenticationStrategy(AuthenticationStrategy)
	 */
	public RestClientBuilder setTargetAuthenticationStrategy(AuthenticationStrategy targetAuthStrategy) {
		httpClientBuilder.setTargetAuthenticationStrategy(targetAuthStrategy);
		return this;
	}

	/**
	 * @param proxyAuthStrategy
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setProxyAuthenticationStrategy(AuthenticationStrategy)
	 */
	public RestClientBuilder setProxyAuthenticationStrategy(AuthenticationStrategy proxyAuthStrategy) {
		httpClientBuilder.setProxyAuthenticationStrategy(proxyAuthStrategy);
		return this;
	}

	/**
	 * @param userTokenHandler
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setUserTokenHandler(UserTokenHandler)
	 */
	public RestClientBuilder setUserTokenHandler(UserTokenHandler userTokenHandler) {
		httpClientBuilder.setUserTokenHandler(userTokenHandler);
		return this;
	}

	/**
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#disableConnectionState()
	 */
	public RestClientBuilder disableConnectionState() {
		httpClientBuilder.disableConnectionState();
		return this;
	}

	/**
	 * @param schemePortResolver
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setSchemePortResolver(SchemePortResolver)
	 */
	public RestClientBuilder setSchemePortResolver(SchemePortResolver schemePortResolver) {
		httpClientBuilder.setSchemePortResolver(schemePortResolver);
		return this;
	}

	/**
	 * @param userAgent
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setUserAgent(String)
	 */
	public RestClientBuilder setUserAgent(String userAgent) {
		httpClientBuilder.setUserAgent(userAgent);
		return this;
	}

	/**
	 * @param defaultHeaders
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setDefaultHeaders(Collection)
	 */
	public RestClientBuilder setDefaultHeaders(Collection<? extends Header> defaultHeaders) {
		httpClientBuilder.setDefaultHeaders(defaultHeaders);
		return this;
	}

	/**
	 * @param itcp
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#addInterceptorFirst(HttpResponseInterceptor)
	 */
	public RestClientBuilder addInterceptorFirst(HttpResponseInterceptor itcp) {
		httpClientBuilder.addInterceptorFirst(itcp);
		return this;
	}

	/**
	 * @param itcp
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#addInterceptorLast(HttpResponseInterceptor)
	 */
	public RestClientBuilder addInterceptorLast(HttpResponseInterceptor itcp) {
		httpClientBuilder.addInterceptorLast(itcp);
		return this;
	}

	/**
	 * @param itcp
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#addInterceptorFirst(HttpRequestInterceptor)
	 */
	public RestClientBuilder addInterceptorFirst(HttpRequestInterceptor itcp) {
		httpClientBuilder.addInterceptorFirst(itcp);
		return this;
	}

	/**
	 * @param itcp
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#addInterceptorLast(HttpRequestInterceptor)
	 */
	public RestClientBuilder addInterceptorLast(HttpRequestInterceptor itcp) {
		httpClientBuilder.addInterceptorLast(itcp);
		return this;
	}

	/**
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#disableCookieManagement()
	 */
	public RestClientBuilder disableCookieManagement() {
		httpClientBuilder.disableCookieManagement();
		return this;
	}

	/**
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#disableContentCompression()
	 */
	public RestClientBuilder disableContentCompression() {
		httpClientBuilder.disableContentCompression();
		return this;
	}

	/**
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#disableAuthCaching()
	 */
	public RestClientBuilder disableAuthCaching() {
		httpClientBuilder.disableAuthCaching();
		return this;
	}

	/**
	 * @param httpprocessor
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setHttpProcessor(HttpProcessor)
	 */
	public RestClientBuilder setHttpProcessor(HttpProcessor httpprocessor) {
		httpClientBuilder.setHttpProcessor(httpprocessor);
		return this;
	}

	/**
	 * @param retryHandler
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setRetryHandler(HttpRequestRetryHandler)
	 */
	public RestClientBuilder setRetryHandler(HttpRequestRetryHandler retryHandler) {
		httpClientBuilder.setRetryHandler(retryHandler);
		return this;
	}

	/**
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#disableAutomaticRetries()
	 */
	public RestClientBuilder disableAutomaticRetries() {
		httpClientBuilder.disableAutomaticRetries();
		return this;
	}

	/**
	 * @param proxy
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setProxy(HttpHost)
	 */
	public RestClientBuilder setProxy(HttpHost proxy) {
		httpClientBuilder.setProxy(proxy);
		return this;
	}

	/**
	 * @param routePlanner
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setRoutePlanner(HttpRoutePlanner)
	 */
	public RestClientBuilder setRoutePlanner(HttpRoutePlanner routePlanner) {
		httpClientBuilder.setRoutePlanner(routePlanner);
		return this;
	}

	/**
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#disableRedirectHandling()
	 */
	public RestClientBuilder disableRedirectHandling() {
		httpClientBuilder.disableRedirectHandling();
		return this;
	}

	/**
	 * @param connectionBackoffStrategy
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setConnectionBackoffStrategy(ConnectionBackoffStrategy)
	 */
	public RestClientBuilder setConnectionBackoffStrategy(ConnectionBackoffStrategy connectionBackoffStrategy) {
		httpClientBuilder.setConnectionBackoffStrategy(connectionBackoffStrategy);
		return this;
	}

	/**
	 * @param backoffManager
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setBackoffManager(BackoffManager)
	 */
	public RestClientBuilder setBackoffManager(BackoffManager backoffManager) {
		httpClientBuilder.setBackoffManager(backoffManager);
		return this;
	}

	/**
	 * @param serviceUnavailStrategy
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setServiceUnavailableRetryStrategy(ServiceUnavailableRetryStrategy)
	 */
	public RestClientBuilder setServiceUnavailableRetryStrategy(ServiceUnavailableRetryStrategy serviceUnavailStrategy) {
		httpClientBuilder.setServiceUnavailableRetryStrategy(serviceUnavailStrategy);
		return this;
	}

	/**
	 * @param cookieStore
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setDefaultCookieStore(CookieStore)
	 */
	public RestClientBuilder setDefaultCookieStore(CookieStore cookieStore) {
		httpClientBuilder.setDefaultCookieStore(cookieStore);
		return this;
	}

	/**
	 * @param credentialsProvider
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setDefaultCredentialsProvider(CredentialsProvider)
	 */
	public RestClientBuilder setDefaultCredentialsProvider(CredentialsProvider credentialsProvider) {
		httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
		return this;
	}

	/**
	 * @param authSchemeRegistry
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setDefaultAuthSchemeRegistry(Lookup)
	 */
	public RestClientBuilder setDefaultAuthSchemeRegistry(Lookup<AuthSchemeProvider> authSchemeRegistry) {
		httpClientBuilder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
		return this;
	}

	/**
	 * @param contentDecoderMap
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setContentDecoderRegistry(Map)
	 */
	public RestClientBuilder setContentDecoderRegistry(Map<String,InputStreamFactory> contentDecoderMap) {
		httpClientBuilder.setContentDecoderRegistry(contentDecoderMap);
		return this;
	}

	/**
	 * @param config
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#setDefaultRequestConfig(RequestConfig)
	 */
	public RestClientBuilder setDefaultRequestConfig(RequestConfig config) {
		httpClientBuilder.setDefaultRequestConfig(config);
		return this;
	}

	/**
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#useSystemProperties()
	 */
	public RestClientBuilder useSystemProperties() {
		httpClientBuilder.useSystemProperties();
		return this;
	}

	/**
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#evictExpiredConnections()
	 */
	public RestClientBuilder evictExpiredConnections() {
		httpClientBuilder.evictExpiredConnections();
		return this;
	}

	/**
	 * @param maxIdleTime
	 * @param maxIdleTimeUnit
	 * @return This object (for method chaining).
	 * @see HttpClientBuilder#evictIdleConnections(long,TimeUnit)
	 */
	public RestClientBuilder evictIdleConnections(long maxIdleTime, TimeUnit maxIdleTimeUnit) {
		httpClientBuilder.evictIdleConnections(maxIdleTime, maxIdleTimeUnit);
		return this;
	}
}
