package com.robloxvault.app.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.robloxvault.app.data.Account
import com.robloxvault.app.data.RobloxApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a shareable "profile card" PNG for an account: avatar, @username,
 * display name, creation date, and any loaded stats. Uses only public data,
 * so it works whether or not the password was verified.
 */
object ProfileCard {

    suspend fun build(context: Context, account: Account): File? = withContext(Dispatchers.IO) {
        val userId = if (account.userId > 0) account.userId
        else if (account.username.isNotBlank()) RobloxApi.userIdForUsername(account.username) ?: return@withContext null
        else return@withContext null

        val name = account.username.ifBlank { "user" }
        val avatar = RobloxApi.fetchAvatarUrl(userId)
            ?.let { RobloxApi.downloadBytes(it) }
            ?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }

        val w = 1000
        val h = 680
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Background + border (Noctra).
        p.color = Color.parseColor("#0E0F12")
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 40f, 40f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = Color.parseColor("#202733")
        c.drawRoundRect(RectF(6f, 6f, w - 6f, h - 6f), 36f, 36f, p)
        p.style = Paint.Style.FILL

        // Avatar (rounded).
        val avX = 60f; val avY = 80f; val avS = 360f
        val dst = RectF(avX, avY, avX + avS, avY + avS)
        p.color = Color.parseColor("#15181D")
        c.drawRoundRect(dst, 28f, 28f, p)
        if (avatar != null) {
            val save = c.save()
            val path = android.graphics.Path().apply { addRoundRect(dst, 28f, 28f, android.graphics.Path.Direction.CW) }
            c.clipPath(path)
            c.drawBitmap(avatar, Rect(0, 0, avatar.width, avatar.height), dst, p)
            c.restoreToCount(save)
        } else {
            p.color = Color.parseColor("#D8E8FF")
            p.textSize = 160f
            p.typeface = Typeface.DEFAULT_BOLD
            val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            c.drawText(initial, avX + avS / 2 - p.measureText(initial) / 2, avY + avS / 2 + 55f, p)
        }

        // Text column.
        val tx = 470f
        val maxW = w - tx - 40f

        // Username — shrink to fit the full name (no truncation).
        p.typeface = Typeface.DEFAULT_BOLD
        p.color = Color.parseColor("#FFFFFF")
        var us = 66f
        p.textSize = us
        while (p.measureText("@$name") > maxW && us > 26f) { us -= 2f; p.textSize = us }
        c.drawText("@$name", tx, 155f, p)

        p.typeface = Typeface.DEFAULT
        var y = 215f
        if (account.displayName.isNotBlank() && !account.displayName.equals(name, true)) {
            p.color = Color.parseColor("#7A8796")
            p.textSize = 36f
            c.drawText(ellipsize(account.displayName, p, maxW), tx, y, p)
            y += 55f
        }

        p.color = Color.parseColor("#D8E8FF")
        p.textSize = 40f
        val lh = 50f
        fun num(v: Long) = if (v >= 0) "%,d".format(v) else "—"
        c.drawText("Created: ${account.createdIso.substringBefore('T').ifBlank { "—" }}", tx, y, p); y += lh
        if (account.robux >= 0) { c.drawText("Robux: ${num(account.robux)}", tx, y, p); y += lh }
        c.drawText("Premium: ${if (account.premium) "Yes" else "No"}", tx, y, p); y += lh
        c.drawText("Friends: ${num(account.friends)}", tx, y, p); y += lh
        c.drawText("Followers: ${num(account.followers)}", tx, y, p); y += lh + 8f

        // HIT line (replaces RAP) — green when the password worked.
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 50f
        p.color = if (account.passwordWorked) Color.parseColor("#3BA55D") else Color.parseColor("#7A8796")
        c.drawText("HIT ~ flyingroach33", tx, y, p)
        y += 48f

        p.typeface = Typeface.DEFAULT
        p.textSize = 32f
        p.color = Color.parseColor("#7A8796")
        c.drawText(if (account.passwordWorked) "Password works" else "Password unverified", tx, y, p)

        // Footer.
        p.color = Color.parseColor("#515667")
        p.textSize = 28f
        c.drawText("Roblox Vault", tx, h - 34f, p)

        val dir = File(context.filesDir, "cards").apply { mkdirs() }
        val file = File(dir, "${account.id}.png")
        runCatching {
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        }.getOrElse { return@withContext null }
        bmp.recycle()
        file
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var s = text
        while (s.isNotEmpty() && paint.measureText("$s…") > maxWidth) s = s.dropLast(1)
        return "$s…"
    }
}
