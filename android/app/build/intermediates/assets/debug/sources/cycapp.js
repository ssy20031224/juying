/*
 * 次元城APP（pc.cycback.org）JS 源 —— 海阔「阅动漫」子源移植
 * App JSON 接口 · 真播放（已对真实接口验证 2026-06-28）
 * version: 2.0.0
 *
 * 接口（实测）：
 *   搜索  GET /video/search?text={KW}&pg={page}&type_id=0&limit=20   → {data:[{vod_id,name,pic,remarks,year}]}
 *   分类  GET /video/query?page={page}&limit=20&tid={tid}            tid: 20 TV动画/21 剧场版/26 4K专区/27 国漫
 *   详情  GET /video/info/{vod_id}        → {data:{vod_name,vod_pic,vod_content,vod_class,vod_year,vod_play_from:[{code,name}]}}
 *   选集  GET /video/play_url?id={vod_id}&from={线路code}  → {data:[{name,url,needParse}]}
 *   取流  选集 url 若含 m3u8 直接用；否则 GET 该 url（带 cyc-desktop UA + Referer 自身）→ JSON.url（字节跳动 CDN 真流）
 *   注：同品牌网页版见 cycani.js（cycani.org）。
 */

var EXT  = (typeof ext !== 'undefined' && ext) ? ext : {};
var HOST = (EXT.host || 'https://pc.cycback.org').replace(/\/+$/, '');
var UA_PC = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) cyc-desktop/1.0.8 Chrome/128.0.6613.36 Electron/32.0.1 Safari/537.36';
var TIMEOUT = 15000;

function trim(s) { return s == null ? '' : String(s).replace(/^\s+|\s+$/g, ''); }
function clean(s) {
    if (!s) return '';
    return trim(String(s).replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/[\u3000]+/g, ' '));
}
function guessType(u) {
    var l = (u || '').toLowerCase();
    if (l.indexOf('.m3u8') >= 0) return 'm3u8';
    if (l.indexOf('.mp4') >= 0) return 'mp4';
    if (l.indexOf('.flv') >= 0) return 'flv';
    return 'auto';
}
function api(path, ref) {
    try {
        return request(HOST + path, JSON.stringify({ headers: { 'User-Agent': UA_PC, 'Referer': ref || (HOST + '/') }, timeout: TIMEOUT })) || '';
    } catch (e) { log('[cyc] api err ' + path + ' :: ' + e); return ''; }
}
function mapList(arr) {
    var out = [];
    if (!arr || !arr.length) return out;
    for (var i = 0; i < arr.length; i++) {
        var v = arr[i] || {};
        if (v.vod_id == null) continue;
        out.push({
            id: String(v.vod_id), name: trim(v.name || v.vod_name), pic: v.pic || v.vod_pic || '',
            type: '', year: v.year ? String(v.year) : '', remarks: trim(v.remarks || v.vod_remarks || ''), desc: ''
        });
    }
    return out;
}
function parseData(jsonStr) { var j = parseJson(jsonStr) || {}; return mapList(j.data); }

// ───────────────────────── 契约入口 ─────────────────────────
var CATS = [
    { key: '20', title: 'TV动画' },
    { key: '21', title: '剧场版' },
    { key: '26', title: '4K专区' },
    { key: '27', title: '国漫' }
];
function isTid(k) { return /^\d+$/.test(k); }

function categories() {
    var arr = [{ key: '', title: '推荐' }];
    for (var i = 0; i < CATS.length; i++) arr.push({ key: CATS[i].key, title: CATS[i].title });
    return JSON.stringify(arr);
}

// 首页分区：动画排行榜 + 各分类前 12（点「更多」按 key 跳到对应分类）
function homeSections() {
    var out = [];
    var rank = parseData(api('/rank/video_list?id=1'));
    if (rank.length) out.push({ title: '动画排行榜', key: '20', items: rank.slice(0, 12) });
    for (var i = 0; i < CATS.length; i++) {
        var c = CATS[i];
        var lst = parseData(api('/video/query?page=1&limit=12&tid=' + c.key));
        if (lst.length) out.push({ title: c.title, key: c.key, items: lst.slice(0, 12) });
    }
    return JSON.stringify(out);
}

function search(keyword, page) {
    page = page || 1;
    var key = trim(keyword);
    if (!key) return JSON.stringify(parseData(api('/video/query?page=' + page + '&limit=20&tid=20')));
    if (isTid(key)) return JSON.stringify(parseData(api('/video/query?page=' + page + '&limit=20&tid=' + key)));
    return JSON.stringify(parseData(api('/video/search?text=' + encodeUri(key) + '&pg=' + page + '&type_id=0&limit=20')));
}

function detail(id) {
    var out = { id: id, name: '', pic: '', desc: '', type: '', year: '', remarks: '', episodes: [] };
    var j = parseJson(api('/video/info/' + id)) || {};
    var v = j.data || {};
    if (!v.vod_id && !v.vod_name) return JSON.stringify(out);
    out.name = trim(v.vod_name);
    out.pic  = v.vod_pic || '';
    out.year = v.vod_year ? String(v.vod_year) : '';
    out.type = (v.vod_class || '').split(',').join(' ') || '';
    out.desc = clean(v.vod_content || v.vod_blurb || '');

    var froms = v.vod_play_from || [];
    for (var i = 0; i < froms.length; i++) {
        var code = froms[i].code;
        if (!code) continue;
        var route = trim(froms[i].name) || code;
        var pj = parseJson(api('/video/play_url?id=' + id + '&from=' + encodeUri(code))) || {};
        var eps = pj.data || [];
        for (var e = 0; e < eps.length; e++) {
            if (!eps[e] || !eps[e].url) continue;
            out.episodes.push({ name: trim(eps[e].name) || ('第' + (e + 1) + '集'), url: eps[e].url, route: route });
        }
    }
    return JSON.stringify(out);
}

function play(flag) {
    var res = { url: '', type: 'auto', referer: '' };
    var u = String(flag || '').split('#')[0];
    if (!u) return JSON.stringify(res);
    if (/m3u8/i.test(u)) { res.url = u; res.type = 'm3u8'; return JSON.stringify(res); }
    try {
        var j = parseJson(request(u, JSON.stringify({ headers: { 'User-Agent': UA_PC, 'Referer': u }, timeout: TIMEOUT }))) || {};
        if (j.url) { res.url = j.url; res.type = guessType(j.url); return JSON.stringify(res); }
    } catch (e) { log('[cyc] play err ' + e); }
    if (/\.(mp4|m3u8|flv)/i.test(u)) { res.url = u; res.type = guessType(u); }
    return JSON.stringify(res);
}