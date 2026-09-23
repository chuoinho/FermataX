package me.aap.fermata.addon.web.audio;

/**
 * Shared JavaScript DSP engine source provider for all FermataX Web Audio bridges
 * (YouTube, Stremio, and Generic Web Browser).
 *
 * Implements standard 10-band octave equalization (lowshelf/highshelf + peaking),
 * DynamicsCompressor with dynamic knee/attack/release, adaptive make-up gain,
 * and frequency response peak calculation (dinhDb on 96-point log scale).
 */
public final class WebAudioJsSource {
	private WebAudioJsSource() {}

	/**
	 * Core DSP logic snippet containing:
	 * - Normalization & unity profile helpers
	 * - Smooth audio parameter transition helper
	 * - 96-point log scale frequency response peak calculation (dinhDb)
	 * - Neutralization helper
	 * - Dynamic threshold & makeup gain application (apply)
	 * - Complete 10-band BiquadFilter + DynamicsCompressor + Gain node builder
	 */
	public static final String CORE_DSP_LOGIC = """
			  var VERSION = 1, MIN_DB = -15, MAX_DB = 15, MIN_PREAMP = -60, Q = Math.SQRT2;
			  var finite = Number.isFinite || function(v){ return typeof v === 'number' && isFinite(v); };
			  var unity = function(){ return {v:VERSION,m:false,e:false,b:[0,0,0,0,0,0,0,0,0,0],p:0}; };
			  var normalize = function(v){
			    if(!v||v.v!==VERSION||typeof v.m!=='boolean'||typeof v.e!=='boolean'||!Array.isArray(v.b)||v.b.length!==10||!finite(v.p)||v.p<MIN_PREAMP||v.p>0)return null;
			    var b=[];
			    for(var i=0;i<10;i++){
			      if(!finite(v.b[i])||v.b[i]<MIN_DB||v.b[i]>MAX_DB)return null;
			      b.push(v.b[i]);
			    }
			    return {v:VERSION,m:v.m,e:v.e,b:b,p:v.p};
			  };
			  var sourceOf = function(v){ try { return String(v.currentSrc || v.src || ''); } catch(_) { return ''; } };
			  var isBlob = function(v){ return String(v).slice(0, 5).toLowerCase() === 'blob:'; };
			  var smooth = function(p,v,c){
			    try { p.cancelScheduledValues(c.currentTime); p.setTargetAtTime(v, c.currentTime, 0.015); }
			    catch(_) { try { p.value = v; } catch(ignored) {} }
			  };
			  var LUOI_N = 96, luoiF = null, luoiMag = null, luoiPha = null, luoiTong = null;
			  var dinhDb = function(loc){
			    if (!luoiF) {
			      luoiF = new Float32Array(LUOI_N); luoiMag = new Float32Array(LUOI_N);
			      luoiPha = new Float32Array(LUOI_N); luoiTong = new Float32Array(LUOI_N);
			      for (var i = 0; i < LUOI_N; i++) luoiF[i] = 20 * Math.pow(1000, i / (LUOI_N - 1));
			    }
			    for (var j = 0; j < LUOI_N; j++) luoiTong[j] = 1;
			    for (var k = 0; k < loc.length; k++) {
			      try { loc[k].getFrequencyResponse(luoiF, luoiMag, luoiPha); } catch(_) { return null; }
			      for (var m = 0; m < LUOI_N; m++) luoiTong[m] *= luoiMag[m];
			    }
			    var max = 0;
			    for (var n = 0; n < LUOI_N; n++) { if (luoiTong[n] > max) max = luoiTong[n]; }
			    return max > 0 ? 20 * (Math.log(max) / Math.LN10) : 0;
			  };
			  var neutral = function(owner){
			    if (!owner) return;
			    try {
			      smooth(owner.preamp.gain, 1, owner.context);
			      for (var i = 0; i < owner.filters.length; i++) smooth(owner.filters[i].gain, 0, owner.context);
			      if (owner.nen) { smooth(owner.nen.threshold, 0, owner.context); smooth(owner.nen.ratio, 1, owner.context); }
			      if (owner.bu) smooth(owner.bu.gain, 1, owner.context);
			    } catch(_) {}
			  };
			  var apply = function(owner){
			    if (!owner || owner.closed) return;
			    var enabled = profile.m && profile.e;
			    try {
			      smooth(owner.preamp.gain, enabled ? Math.pow(10, profile.p / 20) : 1, owner.context);
			      var rawMax = 0;
			      for (var i = 0; i < owner.filters.length; i++) {
			        var g = enabled ? profile.b[i] : 0;
			        smooth(owner.filters[i].gain, g, owner.context);
			        if (g > rawMax) rawMax = g;
			      }
			      if (!enabled || !owner.nen || !owner.bu) {
			        if (owner.nen) { smooth(owner.nen.threshold, 0, owner.context); smooth(owner.nen.ratio, 1, owner.context); }
			        if (owner.bu) smooth(owner.bu.gain, 1, owner.context);
			        return;
			      }
			      var peak = dinhDb(owner.filters);
			      if (peak === null || !finite(peak)) peak = rawMax;
			      if (peak < rawMax) peak = rawMax;
			      if (peak <= 0) {
			        smooth(owner.nen.threshold, 0, owner.context);
			        smooth(owner.nen.ratio, 1, owner.context);
			        smooth(owner.bu.gain, 1, owner.context);
			      } else {
			        smooth(owner.nen.threshold, -peak, owner.context);
			        smooth(owner.nen.ratio, 4, owner.context);
			        smooth(owner.bu.gain, Math.pow(10, (peak * 0.75) / 20), owner.context);
			      }
			    } catch(_) { neutral(owner); }
			  };
			  var buildAudioNodes = function(context){
			    var pre = context.createGain();
			    var filters = [], node = pre;
			    for (var i = 0; i < 10; i++) {
			      var f = context.createBiquadFilter();
			      f.type = (i === 0) ? 'lowshelf' : (i === 9) ? 'highshelf' : 'peaking';
			      f.frequency.value = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000][i];
			      f.Q.value = Q;
			      filters.push(f);
			      node.connect(f);
			      node = f;
			    }
			    var nen = context.createDynamicsCompressor();
			    nen.knee.value = 12;
			    nen.attack.value = 0.004;
			    nen.release.value = 0.2;
			    node.connect(nen);
			    var bu = context.createGain();
			    bu.gain.value = 1;
			    nen.connect(bu);
			    var output = context.createGain();
			    bu.connect(output);
			    output.connect(context.destination);
			    return { preamp: pre, filters: filters, nen: nen, bu: bu, output: output };
			  };
			""";

	public static String updateProfileSource(String globalName, long generation, WebAudioProfile profile) {
		return "(function(){var b=window." + globalName + ";return !!(b&&b.version===1&&" +
				"b.updateProfile(" + generation + "," + profile.toJavascriptObject() + "));})();";
	}

	public static String teardownSource(String globalName, long generation) {
		return "(function(){var b=window." + globalName + ";return !!(b&&b.version===1&&" +
				"b.teardown(" + generation + "));})();";
	}
}
