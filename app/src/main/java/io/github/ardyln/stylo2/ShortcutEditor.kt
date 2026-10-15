package io.github.ardyln.stylo2

import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Labels and the dialogs that pick a shortcut's action and its details. */
class ShortcutEditor(private val context: Context) {

    /** Shows the shortcut's icon: the app's own icon for "open an app", else a symbol in a circle. */
    fun bindIcon(view: android.widget.ImageView, s: Shortcut) {
        val appIcon = if (s.action == Shortcut.Action.OPEN_APP) s.arg?.let {
            try { context.packageManager.getApplicationIcon(it) } catch (e: PackageManager.NameNotFoundException) { null }
        } else null
        if (appIcon != null) {
            view.background = null
            view.setPadding(0, 0, 0, 0)
            view.imageTintList = null
            view.setImageDrawable(appIcon)
            return
        }
        val pad = (8 * context.resources.displayMetrics.density).toInt()
        view.setBackgroundResource(R.drawable.tool_icon_background)
        view.setPadding(pad, pad, pad, pad)
        val tint = android.util.TypedValue()
        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSecondaryContainer, tint, true)
        view.imageTintList = android.content.res.ColorStateList.valueOf(tint.data)
        view.setImageResource(
            when (s.action) {
                Shortcut.Action.OPEN_APP -> R.drawable.ic_action_apps
                Shortcut.Action.MEDIA_PLAY_PAUSE -> R.drawable.ic_action_play_pause
                Shortcut.Action.MEDIA_NEXT -> R.drawable.ic_action_next
                Shortcut.Action.MEDIA_PREVIOUS -> R.drawable.ic_action_previous
                Shortcut.Action.BROADCAST -> R.drawable.ic_action_broadcast
                Shortcut.Action.WEBHOOK -> R.drawable.ic_action_webhook
                Shortcut.Action.BUZZ -> R.drawable.ic_action_buzz
                Shortcut.Action.TOGGLE_PRESENTATION -> R.drawable.ic_presentation
                Shortcut.Action.TOGGLE_POINTER -> R.drawable.ic_action_pointer
                Shortcut.Action.TOGGLE_FEEDBACK -> R.drawable.ic_action_texture
                Shortcut.Action.NEXT_TEXTURE -> R.drawable.ic_texture_pencil
                Shortcut.Action.NEXT_PAGE -> R.drawable.ic_action_page_next
                Shortcut.Action.PREVIOUS_PAGE -> R.drawable.ic_action_page_previous
                Shortcut.Action.SCROLL_UP, Shortcut.Action.SCROLL_DOWN -> R.drawable.ic_gesture_swipe
                Shortcut.Action.APPLY_PRESET -> R.drawable.ic_presets
                Shortcut.Action.CLICK, Shortcut.Action.RIGHT_CLICK, Shortcut.Action.MIDDLE_CLICK ->
                    R.drawable.ic_gesture_click
                Shortcut.Action.NOTHING -> R.drawable.ic_texture_off
            }
        )
    }

    fun tapsLabel(taps: Int): String =
        if (taps == 1) context.getString(R.string.shortcut_taps_1)
        else context.getString(R.string.shortcut_taps_n, taps)

    fun actionLabel(s: Shortcut): String = when (s.action) {
        Shortcut.Action.OPEN_APP -> context.getString(R.string.action_open_app_named, s.arg?.let { appLabel(it) } ?: "?")
        Shortcut.Action.BROADCAST -> context.getString(R.string.action_broadcast_named, s.arg ?: "?")
        Shortcut.Action.WEBHOOK -> context.getString(R.string.action_webhook_named, webhookHost(s.arg))
        Shortcut.Action.APPLY_PRESET -> context.getString(
            R.string.action_apply_preset_named, PresetStore(context).get(s.arg)?.name ?: "?",
        )
        Shortcut.Action.SCROLL_UP -> context.getString(R.string.action_scroll_up_named, s.arg?.toIntOrNull() ?: 1)
        Shortcut.Action.SCROLL_DOWN -> context.getString(R.string.action_scroll_down_named, s.arg?.toIntOrNull() ?: 1)
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
            Shortcut.Action.NEXT_PAGE -> R.string.action_next_page
            Shortcut.Action.PREVIOUS_PAGE -> R.string.action_previous_page
            Shortcut.Action.SCROLL_UP -> R.string.action_scroll_up
            Shortcut.Action.APPLY_PRESET -> R.string.action_apply_preset
            Shortcut.Action.SCROLL_DOWN -> R.string.action_scroll_down
            Shortcut.Action.CLICK -> R.string.action_click
            Shortcut.Action.RIGHT_CLICK -> R.string.action_right_click
            Shortcut.Action.MIDDLE_CLICK -> R.string.action_middle_click
            Shortcut.Action.NOTHING -> R.string.action_nothing
        }
    )

    /** Shows only the host: the rest of a webhook URL is a secret. */
    private fun webhookHost(url: String?): String =
        url?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() } ?: "?"

    /** Picks an action (and its app, intent action or URL), then calls [onDone]. */
    /**
     * [presentation]: also offer scroll, click and nothing first (presentation gestures).
     * [allowNothing]: offer "Nothing" first (triggers that can be left unused, like the charger).
     */
    fun chooseAction(
        title: String, existing: Shortcut?, presentation: Boolean = false, allowNothing: Boolean = false,
        onDone: (Shortcut) -> Unit,
    ) {
        val actions = Shortcut.Action.entries.filter { !it.presentationOnly }.let { normal ->
            when {
                presentation -> Shortcut.Action.entries.filter { it.presentationOnly } + normal
                allowNothing -> listOf(Shortcut.Action.NOTHING) + normal
                else -> normal
            }
        }
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
                    Shortcut.Action.APPLY_PRESET -> pickPreset { onDone(base.copy(arg = it)) }
                    Shortcut.Action.SCROLL_UP, Shortcut.Action.SCROLL_DOWN -> pickScrollSteps(previous) {
                        onDone(base.copy(arg = it.toString()))
                    }
                    else -> onDone(base.copy(arg = null))
                }
            }
            .show()
    }

    private fun pickPreset(onPicked: (String) -> Unit) {
        val presets = PresetStore(context).load().sortedBy { it.name.lowercase() }
        if (presets.isEmpty()) {
            android.widget.Toast.makeText(context, R.string.presets_none_yet, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_apply_preset)
            .setItems(presets.map { it.name }.toTypedArray()) { _, which -> onPicked(presets[which].id) }
            .show()
    }

    private fun pickScrollSteps(previous: String?, onPicked: (Int) -> Unit) {
        val max = ShortcutRunner.MAX_SCROLL
        val labels = (1..max).map { context.getString(R.string.scroll_strength_item, it) }.toTypedArray()
        val current = (previous?.toIntOrNull() ?: 1).coerceIn(1, max) - 1
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.scroll_strength_title)
            .setSingleChoiceItems(labels, current) { d, which ->
                d.dismiss()
                onPicked(which + 1)
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

    private fun pickApp(onPicked: (String) -> Unit) = AppPicker.show(context, R.string.choose_app, onPicked = onPicked)
}
