package com.nezhraba.foldtodefaults

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import java.util.function.Predicate

private const val PLUGIN_ID = "com.nezhraba.foldtodefaults"
private const val NOTIFICATION_GROUP_ID = "Fold to Defaults"
internal const val SETTINGS_HINT_SHOWN_KEY = "com.nezhraba.foldtodefaults.settingsHintShown"
private const val FOLDING_CONFIGURABLE_ID = "editor.preferences.folding"

/**
 * Tells the user, once per IDE installation, where the defaults come from. Some languages (PHP, for
 * example) ignore the generic options and have their own section on that settings page.
 */
@Synchronized
internal fun showSettingsHintOnce(project: Project?) {
    val properties = PropertiesComponent.getInstance()
    if (properties.isTrueValue(SETTINGS_HINT_SHOWN_KEY)) return
    properties.setValue(SETTINGS_HINT_SHOWN_KEY, true)

    NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
        .createNotification(
            "Fold to Defaults",
            "Folding is reset to the defaults from <i>Settings | Editor | General | Code Folding</i>. " +
                "Some languages, such as PHP, have their own section there.",
            NotificationType.INFORMATION,
        )
        .addAction(NotificationAction.createSimpleExpiring("Open Code Folding Settings") {
            // Select by id: the String overload of showSettingsDialog matches the localized display name.
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, Predicate(::isFoldingConfigurable), null)
        })
        .notify(project)
}

internal fun isFoldingConfigurable(configurable: Configurable) =
    (configurable as? SearchableConfigurable)?.id == FOLDING_CONFIGURABLE_ID

/** Covers installation followed by a restart. */
internal class SettingsHintStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) = showSettingsHintOnce(project)
}

/** Covers installation without a restart, where startup activities don't run for open projects. */
internal class SettingsHintPluginListener : DynamicPluginListener {
    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId.idString != PLUGIN_ID) return
        showSettingsHintOnce(ProjectManager.getInstance().openProjects.firstOrNull())
    }
}
