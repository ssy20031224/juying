export type SourceRuntime = "native" | "js-worker" | "browser-worker";
export type SourceAdapterKind = "lanerc" | "AuvFun" | "cycapp" | "jinpai" | "sanqiu";

export type Source = {
  key: string;
  title: string;
  codeUrl: string;
  localFile: string;
  enabled: boolean;
  runtime: SourceRuntime;
  adapter?: SourceAdapterKind;
};

// This is the complete inventory extracted from remote_sources/source_urls.json.
// Inventory does not mean live execution: only sources with an audited adapter
// are queried, and remote JS is never evaluated in the request process.
export const SOURCES: Source[] = [
  { key: "AuvFun", title: "AuvFun", codeUrl: "https://js.z1i.cn/js/AuvFun.js", localFile: "AuvFun.js", enabled: true, runtime: "native", adapter: "AuvFun" },
  { key: "lanerc", title: "Lanerc", codeUrl: "https://js.z1i.cn/js/lanerc_legacy.js", localFile: "lanerc.js", enabled: true, runtime: "native", adapter: "lanerc" },
  { key: "jinpai", title: "金牌", codeUrl: "https://js.z1i.cn/js/jinpaiapp.js", localFile: "jinpai.js", enabled: true, runtime: "native", adapter: "jinpai" },
  { key: "cycapp", title: "次元城", codeUrl: "https://js.z1i.cn/js/cyc.js", localFile: "cycapp.js", enabled: true, runtime: "native", adapter: "cycapp" },
  { key: "guazi", title: "瓜子", codeUrl: "https://js.z1i.cn/js/guazi.js", localFile: "guazi.js", enabled: true, runtime: "js-worker" },
  { key: "shuangxing", title: "双星", codeUrl: "https://js.z1i.cn/js/shuangxing99.js", localFile: "shuangxing.js", enabled: true, runtime: "js-worker" },
  { key: "xifanacg", title: "稀饭动漫", codeUrl: "https://js.z1i.cn/js/xifanacg.js", localFile: "xifanacg.js", enabled: true, runtime: "browser-worker" },
  { key: "yzx", title: "云帆", codeUrl: "https://js.z1i.cn/js/yzx.js", localFile: "yzx.js", enabled: true, runtime: "js-worker" },
  { key: "sanqiu", title: "三秋", codeUrl: "https://js.z1i.cn/js/sanqiu.js", localFile: "sanqiu.js", enabled: true, runtime: "native", adapter: "sanqiu" },
  { key: "akianime", title: "AkiAnime", codeUrl: "https://js.z1i.cn/js/akianime.js", localFile: "akianime.js", enabled: true, runtime: "browser-worker" },
  { key: "lmm85", title: "动漫在线", codeUrl: "https://js.z1i.cn/js/lmm85.js", localFile: "lmm85.js", enabled: true, runtime: "browser-worker" },
  { key: "gugu", title: "咕咕动漫", codeUrl: "https://js.z1i.cn/js/gugu.js", localFile: "gugu.js", enabled: true, runtime: "js-worker" },
  { key: "dmbus", title: "动漫巴士", codeUrl: "https://js.z1i.cn/js/dmbus.js", localFile: "dmbus.js", enabled: true, runtime: "browser-worker" },
];
