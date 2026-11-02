package me.aap.utils.net;




import java.net.URLDecoder;
import java.net.URLEncoder;


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
}
