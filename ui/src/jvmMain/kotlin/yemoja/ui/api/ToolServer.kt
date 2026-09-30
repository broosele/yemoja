package yemoja.ui.api

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.CoroutineContext
import yemoja.data.Stored

/*
 * The tools carried over MCP, which is what an agent speaks.
 *
 * See ../../../../../../api/doc.md — `API-4`.
 */

/**
 * An MCP server offering [tools], with the instructions and the manual's data chapters beside them.
 *
 * Every call is carried onto [onto] before it reaches the tools, because the Universe is not safe
 * to use from two threads and the protocol answers on several. A window passes the thread its
 * screens run on.
 */
fun toolServer(tools: Tools, onto: CoroutineContext): Server {
    val capabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(listChanged = false),
        resources = ServerCapabilities.Resources(listChanged = false, subscribe = false),
    )
    val server = Server(Implementation(NAME, VERSION), ServerOptions(capabilities), INSTRUCTIONS)

    server.addTool(
        "guide",
        "Read this first. How to work with this logbook: the rules, the tools, and every type of " +
            "item with its fields and units. The same text is in CLAUDE.md and AGENTS.md where " +
            "you were started.",
    ) { carried(onto) { Reply(tools.briefing()) } }

    server.addTool(
        "describe",
        "Every type of item in the logbook, or one, as JSON: each field's kind, unit, role, and " +
            "the words it takes or suggests. Read this or guide before reading items.",
        schemaOf(
            optional = mapOf("type" to "The type to describe, such as dive, or all if left out."),
        ),
    ) { request -> carried(onto) { tools.describe(request.text("type")) } }

    server.addTool(
        "list",
        "Every item of a type, ${Tools.PAGE} at a time, in the order the type keeps them. Name " +
            "the fields you need and only those come back; leave them out for whole items. Pass " +
            "back the cursor to read the next page.",
        ToolSchema(
            properties = buildJsonObject {
                property("type", "The type to list, such as dive.")
                property("cursor", "The next cursor a previous page gave.")
                putJsonObject("fields") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put(
                        "description",
                        "The fields wanted of each item, such as max_depth or " +
                            "environment.visibility. Whole items where left out.",
                    )
                }
            },
            required = listOf("type"),
        ),
    ) { request ->
        val fields = (request.arguments?.get("fields") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        carried(onto) { tools.list(request.text("type").orEmpty(), request.text("cursor"), fields) }
    }

    server.addTool(
        "get",
        "One item, whole, by its id.",
        schemaOf(required = mapOf("id" to "The item's id, such as 2026-04-28#1, without the @.")),
    ) { request -> carried(onto) { tools.get(request.text("id").orEmpty()) } }

    server.addTool(
        "series",
        "The samples of one series on an item, as pairs of a second and a value.",
        schemaOf(
            required = mapOf(
                "id" to "The item's id.",
                "path" to "The series, naming each entry on the way: profiles.p1.depth, or " +
                    "profiles.p1.pressures.g1 for one key of a keyed series.",
            ),
        ),
    ) { request ->
        val id = request.text("id").orEmpty()
        carried(onto) { tools.series(id, request.text("path").orEmpty()) }
    }

    server.addTool(
        "aggregate",
        "A figure over items you chose: count, sum, minimum, maximum, range, mean, weighted " +
            "mean, median or standard deviation. Says what it was based on.",
        ToolSchema(
            properties = buildJsonObject {
                putJsonObject("ids") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "The ids of the items, all of one type.")
                }
                property(
                    "path",
                    "The field on each item: max_depth, environment.bottom_temperature, or " +
                        "gas_sources.*.sac for every entry of a keyed collection.",
                )
                property(
                    "measure",
                    "count, sum, minimum, maximum, range, mean, weighted mean, median or " +
                        "standard deviation.",
                )
                property(
                    "weight",
                    "For a weighted mean, the field each value is weighted by, such as duration.",
                )
            },
            required = listOf("ids", "path", "measure"),
        ),
    ) { request ->
        val ids = (request.arguments?.get("ids") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        carried(onto) {
            tools.aggregate(
                ids,
                request.text("path").orEmpty(),
                request.text("measure").orEmpty(),
                request.text("weight"),
            )
        }
    }

    server.addTool(
        "stage_set",
        "Stage a value in one field of one item, for the user to review. Nothing changes until " +
            "they apply it.",
        ToolSchema(
            properties = buildJsonObject {
                property("id", "The item's id.")
                property(
                    "path",
                    "The field: rating, environment.visibility, or gas_sources.g1.usage for a " +
                        "field inside an entry of a keyed collection. A key the item does not " +
                        "have yet makes that entry.",
                )
                property(
                    "value",
                    "What to put there, written as a file writes it: a string for one value, a " +
                        "list for a field that holds several, an object for a block. Leave it " +
                        "out to clear the field.",
                )
            },
            required = listOf("id", "path"),
        ),
    ) { request ->
        val id = request.text("id").orEmpty()
        val path = request.text("path").orEmpty()
        val value = request.arguments?.get("value")?.let { storedOf(it) }
        carried(onto) { tools.stageSet(id, path, value) }
    }

    server.addTool(
        "stage_add",
        "Stage a new item of a type, for the user to review. It is named when they apply it.",
        ToolSchema(
            properties = buildJsonObject {
                property("type", "The type to add, such as dive_site.")
                putJsonObject("fields") {
                    put("type", "object")
                    put("description", "The fields it holds, each written as a file writes it.")
                }
            },
            required = listOf("type"),
        ),
    ) { request ->
        val type = request.text("type").orEmpty()
        val fields = (request.arguments?.get("fields") as? JsonObject)
            ?.mapValues { (_, held) -> storedOf(held) }
            .orEmpty()
        carried(onto) { tools.stageAdd(type, fields) }
    }

    server.addTool(
        "stage_delete",
        "Stage an item to be deleted, for the user to review. References to it are left dangling.",
        schemaOf(required = mapOf("id" to "The item's id.")),
    ) { request -> carried(onto) { tools.stageDelete(request.text("id").orEmpty()) } }

    server.addTool(
        "staged",
        "What is staged so far: each item, and each field as it is and as it would be.",
    ) { carried(onto) { tools.staged() } }

    server.addTool(
        "plan",
        "Calculate a dive plan: the stops, the runtime, the gas it takes and what the model has " +
            "to say against it. Reads no logbook and changes nothing, so it is always allowed. " +
            "The plan is written as planning-from-a-file.md describes.",
        ToolSchema(
            properties = buildJsonObject {
                putJsonObject("plan") {
                    put("type", "object")
                    put(
                        "description",
                        "The plan: lines, gases, and any setting it names. See the chapter " +
                            "planning-from-a-file.md for every field.",
                    )
                }
            },
            required = listOf("plan"),
        ),
    ) { request ->
        val plan = request.arguments?.get("plan")?.let { storedOf(it) }
        carried(onto) {
            if (plan == null) Reply("no plan was given", refused = true) else tools.plan(plan)
        }
    }

    server.addTool(
        "create_plan",
        "Stage a dive plan as a profile on a dive, for the user to review. Nothing changes until " +
            "they apply it. The plan is written as for the plan tool.",
        ToolSchema(
            properties = buildJsonObject {
                putJsonObject("plan") {
                    put("type", "object")
                    put("description", "The plan, as for the plan tool.")
                }
                property("dive", "The dive's id. Leave it out to make a dive for the plan.")
                property("name", "What to call the plan within the dive, such as Plan A.")
            },
            required = listOf("plan"),
        ),
    ) { request ->
        val plan = request.arguments?.get("plan")?.let { storedOf(it) }
        val dive = request.text("dive")
        val name = request.text("name") ?: PLAN
        carried(onto) {
            if (plan == null) {
                Reply("no plan was given", refused = true)
            } else {
                tools.createPlan(plan, dive, name)
            }
        }
    }

    server.addTool(
        "files",
        "Where the logbook's files are and how to treat them, for the rare case the other tools " +
            "cannot do what is asked. Refused unless the user has ticked Allow raw file access.",
    ) { carried(onto) { tools.files() } }

    server.addResource(
        uri = "$RESOURCES${BRIEFING}",
        name = BRIEFING,
        description = "How to work with this logbook, and what it holds. Read this first.",
        mimeType = "text/markdown",
    ) { request ->
        val text = withContext(onto) { tools.briefing() }
        ReadResourceResult(listOf(TextResourceContents(text, request.uri, "text/markdown")))
    }

    for (chapter in CHAPTERS) {
        server.addResource(
            uri = "$RESOURCES$chapter",
            name = chapter,
            description = "The manual's chapter $chapter.",
            mimeType = "text/markdown",
        ) { request ->
            val text = bundled("manual/$chapter")
            ReadResourceResult(listOf(TextResourceContents(text, request.uri, "text/markdown")))
        }
    }
    return server
}

/** What a plan is called where the agent does not say, which is what the window calls its first. */
private const val PLAN = "Plan A"

/** Serves [server] to one agent, reading its requests from [input] and answering on [output]. */
suspend fun serve(server: Server, input: Source, output: Sink): ServerSession =
    server.createSession(StdioServerTransport(input = input, output = output) {})

private suspend fun carried(onto: CoroutineContext, call: () -> Reply): CallToolResult {
    val reply = withContext(onto) { call() }
    return CallToolResult(listOf(TextContent(reply.text)), isError = reply.refused)
}

/**
 * What an agent sent, as the neutral shape every source hands a value over in.
 *
 * A field reads its own value, so what arrives has only to keep its shape: a list stays a list, an
 * object stays a group, and everything else is one leaf. Reading a list as nothing is what made
 * `stage_set` clear a dive's gear instead of copying it. `DATA-64`.
 */
private fun storedOf(held: JsonElement): Stored = when (held) {
    is JsonArray -> Stored.Elements(held.map { storedOf(it) })
    is JsonObject -> Stored.Members(held.mapValues { (_, each) -> storedOf(each) })
    is JsonPrimitive -> Stored.Leaf(held.contentOrNull)
}

private fun CallToolRequest.text(name: String): String? =
    (arguments?.get(name) as? JsonPrimitive)?.contentOrNull

private fun schemaOf(
    required: Map<String, String> = emptyMap(),
    optional: Map<String, String> = emptyMap(),
): ToolSchema =
    ToolSchema(
        properties = buildJsonObject {
            for ((name, description) in required + optional) property(name, description)
        },
        required = required.keys.toList(),
    )

private fun JsonObjectBuilder.property(name: String, description: String) {
    putJsonObject(name) {
        put("type", "string")
        put("description", description)
    }
}

private fun bundled(path: String): String {
    val stream = Bundled::class.java.getResourceAsStream("/$path")
        ?: error("$path is not bundled, and the build should have done that")
    return stream.bufferedReader().use { it.readText() }
}

/** Something to look up resources from. A function has no class of its own to ask. */
private object Bundled

/**
 * The name of every tool the server offers, which is also what the window allows an agent to call
 * without refusing it. A test holds the two to each other, so a tool added to one is not missing
 * from the other.
 */
internal val TOOL_NAMES: List<String> = listOf(
    "guide", "describe", "list", "get", "series", "aggregate",
    "stage_set", "stage_add", "stage_delete", "staged", "plan", "create_plan", "files",
)

/** The manual's chapters an agent may read, being the definition of what it is reading. */
private val CHAPTERS: List<String> = listOf("data-fields.md", Tools.FORMAT)

/** What the briefing is called as a resource. On disk it takes the names agents read. */
internal const val BRIEFING = "briefing.md"

/** Where a chapter is found, as a resource's address. */
private const val RESOURCES = Tools.RESOURCES

/** What the server calls itself when an agent asks. */
private const val NAME = "yemoja"

/** The version of the tools, which moves when what they answer changes shape. */
private const val VERSION = "1"
