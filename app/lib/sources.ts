export type SourceRuntime = "native" | "js-worker" | "browser-worker";
export type SourceAdapterKind = "lanerc" | "AuvFun" | "cycapp" | "jinpai" | "sanqiu" | "yzx" | "xifanacg" | "gugu" | "guazi" | "shuangxing" | "akianime" | "lmm85" | "dmbus" | "shutiao";

export type Source = {
  key: string;
  title: string;
  codeUrl: string;
  localFile: string;
  enabled: boolean;
  runtime: SourceRuntime;
  adapter?: SourceAdapterKind;
};

export const SOURCES: Source[] = [
  { key: "AuvFun", title: "AuvFun", codeUrl: "https://js.z1i.cn/js/AuvFun.js", localFile: "AuvFun.js", enabled: true, runtime: "js-worker" },
  { key: "lanerc", title: "Lanerc", codeUrl: "https://js.z1i.cn/js/lanerc_legacy.js", localFile: "lanerc.js", enabled: true, runtime: "js-worker" },
  { key: "jinpai", title: "金牌", codeUrl: "https://js.z1i.cn/js/jinpaiapp.js", localFile: "jinpai.js", enabled: true, runtime: "native", adapter: "jinpai" },
  { key: "cycapp", title: "次元城", codeUrl: "https://js.z1i.cn/js/cyc.js", localFile: "cycapp.js", enabled: true, runtime: "native", adapter: "cycapp" },
  { key: "guazi", title: "瓜子", codeUrl: "https://js.z1i.cn/js/guazi.js", localFile: "guazi.js", enabled: true, runtime: "js-worker" },
  { key: "shuangxing", title: "双星", codeUrl: "https://js.z1i.cn/js/shuangxing99.js", localFile: "shuangxing.js", enabled: true, runtime: "js-worker" },
  { key: "xifanacg", title: "稀饭动漫", codeUrl: "https://js.z1i.cn/js/xifanacg.js", localFile: "xifanacg.js", enabled: true, runtime: "js-worker" },
  { key: "yzx", title: "云帆", codeUrl: "https://js.z1i.cn/js/yzx.js", localFile: "yzx.js", enabled: false, runtime: "js-worker" },
  { key: "sanqiu", title: "三秋", codeUrl: "https://js.z1i.cn/js/sanqiu.js", localFile: "sanqiu.js", enabled: true, runtime: "native", adapter: "sanqiu" },
  { key: "akianime", title: "AkiAnime", codeUrl: "https://js.z1i.cn/js/akianime.js", localFile: "akianime.js", enabled: false, runtime: "js-worker" },
  { key: "lmm85", title: "动漫在线", codeUrl: "https://js.z1i.cn/js/lmm85.js", localFile: "lmm85.js", enabled: false, runtime: "js-worker" },
  { key: "gugu", title: "咕咕动漫", codeUrl: "https://js.z1i.cn/js/gugu.js", localFile: "gugu.js", enabled: true, runtime: "js-worker" },
  { key: "dmbus", title: "动漫巴士", codeUrl: "https://js.z1i.cn/js/dmbus.js", localFile: "dmbus.js", enabled: false, runtime: "js-worker" },
  { key: "shutiao", title: "薯条", codeUrl: "https://js.z1i.cn/js/shutiao.js", localFile: "shutiao.js", enabled: true, runtime: "js-worker" },
];
