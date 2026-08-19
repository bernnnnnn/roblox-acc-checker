package com.robloxvault.app.autofill

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.robloxvault.app.R
import com.robloxvault.app.data.AccountStore

/**
 * Lets the vault fill saved credentials into OTHER apps (like the Roblox app)
 * and websites through Android's Autofill framework — the supported way a
 * password manager offers logins. The user taps the field, picks an account,
 * and the username/password are filled; they still press Log In and complete
 * any verification themselves.
 */
class VaultAutofillService : AutofillService() {

    private class Fields(var username: AutofillId?, var password: AutofillId?)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) { callback.onSuccess(null); return }

        val fields = Fields(null, null)
        for (i in 0 until structure.windowNodeCount) {
            traverse(structure.getWindowNodeAt(i).rootViewNode, fields)
        }
        if (fields.username == null && fields.password == null) {
            callback.onSuccess(null); return
        }

        val accounts = runCatching { AccountStore(this).load() }.getOrDefault(emptyList())
        if (accounts.isEmpty()) { callback.onSuccess(null); return }

        val response = FillResponse.Builder()
        accounts.take(30).forEach { acc ->
            val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
                setTextViewText(R.id.autofill_label, "@${acc.username}")
            }
            val dataset = Dataset.Builder()
            var added = false
            fields.username?.let {
                dataset.setValue(it, AutofillValue.forText(acc.username), presentation); added = true
            }
            fields.password?.let {
                dataset.setValue(it, AutofillValue.forText(acc.password), presentation); added = true
            }
            if (added) response.addDataset(dataset.build())
        }
        callback.onSuccess(response.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // The vault manages accounts itself; nothing to save from here.
        callback.onSuccess()
    }

    private fun traverse(node: AssistStructure.ViewNode, fields: Fields) {
        val id = node.autofillId
        if (id != null) {
            val hints = node.autofillHints
            val isPassword = isPasswordField(node)
            when {
                hints != null && hints.any { it.contains("password", true) } -> fields.password = id
                isPassword -> fields.password = id
                hints != null && hints.any {
                    it.contains("username", true) || it.contains("email", true) || it.contains("phone", true)
                } -> fields.username = id
                fields.username == null && isTextEntry(node) -> fields.username = id // fallback
            }
        }
        for (i in 0 until node.childCount) traverse(node.getChildAt(i), fields)
    }

    private fun isTextEntry(node: AssistStructure.ViewNode): Boolean {
        val cls = node.className ?: return false
        return cls.contains("EditText") || node.htmlInfo?.tag == "input"
    }

    private fun isPasswordField(node: AssistStructure.ViewNode): Boolean {
        val type = node.inputType
        val variations = InputType.TYPE_TEXT_VARIATION_PASSWORD or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD or
            InputType.TYPE_NUMBER_VARIATION_PASSWORD
        return (type and variations) != 0
    }
}
