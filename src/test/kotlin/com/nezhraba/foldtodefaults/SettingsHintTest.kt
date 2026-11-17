package com.nezhraba.foldtodefaults

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.function.Predicate

class SettingsHintTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // The startup activity may already have shown the hint for the test project.
        PropertiesComponent.getInstance().unsetValue(SETTINGS_HINT_SHOWN_KEY)
    }

    override fun tearDown() {
        try {
            PropertiesComponent.getInstance().unsetValue(SETTINGS_HINT_SHOWN_KEY)
        } finally {
            super.tearDown()
        }
    }

    fun testHintIsShownOnlyOnce() {
        val shown = mutableListOf<Notification>()
        // Only application-level notifications reach the application bus; project-level ones would show
        // in a single project window.
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
            .subscribe(Notifications.TOPIC, object : Notifications {
                override fun notify(notification: Notification) {
                    shown += notification
                }
            })

        showSettingsHintOnce()
        showSettingsHintOnce()

        assertEquals(1, shown.size)
        assertEquals("Open Code Folding Settings", shown.single().actions.single().templateText)
    }

    fun testPredicateFindsCodeFoldingPage() {
        val groups = ShowSettingsUtilImpl.getConfigurableGroups(project, true).toList()
        assertNotNull(ConfigurableVisitor.find(Predicate(::isFoldingConfigurable), groups))
    }
}
