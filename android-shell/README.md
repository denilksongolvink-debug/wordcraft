# WordCraft Android Shell

Capacitor WebView shell for the WordCraft web (WASM) build.

## Setup

```bash
cd android-shell
npm install
npx cap add android
```

## Build

The web build is copied to `www/` by the CI workflow. To do it locally:

```bash
cd ../apps/wordcraft-web
trunk build --release
cd ../../android-shell
cp -r ../../dist/web/* www/
npx cap sync android
```

## DeX

The AndroidManifest.xml includes:
- `android:resizeableActivity="true"`
- `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden|density|uiMode"`
- `android:windowSoftInputMode="adjustResize"`

These prevent the app from restarting on window resize in DeX.

## File bridge

Saving uses the browser download fallback (blob → anchor). On Android WebView,
intercept `<a download>` clicks and save via `@capacitor/filesystem` + `@capacitor/share`.
Implementation: `android/app/src/main/java/com/wordcraft/app/MainActivity.kt`