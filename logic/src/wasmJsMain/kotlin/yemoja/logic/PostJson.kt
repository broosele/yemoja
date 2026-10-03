package yemoja.logic

/** Never called. The website's planner asks for no tide, and a browser's own fetch is asynchronous. */
actual fun postJson(url: String, body: String): Posted =
    throw UnsupportedOperationException("a browser build posts nothing to $url")
