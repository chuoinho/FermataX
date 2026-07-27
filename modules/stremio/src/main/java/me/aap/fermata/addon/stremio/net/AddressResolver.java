package me.aap.fermata.addon.stremio.net;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

@FunctionalInterface
public interface AddressResolver {
	List<InetAddress> resolve(String host) throws IOException;
}
