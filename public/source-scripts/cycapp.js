


var APP_NAME = 'cyc_android';
var TXT_DOH  = [
    'https://dns.alidns.com/resolve?name=newapp.cycapp.org&type=txt',
    'https://doh.pub/dns-query?name=newapp.cycapp.org&type=txt'
];
var BASES    = [
    'https://mapi.babel.gold',
    'https://mapi.cycback.org'
];
var UA       = 'okhttp/4.12.0';

var RESOLVED_HOST = '';
var ZONES_CACHE   = null;


function trim(s) {
    return s == null ? '' : String(s).replace(/^\s+|\s+$/g, '');
}


function rstrip(s) {
    return trim(s).replace(/\/+$/, '');
}


function jparse(s) {
    if (typeof parseJson === 'function') return parseJson(s);
    return JSON.parse(String(s));
}


function buildQuery(obj) {
    var keys = [], out = [];
    for (var k in obj) if (obj.hasOwnProperty(k) && obj[k] != null && obj[k] !== '') keys.push(k);
    keys.sort();
    for (var i = 0; i < keys.length; i++) {
        var key = keys[i];
        out.push(encodeURIComponent(key) + '=' + encodeURIComponent(String(obj[key])));
    }
    return out.join('&');
}


function hdr() {
    return {
        'User-Agent': UA,
        'Accept': 'application/json, text/plain, */*',
        'X-App-Name': APP_NAME
    };
}


function clean(s) {
    if (s == null) return '';
    return trim(String(s)
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/g, ' ')
        .replace(/&amp;/g, '&')
        .replace(/&quot;/g, '"')
        .replace(/&#0?39;/g, "'")
        .replace(/&#x27;/gi, "'")
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>'));
}


function normalizePic(url) {
    url = trim(url).replace(/&amp;/gi, '&');
    if (!url) return '';

    var original = url;
    var isBaiduProxy = /^https?:\/\/gimg\d*\.baidu\.com\/gimg\//i.test(url);
    if (isBaiduProxy) {
        var m = /[?&]src=([^&]+)/i.exec(url);
        if (m && m[1]) {
            url = m[1];
            
            for (var i = 0; i < 2 && /%[0-9a-f]{2}/i.test(url); i++) {
                try {
                    var decoded = decodeURIComponent(url);
                    if (decoded === url) break;
                    url = decoded;
                } catch (e) {
                    break;
                }
            }
        }
    }

    url = trim(url);
    if (/^\/\//.test(url)) return 'https:' + url;
    if (/^[a-z][a-z0-9+.-]*:\/\//i.test(url)) return url;
    if (/^[\w.-]+\.[a-z]{2,}(?::\d+)?\//i.test(url)) return 'https://' + url;

    
    if (isBaiduProxy) return original;
    return url;
}


function guessType(url) {
    url = trim(url).toLowerCase();
    if (url.indexOf('.m3u8') >= 0) return 'm3u8';
    if (url.indexOf('.mp4') >= 0) return 'mp4';
    if (url.indexOf('.flv') >= 0) return 'flv';
    return 'auto';
}


function pushHost(list, seen, url) {
    var h = rstrip(url);
    if (!h || seen[h]) return;
    seen[h] = 1;
    list.push(h);
}


function extractTxtHost(raw) {
    var s = trim(raw);
    if (!s || s.charAt(0) !== '{') return '';
    try {
        var j = jparse(s);
        var ans = j.Answer || [];
        for (var i = 0; i < ans.length; i++) {
            var data = trim((ans[i] || {}).data);
            if (!data) continue;
            data = data.replace(/^"+|"+$/g, '');
            if (/^https?:\/\//i.test(data)) return rstrip(data);
        }
    } catch (e) {}
    return '';
}


function candidateHosts() {
    var out = [], seen = {}, i, raw, txtHost;

    for (i = 0; i < TXT_DOH.length; i++) {
        try {
            raw = request(TXT_DOH[i], JSON.stringify({ headers: hdr(), timeout: 8000 })) || '';
            txtHost = extractTxtHost(raw);
            if (txtHost) pushHost(out, seen, txtHost);
        } catch (e) {}
    }

    for (i = 0; i < BASES.length; i++) pushHost(out, seen, BASES[i]);

    try {
        if (typeof ext === 'string' && ext.indexOf('http') >= 0) {
            var arr = ext.split(',');
            for (i = 0; i < arr.length; i++) pushHost(out, seen, arr[i]);
        } else if (ext && ext.hosts && ext.hosts.length) {
            for (i = 0; i < ext.hosts.length; i++) pushHost(out, seen, ext.hosts[i]);
        } else if (ext && ext.host) {
            pushHost(out, seen, ext.host);
        }
    } catch (e2) {}

    return out;
}


function parseResp(raw) {
    var s = trim(raw);
    if (!s) return null;
    if (s.charAt(0) === '{' || s.charAt(0) === '[') {
        try { return jparse(s); } catch (e) { return { code: -1, msg: String(e), raw: s.substr(0, 200) }; }
    }
    return { code: -1, msg: 'unexpected response', raw: s.substr(0, 200) };
}


function verifyHost(h) {
    try {
        var raw = request(rstrip(h) + '/index/recommend', JSON.stringify({ headers: hdr(), timeout: 10000 })) || '';
        var j = parseResp(raw) || {};
        return j.code === 0 && j.data && j.data.list && j.data.list.length >= 1;
    } catch (e) {
        return false;
    }
}


function host() {
    if (RESOLVED_HOST) return RESOLVED_HOST;
    var list = candidateHosts();
    for (var i = 0; i < list.length; i++) {
        if (verifyHost(list[i])) {
            RESOLVED_HOST = rstrip(list[i]);
            return RESOLVED_HOST;
        }
    }
    RESOLVED_HOST = rstrip(BASES[0]);
    return RESOLVED_HOST;
}


function callApi(path, query) {
    var p = (path || '').charAt(0) === '/' ? path : ('/' + path);
    var qs = buildQuery(query || {});
    var url = host() + p + (qs ? ('?' + qs) : '');
    var raw = request(url, JSON.stringify({ headers: hdr(), timeout: 15000 })) || '';
    return parseResp(raw);
}


function zoneTitle(zoneId) {
    var zones = fetchZonesCached();
    for (var i = 0; i < zones.length; i++) {
        if (String(zones[i].id) === String(zoneId)) return zones[i].name;
    }
    return '';
}


function mapVideo(v, zoneName) {
    if (!v) return null;
    var id = String(v.video_id || v.id || '');
    var title = clean(v.title || '');
    if (!id || !title) return null;
    return {
        id: id,
        name: title,
        pic: normalizePic(v.cover_url || v.cover || ''),
        type: zoneName || zoneTitle(v.zone_id) || clean(v.area || ''),
        year: v.year ? String(v.year) : '',
        remarks: clean(v.remarks || ''),
        desc: clean(v.description || '')
    };
}


function mapList(arr, zoneName) {
    var out = [], seen = {};
    if (!arr) return out;
    for (var i = 0; i < arr.length; i++) {
        var item = mapVideo(arr[i], zoneName);
        if (!item || seen[item.id]) continue;
        seen[item.id] = 1;
        out.push(item);
    }
    return out;
}


function fetchZonesCached() {
    if (ZONES_CACHE) return ZONES_CACHE;
    var j = callApi('/video-zones') || {};
    var arr = (j.data && j.data.list) || [];
    var out = [];
    for (var i = 0; i < arr.length; i++) {
        var z = arr[i] || {};
        if (!z.id || !z.name) continue;
        out.push({
            id: z.id,
            name: String(z.name),
            filters: z.filters || {}
        });
    }
    ZONES_CACHE = out;
    return ZONES_CACHE;
}


function defaultZoneId() {
    var zones = fetchZonesCached();
    return zones.length ? String(zones[0].id) : '1';
}


function fetchHomeGroups() {
    var j = callApi('/index/recommend') || {};
    return (j.data && j.data.list) || [];
}


function fetchZoneVideos(zoneId, page, filters) {
    var q = {
        zone_id: zoneId,
        page: page || 1
    };
    filters = filters || {};
    if (filters.category) q.category = filters.category;
    if (filters.year) q.year = filters.year;
    var j = callApi('/videos', q) || {};
    return mapList((j.data && j.data.list) || [], zoneTitle(zoneId));
}


function searchByKeyword(keyword, page) {
    var j = callApi('/videos/search', {
        q: keyword,
        page: page || 1
    }) || {};
    return mapList((j.data && j.data.list) || []);
}


function fetchSectionsAll(videoId, playerCode) {
    var out = [], seen = {}, page = 1, guard = 0;
    while (guard++ < 20) {
        var j = callApi('/videos/' + videoId + '/sections', {
            player_code: playerCode,
            page: page
        }) || {};
        var data = j.data || {};
        var list = data.list || [];
        if (!list.length) break;
        for (var i = 0; i < list.length; i++) {
            var sec = list[i] || {};
            var sid = String(sec.id || '');
            if (!sid || seen[sid]) continue;
            seen[sid] = 1;
            out.push({
                id: sid,
                title: clean(sec.title || ('第' + (out.length + 1) + '集'))
            });
        }
        var pager = data.pager || {};
        var total = parseInt(pager.total || 0, 10);
        var size = parseInt(pager.page_size || list.length || 1, 10);
        if (!total || out.length >= total || list.length < size) break;
        page += 1;
    }
    return out;
}


function buildZoneFilters(filters) {
    var out = [], cats = filters.categories || [], years = filters.years || [];
    var catValues = [{ n: '全部', v: '' }];
    var yearValues = [{ n: '全部', v: '' }];

    for (var i = 0; i < cats.length; i++) {
        var c = trim(cats[i]);
        if (!c || c === '分类资源不代表全部资源') continue;
        catValues.push({ n: c, v: c });
    }
    for (var j = 0; j < years.length; j++) {
        var y = trim(years[j]);
        if (!y) continue;
        yearValues.push({ n: y, v: y });
    }

    if (catValues.length > 1) out.push({ key: 'category', name: '分类', value: catValues });
    if (yearValues.length > 1) out.push({ key: 'year', name: '年份', value: yearValues });
    return out;
}


function categories() {
    var zones = fetchZonesCached();
    var out = [{ key: '', title: '推荐' }];
    for (var i = 0; i < zones.length; i++) {
        out.push({
            key: String(zones[i].id),
            title: zones[i].name,
            filters: buildZoneFilters(zones[i].filters || {})
        });
    }
    return JSON.stringify(out);
}


function homeSections() {
    var groups = fetchHomeGroups();
    var out = [];
    for (var i = 0; i < groups.length; i++) {
        var g = groups[i] || {};
        var items = mapList(g.videos || [], g.name || '');
        if (!items.length) continue;
        out.push({
            title: clean(g.name || '推荐'),
            key: g.zone_id ? String(g.zone_id) : '',
            items: items
        });
    }
    return JSON.stringify(out);
}


function search(keyword, page) {
    var q = trim(keyword);
    page = page || 1;
    if (!q) return JSON.stringify(fetchZoneVideos(defaultZoneId(), page, {}));
    if (/^\d+$/.test(q)) return JSON.stringify(fetchZoneVideos(q, page, {}));
    return JSON.stringify(searchByKeyword(q, page));
}


function searchFiltered(category, filtersJson, page) {
    var zoneId = trim(category);
    page = page || 1;
    if (!zoneId) return search('', page);

    var filters = {};
    if (filtersJson) {
        try { filters = jparse(filtersJson) || {}; } catch (e) {}
    }

    return JSON.stringify(fetchZoneVideos(zoneId, page, {
        category: trim(filters.category || ''),
        year: trim(filters.year || '')
    }));
}


function detail(id) {
    var videoId = trim(id);
    var j = callApi('/videos/' + videoId) || {};
    var d = j.data || {};
    var out = {
        id: videoId,
        name: clean(d.title || ''),
        pic: normalizePic(d.cover_url || ''),
        desc: clean(d.description || ''),
        type: zoneTitle(d.zone_id),
        remarks: clean(d.remarks || ''),
        year: d.year ? String(d.year) : '',
        actor: (d.actor && d.actor.join) ? clean(d.actor.join(' / ')) : clean(d.actor || ''),
        director: (d.director && d.director.join) ? clean(d.director.join(' / ')) : clean(d.director || ''),
        episodes: []
    };

    var playFrom = d.play_from || [];
    for (var i = 0; i < playFrom.length; i++) {
        var pf = playFrom[i] || {};
        var code = trim(pf.code);
        if (!code) continue;
        var route = clean(pf.title || code);
        var secs = fetchSectionsAll(videoId, code);
        for (var j2 = 0; j2 < secs.length; j2++) {
            out.episodes.push({
                name: secs[j2].title,
                url: secs[j2].id + '@' + code,
                route: route
            });
        }
    }
    return JSON.stringify(out);
}


function play(flag) {
    var parts = String(flag || '').split('@');
    var sectionId = trim(parts[0]);
    var out = { url: '', type: 'auto' };
    if (!sectionId) {
        out._note = 'missing sectionId';
        return JSON.stringify(out);
    }

    var j = callApi('/sections/' + sectionId + '/play-url') || {};
    var d = j.data || {};
    var url = trim(d.url || '');
    if (!url) {
        out._server_msg = j.msg || 'empty play url';
        out._server_code = j.code;
        return JSON.stringify(out);
    }
    out.url = url;
    out.type = guessType(url);
    return JSON.stringify(out);
}


function related(id) {
    var j = callApi('/videos/' + trim(id) + '/recommendations') || {};
    return JSON.stringify(mapList((j.data && j.data.list) || []));
}


if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        categories: categories,
        homeSections: homeSections,
        search: search,
        searchFiltered: searchFiltered,
        detail: detail,
        play: play,
        related: related,
        _internal: {
            host: host,
            callApi: callApi,
            fetchSectionsAll: fetchSectionsAll,
            fetchZonesCached: fetchZonesCached
        }
    };
}
