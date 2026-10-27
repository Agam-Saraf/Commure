package com.commure.backend.video;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Stand-in for the third-party API on a real socket. Tests queue responses; the last one repeats.
 * Tracks peak concurrency so the backend's 6-request cap can be asserted.
 */
final class FakeTransformServer implements AutoCloseable {

	record Reply(int status, Map<String, String> headers, byte[] body, Duration delay) {

		static Reply video(byte[] body, String requestId) {
			return new Reply(200, Map.of("Content-Type", "video/mp4", "X-Request-Id", requestId), body, Duration.ZERO);
		}

		static Reply error(int status, String requestId, String retryAfter) {
			var headers = retryAfter == null
					? Map.of("Content-Type", "application/json", "X-Request-Id", requestId)
					: Map.of("Content-Type", "application/json", "X-Request-Id", requestId, "Retry-After", retryAfter);
			return new Reply(status, headers, "{\"error\":\"simulated\"}".getBytes(StandardCharsets.UTF_8), Duration.ZERO);
		}

		Reply delayed(Duration value) {
			return new Reply(status, headers, body, value);
		}
	}

	record Received(String authorization, String contentType, String body) {

		/** Filename of the multipart {@code clip} part. */
		String filename() {
			int start = body.indexOf("filename=\"") + "filename=\"".length();
			return body.substring(start, body.indexOf('"', start));
		}

		/** Raw bytes of the multipart {@code clip} part. */
		byte[] clip() {
			String boundary = contentType.substring(contentType.indexOf("boundary=") + "boundary=".length())
				.replace("\"", "");
			int start = body.indexOf("\r\n\r\n", body.indexOf("name=\"clip\"")) + 4;
			int end = body.indexOf("\r\n--" + boundary, start);
			return body.substring(start, end).getBytes(StandardCharsets.ISO_8859_1);
		}
	}

	/** Builds the reply from the request; {@code arrival} is the 0-based order in which requests arrived. */
	@FunctionalInterface
	interface Responder {

		Reply reply(int arrival, Received request);
	}

	private final HttpServer server;
	private final ExecutorService executor = Executors.newCachedThreadPool();
	private final ConcurrentLinkedDeque<Reply> replies = new ConcurrentLinkedDeque<>();
	private volatile Responder responder;
	private final List<Received> received = new CopyOnWriteArrayList<>();
	private final AtomicInteger inFlight = new AtomicInteger();
	private final AtomicInteger peakInFlight = new AtomicInteger();

	FakeTransformServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/transform", this::handle);
		server.setExecutor(executor);
		server.start();
	}

	String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	/** Waits for requests abandoned by earlier tests (e.g. client timeouts) so peaks start from zero. */
	void reset(Reply... queue) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (inFlight.get() > 0 && System.nanoTime() < deadline) {
			Thread.onSpinWait();
		}
		responder = null;
		replies.clear();
		replies.addAll(List.of(queue));
		received.clear();
		peakInFlight.set(0);
	}

	/** Replaces the reply queue with a per-request responder. */
	void reset(Responder value) {
		reset();
		responder = value;
	}

	List<Received> received() {
		return received;
	}

	int peakInFlight() {
		return peakInFlight.get();
	}

	private void handle(HttpExchange exchange) throws IOException {
		try (exchange) {
			byte[] requestBody = exchange.getRequestBody().readAllBytes();
			var request = new Received(
					exchange.getRequestHeaders().getFirst("Authorization"),
					exchange.getRequestHeaders().getFirst("Content-Type"),
					new String(requestBody, StandardCharsets.ISO_8859_1));
			int arrival;
			synchronized (received) {
				received.add(request);
				arrival = received.size() - 1;
			}
			Responder current = responder;
			Reply reply = current != null ? current.reply(arrival, request)
					: replies.size() > 1 ? replies.pollFirst() : replies.peekFirst();
			if (reply == null) {
				reply = Reply.error(500, "no-reply-queued", null);
			}
			process(reply.delay());
			reply.headers().forEach(exchange.getResponseHeaders()::set);
			exchange.sendResponseHeaders(reply.status(), reply.body().length == 0 ? -1 : reply.body().length);
			exchange.getResponseBody().write(reply.body());
		}
	}

	/**
	 * Counted only until the reply is ready: once the client has the response it may release its
	 * permit and send the next request before this handler thread finishes.
	 */
	private void process(Duration delay) {
		peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		finally {
			inFlight.decrementAndGet();
		}
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}
}
