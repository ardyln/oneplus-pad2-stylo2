package io.github.ardyln.stylo2

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Labels and the dialogs that pick a shortcut's action and its details. */
class ShortcutEditor(private val context: Context) {

    fun tapsLabel(taps: Int): String =
        if (taps == 1) context.getString(R.string.shortcut_taps_1)
        else context.getString(R.string.shortcut_taps_n, taps)

    fun actionLabel(s: Shortcut): String = when (s.action) {
        Shortcut.Action.OPEN_APP -> context.getString(R.string.action_open_app_named, s.arg?.let { appLabel(it) } ?: "?")
        Shortcut.Action.BROADCAST -> context.getString(R.string.action_broadcast_named, s.arg ?: "?")
        Shortcut.Action.WEBHOOK -> context.getString(R.string.action_webhook_named, webhookHost(s.arg))
        else -> actionName(s.action)
    }

    private fun actionName(a: Shortcut.Action): String = context.getString(
        when (a) {
            Shortcut.Action.OPEN_APP -> R.string.action_open_app
            Shortcut.Action.MEDIA_PLAY_PAUSE -> R.string.action_media_play_pause
            Shortcut.Action.MEDIA_NEXT -> R.string.action_media_next
            Shortcut.Action.MEDIA_PREVIOUS -> R.string.action_media_previous
            Shortcut.Action.BROADCAST -> R.string.action_broadcast
            Shortcut.Action.WEBHOOK -> R.string.action_webhook
            Shortcut.Action.BUZZ -> R.string.action_buzz
            Shortcut.Action.TOGGLE_PRESENTATION -> R.string.action_toggle_presentation
            Shortcut.Action.TOGGLE_POINTER -> R.string.action_toggle_pointer
            Shortcut.Action.TOGGLE_FEEDBACK -> R.string.action_toggle_feedback
            Shortcut.Action.NEXT_TEXTURE -> R.string.action_next_texture
        }
    )

    /** Shows only the host: the rest of a webhook URL is a secret. */
    private fun webhookHost(url: String?): String =
        url?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() } ?: "?"

    /** Picks an action (and its app, intent action or URL), then calls [onDone]. */
    fun chooseAction(title: String, existing: Shortcut?, onDone: (Shortcut) -> Unit) {
        val actions = Shortcut.Action.entries
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(actions.map { actionName(it) }.toTypedArray()) { _, which ->
                val action = actions[which]
                val base = existing?.copy(action = action) ?: Shortcut(action = action)
                val previous = if (existing?.action == action) existing.arg else null
                when (action) {
                    Shortcut.Action.OPEN_APP -> pickApp { onDone(base.copy(arg = it)) }
                    Shortcut.Action.BROADCAST -> askText(
                        R.string.broadcast_action_title, R.string.broadcast_action_hint,
                        R.string.broadcast_action_help, previous,
                        valid = { it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) },
                    ) { onDone(base.copy(arg = it)) }
                    Shortcut.Action.WEBHOOK -> askText(
                        R.string.webhook_title, R.string.webhook_hint, R.string.webhook_help, previous,
                        valid = { it.startsWith("http://") || it.startsWith("https://") },
                    ) { onDone(base.copy(arg = it)) }
                    else -> onDone(base.copy(arg = null))
                }
            }
            .show()
    }

    private fun askText(
        title: Int, hint: Int, help: Int, initial: String?,
        valid: (String) -> Boolean, onDone: (String) -> Unit,
    ) {
        val pad = (24 * context.resources.displayMetrics.density).toInt()
        val input = EditText(context).apply {
            setHint(hint)
            setText(initial ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
            addView(TextView(context).apply {
                setText(help)
                setPadding(0, pad / 2, 0, 0)
            })
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val text = input.text.toString().trim()
            if (valid(text)) {
                dialog.dismiss()
                onDone(text)
            } else {
                input.error = context.getString(R.string.invalid_value)
            }
        }
    }

    private fun appLabel(pkg: String): String {
        val pm = context.packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    private fun pickApp(onPicked: (String) -> Unit) {
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.choose_app)
            .setItems(apps.map { it.second }.toTypedArray()) { _, which -> onPicked(apps[which].first) }
            .show()
    }
}
