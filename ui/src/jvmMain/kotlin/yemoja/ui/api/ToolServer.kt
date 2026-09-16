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
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.coroutines.CoroutineContext

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
        "describe",
        "Every type of item in the logbook, or one, with each field's kind, unit, role and words.",
        schemaOf(
            optional = mapOf("type" to "The type to describe, such as dive, or all if left out."),
        ),
    ) { request -> carried(onto) { tools.describe(request.text("type")) } }

    server.addTool(
        "list",
        "Every item of a type, whole, ${Tools.PAGE} at a time. Pass back the cursor to read the " +
            "next page.",
        schemaOf(
            required = mapOf("type" to "The type to list, such as dive."),
            optional = mapOf("cursor" to "The next cursor a previous page gave."),
        ),
    ) { request ->
        carried(onto) { tools.list(request.text("type").orEmpty(), request.text("cursor")) }
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

/** Serves [server] to one agent, reading its requests from [input] and answering on [output]. */
suspend fun serve(server: Server, input: Source, output: Sink): ServerSession =
    server.createSession(StdioServerTransport(input = input, output = output) {})

private suspend fun carried(onto: CoroutineContext, call: () -> Reply): CallToolResult {
    val reply = withContext(onto) { call() }
    return CallToolResult(listOf(TextContent(reply.text)), isError = reply.refused)
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

/** The manual's chapters an agent may read, being the definition of what it is reading. */
private val CHAPTERS: List<String> = listOf("data-fields.md", "data-format.md")

/** Where a chapter is found, as a resource's address. */
private const val RESOURCES = "yemoja://manual/"

/** What the server calls itself when an agent asks. */
private const val NAME = "yemoja"

/** The version of the tools, which moves when what they answer changes shape. */
private const val VERSION = "1"
