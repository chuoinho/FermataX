package me.aap.fermata.addon.web.tiktok;

/**
 * Embedded automotive scripts for TikTok Web player.
 * Ported from CarStream Tech (tt_clean.js and tt_nav.js).
 */
public final class TikTokScripts {

	public static final String TT_CLEAN = """
// Don va sap xep lai giao dien TikTok cho man hinh xe.
//
// Do duoc tren DHU cau hinh CR-V (khung nhin 860x530 CSS px):
//   video 348x619  <  section[feed-video] 348x619  <  div.ContentFlexLayout 676x619
//     <  article[recommend-list-item-container] 740x651  <  DivColumnListContainer 788x530
//
// Da sua, theo dung thu tu do duoc tren xe:
//   1. Video cao 619 trong khung nhin 530 -> bi cat
//   2. Moi muc cao 651 ma cuon buoc 530 -> lech dan, co luc phai bam hai lan
//      moi sang video moi (xem tt_nav.js, no cuon dung clientHeight)
//   3. Video chi chiem 348/860 be ngang -> chuoi boc bi ghim boi min-height:auto
//      cua o flex, phai them min-height:0 moi tha ra
//   4. Mo ta nam TREN video vi no dung truoc trong DOM -> phai dung order,
//      doi truc sang cot khong du
//   5. Lop nen mo phia sau: bo han, de den
//
// KHONG viet so pixel nao trong day. App chay tren nhieu xe voi man hinh
// khac nhau - 800x480, 1280x720, man rong - nen moi kich thuoc deu tinh theo
// vh va %. Chinh cho mot xe la hong cac xe khac.
//
// Bam vao data-e2e va CAU TRUC (:has), khong bam vao ten lop: ten lop cua
// TikTok la ma bam kieu css-1gp40pd-7937d88b--DivContentFlexLayout, doi lien
// tuc. Phan data-e2e thi on dinh vi chinh TikTok dung no de chay kiem thu.

(function () {
  if (window.__cntTtClean) { return; }
  window.__cntTtClean = true;

  function dbg(msg) {
    try {
      if (window.CntBridge && window.CntBridge.webLog) {
        window.CntBridge.webLog('tiktok ' + msg);
      }
    } catch (e) { /* bo qua */ }
  }

  var ITEM = 'article[data-e2e="recommend-list-item-container"]';
  var VIDEO = 'section[data-e2e="feed-video"]';

  // Dai quang cao tai app va banner che khuat noi dung
  var HIDE = [
    '[class*="AppPromot"]',
    '[class*="DivDownload"]',
    '[data-e2e*="download"]',
    '[data-e2e*="app-store"]',
    '[data-e2e*="google-play"]',
    '[class*="BannerDownload"]',
    '[class*="OverlayTop"]',
    '[class*="MaskLayer"]'
  ];

  // Ten kenh, mo ta, nhac: giu lai nhung dua xuong duoi video.
  var INFO = [
    '[data-e2e*="desc"]',
    '[data-e2e*="nickname"]',
    '[data-e2e*="uniqueid"]',
    '[data-e2e*="username"]'
  ].join(',');

  // Nut chuyen video cua TikTok: AN di vi da co thanh dieu khien noi (Floating Nav Capsule).
  var HIDE_NAV = [
    '[data-e2e="feed-navigation-next"]',
    '[data-e2e="feed-navigation-prev"]'
  ];

  function css() {
    return [
      HIDE.join(',') + '{display:none!important;}',

      HIDE_NAV.join(',') + '{display:none!important;}',

      // 0. Bo hai khoang trang o canh trai va phia duoi. Dat nen den AMOLED cho toan bo trang.
      'html,body{background:#000!important;margin:0!important;padding:0!important;'
        + 'width:100%!important;height:100%!important;overflow:hidden!important;}',

      // Thanh Header tren cung va Logo TikTok o cot ben trai: An di de toi uu 100% dien tich hien thi video va thanh dieu huong.
      // Chuc nang login van su dung binh thuong thong qua nut Profile/Avatar o cot ben trai hoac Like/Bookmark o cot ben phai.
      'header,#app-header,[id="app-header"],[class*="DivHeaderContainer"],'
        + '[data-e2e="tiktok-logo"],[class*="DivLogoWrapper"]{display:none!important;}',

      // An 2 nut Following va Upload o thanh dieu huong ben trai theo yeu cau, giu lai For You, Explore, Live, Profile, More
      '[class*="DivMainNavContainer"] > :has([data-e2e="nav-following"]),'
        + '[class*="DivMainNavContainer"] > :has([data-e2e="nav-upload"]),'
        + '[data-e2e="nav-following"],[data-e2e="nav-upload"]{display:none!important;}',

      'nav{background:#000!important;}',
      'nav *{color:#fff!important;}',

      'main,[class*="ColumnListContainer"],[class*="BaseBodyContainer"],'
        + '[class*="MainContainer"]'
        + '{width:100%!important;max-width:none!important;'
        + 'margin:0!important;padding:0!important;background:#000!important;'
        + 'border:0!important;}',
      ITEM + '{max-width:none!important;background:#000!important;}',

      'main{margin:0!important;width:100%!important;flex:1 1 auto!important;}',

      // 1. Moi muc cao DUNG mot khung nhin.
      ITEM + '{height:100vh!important;min-height:100vh!important;'
        + 'max-height:100vh!important;margin:0!important;padding:0!important;}',

      // 2. Video can giua, cot tuong tac xa hoi hien thi ben canh.
      'div:has(> ' + VIDEO + '){display:flex!important;'
        + 'flex-direction:row!important;justify-content:center!important;'
        + 'align-items:center!important;width:100%!important;'
        + 'height:100%!important;max-width:none!important;'
        + 'margin:0!important;padding:0!important;}',

      VIDEO + '{order:0!important;display:flex!important;'
        + 'flex-direction:column!important;}',

      VIDEO + ' > *:has(video){flex:1 1 auto!important;min-height:0!important;'
        + 'height:100%!important;}',

      // Cot tuong tac xa hoi (tim, binh luan, chia se, avatar, music) hien thi ro rang ben canh video
      'section[class*="SectionActionBarContainer"],[class*="SectionActionBar"]'
        + '{display:flex!important;visibility:visible!important;opacity:1!important;'
        + 'z-index:50!important;margin-left:8px!important;margin-right:8px!important;}',
      'section[class*="SectionActionBarContainer"] *,[class*="SectionActionBar"] *'
        + '{visibility:visible!important;}',
      'div:has(> ' + VIDEO + ') > *:not(' + VIDEO + '){display:flex!important;'
        + 'visibility:visible!important;opacity:1!important;z-index:10!important;'
        + 'margin-left:8px!important;}',
      '[data-e2e*="like"],[data-e2e*="comment"],[data-e2e*="share"],'
        + '[data-e2e*="favorite"],[data-e2e*="avatar"],[data-e2e*="music"]'
        + '{visibility:visible!important;display:flex!important;}',

      //    Chu phai chay theo dong binh thuong, khong duoc dinh de len video.
      INFO + '{position:static!important;transform:none!important;'
        + 'inset:auto!important;max-width:100%!important;}',

      // 2b. Khoi chu cua TikTok: ghim vao DAY khung video.
      //
      //     Do duoc chuoi that: video-desc nam sau 8 cap trong mot lop phu -
      //     DivMediaCardOverlay > ...OverlayBottomSection > ...OverlayBottom
      //     > DivOverlayBottomContent > ... > [video-desc].
      //
      //     Ten da noi ro "OverlayBottom": TikTok von day khoi nay xuong day
      //     bang margin-top:auto. Luat margin:0!important minh dat de don
      //     khoang cach da GIET dung cai margin do, nen no roi len dau. Thay
      //     vi tra margin lai (de bi luat khac de len), ghim thang bang
      //     position - chac chan hon va khong phu thuoc thu tu luat.
      //
      //     Chi ghim lop NGOAI CUNG. Cac lop ben trong phai chay theo dong
      //     binh thuong, khong thi chung chong len nhau.
      '[class*="DivMediaCardOverlay"]:not([class*="Bottom"]):not([class*="Top"])'
        + '{position:absolute!important;top:auto!important;bottom:0!important;'
        + 'left:0!important;right:0!important;height:auto!important;'
        + 'max-height:30%!important;display:flex!important;'
        + 'flex-direction:column!important;justify-content:flex-end!important;'
        + 'pointer-events:none!important;}',
      '[class*="OverlayBottom"]{position:static!important;top:auto!important;'
        + 'bottom:auto!important;margin:0!important;width:100%!important;}',

      // 3. Ca CHUOI BOC tu khung video xuong toi the <video> phai gian het co.
      //
      //    Do duoc: section[feed-video] van 348x619 trong khi cha no rong 740
      //    va muc cao 530. No la mot o flex bi ghim be ngang, va ben trong con
      //    sau lop boc nua cung ghim theo. Video 9:16 trong khung 348x530 chi
      //    ve ra 298px, ho 25px moi ben va 89px phia duoi - do la cho lop nen
      //    lo ra. Keo lop nen chi chua ngon; goc la chuoi boc nay.
      //
      //    Chon bang :has(video) de bat dung cac lop boc, khong phai ke ten
      //    tung cai - ten lop cua TikTok la ma bam, doi lien tuc.
      ITEM + '{width:100%!important;}',
      //    min-height:0 la mau chot. O flex mac dinh co min-height:auto,
      //    tuc khong duoc co xuong duoi kich thuoc noi dung - no vo hieu hoa
      //    ca max-height:100%. Do duoc: section cao 619 = 348 x 16/9, tuc
      //    chieu cao tinh tu be ngang CU con dinh lai. Them min-height:0 va
      //    aspect-ratio:auto thi moi tha ra.
      VIDEO + '{flex:1 1 auto!important;}',
      VIDEO + ',' + VIDEO + ' *:has(video){width:100%!important;'
        + 'height:100%!important;max-width:none!important;max-height:100%!important;'
        + 'min-width:0!important;min-height:0!important;aspect-ratio:auto!important;'
        + 'padding:0!important;flex:1 1 auto!important;margin:0!important;}',

      // 4. Video vua khung, khong cat.
      //
      //    DA THU width:auto de hop video bang dung phan ve ra, cho khoi chu
      //    rong theo video - va HONG: be ngang suy tu chieu cao, chieu cao
      //    suy tu cha, thanh phu thuoc vong nen ca chuoi co ve 0 va man hinh
      //    den thui. Phai giu width:100% va de object-fit lo phan can giua.
      VIDEO + ' video{width:100%!important;height:100%!important;'
        + 'max-height:100%!important;object-fit:contain!important;}',

      // 5. Bo han lop nen mo phia sau video, de den.
      //
      //    TikTok dat mot ban sao lam mo cua video phia sau de lap cho trong
      //    hai ben - video doc tren khung ngang thi luon thua cho. Da thu giu
      //    lai va keo cho phu khung, nhung anh mo bi cat rat nhieu khi video
      //    doc va nhin roi. Den thuan gon hon va do phan tan khi lai xe.
      //
      //    Danh ca hai duong dat anh: the <img> va CSS background-image
      //    (trinh phat xgplayer dung cach thu hai). Khai !important trong
      //    bang mau thang ca inline style nen khong can duyet bang JS.
      VIDEO + ' img{display:none!important;}',

      //    NHUNG BAI ANH THI PHAI CHUA LAI.
      //
      //    TikTok co loai bai "nhieu anh keo qua keo lai" (photo mode). No KHONG
      //    co the <video> nao ca - noi dung that la nhung the <img> nam trong mot
      //    Swiper, va Swiper do lai nam TRONG CHINH section[data-e2e="feed-video"].
      //    Nen luat tren xoa luon noi dung: man hinh den thui ma nhac van chay
      //    (tieng di bang phan tu rieng, khong dinh gi den bo cuc).
      //
      //    Do that 16/8/2026 tren bai .../photo/7634895399303646484, soi bang
      //    Chrome DevTools gan vao chinh WebView cua app:
      //      giua man hinh   = div.swiper-slide.swiper-slide-active
      //      cay cha        = .swiper < DivSwiperContainer < DivPhotoVideoContainer
      //                       < DivPhotoPlayerContainer < section[feed-video]
      //      anh dang hien  = img.…ImgPhotoSlide  0x0  display:none
      //    Va da thu tung bo chon trong HIDE: khong cai nao khop - thu phat dung
      //    la luat ngay tren.
      //
      //    Bat lai CHI trong Swiper, khong bat lai chung chung: anh nen mo cua
      //    video nam NGOAI swiper nen van bi an dung nhu y dinh ban dau.
      //    Do dam hon luat tren (them mot lop) nen thang, khong can dat sau.
      VIDEO + ' .swiper img,' + VIDEO + ' [class*="ImgPhotoSlide"]'
        + '{display:block!important;width:100%!important;height:100%!important;'
        + 'object-fit:contain!important;}',

      VIDEO + ' *{background-image:none!important;}',
      VIDEO + '{background-color:#000!important;position:relative!important;}',

      // Thanh cuon chiem cho ma man hinh xe khong keo tay bao gio.
      '::-webkit-scrollbar{display:none!important;}',
      'body{overflow:hidden!important;}'
    ].join('');
  }

  function inject() {
    var head = document.head || document.documentElement;
    if (!head) {
      setTimeout(inject, 200);
      return;
    }
    if (document.getElementById('__cnt_tt_clean')) { return; }

    var style = document.createElement('style');
    style.id = '__cnt_tt_clean';
    style.textContent = css();
    head.appendChild(style);
  }

  inject();
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', inject);
  }

  // ------------------------------------------------------------- chan doan

  //
  // Thay cho DevTools: ghi cau truc tu <video> len goc kem kich thuoc.
  // Dem so phan tu bi an phai lam SAU khi trang dung xong - ban truoc dem
  // ngay luc chen CSS nen luon ra 0 va khong noi len dieu gi.

  window.__cntTtDump = function () {
    var n = 0;
    for (var i = 0; i < HIDE.length; i++) {
      try { n += document.querySelectorAll(HIDE[i]).length; } catch (e) { /* bo qua */ }
    }

    var v = document.querySelector('video');
    if (!v) {
      dbg('dump: an ' + n + ' phan tu, chua co the video nao');
      return;
    }

    var el = v, line = [], step = 0;
    while (el && el !== document.documentElement && step < 10) {
      var e2e = el.getAttribute && el.getAttribute('data-e2e');
      var cls = '';
      if (el.className && typeof el.className === 'string') {
        // Chi lay phan co nghia o duoi ten lop, bo ma bam.
        var m = el.className.match(/--([A-Za-z]+)/);
        cls = m ? '.' + m[1] : '';
      }
      var r = el.getBoundingClientRect();
      line.push(el.tagName.toLowerCase() + (e2e ? '[' + e2e + ']' : '') + cls
        + ' ' + Math.round(r.width) + 'x' + Math.round(r.height));
      el = el.parentElement;
      step++;
    }

    dbg('dump: an ' + n + ' phan tu, khung nhin '
        + window.innerWidth + 'x' + window.innerHeight);
    dbg('dump chuoi: ' + line.join('  <  '));

    // Quet XUONG trong khung video: lop nen la anh em nam ben trong, khong
    // phai to tien, nen chuoi di len o tren khong bao gio thay no.
    var sec = v.closest ? v.closest(VIDEO) : null;
    if (!sec) { return; }
    var inner = sec.querySelectorAll('*');
    var found = [];
    for (var k = 0; k < inner.length && found.length < 12; k++) {
      var el2 = inner[k];
      if (el2.tagName === 'VIDEO') { continue; }
      var st2 = window.getComputedStyle(el2);
      var bg = st2.backgroundImage;
      var isImg = el2.tagName === 'IMG';
      if (!isImg && (!bg || bg === 'none')) { continue; }
      var r2 = el2.getBoundingClientRect();
      var m2 = (typeof el2.className === 'string')
        ? el2.className.match(/--([A-Za-z]+)/) : null;
      found.push(el2.tagName.toLowerCase() + (m2 ? '.' + m2[1] : '')
        + ' ' + Math.round(r2.width) + 'x' + Math.round(r2.height)
        + (isImg ? ' <img>' : ' bg') + ' pos=' + st2.position);
    }
    var rs = sec.getBoundingClientRect();
    dbg('dump khung video ' + Math.round(rs.width) + 'x' + Math.round(rs.height)
        + ' | lop nen (' + found.length + '): '
        + (found.join('  |  ') || 'khong thay cai nao'));

    // Chu dang o dau: liet ke moi phan tu INFO kem vi tri va chuoi cha, de
    // biet cai nao con nam de len video va vi sao.
    var info = document.querySelectorAll(INFO);
    var seen = [];
    for (var q = 0; q < info.length && seen.length < 8; q++) {
      var e3 = info[q];
      var r3 = e3.getBoundingClientRect();
      if (r3.width === 0 && r3.height === 0) { continue; }

      // Cha gan nhat khong chay theo dong binh thuong - thu pham keo chu
      // len de tren video.
      var anchor = 'flow';
      var up = e3.parentElement, lvl = 0;
      while (up && up !== document.body && lvl < 6) {
        var pos = window.getComputedStyle(up).position;
        if (pos === 'absolute' || pos === 'fixed') {
          var mm = (typeof up.className === 'string')
            ? up.className.match(/--([A-Za-z]+)/) : null;
          anchor = pos + ':' + up.tagName.toLowerCase() + (mm ? '.' + mm[1] : '');
          break;
        }
        up = up.parentElement; lvl++;
      }

      seen.push((e3.getAttribute('data-e2e') || '?')
        + ' ' + Math.round(r3.left) + ',' + Math.round(r3.top)
        + ' ' + Math.round(r3.width) + 'x' + Math.round(r3.height)
        + ' cha=' + anchor);
    }
    dbg('dump chu (' + info.length + '): '
        + (seen.join('  |  ') || 'khong thay cai nao'));

    // Chuoi NGOAI: tu vung cuon len toi body. Day la cho chua tung nhin, ma
    // hai khoang trang lai nam o do.
    var outer = [], up3 = sec.closest(ITEM), lv3 = 0;
    while (up3 && lv3 < 8) {
      var r5 = up3.getBoundingClientRect();
      var st5 = window.getComputedStyle(up3);
      var m5 = (typeof up3.className === 'string')
        ? up3.className.match(/--([A-Za-z]+)/) : null;
      outer.push(up3.tagName.toLowerCase() + (m5 ? '.' + m5[1] : '')
        + ' ' + Math.round(r5.left) + ',' + Math.round(r5.top)
        + ' ' + Math.round(r5.width) + 'x' + Math.round(r5.height)
        + ' pad=' + st5.paddingLeft + '/' + st5.paddingBottom
        + ' bg=' + st5.backgroundColor.replace(/\\s/g, ''));
      up3 = up3.parentElement; lv3++;
    }
    dbg('dump chuoi ngoai: ' + outer.join('  <  '));

    // Mo ta nam o cap nao so voi khung video - quyet dinh luat order phai
    // dat o dau moi voi toi no.
    var d = document.querySelector('[data-e2e*="desc"]');
    if (d) {
      var chain = [], up2 = d, lv2 = 0;
      while (up2 && up2 !== document.body && lv2 < 8) {
        var e4 = up2.getAttribute && up2.getAttribute('data-e2e');
        var m4 = (typeof up2.className === 'string')
          ? up2.className.match(/--([A-Za-z]+)/) : null;
        var r4 = up2.getBoundingClientRect();
        chain.push(up2.tagName.toLowerCase() + (e4 ? '[' + e4 + ']' : '')
          + (m4 ? '.' + m4[1] : '')
          + ' ' + Math.round(r4.width) + 'x' + Math.round(r4.height)
          + ' o=' + window.getComputedStyle(up2).order);
        up2 = up2.parentElement; lv2++;
      }
      dbg('dump duong mo ta: ' + chain.join('  <  '));
    }
  };

  setTimeout(window.__cntTtDump, 4000);
})();

	""";

	public static final String TT_NAV = """
// Nut next / previous tren vo lang, cho TikTok.
//
// TikTok khong co "bai tiep theo" nhu trinh phat nhac - no la mot dong video
// cuon doc. Nen next = xuong video moi hon, previous = len video vua xem.
//
// HAI DIEU DA HOC DUOC TREN XE, dung bo:
//
// 1. PHAI DONG BO VA TUC THI. Khi dang o man hinh CNController thi car
//    activity cua CNMedia bi day xuong nen, Chromium tam dung
//    requestAnimationFrame va siet bo dem thoi gian. Cuon 'smooth' chay bang
//    rAF, con setTimeout thi bi hoan - ca hai nam im toi khi app hien lai roi
//    chay don mot loat.
//
// 2. PHAI KIEM CHUNG, dung tin la da lam duoc. Ban truoc gan scrollTop cho
//    mot div cuon duoc bat ky roi bao thanh cong, nen cac duong sau khong bao
//    gio duoc thu - trong khi video khong he doi. Gio moi duong deu phai
//    chung minh la co gi do that su thay doi.

(function () {
  if (window.__cntTtNav) { return; }
  window.__cntTtNav = true;

  function dbg(msg) {
    try {
      if (window.CntBridge && window.CntBridge.webLog) {
        window.CntBridge.webLog('tiktok ' + msg);
      }
    } catch (e) { /* bo qua */ }
  }

  function describe(el) {
    if (!el || el.nodeType !== 1) { return '?'; }
    var e2e = el.getAttribute('data-e2e');
    var cls = '';
    if (el.className && typeof el.className === 'string') {
      cls = '.' + el.className.trim().split(/\\s+/).slice(0, 2).join('.');
    }
    return el.tagName.toLowerCase() + (e2e ? '[' + e2e + ']' : '') + cls;
  }

  function visible(el) {
    if (!el || !el.getClientRects().length) { return false; }
    var st = window.getComputedStyle(el);
    return st.visibility !== 'hidden' && st.display !== 'none';
  }

  // ---------------------------------------------------------------- duong 1

  /**
   * Gia lap re chuot vao khung video.
   *
   * Hai nut feed-navigation-next/prev KHONG co san trong DOM - React chi dung
   * chung ra khi con tro vao khung video. App chay o nen thi khong co con tro
   * nao, nen phai tu ban su kien chuot vao.
   */
  function hoverFeed() {
    var v = document.querySelector('video');
    var host = null;
    if (v) {
      host = v.closest('[data-e2e="recommend-list-item-container"]')
          || v.closest('[data-e2e="feed-video"]')
          || v.parentElement;
    }
    if (!host) { host = document.body; }

    var r = host.getBoundingClientRect();
    var opts = {
      bubbles: true, cancelable: true, view: window,
      clientX: r.left + r.width / 2,
      clientY: r.top + r.height / 2
    };
    var types = ['pointerover', 'pointerenter', 'pointermove',
                 'mouseover', 'mouseenter', 'mousemove'];
    for (var i = 0; i < types.length; i++) {
      try {
        var ev = (types[i].indexOf('pointer') === 0 && window.PointerEvent)
          ? new PointerEvent(types[i], opts)
          : new MouseEvent(types[i], opts);
        host.dispatchEvent(ev);
      } catch (e) { /* bo qua loai su kien nay */ }
    }
    return host;
  }

  /**
   * Nut chuyen video cua chinh TikTok.
   *
   *   <button data-e2e="feed-navigation-next" aria-disabled="false">
   *   <button data-e2e="feed-navigation-prev" aria-disabled="false">
   *
   * KHONG duoc doi hoi nut phai dang hien. Hai nut nay chi hien khi re chuot
   * vao khung video, ma app o nen thi khong co con tro nao - do la ly do ban
   * truoc bo qua chung du bo loc ten da khop. click() van kich hoat duoc phan
   * tu dang an, nen dieu kien "phai nhin thay" chi lam hong viec.
   */
  function clickArrow(down) {
    var exact = down ? 'feed-navigation-next' : 'feed-navigation-prev';
    var el = document.querySelector('[data-e2e="' + exact + '"]');

    if (!el) {
      // Du phong: TikTok doi ten thi bat theo tu khoa.
      var want = down
        ? ['arrow-right', 'arrow-down', 'video-next', 'next']
        : ['arrow-left', 'arrow-up', 'video-prev', 'prev'];
      var all = document.querySelectorAll('[data-e2e]');
      for (var i = 0; i < all.length && !el; i++) {
        var e2e = (all[i].getAttribute('data-e2e') || '').toLowerCase();
        for (var j = 0; j < want.length; j++) {
          if (e2e.indexOf(want[j]) !== -1) { el = all[i]; break; }
        }
      }
    }

    if (!el) { return false; }

    if (el.getAttribute('aria-disabled') === 'true' || el.disabled) {
      dbg('duong 1: ' + describe(el) + ' dang bi vo hieu');
      return false;
    }

    el.click();
    dbg('duong 1: bam ' + describe(el));
    return true;
  }

  // ---------------------------------------------------------------- duong 2

  /** Moi to tien cuon duoc cua <video>, tu trong ra ngoai. */
  function scrollableAncestors() {
    var out = [];
    var el = document.querySelector('video');
    while (el && el !== document.documentElement) {
      var st = window.getComputedStyle(el);
      var oy = st.overflowY;
      if ((oy === 'auto' || oy === 'scroll' || oy === 'overlay')
          && el.scrollHeight > el.clientHeight + 10) {
        out.push(el);
      }
      el = el.parentElement;
    }
    var doc = document.scrollingElement || document.documentElement;
    if (doc && doc.scrollHeight > doc.clientHeight + 10) {
      out.push(doc);
    }
    return out;
  }

  /**
   * Cuon dung mot khung nhin, tuc thi, VA KIEM lai scrollTop co doi khong.
   * Khong doi thi tra false de con thu duong khac.
   */
  function scrollOne(down) {
    var list = scrollableAncestors();
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      var step = el.clientHeight || window.innerHeight;
      var before = el.scrollTop;

      // Phan tu co CSS scroll-behavior:smooth thi gan scrollTop se cuon co
      // hieu ung - chay bang rAF, dang bi dung o nen - nen doc lai ngay van
      // ra gia tri cu. Ep ve 'auto' trong luc gan.
      var keep = el.style.scrollBehavior;
      el.style.scrollBehavior = 'auto';
      el.scrollTop = before + (down ? step : -step);
      el.style.scrollBehavior = keep;

      if (el.scrollTop !== before) {
        // Ep tinh lai bo cuc ngay: doc mot thuoc tinh bo cuc buoc Blink phai
        // cap nhat, khong cho toi luot dung hinh - ma luot do khong chay khi
        // trang o nen. Khong bao dam TikTok nhan ra ngay, nhung khong ep thi
        // chac chan la khong.
        void el.offsetHeight;
        void el.getBoundingClientRect();
        var vv = el.querySelector('video');
        if (vv) { void vv.getBoundingClientRect(); }

        dbg('duong 2: cuon ' + describe(el)
            + ' ' + before + '->' + el.scrollTop
            + ' (cao ' + el.clientHeight + '/' + el.scrollHeight + ')');
        return true;
      }
      dbg('duong 2: ' + describe(el) + ' khong nhuc nhich o ' + before);
    }
    return false;
  }

  // ---------------------------------------------------------------- duong 3

  /** Keo the boc video ke tiep vao tam nhin, khong hieu ung. */
  function scrollIntoNext(down) {
    var video = document.querySelector('video');
    if (!video) { return false; }

    var item = video;
    for (var i = 0; i < 8 && item.parentElement; i++) {
      item = item.parentElement;
      if (item.clientHeight >= window.innerHeight * 0.8) { break; }
    }
    var target = down ? item.nextElementSibling : item.previousElementSibling;
    if (!target) {
      dbg('duong 3: ' + describe(item) + ' khong co anh em ke');
      return false;
    }
    target.scrollIntoView({ behavior: 'instant', block: 'start' });
    dbg('duong 3: keo ' + describe(target) + ' vao tam nhin');
    return true;
  }

  // ---------------------------------------------------------------- duong 4

  /** Lan banh chuot. Nhieu danh sach cuon ao nghe wheel chu khong nghe scrollTop. */
  function wheel(down) {
    var video = document.querySelector('video');
    var target = video || document.body;
    var delta = down ? window.innerHeight : -window.innerHeight;
    try {
      target.dispatchEvent(new WheelEvent('wheel', {
        deltaY: delta, deltaMode: 0, bubbles: true, cancelable: true
      }));
      dbg('duong 4: lan banh ' + delta);
      return true;
    } catch (e) {
      return false;
    }
  }

  // ---------------------------------------------------------------- duong 5

  /**
   * Phim mui ten. De cuoi cung vi khi trang o nen thi visibilityState la
   * 'hidden', nhieu trang bo qua phim luc do.
   */
  function pressArrow(down) {
    var opts = {
      key: down ? 'ArrowDown' : 'ArrowUp',
      code: down ? 'ArrowDown' : 'ArrowUp',
      keyCode: down ? 40 : 38,
      which: down ? 40 : 38,
      bubbles: true,
      cancelable: true
    };
    var targets = [document, document.body, window];
    var video = document.querySelector('video');
    if (video) { targets.push(video); }

    for (var i = 0; i < targets.length; i++) {
      try {
        targets[i].dispatchEvent(new KeyboardEvent('keydown', opts));
        targets[i].dispatchEvent(new KeyboardEvent('keyup', opts));
      } catch (e) { /* bo qua dich nay */ }
    }
    dbg('duong 5: gui phim ' + opts.key);
  }

  // ------------------------------------------------------------------------

  /**
   * Liet ke thu can nhin, KHONG cat bot theo thu tu DOM.
   *
   * Ban truoc cat o 30 ten dau nen chi thay thanh dieu huong - toan bo ten
   * thuoc khung video bi cat mat. Gio loc theo tu khoa thay vi cat.
   */
  function dumpCandidates() {
    var WANT = /video|arrow|item|feed|recommend|browse|player|swiper|slide|next|prev/i;

    var hit = [], other = 0;
    var all = document.querySelectorAll('[data-e2e]');
    for (var i = 0; i < all.length; i++) {
      var e2e = all[i].getAttribute('data-e2e');
      if (!e2e) { continue; }
      if (WANT.test(e2e)) {
        if (hit.indexOf(e2e) === -1) { hit.push(e2e); }
      } else {
        other++;
      }
    }
    dbg('data-e2e lien quan (' + hit.length + '/' + all.length + ', bo qua '
        + other + '): ' + (hit.join(', ') || 'KHONG CO CAI NAO'));

    // Nut co the bam duoc, tim theo nhan tro nang thay vi theo lop.
    var btns = [];
    var cand = document.querySelectorAll('button, [role="button"]');
    for (var j = 0; j < cand.length && btns.length < 25; j++) {
      var lab = (cand[j].getAttribute('aria-label') || '').trim();
      if (lab) { btns.push(lab); }
    }
    dbg('aria-label cua nut (' + cand.length + ' nut): ' + btns.join(' | '));

    // Vung cuon kem kich thuoc that, de biet vi sao khong nhuc nhich.
    var sc = scrollableAncestors();
    for (var k = 0; k < sc.length; k++) {
      dbg('vung cuon: ' + describe(sc[k])
          + ' scrollTop=' + sc[k].scrollTop
          + ' clientH=' + sc[k].clientHeight
          + ' scrollH=' + sc[k].scrollHeight
          + ' max=' + (sc[k].scrollHeight - sc[k].clientHeight));
    }
    if (!sc.length) { dbg('vung cuon: khong tim thay'); }
  }

  /**
   * Chay het cac duong cho toi khi video that su doi.
   *
   * Khong the cho de kiem tra (bo dem thoi gian bi siet khi o nen), nen kiem
   * ngay sau moi buoc. Cuon la dong bo nen dau van tay doi ngay neu an.
   */
  /**
   * Chay lan luot cho toi khi MOT duong lam duoc viec.
   *
   * KHONG duoc doi chieu video da doi chua roi moi coi la thanh cong: TikTok
   * doi video bat dong bo, ngay sau khi cuon thi currentSrc van la cai cu.
   * Ban truoc dung dau van tay lam dieu kien nen cuon thanh cong van bi coi
   * la truot, cac duong sau chay tiep va KEO NGUOC lai - next thi cac duong
   * thua tinh co cung day xuong nen van ra ket qua, con prev thi triet tieu
   * nhau. Moi duong tu do lay ket qua cua chinh no (scrollTop co doi khong,
   * co bam trung nut khong) va tin vao do.
   */
  function go(down) {
    hoverFeed();
    if (clickArrow(down)) { return; }
    if (scrollOne(down)) { return; }
    if (scrollIntoNext(down)) { return; }

    // Nut co the duoc React dung ra sau khi re chuot. Cho bang microtask chu
    // khong bang setTimeout - microtask khong bi siet khi trang o nen.
    Promise.resolve().then(function () {
      if (clickArrow(down)) { return; }
      wheel(down);
      pressArrow(down);
      dumpCandidates();
    });
  }

  window.__cntNext = function () { go(true); };
  window.__cntPrev = function () { go(false); };

  /**
   * Muc dang xem trong dong video.
   *
   * Dong video co hang chuc muc cung nam trong DOM, moi muc mot video-desc
   * rieng. querySelector tra ve cai DAU TIEN nen ten bai tren the dang phat
   * luon la cua video dau danh sach va khong bao gio doi khi chuyen bai.
   */
  function activeItem() {
    // Uu tien muc chua video dang phat.
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      if (!vids[i].paused && vids[i].closest) {
        var c = vids[i].closest('[data-e2e="recommend-list-item-container"]');
        if (c) { return c; }
      }
    }
    // Khong co cai nao dang phat -> lay muc gan giua khung nhin nhat.
    var items = document.querySelectorAll('[data-e2e="recommend-list-item-container"]');
    var mid = window.innerHeight / 2;
    var best = null, bestDist = Infinity;
    for (var j = 0; j < items.length; j++) {
      var r = items[j].getBoundingClientRect();
      var d = Math.abs(r.top + r.height / 2 - mid);
      if (d < bestDist) { bestDist = d; best = items[j]; }
    }
    return best;
  }

  /** The <video> cua muc dang xem, de playstate.js bao dung bai. */
  window.__cntVideo = function () {
    var item = activeItem();
    return item ? item.querySelector('video') : null;
  };

  // Anh thu nho: KHONG gui gi ca.
  //
  // Anh cua TikTok la data: URI nhung thang trong trang, tai ve roi giai ma
  // ton cong ma the dang phat tren xe chi hien mot o be xiu. Bo han, de
  // ControllerService dung icon cua app lam anh - nhin ra ngay la TikTok.

  /** Mo ta video lam ten bai tren the dang phat cua xe. */
  window.__cntTitle = function () {
    var item = activeItem();
    var el = item ? item.querySelector('[data-e2e="video-desc"]') : null;
    if (!el) {
      el = document.querySelector('[data-e2e="browse-video-desc"], [data-e2e="video-desc"]');
    }
    return (el && el.textContent.trim()) ? el.textContent.trim() : null;
  };

  dbg('tt_nav da nap');
})();

	""";

	public static final String PLAY_STATE = """
(function () {
  if (window.__cntPlayState) { return; }
  window.__cntPlayState = true;

  var lastPlaying = null;
  var lastKey = '';

  function bridge() {
    return (window.CntBridge && window.CntBridge.setPlaying) ? window.CntBridge : null;
  }

  function reportPlaying(playing) {
    if (playing === lastPlaying) { return; }
    lastPlaying = playing;
    try {
      var b = bridge();
      if (b) { b.setPlaying(playing); }
    } catch (e) { /* chua co cau noi */ }
  }

  function videoId() {
    try {
      var u = new URL(location.href);
      var v = u.searchParams.get('v');
      if (v) { return v; }
      var m = u.pathname.match(/^\\/shorts\\/([A-Za-z0-9_-]{11})/);
      return m ? m[1] : '';
    } catch (e) {
      return '';
    }
  }

  function title() {
    try {
      if (typeof window.__cntTitle === 'function') {
        var custom = window.__cntTitle();
        if (custom) { return String(custom).trim(); }
      }
    } catch (e) { /* moc hong */ }

    var sel = [
      'ytm-slim-video-information-renderer h2',
      '.slim-video-information-title',
      'h1.title',
      'ytd-watch-metadata h1'
    ];
    for (var i = 0; i < sel.length; i++) {
      var el = document.querySelector(sel[i]);
      if (el && el.textContent && el.textContent.trim()) {
        return el.textContent.trim();
      }
    }
    var d = (document.title || '').replace(/\\s*[-|]\\s*[^-|]{1,20}\\s*$/, '').trim();
    return laTenTrang(d) ? '' : d;
  }

  function laTenTrang(s) {
    if (!s) { return false; }
    var host = (location.hostname || '').toLowerCase();
    var k = s.toLowerCase().replace(/\\s+/g, '');
    return k.length > 0 && host.indexOf(k) >= 0;
  }

  var tenCuId = '';
  var tenCu = '';

  function titleOnDinh() {
    var id = videoId();
    var t = title();
    if (t) {
      tenCuId = id;
      tenCu = t;
      return t;
    }
    return (id && id === tenCuId) ? tenCu : '';
  }

  function currentVideo() {
    try {
      if (typeof window.__cntVideo === 'function') {
        var v = window.__cntVideo();
        if (v) { return v; }
      }
    } catch (e) { /* moc hong */ }
    return document.querySelector('video');
  }

  function thumb(v) {
    try {
      if (typeof window.__cntThumb === 'function') {
        var custom = window.__cntThumb();
        if (custom) { return String(custom); }
      }
    } catch (e) { /* moc hong */ }
    return '';
  }

  function report() {
    var v = currentVideo();
    if (!v) {
      reportPlaying(false);
      return;
    }
    reportPlaying(!v.paused);

    var b = bridge();
    if (!b || !b.setNowPlaying) { return; }

    var id = videoId();
    var name = titleOnDinh();
    var pos = Math.round((v.currentTime || 0) * 1000);
    var dur = Math.round((isFinite(v.duration) ? v.duration : 0) * 1000);

    var art = thumb(v);

    var key = id + '|' + name + '|' + art + '|' + (!v.paused)
            + '|' + Math.floor(pos / 5000);
    if (key === lastKey) { return; }
    lastKey = key;

    try {
      b.setNowPlaying(id, name, art, pos, dur, !v.paused);
    } catch (e) { /* bo qua */ }
  }

  setInterval(report, 1000);
  report();

  ['seeked', 'loadedmetadata', 'durationchange', 'ended', 'ratechange']
    .forEach(function (name) {
      document.addEventListener(name, function (e) {
        if (!e.target || e.target.tagName !== 'VIDEO') { return; }
        lastKey = '';
        report();
      }, true);
    });

  if (typeof window.__cntNext !== 'function') {
    window.__cntNext = function () { };
  }
  if (typeof window.__cntPrev !== 'function') {
    window.__cntPrev = function () { history.back(); };
  }

  window.__cntSeek = function (ms, absolute) {
    var v = currentVideo();
    if (!v || !isFinite(v.duration)) { return; }
    var target = absolute ? (ms / 1000) : (v.currentTime + ms / 1000);
    v.currentTime = Math.max(0, Math.min(target, v.duration));
    lastKey = '';
    report();
  };
})();
	""";

	public static String injectSource() {
		return "(function() { " +
				"  try { " + TT_CLEAN + " } catch (e) { console.error('TT_CLEAN err', e); } " +
				"  try { " + TT_NAV + " } catch (e) { console.error('TT_NAV err', e); } " +
				"  try { " + PLAY_STATE + " } catch (e) { console.error('PLAY_STATE err', e); } " +
				"})();";
	}

	private TikTokScripts() {
	}
}
