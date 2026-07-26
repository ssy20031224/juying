import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "聚映 · 多源观影",
  description: "聚合已获授权来源的影片元数据与临时播放入口，不保存影片文件。",
  icons: { icon: "/favicon.svg", shortcut: "/favicon.svg" },
  manifest: "/manifest.webmanifest",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="zh-CN"><body>{children}</body></html>;
}
