package com.wordcraft.app

import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {

    /** The WebView asks for a file, the activity result delivers it. One at a time. */
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uris ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.onReceiveValue(uris?.takeIf { it.isNotEmpty() }?.toTypedArray() ?: emptyArray())
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
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // The app's Save/Export path is a blob URL plus a click on <a download>, which the
                // WebView ignores. Intercept it and hand the bytes to FileBridge instead.
                view.evaluateJavascript(INSTALL_FILE_BRIDGE, null)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                // rfd's file picker is a hidden <input type="file">; without this override the
                // WebView never opens the system picker and the chosen file never arrives.
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = callback
                pickFile.launch(arrayOf("*/*"))
                return true
            }
        }
    }

    private companion object {
        val INSTALL_FILE_BRIDGE = """
            (function () {
              if (window.__wordcraftFileBridge) { return; }
              window.__wordcraftFileBridge = true;
              document.addEventListener('click', function (event) {
                var target = event.target;
                if (!target || typeof target.closest !== 'function') { return; }
                var anchor = target.closest('a[download]');
                if (!anchor || !anchor.href) { return; }
                event.preventDefault();
                var name = anchor.getAttribute('download') || 'document';
                fetch(anchor.href)
                  .then(function (response) { return response.blob(); })
                  .then(function (blob) {
                    return new Promise(function (resolve) {
                      var reader = new FileReader();
                      reader.onload = function () {
                        resolve(String(reader.result).split(',')[1] || '');
                      };
                      reader.readAsDataURL(blob);
                    });
                  })
                  .then(function (base64) {
                    if (window.Capacitor && Capacitor.Plugins && Capacitor.Plugins.FileBridge) {
                      Capacitor.Plugins.FileBridge.save({ name: name, data: base64 });
                    }
                  })
                  .catch(function (error) { console.error('WordCraft save failed', error); });
              }, true);
            })();
        """.trimIndent()
    }
}
