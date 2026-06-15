package com.a11.flipperlauncher.terminal

import com.a11.flipperlauncher.data.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEngineTest {

    private val engine = TerminalEngine()

    @Test
    fun commonShowsUsefulCommandMap() {
        val result = engine.execute("common", env())

        assertTrue(result.lines.any { it.text.contains("common commands") })
        assertTrue(result.lines.any { it.text.contains("camera, phone, messages") })
    }

    @Test
    fun dirListsAppsLikeLs() {
        val result = engine.execute("dir", env(app("Camera", "com.sec.android.app.camera")))

        assertTrue(result.lines.any { it.text.contains("1 apps") })
        assertTrue(result.lines.any { it.text.contains("Camera") })
    }

    @Test
    fun lsCanReadPseudoPaths() {
        val result = engine.execute("ls notes", env(notes = listOf("payload checklist")))

        assertTrue(result.lines.any { it.text.contains("notes // 1") })
        assertTrue(result.lines.any { it.text.contains("payload checklist") })
    }

    @Test
    fun bareUniqueAppNameLaunchesApp() {
        val result = engine.execute("camera", env(app("Camera", "com.sec.android.app.camera")))

        val action = result.actions.single() as TermAction.Launch
        assertEquals("com.sec.android.app.camera", action.pkg)
        assertTrue(result.lines.any { it.text.contains("launching Camera") })
    }

    @Test
    fun ambiguousBareAppNameShowsTapTargetsInsteadOfGuessing() {
        val result = engine.execute(
            "maps",
            env(
                app("Google Maps", "com.google.android.apps.maps"),
                app("Maps.me", "com.mapswithme.maps.pro"),
            ),
        )

        assertTrue(result.actions.isEmpty())
        assertTrue(result.lines.any { it.text.contains("multiple app matches") })
        assertTrue(result.lines.count { it.action is TermAction.Launch } == 2)
    }

    @Test
    fun androidCmdWithArgumentsHandsOffToTermux() {
        val result = engine.execute(
            "cmd package list packages",
            env(app("Termux", "com.termux")),
        )

        assertTrue(result.lines.any { it.text.contains("cmd // Termux") })
        assertTrue(result.lines.any { it.text.contains("cmd package list packages") })
        assertTrue(result.lines.any { it.action is TermAction.Launch })
    }

    @Test
    fun dashAppsOpensTheAppsOverlay() {
        val result = engine.execute("-apps", env(app("Camera", "com.sec.android.app.camera")))

        assertTrue(result.actions.any { it is TermAction.GoToApps })
        assertTrue(result.lines.any { it.text.contains("opening apps") })
    }

    @Test
    fun editWithFileNameOpensThatFileInEditor() {
        val result = engine.execute("edit main.c", env())

        val open = result.actions.filterIsInstance<TermAction.OpenInEditor>().single()
        assertEquals("main.c", open.name)
        assertTrue(result.lines.any { it.text.contains("editing main.c") })
    }

    @Test
    fun bareEditListsWorkspaceAndJumpsToEditor() {
        val result = engine.execute("code", env(codeFiles = listOf("main.c", "scan.py")))

        assertTrue(result.actions.any { it is TermAction.GoToCode })
        // each workspace file is a tappable "open in editor" row
        val opens = result.lines.mapNotNull { it.action as? TermAction.OpenInEditor }.map { it.name }
        assertEquals(listOf("main.c", "scan.py"), opens)
    }

    private fun env(
        vararg apps: AppInfo,
        notes: List<String> = emptyList(),
        codeFiles: List<String> = emptyList(),
    ) = TermEnv(
        apps = apps.toList(),
        favorites = emptyList(),
        aliases = emptyMap(),
        notes = notes,
        history = listOf("help"),
        accent = "orange",
        device = DeviceSnapshot(
            time = "12:00",
            date = "Fri 12 Jun 2026",
            batteryPct = 88,
            charging = false,
            model = "Flipper Device",
            androidRelease = "12",
            sdkInt = 31,
            uptime = "1h 2m",
        ),
        codeFiles = codeFiles,
    )

    private fun app(label: String, pkg: String) = AppInfo(
        label = label,
        packageName = pkg,
        versionName = "1.0",
        icon = null,
    )
}
