import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.wordcraft.app",
  appName: "WordCraft",
  // Capacitor writes this into the Android manifest as `versionName`; without it
  // the APK ships as "1.0" regardless of the workspace version.
  version: "0.8.0",
  webDir: "www",
  server: {
    androidScheme: "https",
  },
  android: {
    allowMixedContent: false,
  },
};

export default config;