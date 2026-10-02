package io.github.nd2026.edited.ui.docking

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.awt.Rectangle

/**
 * Versioned JSON form of [DockLayoutState]. The format is written by hand (no compiler plugin) so
 * decoding can stay lenient: unknown fields are ignored, older schema versions are migrated, and
 * anything malformed is reported as null so callers fall back to the default layout.
 *
 * ```json
 * {"schemaVersion":1,
 *  "root":{"type":"split","axis":"HORIZONTAL","ratio":0.2,"first":{...},"second":{...}},
 *  "hidden":{"LEFT":["structure"]},
 *  "floating":[{"pane":"graph","x":10,"y":20,"width":400,"height":300}],
 *  "focused":"editor"}
 * ```
 * A tab group is `{"type":"tabs","panes":["a","b"],"active":"b"}`; an empty root is `{"type":"empty"}`.
 */
object DockLayoutCodec {
    private val json = Json { prettyPrint = true }

    /** Migrations from version N to N+1, keyed by N. None exist yet; version 1 is the first format. */
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    fun encode(state: DockLayoutState): String = json.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("schemaVersion", DockLayoutState.CURRENT_SCHEMA_VERSION)
        put("root", encodeNode(state.root))
        put("hidden", buildJsonObject {
            for ((edge, panes) in state.hidden) {
                put(edge.name, buildJsonArray { panes.forEach { add(JsonPrimitive(it.value)) } })
            }
        })
        put("floating", buildJsonArray {
            for (floating in state.floating) add(buildJsonObject {
                put("pane", floating.paneId.value)
                put("x", floating.bounds.x)
                put("y", floating.bounds.y)
                put("width", floating.bounds.width)
                put("height", floating.bounds.height)
            })
        })
        state.focusedPane?.let { put("focused", it.value) }
    })

    /**
     * Parses [text] without validating pane ids against any registry; run the result through
     * [DockLayoutReconciler] before use. Returns null for malformed JSON or an unsupported version.
     */
    fun decode(text: String): DockLayoutState? = runCatching {
        var obj = Json.parseToJsonElement(text).jsonObject
        var version = obj.getValue("schemaVersion").jsonPrimitive.int
        if (version < 1 || version > DockLayoutState.CURRENT_SCHEMA_VERSION) return null
        while (version < DockLayoutState.CURRENT_SCHEMA_VERSION) {
            obj = migrations[version]?.invoke(obj) ?: return null
            version++
        }
        DockLayoutState(
            root = obj["root"]?.let(::decodeNode) ?: DockNode.Empty,
            hidden = obj["hidden"]?.jsonObject.orEmpty().mapNotNull { (edge, panes) ->
                val dockEdge = DockEdge.entries.firstOrNull { it.name == edge } ?: return@mapNotNull null
                dockEdge to panes.jsonArray.mapNotNull { paneIdOrNull(it) }
            }.toMap(),
            floating = obj["floating"]?.jsonArray.orEmpty().mapNotNull { element ->
                runCatching {
                    val o = element.jsonObject
                    FloatingPaneState(
                        PaneId(o.getValue("pane").jsonPrimitive.content),
                        Rectangle(
                            o.getValue("x").jsonPrimitive.int,
                            o.getValue("y").jsonPrimitive.int,
                            o.getValue("width").jsonPrimitive.int,
                            o.getValue("height").jsonPrimitive.int,
                        ),
                    )
                }.getOrNull()
            },
            focusedPane = obj["focused"]?.let(::paneIdOrNull),
            schemaVersion = DockLayoutState.CURRENT_SCHEMA_VERSION,
        )
    }.getOrNull()

    private fun encodeNode(node: DockNode): JsonObject = when (node) {
        DockNode.Empty -> buildJsonObject { put("type", "empty") }
        is DockNode.Tabs -> buildJsonObject {
            put("type", "tabs")
            put("panes", buildJsonArray { node.paneIds.forEach { add(JsonPrimitive(it.value)) } })
            put("active", node.active.value)
        }
        is DockNode.Split -> buildJsonObject {
            put("type", "split")
            put("axis", node.axis.name)
            put("ratio", node.ratio)
            put("first", encodeNode(node.first))
            put("second", encodeNode(node.second))
        }
    }

    /**
     * Decodes leniently into a [RawNode] first, since a saved tree may hold duplicates or empty
     * groups that the strict [DockNode] constructors reject; [DockLayoutReconciler] repairs them.
     */
    private fun decodeNode(element: JsonElement): DockNode = toDockNode(decodeRaw(element))

    private fun decodeRaw(element: JsonElement): RawNode {
        val obj = element.jsonObject
        return when (obj["type"]?.jsonPrimitive?.content) {
            "tabs" -> RawNode.Tabs(
                obj["panes"]?.jsonArray.orEmpty().mapNotNull(::paneIdOrNull),
                obj["active"]?.let(::paneIdOrNull),
            )
            "split" -> RawNode.Split(
                Axis.valueOf(obj.getValue("axis").jsonPrimitive.content),
                obj.getValue("ratio").jsonPrimitive.float,
                decodeRaw(obj.getValue("first")),
                decodeRaw(obj.getValue("second")),
            )
            else -> RawNode.Empty
        }
    }

    private sealed interface RawNode {
        data class Tabs(val panes: List<PaneId>, val active: PaneId?) : RawNode
        data class Split(val axis: Axis, val ratio: Float, val first: RawNode, val second: RawNode) : RawNode
        data object Empty : RawNode
    }

    private fun toDockNode(raw: RawNode, seen: MutableSet<PaneId> = mutableSetOf()): DockNode = when (raw) {
        RawNode.Empty -> DockNode.Empty
        is RawNode.Tabs -> {
            val panes = raw.panes.filter { seen.add(it) }
            if (panes.isEmpty()) DockNode.Empty
            else DockNode.Tabs(panes, raw.active?.takeIf { it in panes } ?: panes.first())
        }
        is RawNode.Split -> {
            val first = toDockNode(raw.first, seen)
            val second = toDockNode(raw.second, seen)
            when {
                first == DockNode.Empty -> second
                second == DockNode.Empty -> first
                else -> DockNode.Split(raw.axis, clampRatio(raw.ratio), first, second)
            }
        }
    }

    private fun paneIdOrNull(element: JsonElement): PaneId? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }?.let(::PaneId)

    private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()
    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}

internal fun clampRatio(ratio: Float): Float =
    if (ratio.isFinite()) ratio.coerceIn(DockManager.MIN_RATIO, DockManager.MAX_RATIO) else 0.5f
