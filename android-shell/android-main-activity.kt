package com.wordcraft.app

import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import com.getcapacitor.BridgeActivity
import com.getcapacitor.BridgeWebChromeClient
import com.getcapacitor.BridgeWebViewClient

class MainActivity : BridgeActivity() {

    /** The WebView asks for a file, the activity result delivers it. One at a time. */
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        // A cancelled picker hands back null; the WebView expects null for "nothing was picked".
        callback?.onReceiveValue(uri?.let { arrayOf(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be before super.onCreate: Capacitor reads the plugin list while building the bridge.
        registerPlugin(FileBridge::class.java)
        super.onCreate(savedInstanceState)

        val webView = bridge.webView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        // Capacitor's own clients must survive: the web view client serves the app
        // (shouldInterceptRequest) and answers navigation, and the chrome client
        // answers the JS dialogs. Replacing them with plain ones leaves the WebView
        // with no page at all, so both are extended instead of overwritten.
        bridge.setWebViewClient(
            object : BridgeWebViewClient(bridge) {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    // The app's Save/Export path is a blob URL plus a click on <a download>, which the
                    // WebView ignores. Intercept it and hand the bytes to FileBridge instead.
                    view.evaluateJavascript(INSTALL_FILE_BRIDGE, null)
                }
            },
        )

        webView.webChromeClient = object : BridgeWebChromeClient(bridge) {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                // rfd's file picker is a hidden <input type="file">; without this override the
                // WebView never opens the system picker and the chosen file never arrives.
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = callback
                return try {
                    pickFile.launch(DOCUMENT_TYPES)
                    true
                } catch (e: Exception) {
                    // No document picker on this device: let Capacitor ask instead.
                    pendingFileCallback = null
                    super.onShowFileChooser(view, callback, params)
                }
            }
        }
    }

    override fun onDestroy() {
        // A pending picker must never leave the WebView waiting for a file that
        // will not arrive.
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
        super.onDestroy()
    }

    private companion object {
        /** What the picker offers: every file the app knows how to open. */
        val DOCUMENT_TYPES = arrayOf("*/*")

        val INSTALL_FILE_BRIDGE = """
            (function () {
              if (window.__wordcraftFileBridge) { return; }
              window.__wordcraftFileBridge = true;

              function plugin() {
                var cap = window.Capacitor;
                if (!cap || !cap.Plugins) { return null; }
                return cap.Plugins.FileBridge || null;
              }

              function toBase64(blob) {
                return new Promise(function (resolve, reject) {
                  var reader = new FileReader();
                  reader.onerror = function () { reject(new Error('could not read the file')); };
                  reader.onload = function () {
                    var text = String(reader.result || '');
                    var comma = text.indexOf(',');
                    resolve(comma >= 0 ? text.slice(comma + 1) : '');
                  };
                  reader.readAsDataURL(blob);
                });
              }

              document.addEventListener('click', function (event) {
                var target = event.target;
                if (!target || target.nodeType !== 1 || typeof target.closest !== 'function') { return; }
                var anchor = target.closest('a[download]');
                if (!anchor) { return; }
                var href = anchor.href || '';
                // Only the blob the app just built is ours; every other link is
                // the page's own business.
                if (href.indexOf('blob:') !== 0 && href.indexOf('data:') !== 0) { return; }
                event.preventDefault();
                var name = anchor.getAttribute('download') || 'document';
                var bridge = plugin();
                if (!bridge) {
                  console.error('WordCraft: the FileBridge plugin is missing, ' + name + ' was not saved');
                  return;
                }
                fetch(href)
                  .then(function (response) { return response.blob(); })
                  .then(toBase64)
                  .then(function (data) { return bridge.save({ name: name, data: data }); })
                  .then(function (result) {
                    console.log('WordCraft: saved ' + (result && result.name ? result.name : name));
                  })
                  .catch(function (error) {
                    console.error('WordCraft: saving ' + name + ' failed', error);
                  });
              }, true);
            })();
        """.trimIndent()
    }
}
