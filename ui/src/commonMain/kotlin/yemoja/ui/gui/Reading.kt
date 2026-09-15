package yemoja.ui.gui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/*
 * The Manuals tab: the chapters and their sections on the left, the chosen chapter whole on the
 * right, and a section a place in it rather than a page of its own.
 *
 * See ../../../../../../gui/doc.md — `GUI-15`.
 */

/** Where in a chapter to be: a block to scroll to, asked for once and then forgotten. */
private class Wanted(val chapter: Chapter, val block: Int)

/**
 * The manual, read.
 *
 * [onOpen] is given a link that leads out of the manual, which the screens cannot follow
 * themselves; a link to a chapter or a section of one is followed here.
 */
@Composable
internal fun Manuals(chapters: List<Chapter>, onOpen: (String) -> Unit, kept: Kept) {
    // The first chapter, unfolded, the first time; afterwards wherever the reader left it.
    remember(kept) {
        if (!kept.opened) {
            kept.opened = true
            kept.chapter = chapters.firstOrNull()
            kept.unfolded = setOfNotNull(chapters.firstOrNull()?.file)
        }
        kept
    }
    val shown = kept.chapter
    val unfolded = kept.unfolded
    var wanted by remember { mutableStateOf<Wanted?>(null) }
    val goTo = { chapter: Chapter, block: Int ->
        kept.chapter = chapter
        wanted = Wanted(chapter, block)
        kept.unfolded = unfolded + chapter.file
    }
    Row(modifier = Modifier.fillMaxSize()) {
        Selectable {
            LazyColumn(
                state = kept.tree,
                modifier = Modifier.width(SELECTOR).fillMaxHeight().padding(GAP),
            ) {
                for (chapter in chapters) {
                    item(key = chapter.file) {
                        BranchLine(
                            label = chapter.title,
                            depth = 0,
                            open = if (chapter.sections.isEmpty()) {
                                null
                            } else {
                                chapter.file in unfolded
                            },
                            chosen = chapter === shown,
                            onToggle = {
                                kept.unfolded = if (chapter.file in unfolded) {
                                    unfolded - chapter.file
                                } else {
                                    unfolded + chapter.file
                                }
                            },
                            onClick = { goTo(chapter, 0) },
                        )
                    }
                    if (chapter.file !in unfolded) continue
                    items(chapter.sections, key = { chapter.file + "#" + it.anchor }) { section ->
                        Line(section.title, 1, chosen = false) { goTo(chapter, section.block) }
                    }
                }
            }
        }
        VerticalDivider()
        val chapter = shown
        Selectable {
            if (chapter == null) {
                Middle("no manual is bundled")
            } else {
                ChapterView(chapter, wanted, kept.page, onArrived = { wanted = null }) { target ->
                    followed(chapters, target)?.let { (to, block) -> goTo(to, block) }
                        ?: onOpen(target)
                }
            }
        }
    }
}

/** The chapter and block a link inside the manual leads to, or absent where it leads out. */
private fun followed(chapters: List<Chapter>, target: String): Pair<Chapter, Int>? {
    val file = target.substringBefore('#')
    val anchor = target.substringAfter('#', "")
    val chapter = chapters.firstOrNull { it.file == file } ?: return null
    if (anchor.isEmpty()) return chapter to 0
    val section = chapter.sections.firstOrNull { it.anchor == anchor } ?: return chapter to 0
    return chapter to section.block
}

/**
 * One chapter, whole, scrolled to wherever was asked for.
 *
 * Where a block sits is only known once it is laid out, so the scroll waits for the block it
 * wants to report its place rather than guessing at one.
 */
@Composable
private fun ChapterView(
    chapter: Chapter,
    wanted: Wanted?,
    scroll: ScrollState,
    onArrived: () -> Unit,
    onFollow: (String) -> Unit,
) {
    val tops = remember(chapter) { mutableStateMapOf<Int, Int>() }
    LaunchedEffect(chapter, wanted) {
        if (wanted == null || wanted.chapter !== chapter) return@LaunchedEffect
        val top = snapshotFlow { tops[wanted.block] }.filterNotNull().first()
        scroll.animateScrollTo(top)
        onArrived()
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(GAP * 2),
    ) {
        for ((at, block) in chapter.blocks.withIndex()) {
            val placed = if (block is Block.Heading) {
                Modifier.onGloballyPositioned { tops[at] = it.positionInParent().y.roundToInt() }
            } else {
                Modifier
            }
            Column(modifier = placed.widthIn(max = PAGE)) { BlockView(block, onFollow) }
        }
    }
}

@Composable
private fun BlockView(block: Block, onFollow: (String) -> Unit) {
    when (block) {
        is Block.Heading -> Text(
            text = styled(block.text, onFollow),
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineMedium
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            modifier = Modifier.padding(top = if (block.level == 1) 0.dp else GAP, bottom = HALF),
        )
        is Block.Paragraph -> Prose(styled(block.text, onFollow))
        is Block.Bullets -> for (item in block.items) Listed("•", styled(item, onFollow))
        is Block.Numbered -> for ((n, item) in block.items.withIndex()) {
            Listed("${n + 1}.", styled(item, onFollow))
        }
        is Block.Code -> Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
        ) {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(GAP),
            )
        }
        is Block.Table -> TableView(block, onFollow)
        Block.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = GAP))
    }
}

@Composable
private fun Prose(text: AnnotatedString) {
    Text(text = text, style = PROSE, modifier = Modifier.padding(vertical = HALF))
}

@Composable
private fun Listed(mark: String, text: AnnotatedString) {
    Row(modifier = Modifier.padding(start = GAP, top = 2.dp, bottom = 2.dp)) {
        Text(text = mark, style = PROSE, modifier = Modifier.width(MARK))
        Text(text = text, style = PROSE)
    }
}

@Composable
private fun TableView(table: Block.Table, onFollow: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = HALF)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (cell in table.header) {
                Text(
                    text = styled(cell, onFollow),
                    style = PROSE,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(HALF),
                )
            }
        }
        HorizontalDivider()
        for ((n, row) in table.rows.withIndex()) {
            val low = MaterialTheme.colorScheme.surfaceContainerLow
            val stripe = if (n % 2 == 1) low else Transparent
            Row(modifier = Modifier.fillMaxWidth().background(stripe)) {
                for (cell in row) {
                    Text(
                        text = styled(cell, onFollow),
                        style = PROSE,
                        modifier = Modifier.weight(1f).padding(HALF),
                    )
                }
            }
        }
    }
}

/** A line's runs as one styled string, its links clickable. */
@Composable
private fun styled(spans: List<Span>, onFollow: (String) -> Unit): AnnotatedString {
    val code = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    val link = TextLinkStyles(
        SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val strong = SpanStyle(fontWeight = FontWeight.Bold)
    val emphasis = SpanStyle(fontStyle = FontStyle.Italic)
    return buildAnnotatedString {
        for (span in spans) {
            when (span) {
                is Span.Plain -> append(span.text)
                is Span.Strong -> withStyle(strong) { append(span.text) }
                is Span.Emphasis -> withStyle(emphasis) { append(span.text) }
                is Span.Code -> withStyle(code) { append(span.text) }
                is Span.Link -> withLink(
                    LinkAnnotation.Clickable(span.target, link) { onFollow(span.target) },
                ) { append(span.text) }
            }
        }
    }
}

private val PROSE: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge

private val Transparent = androidx.compose.ui.graphics.Color.Transparent
private val PAGE = 720.dp
private val MARK = 24.dp
