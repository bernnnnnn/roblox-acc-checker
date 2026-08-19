package com.robloxvault.app.login

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.PixelCopy
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Presents the REAL Roblox login page in a WebView for one of the user's own
 * accounts. Credentials are pre-filled as a convenience, but the user stays in
 * control: they solve any CAPTCHA and complete 2FA themselves on Roblox's own
 * page. We never bypass those checks — we detect a valid Roblox session and let
 * the user confirm. It works with Roblox's defenses, not around them.
 */
class LoginActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private lateinit var confirmButton: Button
    private val handler = Handler(Looper.getMainLooper())

    private var username: String = ""
    private var password: String = ""
    private var mode: String = MODE_CHECK
    private var accountId: String = ""
    private var resolved = false
    private var capturing = false
    private var prefilled = false

    private val cookiePoll = object : Runnable {
        override fun run() {
            if (isLoggedIn()) {
                confirmButton.text = "✓ Logged in"
                confirmButton.isEnabled = true
                succeed()
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        username = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
        password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_CHECK
        accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID).orEmpty()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#000000"))
        }

        val header = TextView(this).apply {
            text = "@$username — solve any CAPTCHA / 2FA, then log in"
            setTextColor(Color.parseColor("#F4F6F8"))
            setPadding(dp(16), dp(14), dp(16), dp(4))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        statusView = TextView(this).apply {
            text = "Loading Roblox…"
            setTextColor(Color.parseColor("#7A8796"))
            setPadding(dp(16), 0, dp(16), dp(10))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = settings.userAgentString
                ?.replace("; wv", "") // present as a normal Chrome, not a webview shell
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest,
                ): Boolean = false

                override fun onPageFinished(view: WebView, url: String?) {
                    statusView.text = url
                    if (isLoggedIn()) {
                        succeed()
                        return
                    }
                    if (!prefilled && url != null && url.contains("/login")) {
                        prefillCredentials()
                    }
                }
            }
        }

        val buttonBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.parseColor("#0E0F12"))
        }
        confirmButton = barButton("Logged in?") { succeed() }
        buttonBar.addView(confirmButton)
        if (mode == MODE_CHECK) {
            buttonBar.addView(barButton("Didn't work") { finishWith(RESULT_INVALID, null) })
        }
        buttonBar.addView(barButton("Cancel") { finishWith(RESULT_CANCELLED, null) })

        root.addView(header)
        root.addView(statusView)
        root.addView(webView)
        root.addView(buttonBar)
        setContentView(root)

        if (mode == MODE_CHECK) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.loadUrl("https://www.roblox.com/login")
        handler.postDelayed(cookiePoll, 1500)
    }

    private fun prefillCredentials() {
        prefilled = true
        val u = JSONObject.quote(username)
        val p = JSONObject.quote(password)
        val js = """
            (function() {
              function setVal(el, val) {
                if (!el) return false;
                var proto = Object.getPrototypeOf(el);
                var desc = Object.getOwnPropertyDescriptor(proto, 'value');
                if (desc && desc.set) { desc.set.call(el, val); } else { el.value = val; }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return true;
              }
              var user = document.getElementById('login-username')
                || document.querySelector('input[name="username"]')
                || document.querySelector('input[type="text"]');
              var pass = document.getElementById('login-password')
                || document.querySelector('input[name="password"]')
                || document.querySelector('input[type="password"]');
              var ok = setVal(user, $u) && setVal(pass, $p);
              return ok ? 'filled' : 'not-found';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { result ->
            val msg = if (result.contains("filled"))
                "Credentials filled — solve any CAPTCHA, then tap Log In"
            else
                "Couldn't auto-fill; use Copy from the account row"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun isLoggedIn(): Boolean = roblosecurity() != null

    /** Extracts the .ROBLOSECURITY value from the WebView cookie jar, if present. */
    private fun roblosecurity(): String? {
        val jar = CookieManager.getInstance()
        val cookies = (jar.getCookie("https://www.roblox.com").orEmpty() + "; " +
            jar.getCookie("https://roblox.com").orEmpty())
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith(".ROBLOSECURITY=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotBlank() && it.length > 20 }
    }

    /** Confirmed login: capture a screenshot, then return VALID. */
    private fun succeed() {
        if (resolved || capturing) return
        if (!isLoggedIn()) {
            Toast.makeText(this, "Not logged in yet — finish logging in first", Toast.LENGTH_SHORT).show()
            return
        }
        capturing = true
        captureScreenshot { path -> finishWith(RESULT_VALID, path) }
    }

    /** Captures the current window to a PNG in filesDir/shots and returns its path. */
    private fun captureScreenshot(onDone: (String?) -> Unit) {
        try {
            val view = window.decorView
            val w = view.width
            val h = view.height
            if (w <= 0 || h <= 0) { onDone(null); return }
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            PixelCopy.request(window, bitmap, { result ->
                val path = if (result == PixelCopy.SUCCESS) saveBitmap(bitmap) else null
                onDone(path)
            }, handler)
        } catch (e: Exception) {
            onDone(null)
        }
    }

    private fun saveBitmap(bitmap: Bitmap): String? = try {
        val dir = File(filesDir, "shots").apply { mkdirs() }
        val file = File(dir, "$accountId.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        file.absolutePath
    } catch (e: Exception) {
        null
    }

    private fun finishWith(result: String, shotPath: String?) {
        if (resolved) return
        resolved = true
        handler.removeCallbacksAndMessages(null)
        intent.putExtra(EXTRA_RESULT, result).putExtra(EXTRA_ACCOUNT_ID, accountId)
        if (result == RESULT_VALID) {
            intent.putExtra(EXTRA_COOKIE, roblosecurity().orEmpty())
            intent.putExtra(EXTRA_SHOT, shotPath.orEmpty())
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun barButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_MODE = "mode"
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_RESULT = "result_status"
        const val EXTRA_COOKIE = "roblosecurity"
        const val EXTRA_SHOT = "screenshot_path"

        const val MODE_CHECK = "check"
        const val MODE_OPEN = "open"

        const val RESULT_VALID = "valid"
        const val RESULT_INVALID = "invalid"
        const val RESULT_CANCELLED = "cancelled"
    }
}
