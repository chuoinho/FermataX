package me.aap.fermata.addon.tv.xtream;

import static me.aap.utils.async.Completed.completed;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.aap.utils.async.FutureRef;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.CheckedConsumer;

final class XtreamRepository {
	private final XtreamJsonStreamParser parser;
	private final XtreamHttpClient http;
	private final FutureRef<List<XtreamCategory>> liveCategories =
			FutureRef.create(this::loadLiveCategories);
	private final FutureRef<List<XtreamCategory>> vodCategories =
			FutureRef.create(this::loadVodCategories);
	private final FutureRef<List<XtreamCategory>> seriesCategories =
			FutureRef.create(this::loadSeriesCategories);
	private final Map<String, FutureRef<List<XtreamChannel>>> liveStreams =
			new ConcurrentHashMap<>();
	private final Map<String, FutureRef<List<XtreamMovie>>> vodStreams =
			new ConcurrentHashMap<>();
	private final Map<String, FutureRef<List<XtreamSeries>>> series = new ConcurrentHashMap<>();
	private final Map<Integer, FutureRef<List<XtreamSeason>>> seriesSeasons =
			new ConcurrentHashMap<>();
	private final Map<Integer, FutureRef<List<XtreamEpgProgram>>> epg = new ConcurrentHashMap<>();

	XtreamRepository(XtreamHttpClient http, XtreamJsonStreamParser parser) {
		this.http = http;
		this.parser = parser;
	}

	FutureSupplier<XtreamStatus> validate() {
		return request(null, null, null, parser::parseStatus);
	}

	FutureSupplier<List<XtreamCategory>> getLiveCategories() {
		return liveCategories.get();
	}

	FutureSupplier<List<XtreamCategory>> getVodCategories() {
		return vodCategories.get();
	}

	FutureSupplier<List<XtreamCategory>> getSeriesCategories() {
		return seriesCategories.get();
	}

	FutureSupplier<List<XtreamChannel>> getLiveStreams(String categoryId) {
		return liveStreams.computeIfAbsent(cacheKey(categoryId),
				key -> FutureRef.create(() -> loadLiveStreams(categoryId))).get();
	}

	FutureSupplier<List<XtreamMovie>> getVodStreams(String categoryId) {
		return vodStreams.computeIfAbsent(cacheKey(categoryId),
				key -> FutureRef.create(() -> loadVodStreams(categoryId))).get();
	}

	FutureSupplier<List<XtreamSeries>> getSeries(String categoryId) {
		return series.computeIfAbsent(cacheKey(categoryId),
				key -> FutureRef.create(() -> loadSeries(categoryId))).get();
	}

	FutureSupplier<List<XtreamSeason>> getSeriesSeasons(int seriesId) {
		return seriesSeasons.computeIfAbsent(seriesId,
				key -> FutureRef.create(() -> loadSeriesSeasons(seriesId))).get();
	}

	FutureSupplier<List<XtreamEpgProgram>> getEpg(int streamId) {
		return epg.computeIfAbsent(streamId, key -> FutureRef.create(() -> loadEpg(streamId))).get();
	}

	FutureSupplier<XtreamChannel> getFirstLiveStream() {
		return request("get_live_streams", null, null, parser::parseFirstLiveStream);
	}

	void warmUp() {
		getLiveCategories();
		getVodCategories();
		getSeriesCategories();
	}

	void clearCache() {
		liveCategories.clear();
		vodCategories.clear();
		seriesCategories.clear();
		liveStreams.clear();
		vodStreams.clear();
		series.clear();
		seriesSeasons.clear();
		epg.clear();
	}

	private FutureSupplier<List<XtreamCategory>> loadLiveCategories() {
		return requestList("get_live_categories", null, null, parser::parseLiveCategories);
	}

	private FutureSupplier<List<XtreamCategory>> loadVodCategories() {
		return requestList("get_vod_categories", null, null, parser::parseVodCategories);
	}

	private FutureSupplier<List<XtreamCategory>> loadSeriesCategories() {
		return requestList("get_series_categories", null, null, parser::parseSeriesCategories);
	}

	private FutureSupplier<List<XtreamChannel>> loadLiveStreams(String categoryId) {
		return requestList("get_live_streams", "category_id", categoryId, parser::parseLiveStreams);
	}

	private FutureSupplier<List<XtreamMovie>> loadVodStreams(String categoryId) {
		return requestList("get_vod_streams", "category_id", categoryId, parser::parseVodStreams);
	}

	private FutureSupplier<List<XtreamSeries>> loadSeries(String categoryId) {
		return requestList("get_series", "category_id", categoryId, parser::parseSeries);
	}

	private FutureSupplier<List<XtreamSeason>> loadSeriesSeasons(int seriesId) {
		return request("get_series_info", "series_id", String.valueOf(seriesId),
				parser::parseSeriesInfo);
	}

	private FutureSupplier<List<XtreamEpgProgram>> loadEpg(int streamId) {
		Map<String, String> params = new LinkedHashMap<>(1);
		params.put("stream_id", String.valueOf(streamId));
		return http.get("get_simple_data_table", params, parser::parseEpg).then(value ->
						value.isEmpty() ? http.get("get_short_epg", params, parser::parseEpg) : completed(value),
				error -> http.get("get_short_epg", params, parser::parseEpg));
	}

	private <T> FutureSupplier<T> request(@Nullable String action, @Nullable String extraKey,
														 @Nullable String extraValue,
														 XtreamHttpClient.ResponseParser<T> responseParser) {
		Map<String, String> params = null;
		if ((extraKey != null) && (extraValue != null)) {
			params = new LinkedHashMap<>(1);
			params.put(extraKey, extraValue);
		}
		return http.get(action, params, responseParser);
	}

	private <T> FutureSupplier<List<T>> requestList(@Nullable String action,
																				 @Nullable String extraKey,
																				 @Nullable String extraValue,
																				 ListResponseParser<T> responseParser) {
		return request(action, extraKey, extraValue, in -> collect(in, responseParser));
	}

	private <T> List<T> collect(InputStream in, ListResponseParser<T> responseParser)
			throws IOException {
		List<T> list = new ArrayList<>();
		responseParser.parse(in, list::add);
		return list;
	}

	private String cacheKey(@Nullable String value) {
		return (value == null) ? "" : value;
	}

	private interface ListResponseParser<T> {
		void parse(InputStream in, CheckedConsumer<T, IOException> consumer) throws IOException;
	}
}
