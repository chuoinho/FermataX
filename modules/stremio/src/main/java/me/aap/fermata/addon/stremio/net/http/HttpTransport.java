package me.aap.fermata.addon.stremio.net.http;

@FunctionalInterface
public interface HttpTransport {
	TransportCall execute(TransportRequest request);
}
