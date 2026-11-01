package me.aap.utils.net;

import static android.content.Context.WIFI_SERVICE;
import static java.net.NetworkInterface.getNetworkInterfaces;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;


import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;

import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class NetUtils {

	/** UTF-8 form encoding using the Android-compatible String overload. */
	public static String urlEncode(String value) {
		try {
			return URLEncoder.encode(value, "UTF-8");
		} catch (java.io.UnsupportedEncodingException impossible) {
			throw new AssertionError(impossible);
		}
	}

	/** UTF-8 form decoding using the Android-compatible String overload. */
	public static String urlDecode(String value) {
		try {
			return URLDecoder.decode(value, "UTF-8");
		} catch (java.io.UnsupportedEncodingException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private static InetAddress getWiFiAddr(Context ctx) {
		WifiManager wmgr = (WifiManager) ctx.getApplicationContext().getSystemService(WIFI_SERVICE);

		if ((wmgr != null) && wmgr.isWifiEnabled()) {
			WifiInfo info = wmgr.getConnectionInfo();

			if (info.getNetworkId() != -1) {
				int ip = info.getIpAddress();

				if (ip != 0) {
					try {
						ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
						return InetAddress.getByAddress(null, buf.putInt(ip).array());
					} catch (UnknownHostException ex) {
						Log.e(ex, "Failed to create InetAddress from int=", ip);
					}
				}
			}
		}

		return null;
	}

	private static InetAddress getEthAddr() {
		InetAddress ip6 = null;

		try {
			for (Enumeration<NetworkInterface> ifs = getNetworkInterfaces(); ifs.hasMoreElements(); ) {
				NetworkInterface i = ifs.nextElement();

				if (i.isLoopback() || i.isVirtual() || !i.isUp() || !i.getName().startsWith("eth")) {
					continue;
				}

				for (Enumeration<InetAddress> addrs = i.getInetAddresses(); addrs.hasMoreElements(); ) {
					InetAddress addr = addrs.nextElement();
					if (addr instanceof Inet6Address) ip6 = addr;
					else return addr;
				}
			}
		} catch (Exception ex) {
			Log.d(ex);
		}

		return ip6;
	}

}
