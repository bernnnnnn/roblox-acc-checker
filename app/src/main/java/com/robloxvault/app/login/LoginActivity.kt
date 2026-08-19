package com.robloxvault.app.login

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.robloxvault.app.R
import com.robloxvault.app.data.RobloxApi
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Logs into one of the user's own accounts on Roblox's REAL login page, but
 * keeps the web page hidden behind a themed splash. It auto-fills and submits
 * the form; the web page is only revealed if a CAPTCHA / 2FA / error actually
 * needs the user. After login it loads the account's home page (still behind the
 * splash) and captures that as the screenshot. No bypassing, no bulk, no proxies.
 */
class LoginActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var splash: View
    private lateinit var statusView: TextView
    private lateinit var solveButton: Button
    private lateinit var invalidButton: Button
    private val handler = Handler(Looper.getMainLooper())

    private var username = ""
    private var password = ""
    private var mode = MODE_CHECK
    private var accountId = ""
    private var startCookie = ""

    private var resolved = false
    private var revealed = false
    private var submitted = false
    private var loggedInHandled = false
    private var verifying = false
    private var attemptStart = 0L

    private val poll = object : Runnable {
        override fun run() {
            if (resolved) return
            // A cookie alone isn't enough — a locked/"confirm you're a human"
            // session also has one. Verify against the API before proceeding.
            if (isLoggedIn() && !loggedInHandled && !verifying) {
                attemptVerify(manual = false)
            }
            if (!revealed) {
                val elapsed = System.currentTimeMillis() - attemptStart
                if (submitted) detectPageState()
                if (elapsed > REVEAL_TIMEOUT_MS) {
                    reveal("Finish logging in (or solve the captcha)")
                }
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

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = settings.userAgentString?.replace("; wv", "")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = false
                override fun onPageFinished(view: WebView, url: String?) {
                    if (resolved) return
                    if (mode == MODE_CHECK && isLoggedIn() && !loggedInHandled && !verifying) {
                        attemptVerify(manual = false)
                        return
                    }
                    if (mode == MODE_CHECK && !submitted && url != null && url.contains("/login")) {
                        prefillAndSubmit()
                    }
                }
            }
        }

        splash = buildSplash()

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        invalidButton = barButton("Didn't work") { finishWith(RESULT_INVALID, null) }.apply { visibility = View.GONE }
        solveButton = barButton("Done") { attemptVerify(manual = true) }.apply { visibility = View.GONE }
        topBar.addView(spacer)
        topBar.addView(solveButton)
        topBar.addView(invalidButton)
        topBar.addView(barButton("Cancel") { finishWith(RESULT_CANCELLED, null) })

        root.addView(webView)
        root.addView(splash)
        root.addView(topBar)
        setContentView(root)

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)

        if (mode == MODE_OPEN) {
            revealWeb()
            statusView.text = "@$username"
            if (startCookie.isNotBlank()) {
                cm.setCookie("https://www.roblox.com", roblosecurityCookie(startCookie)) {
                    webView.loadUrl("https://www.roblox.com/home")
                }
            } else {
                webView.loadUrl("https://www.roblox.com/login")
            }
            return
        }

        // CHECK mode: clear only the session cookie (not the whole jar) so each
        // account logs into its own fresh session, while the browser-trust
        // cookies persist — a "known device" gets flagged/locked far less often.
        statusView.text = "Checking @$username…"
        attemptStart = System.currentTimeMillis()
        val expire = "=; Max-Age=0; Path=/; Domain=.roblox.com"
        cm.setCookie("https://www.roblox.com", ".ROBLOSECURITY$expire")
        cm.setCookie("https://roblox.com", ".ROBLOSECURITY$expire") {
            cm.flush()
            attemptStart = System.currentTimeMillis()
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
            setBackgroundColor(Color.parseColor("#AA000000"))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        column.addView(ProgressBar(this))
        statusView = TextView(this).apply {
            text = "Checking…"
            setTextColor(Color.parseColor("#F4F6F8"))
            setPadding(dp(24), dp(16), dp(24), 0)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        column.addView(statusView)
        frame.addView(bg)
        frame.addView(scrim)
        frame.addView(column)
        return frame
    }

    private fun prefillAndSubmit() {
        submitted = true
        attemptStart = System.currentTimeMillis()
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
              if (!setVal(user, $u) || !setVal(pass, $p)) return 'nofields';
              var btn = document.getElementById('login-button')
                || document.querySelector('button[type="submit"]')
                || document.querySelector('form button');
              if (btn) { setTimeout(function(){ btn.click(); }, 250); return 'submitted'; }
              return 'nobutton';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { }
    }

    /** Reveals the web page if a captcha OR an account-lock / human-check page is shown. */
    private fun detectPageState() {
        val js = """
            (function(){
              var t = document.body ? document.body.innerText : '';
              if (/suspicious activity|Account locked|confirming that you'?re a human|Verify you are human|Start Puzzle/i.test(t)) return 'challenge';
              if (document.querySelector('iframe[src*="arkose"], iframe[src*="funcaptcha"], iframe[title*="verification" i], #FunCAPTCHA, [data-testid*="captcha" i]')) return 'challenge';
              return 'none';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { r ->
            if (!revealed && r != null && r.contains("challenge")) {
                reveal("Tap Continue / solve the check — it finishes automatically")
            }
        }
    }

    /**
     * Verifies the current cookie is a real, unlocked session before treating it
     * as a successful login. Runs off the main thread.
     */
    private fun attemptVerify(manual: Boolean) {
        val cookie = roblosecurity()
        if (cookie == null) {
            if (manual) Toast.makeText(this, "Not logged in yet", Toast.LENGTH_SHORT).show()
            return
        }
        if (verifying || loggedInHandled) return
        verifying = true
        Thread {
            val ok = RobloxApi.isValidSession(cookie)
            runOnUiThread {
                verifying = false
                if (resolved) return@runOnUiThread
                if (ok) {
                    onLoggedIn()
                } else if (!revealed) {
                    reveal("Tap Continue / confirm you're human to finish")
                } else if (manual) {
                    Toast.makeText(this, "Still locked — finish the verification on the page", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /** Detected a valid session: load the home page (behind the splash) and capture it. */
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

    private fun reveal(message: String) {
        revealed = true
        revealWeb()
        statusView.text = message
        solveButton.visibility = View.VISIBLE
        invalidButton.visibility = View.VISIBLE
    }

    private fun revealWeb() {
        splash.visibility = View.GONE
    }

    private fun coverWithSplash(message: String) {
        statusView.text = message
        splash.visibility = View.VISIBLE
        solveButton.visibility = View.GONE
        invalidButton.visibility = View.GONE
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

        private const val REVEAL_TIMEOUT_MS = 9000L
    }
}
