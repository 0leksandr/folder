package com.nezhraba.foldtodefaults

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware

/**
 * Resets every fold region that has a language-defined default to that default.
 *
 * The platform computes the default of each region (descriptor override, otherwise
 * `FoldingBuilder.isCollapsedByDefault`) in every folding update, over all PSI roots of the file,
 * and stores it on the region. [CodeFoldingManager.isCollapsedByDefault] exposes it, so there is no
 * need to rebuild descriptors and match them to regions by range. Regions without a stored default
 * (for example those created by "Fold Selection") are left alone.
 */
class FoldToDefaultsAction : AnAction(), DumbAware {

    // update() only checks data context entries, no PSI or editor state is read.
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled =
            e.getData(CommonDataKeys.EDITOR) != null && e.getData(CommonDataKeys.PSI_FILE) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        foldToDefaults(editor)
    }
}

internal fun foldToDefaults(editor: Editor) {
    val project = editor.project ?: return
    val manager = CodeFoldingManager.getInstance(project)
    val model = editor.foldingModel

    model.runBatchFoldingOperation {
        val defaults = model.allFoldRegions
            .filter { it.isValid && !it.shouldNeverExpand() }
            .mapNotNull { region -> manager.isCollapsedByDefault(region)?.let { region to it } }

        // The platform expands a whole group if any of its regions is expanded by default.
        val expandedGroups = defaults
            .filter { (region, collapsed) -> region.group != null && !collapsed }
            .mapTo(HashSet()) { (region, _) -> region.group }

        for ((region, collapsed) in defaults) {
            val group = region.group
            region.isExpanded = if (group != null) group in expandedGroups else !collapsed
        }
    }
}
