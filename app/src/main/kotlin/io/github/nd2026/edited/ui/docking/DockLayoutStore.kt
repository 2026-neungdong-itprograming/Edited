package io.github.nd2026.edited.ui.docking

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Saves and loads the dock layout as versioned JSON at [path]. */
class DockLayoutStore(val path: Path) {
    /**
     * Loads the saved layout reconciled against [registry], or null when nothing usable is saved
     * (missing, unreadable, corrupt or from a newer schema version) and the default should be used.
     */
    fun load(registry: PaneRegistry, defaults: DockLayoutState): DockLayoutState? {
        val text = try {
            if (!Files.isRegularFile(path)) return null
            Files.readString(path)
        } catch (_: IOException) {
            return null
        }
        val decoded = DockLayoutCodec.decode(text) ?: return null
        return runCatching { DockLayoutReconciler.reconcile(decoded, registry, defaults) }.getOrNull()
    }

    /** Writes [state] atomically, so a crash mid-write never leaves a truncated layout behind. */
    @Throws(IOException::class)
    fun save(state: DockLayoutState) {
        path.parent?.let(Files::createDirectories)
        val temp = Files.createTempFile(path.parent ?: Path.of("."), path.fileName.toString(), ".tmp")
        try {
            Files.writeString(temp, DockLayoutCodec.encode(state))
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    fun delete() {
        Files.deleteIfExists(path)
    }

    companion object {
        /** Per-user layout file: `~/.texted/dock-layout.json`. */
        fun default(): DockLayoutStore =
            DockLayoutStore(Path.of(System.getProperty("user.home"), ".texted", "dock-layout.json"))
    }
}
