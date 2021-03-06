/*
 * Copyright  2002-2006 WYMIWYG (http://wymiwyg.org)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.wymiwyg.wrhapi.test;

import junit.framework.TestCase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.http.ProtocolException;
import org.apache.http.protocol.HttpContext;
import org.wymiwyg.commons.util.Util;

import org.wymiwyg.wrhapi.Handler;
import org.wymiwyg.wrhapi.HandlerException;
import org.wymiwyg.wrhapi.HeaderName;
import org.wymiwyg.wrhapi.Method;
import org.wymiwyg.wrhapi.Request;
import org.wymiwyg.wrhapi.Response;
import org.wymiwyg.wrhapi.ResponseStatus;
import org.wymiwyg.wrhapi.ServerBinding;
import org.wymiwyg.wrhapi.URIScheme;
import org.wymiwyg.wrhapi.WebServer;
import org.wymiwyg.wrhapi.WebServerFactory;
import org.wymiwyg.wrhapi.util.MessageBody2Read;
import org.wymiwyg.wrhapi.util.MessageBody2Write;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import org.apache.http.HttpResponse;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;
import org.wymiwyg.commons.timelogger.SummaryReportWriter;
import org.wymiwyg.commons.timelogger.TimeLogger;

/**
 * @author reto
 * 
 */
public class BaseTests extends TestCase {

	private static final Log log = LogFactory.getLog(BaseTests.class);
	public static final ServerBinding serverBinding = new ServerBinding() {

		public InetAddress getInetAddress() {
			try {
				return InetAddress.getByName("localhost");
			} catch (UnknownHostException e) {
				throw new RuntimeException(e);
			}
		}

		public int getPort() {
			return 8686;
		}
	};

	/**
	 * @return the server used in the tests
	 */
	protected WebServerFactory createServer() {
		return WebServerFactory.newInstance();
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testSimpleBodyWriting() throws Exception {
		testSimpleBody(true);
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testSimpleBodyInputStream() throws Exception {
		testSimpleBody(false);
	}

	private void testSimpleBody(final boolean writeBody) throws Exception {
		final String body = "This is the content of the body";
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, Response response)
					throws HandlerException {
				log.info("handling testSimpleBody");

				if (writeBody) {
					response.setBody(new MessageBody2Write() {

						public void writeTo(WritableByteChannel out)
								throws IOException {
							out.write(ByteBuffer.wrap(body.getBytes()));
						}
					});
				} else {
					response.setBody(new MessageBody2Read() {

						public ReadableByteChannel read() throws IOException {
							return Channels.newChannel(new ByteArrayInputStream(body.getBytes()));
						}
					});
				}
			}
		}, serverBinding);

		try {
			URL serverURL = new URL("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");
			Reader reader = new InputStreamReader(serverURL.openStream());
			StringWriter stringWriter = new StringWriter();

			for (int ch = reader.read(); ch != -1; ch = reader.read()) {
				stringWriter.write(ch);
			}

			assertEquals(body, stringWriter.toString());
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			webServer.stop();
		}
	}

	@Test
	public void testEmptyBodyAnHeader() throws Exception {
		final String body = "";
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, final Response response)
					throws HandlerException {
				log.info("handling testEmptyBody");
				response.setResponseStatus(ResponseStatus.CREATED);
				response.setBody(new MessageBody2Write() {

					public void writeTo(WritableByteChannel out)
							throws IOException {
						try {
							response.setHeader(HeaderName.CONTENT_TYPE, "text/plain");
						} catch (HandlerException ex) {
							throw new RuntimeException(ex);
						}
						out.write(ByteBuffer.wrap(body.getBytes()));
						out.close();
					}
				});
			}
		}, serverBinding);

		try {
			URL serverURL = new URL("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");
			HttpURLConnection connection = (HttpURLConnection) serverURL.openConnection();
			assertEquals("text/plain", connection.getHeaderField("Content-Type"));
			assertEquals(ResponseStatus.CREATED.getCode(),
					connection.getResponseCode());
			Reader reader = new InputStreamReader(connection.getInputStream());
			StringWriter stringWriter = new StringWriter();

			for (int ch = reader.read(); ch != -1; ch = reader.read()) {
				stringWriter.write(ch);
			}

			assertEquals(body, stringWriter.toString());
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			webServer.stop();
		}
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testLongBody() throws Exception {
		final byte[] body = Util.createRandomBytes(10 * 1000000);
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, Response response)
					throws HandlerException {
				log.info("handling testLongBody");
				response.setBody(new MessageBody2Write() {

					public void writeTo(WritableByteChannel out)
							throws IOException {
						out.write(ByteBuffer.wrap(body));
					}
				});
			}
		}, serverBinding);

		try {
			URL serverURL = new URL("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");
			InputStream reader = serverURL.openStream();
			ByteArrayOutputStream bout = new ByteArrayOutputStream(30 * 1000000);

			for (int ch = reader.read(); ch != -1; ch = reader.read()) {
				bout.write(ch);
			}

			byte[] returnedBytes = bout.toByteArray();

			for (int i = 0; i < body.length; i++) {
				assertEquals(body[i], returnedBytes[i]);
			}
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			webServer.stop();
		}
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testResponseHeader() throws Exception {
		final String headerValue = "bla blah";
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, Response response)
					throws HandlerException {
				log.info("handling testResponseHeader");
				response.setHeader(HeaderName.COOKIE, headerValue);
			}
		}, serverBinding);

		try {
			URL serverURL = new URL("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");
			URLConnection connection = serverURL.openConnection();
			connection.connect();
			assertEquals(headerValue, connection.getHeaderField("Cookie"));
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			webServer.stop();
		}
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testScheme() throws Exception {
		final URIScheme[] schemes = new URIScheme[1];
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, Response response)
					throws HandlerException {
				log.info("handling scheme-test");
				schemes[0] = request.getScheme();
			}
		}, serverBinding);

		try {
			URL serverURL = new URL("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");
			URLConnection connection = serverURL.openConnection();
			connection.connect();
			//this is for the connection to get real
			connection.getHeaderField("Cookie");
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			webServer.stop();
		}
		assertEquals(URIScheme.HTTP, schemes[0]);
	}

	/**
	 * set a "cookie" request header and expects the same value in the "cookie"
	 * response header
	 * 
	 * @throws Exception
	 */
	@Test
	public void testMultipleRequestHeader() throws Exception {
		final String headerValue = "bla blah";
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, Response response)
					throws HandlerException {
				log.info("handling testMultipleRequestHeader");

				String receivedHeaderValue = request.getHeaderValues(HeaderName.COOKIE)[0];
				response.setHeader(HeaderName.COOKIE, receivedHeaderValue);
				assertEquals(headerValue, receivedHeaderValue);
			}
		}, serverBinding);

		try {
			URL serverURL = new URL("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");
			URLConnection connection = serverURL.openConnection();
			connection.setRequestProperty("CoOkie", headerValue);
			connection.setRequestProperty("FOO", "bar");
			connection.connect();
			// for the handler to be invoked, something of the response has to
			// be asked
			assertEquals(headerValue, connection.getHeaderField("Cookie"));
			connection.getContentLength();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			webServer.stop();
		}
	}

	/**
	 * Test whether the getPort method returns the actual request port and not the one
	 * included in the host-header
	 * @throws Exception 
	 */
	@Test
	public void testPort() throws Exception {
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, Response response)
					throws HandlerException {
				assertEquals(serverBinding.getPort(), request.getPort());
			}
		}, serverBinding);

		try {
			URL serverURL = new URL("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");
			URLConnection connection = serverURL.openConnection();
			connection.setRequestProperty("host", "foo:88");
			connection.setRequestProperty("FOO", "bar");
			connection.connect();
			// for the handler to be invoked, something of the response has to
			// be asked
			//assertEquals(headerValue, connection.getHeaderField("Cookie"));
			connection.getContentLength();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			webServer.stop();
		}
	}

	/**
	 * @throws Exception
	 */
	@Test
	public void testStatusCode() throws Exception {
		final int statusCode = 302;
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, Response response)
					throws HandlerException {
				log.info("handling testStatusCode");
				response.setResponseStatus(ResponseStatus.getInstanceByCode(statusCode));
				response.setHeader(HeaderName.SERVER, "Ad-Hoc testing server");
				response.setHeader(HeaderName.LOCATION, "http://example.org/");
			}
		}, serverBinding);

		try {
			URI serverURL = new URI("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");
			HttpHead method = new HttpHead(serverURL);
			DefaultHttpClient client = new DefaultHttpClient();
			client.setHttpRequestRetryHandler(null);
			client.setRedirectHandler(nullRedirectHandler);
			HttpResponse response = client.execute(method);
			// for the handler to be invoked, something of the response has to
			// be asked
			log.info(response);
			log.info(response.getStatusLine());
			assertEquals(statusCode, response.getStatusLine().getStatusCode());
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			webServer.stop();
		}
	}

	@Test
	public void testStatusCodeResetInMessageBody() throws Exception {
		final int newStatusCode = 302;
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, final Response response)
					throws HandlerException {
				log.info("handling testStatusCode");
				response.setResponseStatus(ResponseStatus.CREATED);
				response.setHeader(HeaderName.SERVER, "Ad-Hoc testing server");
				response.setHeader(HeaderName.LOCATION, "http://example.org");
				response.setBody(new MessageBody2Write() {

					public void writeTo(WritableByteChannel out) throws IOException {
						try {
							response.setResponseStatus(ResponseStatus.getInstanceByCode(newStatusCode));
						} catch (HandlerException ex) {
							throw new RuntimeException(ex);
						}
						ByteBuffer bb = ByteBuffer.wrap("this is the body".getBytes());
						out.write(bb);
					}
				});
			}
		}, serverBinding);

		try {
			URI serverURL = new URI("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");
			HttpHead method = new HttpHead(serverURL);
			DefaultHttpClient client = new DefaultHttpClient();
			client.setHttpRequestRetryHandler(null);
			client.setRedirectHandler(nullRedirectHandler);
			HttpResponse response = client.execute(method);
			// for the handler to be invoked, something of the response has to
			// be asked
			assertEquals(newStatusCode, response.getStatusLine().getStatusCode());
		} finally {
			webServer.stop();
		}
	}

	@Test
	public void testHeaderAddedInMessageBody() throws Exception {
		final String serverHeaderValue = "Ad-Hoc testing server";
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, final Response response)
					throws HandlerException {

				response.setBody(new MessageBody2Write() {

					public void writeTo(WritableByteChannel out) throws IOException {
						try {

							response.setHeader(HeaderName.SERVER, serverHeaderValue);
						} catch (HandlerException ex) {
							throw new RuntimeException(ex);
						}
						ByteBuffer bb = ByteBuffer.wrap("this is the body".getBytes());
						out.write(bb);
					}
				});
			}
		}, serverBinding);

		try {
			URL serverURL = new URL("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");
			URLConnection connection = serverURL.openConnection();
			connection.connect();
			// for the handler to be invoked, something of the response has to
			// be asked
			assertEquals(serverHeaderValue, connection.getHeaderField("server"));
			connection.getContentLength();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			webServer.stop();
		}
	}

	/**
	 * test is the returned status code matches the one of the HandlerException
	 * thrown, with a HandlerException thrown before a body is set
	 * 
	 * @throws Exception
	 *             on failure
	 */
	@Test
	public void testExceptionStatusCodeBeforeBody() throws Exception {
		final int statusCode = 302;
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, Response response)
					throws HandlerException {
				log.info("handling testStatusCode");
				response.setHeader(HeaderName.SERVER, "Ad-Hoc testing server");
				throw new HandlerException(ResponseStatus.getInstanceByCode(statusCode));
			}
		}, serverBinding);

		try {
			URI serverURL = new URI("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");
			HttpHead method = new HttpHead(serverURL);
			DefaultHttpClient client = new DefaultHttpClient();
			client.setHttpRequestRetryHandler(null);
			client.setRedirectHandler(nullRedirectHandler);
			HttpResponse response = client.execute(method);
			// for the handler to be invoked, something of the response has to
			// be asked
			assertEquals(statusCode, response.getStatusLine().getStatusCode());
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			webServer.stop();
		}
	}

	/**
	 * test is the returned status code matches the one of the HandlerException
	 * thrown, with a HandlerException thrown after a body is set
	 * 
	 * @throws Exception
	 *             on failure
	 */
	/*public void testExceptionStatusCodeAfterBody() throws Exception {
	final int statusCode = 302;
	WebServer webServer = createServer().startNewWebServer(new Handler() {
	public void handle(Request request, Response response)
	throws HandlerException {
	log.info("handling testStatusCode");
	response.setHeader(HeaderName.SERVER, "Ad-Hoc testing server");
	response.setBody(new MessageBody2Write() {
	public void writeTo(WritableByteChannel out)
	throws IOException {
	out.write(ByteBuffer.wrap("my body\n\ncontent\n"
	.getBytes()));
	}
	});

	throw new HandlerException(ResponseStatus.getInstanceByCode(statusCode));
	}
	}, serverBinding);

	try {
	URL serverURL = new URL("http://"
	+ serverBinding.getInetAddress().getHostAddress() + ":"
	+ serverBinding.getPort() + "/");
	HttpClient client = new HttpClient();
	HttpMethod method = new HeadMethod(serverURL.toString());
	method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
	new DefaultHttpMethodRetryHandler(0, false));
	client.executeMethod(method);
	// for the handler to be invoked, something of the response has to
	// be asked
	assertEquals(statusCode, method.getStatusCode());
	} catch (MalformedURLException e) {
	throw new RuntimeException(e);
	} catch (IOException e) {
	throw new RuntimeException(e);
	} finally {
	webServer.stop();
	}
	}*/
	@Test
	public void testRepeated() throws Exception {
		final String body = "This is the content of the body";
		final boolean writeBody = false;
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, Response response)
					throws HandlerException {
				log.info("handling testSimpleBody");

				if (writeBody) {
					response.setBody(new MessageBody2Write() {

						public void writeTo(WritableByteChannel out)
								throws IOException {
							out.write(ByteBuffer.wrap(body.getBytes()));
						}
					});
				} else {
					response.setBody(new MessageBody2Read() {

						public ReadableByteChannel read() throws IOException {
							return Channels.newChannel(new ByteArrayInputStream(body.getBytes()));
						}
					});
				}
			}
		}, serverBinding);

		TimeLogger tl = new TimeLogger();
		for (int r = 0; r < 10000; r++) {
			tl.startSection("iteration");
			URL serverURL = new URL("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");
			Reader reader = new InputStreamReader(serverURL.openStream());
			StringWriter stringWriter = new StringWriter();

			for (int ch = reader.read(); ch != -1; ch = reader.read()) {
				stringWriter.write(ch);
			}

			assertEquals(body, stringWriter.toString());
			tl.endSection();
		}

		webServer.stop();

		tl.writeReport(new PrintWriter(System.out));
		System.out.println("Using SummaryReportWriter:");
		tl.setReportWriter(new SummaryReportWriter());
		tl.writeReport(new PrintWriter(System.out));
	}

	/**
	 * @deprecated uses getBody;
	 * @throws Exception
	 */
	@Deprecated
	public void testPut() throws Exception {
		final byte[] body = Util.createRandomBytes(3); // 10*1000000);
		WebServer webServer = createServer().startNewWebServer(new Handler() {

			public void handle(Request request, Response response)
					throws HandlerException {
				log.info("handling testPut");

				if (!request.getMethod().equals(Method.PUT)) {
					response.setResponseStatus(ResponseStatus.METHOD_NOT_ALLOWED);

					return;
				}

				log.info(request.getBody());
			}
		}, serverBinding);

		try {
			URI serverURL = new URI("http://" + serverBinding.getInetAddress().getHostAddress() + ":" + serverBinding.getPort() + "/");

			DefaultHttpClient client = new DefaultHttpClient();
			client.setHttpRequestRetryHandler(null);
			HttpPut method = new HttpPut(serverURL);
			method.setEntity(new ByteArrayEntity(body));
			HttpResponse response = client.execute(method);
			// for the handler to be invoked, something of the response has to
			// be asked
			log.info("" + response.getStatusLine().getStatusCode());

			// assertEquals(statusCode, method.getStatusCode());
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			webServer.stop();
		}
	}

	private static final RedirectHandler nullRedirectHandler = new RedirectHandler() {

		public boolean isRedirectRequested(HttpResponse response, HttpContext context) {
			return false;
		}

		public URI getLocationURI(HttpResponse response, HttpContext context) throws ProtocolException {
			return null;
		}

	};

	/*
	 * Multiple message-header fields with the same field-name MAY be present in
	 * a message if and only if the entire field-value for that header field is
	 * defined as a comma-separated list [i.e., #(values)]. It MUST be possible
	 * to combine the multiple header fields into one "field-name: field-value"
	 * pair, without changing the semantics of the message, by appending each
	 * subsequent field-value to the first, each separated by a comma.
	 */
}
