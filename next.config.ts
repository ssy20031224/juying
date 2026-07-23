import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  outputFileTracingIncludes: {
    "/*": ["./config/source-scripts/**/*", "./public/source-scripts/**/*"],
  },
};

export default nextConfig;
