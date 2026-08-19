package com.robloxvault.app.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.robloxvault.app.R
import com.robloxvault.app.data.RobloxApi
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Opens Roblox's REAL login page, visible, with the username/password
 * pre-filled. The USER taps Log In themselves and completes any CAPTCHA / 2FA /
 * unlock — this behaves like a normal person logging in, which avoids the
 * automated-login lockouts. Once a genuinely valid (unlocked) session is
 * detected, it captures the home page as a screenshot and stores the session.
 * No auto-submitting, no bypassing of any verification.
 */
class LoginActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var splash: View
    private lateinit var splashText: TextView
    private lateinit var statusView: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var username = ""
    private var password = ""
    private var mode = MODE_CHECK
    private var accountId = ""
    private var startCookie = ""

    private var resolved = false
    private var prefilled = false
    private var verifying = false
    private var loggedInHandled = false

    private val poll = object : Runnable {
        override fun run() {
            if (resolved) return
            if (mode == MODE_CHECK && isLoggedIn() && !loggedInHandled && !verifying) {
                attemptVerify(manual = false)
            }
            handler.postDelayed(this, 1000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        username = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
        password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_CHECK
        accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID).orEmpty()
        startCookie = intent.getStringExtra(EXTRA_COOKIE).orEmpty()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#000000")) }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = settings.userAgentString?.replace("; wv", "")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val url = r.url.toString()
                    if (url.startsWith("http://") || url.startsWith("https://")) return false
                    return openExternal(url)
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    if (resolved) return
                    if (mode == MODE_CHECK && isLoggedIn() && !loggedInHandled && !verifying) {
                        attemptVerify(manual = false)
                        return
                    }
                    if (mode == MODE_CHECK && !prefilled && url != null && url.contains("/login")) {
                        prefill()
                    }
                }
            }
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0E0F12"))
            setPadding(dp(12), dp(6), dp(8), dp(6))
        }
        statusView = TextView(this).apply {
            text = if (mode == MODE_OPEN) "@$username" else "Tap Log In to finish"
            setTextColor(Color.parseColor("#C7D2DE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        bar.addView(statusView)
        if (mode == MODE_CHECK) {
            bar.addView(barButton("Done") { attemptVerify(manual = true) })
            bar.addView(barButton("Didn't work") { finishWith(RESULT_INVALID, null) })
        }
        bar.addView(barButton("Close") { finishWith(RESULT_CANCELLED, null) })

        column.addView(webView)
        column.addView(bar)

        splash = buildSplash()
        splash.visibility = View.GONE

        root.addView(column)
        root.addView(splash)
        setContentView(root)

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)

        if (mode == MODE_OPEN) {
            if (startCookie.isNotBlank()) {
                cm.setCookie("https://www.roblox.com", roblosecurityCookie(startCookie)) {
                    webView.loadUrl("https://www.roblox.com/home")
                }
            } else {
                webView.loadUrl("https://www.roblox.com/login")
            }
            return
        }

        // CHECK: clear only the session cookie (keep browser-trust cookies so the
        // device stays "known"), then show the login page for the user to log in.
        val expire = "=; Max-Age=0; Path=/; Domain=.roblox.com"
        cm.setCookie("https://www.roblox.com", ".ROBLOSECURITY$expire")
        cm.setCookie("https://roblox.com", ".ROBLOSECURITY$expire") {
            cm.flush()
            webView.loadUrl("https://www.roblox.com/login")
        }
        handler.postDelayed(poll, 1500)
    }

    private fun buildSplash(): View {
        val frame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.parseColor("#000000"))
            isClickable = true
        }
        val bg = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.app_bg)
            alpha = 0.42f
        }
        val scrim = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.parseColor("#B3000000"))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        column.addView(android.widget.ProgressBar(this))
        splashText = TextView(this).apply {
            text = "Saving…"
            setTextColor(Color.parseColor("#F4F6F8"))
            setPadding(dp(24), dp(16), dp(24), 0)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        column.addView(splashText)
        frame.addView(bg)
        frame.addView(scrim)
        frame.addView(column)
        return frame
    }

    /** Pre-fills the fields only — the user taps Log In themselves. */
    private fun prefill() {
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
              return (setVal(user, $u) && setVal(pass, $p)) ? 'filled' : 'nofields';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { r ->
            statusView.text = if (r != null && r.contains("filled"))
                "Filled — tap Log In"
            else
                "Log in with your details"
        }
    }

    /** Verifies the cookie is a real, unlocked session before treating it as success. */
    private fun attemptVerify(manual: Boolean) {
        val cookie = roblosecurity()
        if (cookie == null) {
            if (manual) Toast.makeText(this, "Not logged in yet — tap Log In first", Toast.LENGTH_SHORT).show()
            return
        }
        if (verifying || loggedInHandled) return
        verifying = true
        if (manual) statusView.text = "Checking…"
        Thread {
            val ok = RobloxApi.isValidSession(cookie)
            runOnUiThread {
                verifying = false
                if (resolved) return@runOnUiThread
                if (ok) onLoggedIn()
                else if (manual) Toast.makeText(this, "Not verified yet — finish the login / unlock on the page", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun onLoggedIn() {
        if (loggedInHandled || resolved) return
        loggedInHandled = true
        coverWithSplash("Saving profile…")
        webView.loadUrl("https://www.roblox.com/home")
        handler.postDelayed({ captureThenFinish() }, 3200)
    }

    private fun captureThenFinish() {
        if (resolved) return
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        handler.postDelayed({
            val path = drawWebViewToFile()
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            finishWith(RESULT_VALID, path)
        }, 450)
    }

    private fun drawWebViewToFile(): String? = try {
        val w = webView.width
        val h = webView.height
        if (w <= 0 || h <= 0) null else {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            webView.draw(Canvas(bmp))
            val dir = File(filesDir, "shots").apply { mkdirs() }
            val file = File(dir, "$accountId.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            bmp.recycle()
            file.absolutePath
        }
    } catch (e: Exception) {
        null
    }

    private fun coverWithSplash(message: String) {
        splashText.text = message
        splash.visibility = View.VISIBLE
    }

    /** Hands non-web URLs (intent://, roblox://…) to the system, e.g. the unlock deep link. */
    private fun openExternal(url: String): Boolean {
        if (url.startsWith("intent://")) {
            val intent = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()
            if (intent != null) {
                val fallback = intent.getStringExtra("browser_fallback_url")
                if (tryStart(intent)) return true
                intent.`package` = null
                if (tryStart(intent)) return true
                if (fallback != null) { webView.loadUrl(fallback); return true }
            }
            Toast.makeText(this, "Open the Roblox app to finish the account unlock", Toast.LENGTH_LONG).show()
            return true
        }
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (!tryStart(view)) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent); true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: Exception) {
        false
    }

    private fun isLoggedIn(): Boolean = roblosecurity() != null

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

    private fun roblosecurityCookie(value: String): String =
        ".ROBLOSECURITY=$value; Domain=.roblox.com; Path=/"

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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
