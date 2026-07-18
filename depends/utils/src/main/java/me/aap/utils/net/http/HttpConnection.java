package me.aap.utils.net.http;

import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.net.http.HttpHeader.ACCEPT_ENCODING;
import static me.aap.utils.net.http.HttpHeader.CONNECTION;
import static me.aap.utils.net.http.HttpHeader.IF_NONE_MATCH;
import static me.aap.utils.net.http.HttpHeader.USER_AGENT;
import static me.aap.utils.net.http.HttpStatusCode.FOUND;
import static me.aap.utils.net.http.HttpStatusCode.MOVED_PERMANENTLY;
import static me.aap.utils.net.http.HttpStatusCode.PERMANENT_REDIRECT;
import static me.aap.utils.net.http.HttpStatusCode.TEMPORARY_REDIRECT;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import me.aap.utils.app.App;
import me.aap.utils.app.NetApp;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CacheMap;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.BiFunction;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Function;
import me.aap.utils.log.Log;
import me.aap.utils.net.ConnectionClosedException;
import me.aap.utils.net.NetChannel;
import me.aap.utils.net.NetHandler;
import me.aap.utils.net.NetHandler.ConnectOpts;
import me.aap.utils.text.SharedTextBuilder;

/**
 * @author Andrey Pavlenko
 */
public class HttpConnection extends HttpResponseEncoder implements HttpResponseHandler, Closeable {
	private static final CacheMap<ConnectionId, FutureSupplier<HttpConnection>> cache = new CacheMap<>(30);
	private static final Map<URL, URL> permRedirects = new ConcurrentHashMap<>();
	private final NetChannel channel;
	private final Deque<BiFunction<HttpResponse, Throwable, FutureSupplier<?>>> receiveQueue = new ConcurrentLinkedDeque<>();

	public HttpConnection(NetChannel channel) {
		this.channel = channel;
		readMessage(channel);
	}

	public static class Opts extends ConnectOpts {
		public URL url;
		public HttpMethod method = HttpMethod.GET;
		public HttpVersion version = HttpVersion.HTTP_1_1;
		@Nullable
		public NetHandler handler;
		@Nullable
		public Function<HttpRequestBuilder, ByteBuffer[]> builder;
		@Nullable
		public String userAgent = USER_AGENT.getDefaultValue();
		@Nullable
		public String acceptEncoding = HttpHeader.ACCEPT_ENCODING.getDefaultValue();
		@Nullable
		public String ifNonMatch;
		public boolean keepAlive = true;
		public int maxRedirects = 10;
		public int maxReconnects = 10;
		public int responseTimeout;

		public void url(String url) {
			try {
				this.url = new URL(url);
			} catch (MalformedURLException ex) {
				throw new IllegalArgumentException(ex);
			}
		}
	}

	public static Cancellable connect(Consumer<Opts> builder,
			BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer) {
		try {
			Opts o = new Opts();
			builder.accept(o);
			return connect(o, consumer);
		} catch (Throwable ex) {
			consumer.apply(null, ex);
			return Cancellable.CANCELED;
		}
	}

	public static Cancellable connect(Opts o,
			BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer) {
		Req request = (consumer instanceof Req) ? (Req) consumer : new Req(o, consumer);
		connect(request);
		return request;
	}

	private static void connect(Req request) {
		if (request.isCancelled()) return;
		Opts o = request.o;
		if (!checkRedirect(o, request)) return;
		ConnectionId id = new ConnectionId(o.url);
		FutureSupplier<HttpConnection> f;
		o.port = id.port;
		o.host = id.host;
		o.ssl = id.ssl;

		if (o.keepAlive) {
			f = cache.compute(id, (k, v) -> {
				if ((v != null) && !v.isFailed()) {
					HttpConnection c = v.peek();
					if ((c == null) || c.isOpen()) return v;
				}

				return connect(o);
			});
		} else {
			f = connect(o);
		}

		assert f != null;
		request.setPendingConnection(f);
		f.onCompletion((c, err) -> {
			request.clearPendingConnection(f);
			if (request.isCancelled()) {
				if (!o.keepAlive && (c != null)) c.close();
				return;
			}
			sendRequest(c, o, err, request);
		});
	}

	private static FutureSupplier<HttpConnection> connect(Opts o) {
		NetHandler handler = o.handler;

		if (handler == null) {
			App app = App.get();

			if (app instanceof NetApp) {
				handler = ((NetApp) app).getNetHandler();
			} else {
				return failed(new IOException("Unable to create connection without handler"));
			}
		}

		if ((o.responseTimeout != 0) && (o.connectTimeout == 0)) o.connectTimeout = o.responseTimeout;

		if (Log.isLoggableD()) {
			return handler.connect(o).onFailure(err -> Log.e(err, "Connection failed: ", o.url))
					.map(HttpConnection::new);
		} else {
			return handler.connect(o).map(HttpConnection::new);
		}
	}

	private static void sendRequest(HttpConnection c, Opts o, Throwable err,
																	BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer) {
		if (err != null) {
			consumer.apply(null, err);
		} else {
			Req req = (Req) consumer;
			if (!req.setConnection(c)) {
				if (!o.keepAlive) c.close();
				return;
			}
			req.startTimer(c);
			c.sendRequest(req, req);
		}
	}

	public void sendRequest(Function<HttpRequestBuilder, ByteBuffer[]> builder,
													BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer) {
		receiveQueue.addLast(consumer);
		getChannel().write(HttpRequestBuilder.supplier(builder), (v, err) -> {
			if (err != null) onFailure(getChannel(), err);
		});
	}

	@Override
	public FutureSupplier<?> handleResponse(HttpResponse resp) {
		BiFunction<HttpResponse, Throwable, FutureSupplier<?>> c = receiveQueue.pollFirst();

		if (c != null) {
			try {
				FutureSupplier<?> f = c.apply(resp, null);

				if (resp.isConnectionClose()) {
					f.thenRun(() -> close(new ConnectionClosedException("Close response received")));
					return FutureSupplier.noOp();
				} else {
					return f;
				}
			} catch (Throwable ex) {
				onFailure(getChannel(), ex);
				return completedVoid();
			}
		} else {
			onFailure(getChannel(), new IOException("No response handler!"));
			return completedVoid();
		}
	}

	public NetChannel getChannel() {
		return channel;
	}

	public boolean isOpen() {
		return getChannel().isOpen();
	}

	@Override
	public void close() {
		close(new ConnectionClosedException());
	}

	private void close(Throwable cause) {
		getChannel().close();

		while (!receiveQueue.isEmpty()) {
			for (Iterator<BiFunction<HttpResponse, Throwable, FutureSupplier<?>>> it = receiveQueue.iterator(); it.hasNext(); ) {
				BiFunction<HttpResponse, Throwable, FutureSupplier<?>> c = it.next();
				it.remove();

				try {
					c.apply(null, cause);
				} catch (Throwable ex) {
					Log.e(ex, "Consumer failed: ", c);
				}
			}
		}
	}

	@Override
	protected void finalize() {
		close();
	}

	@Override
	protected HttpConnection getConnection(NetChannel channel) {
		return this;
	}

	@NonNull
	@Override
	protected HttpResponseHandler getHandler() {
		return this;
	}

	@Override
	public void onFailure(NetChannel channel, Throwable fail) {
		close(fail);
	}

	private static boolean checkRedirect(Opts o, BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer) {
		URL url = o.url;
		List<URL> viewed = null;

		for (; ; ) {
			URL u = permRedirects.get(url);
			if (u == null) break;
			if (viewed == null) viewed = new ArrayList<>();
			if (viewed.contains(u)) {
				consumer.apply(null, new HttpException("Circular redirect: " + viewed));
				return false;
			}
			viewed.add(url = u);
		}

		o.url = url;
		return true;
	}

	private static final class ConnectionId {
		final int port;
		final String host;
		final boolean ssl;

		ConnectionId(URL url) {
			ssl = "https".equals(url.getProtocol());
			int p = url.getPort();
			String h = url.getHost();
			port = (p == -1) ? (ssl ? 443 : 80) : p;
			host = (h == null) ? "localhost" : h;
		}

		@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
		@Override
		public boolean equals(Object o) {
			ConnectionId id = (ConnectionId) o;
			return host.equals(id.host) && (port == id.port) && (ssl == id.ssl);
		}

		@Override
		public int hashCode() {
			int h = (host.hashCode() ^ port);
			return ssl ? -h : h;
		}

		@Override
		public String toString() {
			return (ssl ? "https://" : "http://") + host + ':' + port;
		}
	}

	private static final class Req implements Function<HttpRequestBuilder, ByteBuffer[]>,
			BiFunction<HttpResponse, Throwable, FutureSupplier<?>>, Runnable, Cancellable {
		private final Opts o;
		private final BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer;
		private FutureSupplier<HttpConnection> pendingConnection;
		private HttpConnection connection;
		private ScheduledFuture<?> timer;
		private boolean cancelled;
		private boolean terminal;

		public Req(Opts o, BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer) {
			this.o = o;
			this.consumer = consumer;
		}

		synchronized boolean isCancelled() {
			return cancelled;
		}

		synchronized void setPendingConnection(FutureSupplier<HttpConnection> pending) {
			if (cancelled && !o.keepAlive) pending.cancel();
			else pendingConnection = pending;
		}

		synchronized void clearPendingConnection(FutureSupplier<HttpConnection> pending) {
			if (pendingConnection == pending) pendingConnection = null;
		}

		synchronized boolean setConnection(HttpConnection connection) {
			if (cancelled || terminal) return false;
			this.connection = connection;
			return true;
		}

		synchronized void startTimer(HttpConnection connection) {
			if ((o.responseTimeout != 0) && (timer == null) && !cancelled && !terminal) {
				timer = connection.getChannel().getHandler().getScheduler()
						.schedule(this, o.responseTimeout, TimeUnit.SECONDS);
			}
		}

		@Override
		public ByteBuffer[] apply(HttpRequestBuilder b) {
			try (SharedTextBuilder tb = SharedTextBuilder.get()) {
				String s = o.url.getPath();
				tb.append(s.isEmpty() ? "/" : s);
				s = o.url.getQuery();

				if (s != null) {
					tb.append('?').append(s);
				} else {
					s = o.url.getRef();
					if (s != null) tb.append('#').append(s);
				}

				b.setRequest(tb, o.method, o.version);

				tb.setLength(0);
				tb.append(o.host);
				if ((o.port != 80) && (o.port != 443)) tb.append(':').append(o.port);
				b.addHeader(HttpHeader.HOST, tb);

				if (o.userAgent != null) {
					if (o.userAgent.equals(USER_AGENT.getDefaultValue())) b.addHeader(USER_AGENT);
					else if (!o.userAgent.isEmpty()) b.addHeader(USER_AGENT, o.userAgent);
				}
				if (o.acceptEncoding != null) {
					if (o.acceptEncoding.equals(ACCEPT_ENCODING.getDefaultValue()))
						b.addHeader(ACCEPT_ENCODING);
					else if (!o.acceptEncoding.isEmpty()) b.addHeader(ACCEPT_ENCODING, o.acceptEncoding);
				}
				if (o.ifNonMatch != null) {
					b.addHeader(IF_NONE_MATCH, o.ifNonMatch);
				}
				if (o.keepAlive) {
					if (o.version == HttpVersion.HTTP_1_0) b.addHeader(CONNECTION, "Keep-Alive");
				} else if (o.version == HttpVersion.HTTP_1_1) {
					b.addHeader(CONNECTION);
				}
			}

			return (o.builder != null) ? o.builder.apply(b) : b.build();
		}

		@Override
		public FutureSupplier<?> apply(HttpResponse resp, Throwable err) {
			synchronized (this) {
				if (cancelled || terminal) return completedVoid();
			}
			if (err != null) {
				if ((o.maxReconnects > 0) && (err instanceof IOException)) {
					Log.d("Trying to reconnect(", o.maxReconnects, "): ", o.url);
					o.maxReconnects--;
					connect(this);
					return completedVoid();
				}

				return deliver(null, err);
			}

			int status = resp.getStatusCode();

			switch (status) {
				case MOVED_PERMANENTLY:
				case FOUND:
				case PERMANENT_REDIRECT:
				case TEMPORARY_REDIRECT:
					CharSequence location = resp.getLocation();

					if (location != null) {
						if (o.maxRedirects > 0) {
							String loc = location.toString();

							if (resp.isConnectionClose()) {
								redirect(loc, status, o.maxRedirects--);
							} else {
								return resp.skipPayload().onCompletion((v, fail) -> {
									if (fail != null) consumer.apply(null, fail);
									else redirect(loc, status, o.maxRedirects--);
								});
							}
						} else {
							Log.w("Maximum number of redirects achieved!");
						}
					} else {
						Log.w(status, " response received, but Location header is not set!");
					}
			}

			return deliver(resp, null);
		}

		private void redirect(String location, int status, int n) {
			if (location.endsWith("#")) location = location.substring(0, location.length() - 1);
			boolean permanent = (status == MOVED_PERMANENTLY) || (status == PERMANENT_REDIRECT);
			if (permanent) Log.d("Permanent redirect(", n, "): ", o.url, " -> ", location);
			else Log.d("Temporary redirect(", n, "): ", o.url, " -> ", location);

			try {
				URL u;

				if (location.startsWith("/")) {
					u = new URL(o.url.getProtocol(), o.url.getHost(), o.url.getPort(), location);
				} else {
					u = new URL(location);
				}

				URL cached = null;
				if (permanent) cached = CollectionUtils.putIfAbsent(permRedirects, o.url, u);
				o.url = (cached == null) ? u : cached;
				connect(this);
			} catch (MalformedURLException ex) {
				deliver(null, ex);
			}
		}


		@Override
		public void run() {
			HttpConnection connection;
			synchronized (this) {
				if ((timer == null) || cancelled || terminal) return;
				timer = null;
				terminal = true;
				connection = this.connection;
			}
			if (connection != null) connection.close();
			consumer.apply(null, new TimeoutException("Request timeout: " + o.url));
		}

		private synchronized void cancelTimer() {
			ScheduledFuture<?> t = timer;

			if (t != null) {
				timer = null;
				t.cancel(false);
			}
		}

		private FutureSupplier<?> deliver(@Nullable HttpResponse response,
				@Nullable Throwable error) {
			synchronized (this) {
				if (cancelled || terminal) return completedVoid();
				terminal = true;
			}
			cancelTimer();
			return consumer.apply(response, error);
		}

		@Override
		public boolean cancel() {
			FutureSupplier<HttpConnection> pending;
			HttpConnection connection;
			synchronized (this) {
				if (cancelled || terminal) return false;
				cancelled = true;
				terminal = true;
				pending = pendingConnection;
				pendingConnection = null;
				connection = this.connection;
				this.connection = null;
			}
			cancelTimer();
			if (!o.keepAlive) {
				if (pending != null) pending.cancel();
				if (connection != null) connection.close();
			}
			return true;
		}
	}
}
