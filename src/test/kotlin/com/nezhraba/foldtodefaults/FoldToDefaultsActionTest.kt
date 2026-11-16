package com.nezhraba.foldtodefaults

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.FoldRegion
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FoldToDefaultsActionTest : BasePlatformTestCase() {

    private val settings get() = JavaCodeFoldingSettings.getInstance()
    private var savedMethods = false
    private var savedJavadocs = false
    private var savedLambdas = false
    private var savedInnerClasses = false

    override fun setUp() {
        super.setUp()
        savedMethods = settings.isCollapseMethods
        savedJavadocs = settings.isCollapseJavadocs
        savedLambdas = settings.isCollapseLambdas
        savedInnerClasses = settings.isCollapseInnerClasses
        settings.isCollapseLambdas = false
        settings.isCollapseInnerClasses = false
    }

    override fun tearDown() {
        try {
            settings.isCollapseMethods = savedMethods
            settings.isCollapseJavadocs = savedJavadocs
            settings.isCollapseLambdas = savedLambdas
            settings.isCollapseInnerClasses = savedInnerClasses
        } finally {
            super.tearDown()
        }
    }

    fun testMethodBodiesCollapsedAndInnerBlocksExpanded() {
        settings.isCollapseMethods = true
        configure()

        setAllExpanded(true)
        runAction()

        assertCollapsed(true, "void foo(java.util.List<String> xs) {")
        assertCollapsed(true, "void bar() {")
        assertCollapsed(false, "for (String s : xs) {")
        assertCollapsed(false, "if (s.isEmpty()) {")
        assertCollapsed(false, "Runnable r = () -> {")
        assertCollapsed(false, "static class Inner {")
        assertCollapsed(true, "void baz() {")
    }

    fun testRestoresDefaultsAfterCollapseAll() {
        settings.isCollapseMethods = true
        configure()

        setAllExpanded(false)
        runAction()

        assertCollapsed(true, "void foo(java.util.List<String> xs) {")
        assertCollapsed(false, "for (String s : xs) {")
        assertCollapsed(false, "if (s.isEmpty()) {")
        assertCollapsed(false, "static class Inner {")
        assertCollapsed(true, "void baz() {")
    }

    fun testMethodBodiesExpandedWhenSettingIsOff() {
        settings.isCollapseMethods = false
        configure()

        setAllExpanded(false)
        runAction()

        assertCollapsed(false, "void foo(java.util.List<String> xs) {")
        assertCollapsed(false, "for (String s : xs) {")
    }

    fun testDocCommentsFollowTheirOwnSetting() {
        settings.isCollapseMethods = false
        settings.isCollapseJavadocs = true
        configure()
        setAllExpanded(true)
        runAction()
        assertTrue("method doc comment", docRegion("/** Method doc */").let { !it.isExpanded })
        assertTrue("class doc comment", docRegion("/** Class doc */").let { !it.isExpanded })
        assertCollapsed(false, "void foo(java.util.List<String> xs) {")
    }

    fun testDocCommentsExpandedWhenSettingIsOff() {
        settings.isCollapseMethods = true
        settings.isCollapseJavadocs = false
        configure()
        setAllExpanded(false)
        runAction()
        assertTrue("method doc comment", docRegion("/** Method doc */").isExpanded)
        assertTrue("class doc comment", docRegion("/** Class doc */").isExpanded)
        assertCollapsed(true, "void foo(java.util.List<String> xs) {")
    }

    fun testManuallyCreatedRegionsAreLeftAlone() {
        settings.isCollapseMethods = true
        configure()
        setAllExpanded(true)

        val text = myFixture.editor.document.text
        val collapsedStart = text.indexOf("System.out.println(s);")
        val collapsedEnd = text.indexOf("System.out.println(s);", collapsedStart + 1) + "System.out.println(s);".length
        myFixture.editor.selectionModel.setSelection(collapsedStart, collapsedEnd)
        myFixture.performEditorAction("CollapseSelection")
        val collapsedManual = myFixture.editor.foldingModel.getFoldRegion(collapsedStart, collapsedEnd)
        assertNotNull("Fold Selection should create a region", collapsedManual)
        assertFalse(collapsedManual!!.isExpanded)

        val expandedStart = text.indexOf("int a = 1;")
        val expandedEnd = text.indexOf("int b = 2;") + "int b = 2;".length
        val expandedManual = runBatch { myFixture.editor.foldingModel.addFoldRegion(expandedStart, expandedEnd, "...") }
        assertNotNull(expandedManual)
        assertTrue(expandedManual!!.isExpanded)

        runAction()

        assertTrue(collapsedManual.isValid)
        assertFalse("manually collapsed region must stay collapsed", collapsedManual.isExpanded)
        assertTrue(expandedManual.isValid)
        assertTrue("manually created expanded region must stay expanded", expandedManual.isExpanded)
        assertCollapsed(true, "void foo(java.util.List<String> xs) {")
    }

    fun testActionIsInFoldingGroup() {
        val manager = ActionManager.getInstance()
        val group = manager.getAction("FoldingGroup") as ActionGroup
        assertTrue(group.getChildren(null).any { it === manager.getAction("FoldToDefaults") })
        assertNotNull(manager.getAction("FoldToDefaults"))
        assertTrue(manager.getAction("FoldToDefaults").templatePresentation.text == "Fold to Defaults")
    }

    private fun configure() {
        myFixture.configureByText("A.java", SOURCE)
        EditorTestUtil.buildInitialFoldingsInBackground(myFixture.editor)
        assertFalse("folding was not built", myFixture.editor.foldingModel.allFoldRegions.isEmpty())
    }

    private fun runAction() {
        assertTrue("action should be enabled", myFixture.testAction(FoldToDefaultsAction()).isEnabled)
    }

    private fun <T> runBatch(block: () -> T): T {
        var result: T? = null
        myFixture.editor.foldingModel.runBatchFoldingOperation { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun setAllExpanded(expanded: Boolean) {
        val model = myFixture.editor.foldingModel
        model.runBatchFoldingOperation {
            model.allFoldRegions.forEach { it.isExpanded = expanded }
        }
    }

    /** The region that starts at the `{` ending [header] (a unique snippet of the source). */
    private fun regionAfter(header: String): FoldRegion {
        val text = myFixture.editor.document.text
        val index = text.indexOf(header)
        assertTrue("'$header' not found", index >= 0)
        val brace = index + header.length - 1
        assertEquals('{', text[brace])
        return myFixture.editor.foldingModel.allFoldRegions.singleOrNull { it.startOffset == brace }
            ?: error("no fold region at '$header': ${describeRegions()}")
    }

    private fun docRegion(comment: String): FoldRegion {
        val start = myFixture.editor.document.text.indexOf(comment)
        assertTrue("'$comment' not found", start >= 0)
        return myFixture.editor.foldingModel.allFoldRegions.singleOrNull { it.startOffset == start }
            ?: error("no fold region for '$comment': ${describeRegions()}")
    }

    private fun assertCollapsed(collapsed: Boolean, header: String) {
        assertEquals(header, collapsed, !regionAfter(header).isExpanded)
    }

    private fun describeRegions() = myFixture.editor.foldingModel.allFoldRegions.joinToString { r ->
        "[${r.startOffset},${r.endOffset}) '${r.placeholderText}' expanded=${r.isExpanded}"
    }

    private companion object {
        val SOURCE = """
            /** Class doc */
            public class A {
                /** Method doc */
                void foo(java.util.List<String> xs) {
                    int a = 1;
                    int b = 2;
                    for (String s : xs) {
                        if (s.isEmpty()) {
                            System.out.println(s);
                            System.out.println(s);
                        }
                    }
                    Runnable r = () -> {
                        System.out.println("a");
                        System.out.println("b");
                    };
                }

                void bar() {
                    System.out.println("x");
                    System.out.println("y");
                }

                static class Inner {
                    void baz() {
                        System.out.println("p");
                        System.out.println("q");
                    }
                }
            }
        """.trimIndent()
    }
}
