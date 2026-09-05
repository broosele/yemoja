package yemoja.logic

import yemoja.data.Item
import yemoja.data.ItemWriter
import yemoja.data.Units
import yemoja.data.json.DiskFileStore
import yemoja.data.json.Json
import yemoja.data.json.LogbookReader
import yemoja.data.json.LogbookWriter
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Writing a real logbook back, item by item, and finding it unchanged.
 *
 * The unit tests ask whether one item survives one file. This asks whether the whole of a
 * populated logbook does, against the real fixture and the real libraries, which is the only
 * thing that can show it. Here rather than in commonTest because it needs a folder.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */
class WrittenLogbookTest {

    /** The fixture, copied so that writing to it changes nothing anybody keeps. */
    private fun copied(): File {
        val to = File.createTempFile("yemoja", "").let { it.delete(); it.mkdirs(); it }
        File("../fixtures/cousteau").copyRecursively(to, overwrite = true)
        return to
    }

    /** An item as a file would hold it, which is the form two items are the same in. */
    private fun written(item: Item): String = Json.write(ItemWriter.write(item, Units.DEFAULT))

    @Test
    fun `every item of a populated logbook survives being written back`() {
        val folder = copied()
        val store = DiskFileStore(folder.path, "../libraries")
        val before = LogbookReader.read(store, Types.ALL)

        // The logbook's own only. A library item is in the set and is not the logbook's to write,
        // which `JSON-26` is about; asking the store where the type lives is how they are told
        // apart without the set having to mark them.
        val mine = Types.ALL.flatMap { type ->
            before.allOf(type).map { type to before.idOf(it)!! }
        }.filter { (type, id) ->
            File(folder, "${type.name}.json").exists() ||
                File(folder, "${type.name}/$id.json").exists()
        }
        val was = mine.associate { (_, id) -> id to written(before[id]!!) }
        for ((type, id) in mine) LogbookWriter.write(store, type, id, before[id]!!)

        val after = LogbookReader.read(store, Types.ALL)
        assertEquals(before.size, after.size, "no item was lost or gained")
        for ((id, before) in was) {
            assertEquals(before, written(after[id]!!), "$id came back different")
        }
        assertEquals(true, was.size > 500, "and there were ${was.size} of them")
    }

    @Test
    fun `writing an unchanged logbook twice changes nothing the second time`() {
        // What "no diff on save" rests on: the written form is settled after one pass, so a save
        // that changed nothing leaves the files alone.
        val folder = copied()
        val store = DiskFileStore(folder.path, "../libraries")
        val set = LogbookReader.read(store, Types.ALL)
        val dives = set.allOf(Types.DIVE).map { set.idOf(it)!! }
        for (id in dives) LogbookWriter.write(store, Types.DIVE, id, set[id]!!)
        val once = dives.associateWith { File(folder, "dive/$it.json").readText() }

        val again = LogbookReader.read(store, Types.ALL)
        for (id in dives) LogbookWriter.write(store, Types.DIVE, id, again[id]!!)
        for (id in dives) {
            val now = File(folder, "dive/$id.json").readText()
            assertEquals(once[id], now, "$id moved on the second save")
        }
    }
}
