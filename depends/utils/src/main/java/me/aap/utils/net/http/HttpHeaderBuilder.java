package me.aap.utils.net.http;



/**
 * @author Andrey Pavlenko
 */
public interface HttpHeaderBuilder {

	HttpMessageBuilder addHeader(HttpHeader h);

	HttpMessageBuilder addHeader(HttpHeader h, long value);

	HttpMessageBuilder addHeader(CharSequence name, long value);

	HttpMessageBuilder addHeader(HttpHeader h, CharSequence value);

	HttpMessageBuilder addHeader(CharSequence name, CharSequence value);

	HttpMessageBuilder addHeader(CharSequence line);
}
