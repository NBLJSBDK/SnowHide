package com.nbljsbdk.snowhide.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nbljsbdk.snowhide.data.repo.BackupRepository
import com.nbljsbdk.snowhide.domain.backup.BackupUseCase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun v1ImportWritesPersistentFieldsAndSkipsRuntimeFields() {
        val grid = context.getSharedPreferences("snowhide_grid", Context.MODE_PRIVATE)
        val settings = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
        grid.edit().clear().commit()
        settings.edit().clear().commit()

        val document = JSONObject()
            .put("version", 1)
            .put(
                "grid",
                JSONObject()
                    .put("grid_items", "[]")
                    .put("folders", "[]")
                    .put("folder_apps", "[]")
                    .put("locked_packages", "[\"com.example.app\"]")
                    .put("frozen_cache", "[\"com.example.runtime\"]"),
            )
            .put(
                "settings",
                JSONObject()
                    .put("show_toast", false)
                    .put("dock_action_icon_size", 26)
                    .put("quick_toggle_members", "[\"com.example.app\"]")
                    .put("quick_toggle_opened", "[\"com.example.runtime\"]")
                    .put("lock_clean_pending", true),
            )

        val count = BackupRepository.importBackup(document.toString())

        assertEquals(7, count)
        assertEquals("[]", grid.getString("grid_items", null))
        assertEquals("[\"com.example.app\"]", grid.getString("locked_packages", null))
        assertEquals(false, settings.getBoolean("show_toast", true))
        assertEquals(26, settings.getInt("dock_action_icon_size", -1))
        assertEquals("[\"com.example.app\"]", settings.getString("quick_toggle_members", null))
        assertEquals(null, settings.getString("lock_clean_pending", null))
    }

    @Test
    fun invalidFieldDoesNotPartiallyImport() {
        val grid = context.getSharedPreferences("snowhide_grid", Context.MODE_PRIVATE)
        val settings = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
        grid.edit().putString("grid_items", "[\"before\"]").commit()
        settings.edit().putBoolean("show_toast", true).commit()

        val invalid = JSONObject()
            .put("version", 1)
            .put("grid", JSONObject().put("grid_items", "[]"))
            .put("settings", JSONObject().put("unknown_dangerous_key", true))

        assertThrows(IllegalArgumentException::class.java) {
            BackupRepository.importBackup(invalid.toString())
        }
        assertEquals("[\"before\"]", grid.getString("grid_items", null))
        assertEquals(true, settings.getBoolean("show_toast", false))
    }

    @Test
    fun useCaseExportsRequestedScopesAndWrapsInvalidImport() {
        val grid = context.getSharedPreferences("snowhide_grid", Context.MODE_PRIVATE)
        val settings = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
        grid.edit().clear().putString("grid_items", "[]").commit()
        settings.edit()
            .clear()
            .putBoolean("show_toast", false)
            .putInt("dock_action_icon_size", 26)
            .commit()

        val useCase = BackupUseCase(BackupRepository)
        assertTrue(JSONObject(useCase.export(BackupUseCase.Scope.GRID).getOrThrow()).has("grid"))
        val exportedSettings = JSONObject(useCase.export(BackupUseCase.Scope.SETTINGS).getOrThrow())
            .getJSONObject("settings")
        assertEquals(26, exportedSettings.getInt("dock_action_icon_size"))
        assertTrue(useCase.import("{\"version\":99,\"grid\":{}} ").isFailure)
    }

    @Test
    fun animationLevelRoundTripsThroughV1Backup() {
        val settings = context.getSharedPreferences("snowhide_settings", Context.MODE_PRIVATE)
        settings.edit().clear().putInt("animation_level", 1).commit()

        val exported = JSONObject(BackupRepository.exportSettings())
            .getJSONObject("settings")
        assertEquals(1, exported.getInt("animation_level"))

        val replacement = JSONObject()
            .put("version", 1)
            .put("settings", JSONObject().put("animation_level", 3))
        BackupRepository.importBackup(replacement.toString())
        assertEquals(3, settings.getInt("animation_level", -1))
    }
}
