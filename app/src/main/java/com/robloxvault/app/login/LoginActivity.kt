package com.robloxvault.app.login

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
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

/**
 * Presents the REAL Roblox login page in a WebView for one of the user's own
 * accounts. Credentials are pre-filled as a convenience, but the user stays in
 * control: they solve any CAPTCHA and complete 2FA themselves on Roblox's own
 * page. We never bypass those checks — we only observe whether a valid Roblox
 * session cookie appears, which tells us the login succeeded.
 *
 * This is deliberately an assisted-login helper, not an automated credential
 * tester: there is no proxy rotation, no bulk grinding, and no challenge
 * solving. It works with Roblox's defenses, not around them.
 */
class LoginActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var username: String = ""
    private var password: String = ""
    private var mode: String = MODE_CHECK
    private var accountId: String = ""
    private var resolved = false
    private var prefilled = false

    private val cookiePoll = object : Runnable {
        override fun run() {
            if (isLoggedIn()) {
                finishWith(RESULT_VALID)
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
            setBackgroundColor(Color.parseColor("#111827"))
        }

        val header = TextView(this).apply {
            text = if (mode == MODE_CHECK)
                "Checking @$username — solve any CAPTCHA / 2FA, then log in"
            else
                "Quick login: @$username"
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(14), dp(16), dp(4))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        statusView = TextView(this).apply {
            text = "Loading Roblox…"
            setTextColor(Color.parseColor("#9CA3AF"))
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
                ): Boolean = false // keep navigation inside the WebView

                override fun onPageFinished(view: WebView, url: String?) {
                    statusView.text = url
                    if (isLoggedIn()) {
                        finishWith(RESULT_VALID)
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
            setBackgroundColor(Color.parseColor("#111827"))
        }
        if (mode == MODE_CHECK) {
            buttonBar.addView(barButton("Mark invalid") { finishWith(RESULT_INVALID) })
        }
        buttonBar.addView(barButton("Cancel") { finishWith(RESULT_CANCELLED) })

        root.addView(header)
        root.addView(statusView)
        root.addView(webView)
        root.addView(buttonBar)
        setContentView(root)

        if (mode == MODE_CHECK) {
            // Fresh session so a previously logged-in account can't mask this one.
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.loadUrl("https://www.roblox.com/login")
        handler.postDelayed(cookiePoll, 1500)
    }

    /** Best-effort pre-fill of the username/password fields on Roblox's form. */
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

    private fun isLoggedIn(): Boolean {
        val cookies = CookieManager.getInstance().getCookie("https://www.roblox.com") ?: return false
        return cookies.contains(".ROBLOSECURITY")
    }

    private fun finishWith(result: String) {
        if (resolved) return
        resolved = true
        handler.removeCallbacksAndMessages(null)
        setResult(
            Activity.RESULT_OK,
            intent.putExtra(EXTRA_RESULT, result).putExtra(EXTRA_ACCOUNT_ID, accountId),
        )
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

        const val MODE_CHECK = "check"
        const val MODE_OPEN = "open"

        const val RESULT_VALID = "valid"
        const val RESULT_INVALID = "invalid"
        const val RESULT_CANCELLED = "cancelled"
    }
}
