package org.ntust.app.tigerduck.auth

import java.io.File

/**
 * `docs/account-state.md`, and the stores the phone app's sources declare: each SharedPreferences
 * and DataStore file with its keys, DataCache's files and folders, and the databases.
 * [AccountStateInventoryTest] checks each against the other, and `AccountSwitchTest` fills what
 * the inventory gives the school account.
 *
 * Read from the sources with regular expressions, as [SignOutClearingTest] reads `logout()`. A
 * store opened under a name this cannot work out, or a key it cannot read, is a problem of its own
 * rather than a store gone unseen. A key handed through a parameter is read where the parameter is
 * named instead: by its constant, which a source that opens one file names `KEY_…`, `K_…` or
 * `…_KEY`.
 */
internal class AccountStateInventory(
    /** The phone app's Kotlin sources, by path under `app/src`. */
    val sources: Map<String, String>,
    /** The inventory's text. */
    val text: String,
) {

    /** One table row: the store, its name, its scope and what clears it. */
    data class Row(val store: String, val name: String, val scope: String, val clearer: Clearer?)

    /** The function that clears a row's store, by its file's name, and what it must call in its place. */
    data class Clearer(val file: String, val function: String, val via: String?)

    /** A key a source reads or writes: its file, its name (`prefix*` for a family), its type and default. */
    data class Key(val file: String, val name: String, val type: String?, val default: String?, val source: String)

    private val rowPattern = Regex("""^\|\s*`?([\w.-]+)`?\s*\|\s*`([^`]+)`\s*\|\s*(\w+)\s*\|(.*)\|\s*$""")
    private val clearerPattern = Regex("""`([\w.]+\.kt)`\s+`(\w+)`(?:\s+via\s+`([^`]+)`)?""")

    /** The inventory's rows. */
    val rows: List<Row> = text.lines().mapNotNull { line ->
        val match = rowPattern.matchEntire(line.trim()) ?: return@mapNotNull null
        val (store, name, scope, rest) = match.destructured
        val clearer = clearerPattern.find(rest)?.destructured?.let { (file, function, via) ->
            Clearer(file, function, via.ifEmpty { null })
        }
        Row(store, name, scope, clearer)
    }

    /** What the sources hold that this cannot read: a store's name or a key. */
    private val unreadable = mutableListOf<String>()

    // ---- reading the sources ----

    private val constantPattern = Regex("""\bconst\s+val\s+(\w+)\s*=\s*"((?:[^"\\$]|\\.)*)"""")

    /** Each source's string constants, by name. */
    private val constants: Map<String, Map<String, String>> by lazy {
        sources.mapValues { (_, text) -> constantPattern.findAll(text).associate { it.groupValues[1] to it.groupValues[2] } }
    }

    /** The source that declares the class or object [name], if one does. */
    private fun declaring(name: String): String? =
        sources.entries.firstOrNull { Regex("""\b(?:class|object|interface)\s+$name\b""").containsMatchIn(it.value) }?.key

    /**
     * The arguments of the call whose `(` is at [open], split at its own commas, with strings and
     * nested brackets kept whole; null when the call never closes.
     */
    private fun arguments(text: String, open: Int): List<String>? {
        val found = mutableListOf<String>()
        var depth = 0
        var start = open + 1
        var index = open
        var quoted = false
        while (index < text.length) {
            val char = text[index]
            when {
                quoted -> if (char == '\\') index++ else if (char == '"') quoted = false
                char == '"' -> quoted = true
                char in "([{" -> depth++
                char in ")]}" -> if (--depth == 0) {
                    found += text.substring(start, index).trim()
                    return found.filter { it.isNotEmpty() }
                }
                char == ',' && depth == 1 -> {
                    found += text.substring(start, index).trim()
                    start = index + 1
                }
            }
            index++
        }
        return null
    }

    /**
     * What the expression [expr] in [source] names: a string, `prefix*` for a template or for
     * anything joined on after a `+`, a constant of [source]'s or of the class it is qualified
     * by, a DataStore key of [source]'s, or what a function of [source]'s returns.
     */
    fun resolve(expr: String, source: String): String? {
        val text = expr.trim().removePrefix("name =").trim()
        val head = text.substringBefore(" + ").trim()
        val joined = head != text
        val own = sources[source].orEmpty()
        val value = when {
            head.startsWith("\"") && head.endsWith("\"") && head.length >= 2 -> head.substring(1, head.length - 1)
            Regex("""^(?:\w+\.)?[A-Z][A-Z0-9_]*$""").matches(head) -> {
                val owner = head.substringBefore('.', "")
                val name = head.substringAfterLast('.')
                owner.takeIf { it.isNotEmpty() }?.let(::declaring)?.let { constants[it]?.get(name) }
                    ?: constants[source]?.get(name)
                    ?: Regex("""\bval\s+$name\s*=\s*\w+PreferencesKey\(\s*"([^"]+)"\s*\)""").find(own)?.groupValues?.get(1)
            }
            Regex("""^\w+\(.*\)$""").matches(head) -> {
                val function = head.substringBefore('(')
                Regex("""\bfun\s+$function\([^)]*\)(?:\s*:\s*String)?\s*=\s*([^\n]+)""").find(own)
                    ?.groupValues?.get(1)?.takeIf { function !in it }?.let { return resolve(it, source)?.let { name -> if (joined && '*' !in name) "$name*" else name } }
            }
            else -> null
        } ?: return null
        return when {
            '$' in value -> value.substringBefore('$') + "*"
            joined -> "$value*"
            else -> value
        }
    }

    private val openings = Regex("""\b(getSharedPreferences|EncryptedSharedPreferences\.create|preferencesDataStore)\(""")

    /** Each opening of a store in the sources: its source, where, and its name. */
    private data class Opening(val source: String, val at: Int, val name: String)

    private val allOpenings: List<Opening> by lazy {
        sources.flatMap { (path, text) ->
            openings.findAll(text).mapNotNull { match ->
                val args = arguments(text, match.range.last) ?: return@mapNotNull null
                val expr = (if (match.groupValues[1] == "EncryptedSharedPreferences.create") args.getOrNull(1) else args.firstOrNull())
                    ?: return@mapNotNull null
                val name = resolve(expr, path)
                if (name == null) {
                    unreadable += "$path opens a store whose name this cannot read: `$expr`"
                    null
                } else {
                    Opening(path, match.range.first, name)
                }
            }.toList()
        }
    }

    /**
     * Sources handed a file another source opens, under no constant of their own: the token stores
     * and the owed unregister share the credential store CredentialManager opens.
     */
    private val sharedFiles = mapOf(
        "V3TokenStore.kt" to "tigerduck_credentials",
        "PendingUnregisterPrefs.kt" to "tigerduck_credentials",
    )

    /**
     * Each SharedPreferences and DataStore file the sources open, with the sources that read and
     * write it: those that open it, and the source of a class handed it under that class's own
     * constant, `PinAcceptanceStore(getSharedPreferences(PinAcceptanceStore.FILE, …))`.
     */
    val prefsFiles: Map<String, Set<String>> by lazy {
        val found = mutableMapOf<String, MutableSet<String>>()
        allOpenings.forEach { opening ->
            found.getOrPut(opening.name) { mutableSetOf() } += opening.source
            val text = sources.getValue(opening.source)
            Regex("""^\s*(\w+)\.[A-Z][A-Z0-9_]*\b""").find(text.substring(opening.at).substringAfter('('))
                ?.groupValues?.get(1)?.let(::declaring)?.let { found.getValue(opening.name) += it }
        }
        sharedFiles.forEach { (file, store) ->
            sources.keys.filter { it.endsWith("/$file") }.forEach { found.getOrPut(store) { mutableSetOf() } += it }
        }
        found
    }

    /** What the sources pass a key as without naming it, and where its keys are read instead. */
    private val namedElsewhere = mapOf(
        // DisplayScale's entries; see [keys].
        "scale.key" to "DisplayScale.kt",
        // The inbox's K_FETCHED, or K_FETCHED_PREFIX and another folder's name.
        "fetchedKey(folder)" to "MailStateStore.kt",
    )

    private val keyCall = Regex(
        """(\.?)\b(get(?:Boolean|Int|Long|Float|String|StringSet)|put(?:Boolean|Int|Long|Float|String|StringSet)|remove|contains)\(""",
    )

    /** The file each SharedPreferences variable, editor or function of [path] is, by its name. */
    private fun receivers(path: String, text: String): Map<String, String> {
        val found = mutableMapOf<String, String>()
        val declaration = Regex(
            """(?:(?:val|var)\s+(\w+)(?:\s*:\s*[\w.<>?]+)?\s*(?:=|by\s+lazy\s*\{)|fun\s+(\w+)\([^)]*\)(?:\s*:\s*[\w.]+)?\s*=)\s*(?:[\w.]+\.)?$""",
        )
        allOpenings.filter { it.source == path }.forEach { opening ->
            val before = text.substring(maxOf(0, opening.at - 200), opening.at)
            declaration.find(before)?.let { found[it.groupValues[1].ifEmpty { it.groupValues[2] }] = opening.name }
        }
        Regex("""(?:val|var)\s+(\w+)\s*=\s*(\w+)\.edit\(\)""").findAll(text).forEach { match ->
            found[match.groupValues[2]]?.let { found[match.groupValues[1]] = it }
        }
        return found
    }

    /**
     * The first name of the call chain whose next link is the `.` at [dot]: `prefs` in
     * `prefs.edit().remove`. A link may start its own line, as a chain broken over lines does.
     */
    private fun chainStart(text: String, dot: Int): String? {
        var index = dot - 1
        while (index >= 0) {
            while (index >= 0 && text[index].isWhitespace()) index--
            if (index >= 0 && text[index] == ')') {
                var depth = 0
                while (index >= 0) {
                    if (text[index] == ')') depth++
                    if (text[index] == '(' && --depth == 0) break
                    index--
                }
                index--
            }
            if (index < 0 || !text[index].isJavaIdentifierPart()) return null
            val stop = index
            while (index >= 0 && text[index].isJavaIdentifierPart()) index--
            val name = text.substring(index + 1, stop + 1)
            if (index >= 0 && text[index] == '?') index--
            if (index < 0 || text[index] != '.') return name
            index--
        }
        return null
    }

    /**
     * For a call with no receiver at [at], inside `prefs.edit().apply { … }` or `prefs.edit { … }`,
     * the first name of the editor's chain; null anywhere else.
     */
    private fun editorBlockStart(text: String, at: Int): String? {
        var depth = 0
        var index = at - 1
        while (index >= 0) {
            when (text[index]) {
                '}' -> depth++
                '{' -> if (depth-- == 0) {
                    var end = index - 1
                    while (end >= 0 && text[end].isWhitespace()) end--
                    val dot = text.lastIndexOf('.', end)
                    if (dot < 0 || text.substring(dot + 1, end + 1) !in setOf("apply", "edit", "run", "also")) return null
                    return chainStart(text, dot)
                }
            }
            index--
        }
        return null
    }

    /**
     * The keys the sources read and write, by file. A call on a SharedPreferences, an editor of
     * one, a function handing one out or inside an editor's block is placed in the file the
     * receiver is ([receivers]); a call on another `prefs` or editor, in the file its source reads
     * and writes when there is one. Also: the constants of keys in a source of one file; the keys
     * of a DataStore's `…PreferencesKey`; those `AppPreferences.getOrCreateString` takes from
     * other sources; and DisplayScale's entries, which name the keys AppPreferences reads them
     * under.
     */
    val keys: Map<String, List<Key>> by lazy {
        val found = mutableListOf<Key>()
        sources.forEach { (path, text) ->
            val named = receivers(path, text)
            val fallback = prefsFiles.filterValues { path in it }.keys.singleOrNull()
            keyCall.findAll(text).forEach { match ->
                val call = match.groupValues[2]
                val args = arguments(text, match.range.last) ?: return@forEach
                val wanted = if (call.startsWith("get") || call.startsWith("put")) 2 else 1
                if (args.size != wanted) return@forEach
                val receiver = if (match.groupValues[1] == ".") chainStart(text, match.range.first) else editorBlockStart(text, match.range.first)
                val file = named[receiver]
                    ?: fallback?.takeIf { receiver != null && Regex("""(?i)(prefs|preferences|editor|edit)$""").containsMatchIn(receiver) }
                    ?: return@forEach
                val expr = args[0]
                val name = resolve(expr, path)
                if (name == null) {
                    // A parameter or a local the key is handed in, read at its constant.
                    if (!Regex("""^[a-z]\w*$""").matches(expr) && expr !in namedElsewhere) {
                        unreadable += "$path reads or writes a key of $file this cannot read: `$expr`"
                    }
                    return@forEach
                }
                val type = Regex("""^(?:get|put)(\w+)$""").find(call)?.groupValues?.get(1)
                val default = if (call.startsWith("get")) args[1] else null
                found += Key(file, name, type, default, path)
            }
            if (fallback != null) {
                constants[path].orEmpty().forEach { (name, value) ->
                    if (Regex("""^(?:KEY|K)_\w+$|^\w+_KEY$""").matches(name) && !name.startsWith("EXTRA_")) {
                        found += Key(fallback, if (name.endsWith("_PREFIX")) "$value*" else value, null, null, path)
                    }
                }
                Regex("""\b(\w+)PreferencesKey\(\s*([^)]+?)\s*\)""").findAll(text).forEach { match ->
                    resolve(match.groupValues[2], path)?.let { found += Key(fallback, it, null, null, path) }
                }
            }
            Regex("""getOrCreateString\(\s*([^,()]+?)\s*\)""").findAll(text).forEach { match ->
                resolve(match.groupValues[1], path)?.let { found += Key("tigerduck_prefs", it, "String", "null", path) }
            }
            if (path.endsWith("/DisplayScale.kt")) {
                Regex("""^\s*[A-Z]\w*\(\s*"(\w+)"\s*,""", RegexOption.MULTILINE).findAll(text).forEach {
                    found += Key("tigerduck_prefs", it.groupValues[1], "Float", null, path)
                }
            }
        }
        // A key's constant says only its name, and a write no default: a read says both.
        found.groupBy { it.file to it.name }.values
            .map { same -> same.firstOrNull { it.default != null } ?: same.firstOrNull { it.type != null } ?: same.first() }
            .groupBy { it.file }
    }

    private val dataCache: String get() = sources.keys.single { it.endsWith("/data/cache/DataCache.kt") }

    /**
     * The names of the files and folders DataCache keeps: `prefix_*.json` for a family, `name/`
     * for a folder, and `*.tmp` for what a write a process death cut short leaves beside its file.
     */
    val cacheFiles: Set<String> by lazy {
        val text = sources.getValue(dataCache)
        Regex(""""([\w-]+\.json)"""").findAll(text).map { it.groupValues[1] }.toSet() +
            Regex(""""([\w-]+_)\$\{?\w+}?\.json"""").findAll(text).map { it.groupValues[1] + "*.json" } +
            Regex("""const\s+val\s+\w+_DIR\s*=\s*"([\w-]+)"""").findAll(text).map { it.groupValues[1] + "/" } +
            listOfNotNull("*.tmp".takeIf { "\".tmp\"" in text })
    }

    /**
     * Where DataCache keeps [name]: `cache` (`cacheDir/TigerDuckCache`), `data`
     * (`filesDir/TigerDuckData`) or `files` (`filesDir`), as the first line naming it says.
     */
    fun cacheRoot(name: String): String? {
        val text = sources.getValue(dataCache)
        val names = aliases(name, listOf(dataCache))
        text.lines().filter { line -> quoted(name) in line || names.any { Regex("""\b$it\b""").containsMatchIn(line) } }
            .forEach { line ->
                when {
                    Regex("""\b(?:saveToUserData\w*|loadFromUserData|userDataDir)\b""").containsMatchIn(line) -> return "data"
                    "context.filesDir" in line -> return "files"
                    Regex("""\b(?:save|load)\s*[(<]|\bcacheDir\b""").containsMatchIn(line) -> return "cache"
                }
            }
        return null
    }

    /** The databases the sources name. */
    val databases: Set<String> by lazy {
        sources.values.flatMap { text -> Regex(""""([\w-]+\.db)"""").findAll(text).map { it.groupValues[1] }.toList() }.toSet()
    }

    // ---- what a clearer does ----

    /** The source named [file]: one of them, or null with none or several. */
    fun sourceNamed(file: String): String? = sources.keys.filter { it.substringAfterLast('/') == file }.singleOrNull()

    /**
     * The body of the function [name] declares in [text]: its block, or its expression with the
     * lines indented under it, or the block of an object handed over as `name = object : … {`.
     * A declaration with neither, as in an interface, is passed over.
     */
    fun body(text: String, name: String): String? {
        Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?$name\s*\(""").findAll(text).forEach { match ->
            val close = matching(text, match.range.last, '(', ')') ?: return@forEach
            val lineEnd = text.indexOf('\n', close).let { if (it < 0) text.length else it }
            val rest = text.substring(close + 1, lineEnd)
            val brace = rest.indexOf('{')
            val equals = rest.indexOf('=')
            when {
                equals >= 0 && (brace < 0 || equals < brace) -> return indented(text, match.range.first, close + 1 + equals)
                brace >= 0 -> return block(text, close + 1 + brace)
            }
        }
        return Regex("""\b$name\s*=\s*object\b[^{]*\{""").find(text)?.let { block(text, it.range.last) }
    }

    private fun matching(text: String, open: Int, opening: Char, closing: Char): Int? {
        var depth = 0
        for (index in open until text.length) {
            if (text[index] == opening) depth++
            if (text[index] == closing && --depth == 0) return index
        }
        return null
    }

    private fun block(text: String, open: Int): String? = matching(text, open, '{', '}')?.let { text.substring(open, it + 1) }

    private fun indent(text: String, at: Int): Int {
        val start = text.lastIndexOf('\n', at) + 1
        return text.substring(start).takeWhile { it == ' ' }.length
    }

    /** From [from] to the end of its line, and every line after it indented deeper than [declaration]'s. */
    private fun indented(text: String, declaration: Int, from: Int): String {
        val own = indent(text, declaration)
        var end = text.indexOf('\n', from).let { if (it < 0) text.length else it }
        while (end < text.length) {
            val next = text.indexOf('\n', end + 1).let { if (it < 0) text.length else it }
            val line = text.substring(end + 1, next)
            if (line.isNotBlank() && line.takeWhile { it == ' ' }.length <= own) break
            end = next
        }
        return text.substring(from, end)
    }

    /** [name] as a source quotes it: `"name"`, `"prefix` for a family, `suffix"` for `*suffix`. */
    private fun quoted(name: String): String {
        val literal = name.removeSuffix("/")
        return when {
            literal.startsWith("*") -> literal.removePrefix("*") + "\""
            '*' in literal -> "\"" + literal.substringBefore('*')
            else -> "\"$literal\""
        }
    }

    /**
     * The names that stand for [value] in [paths]: the constants holding it (its fixed part for
     * a family), and each function or property whose declaration, with the lines indented under
     * it, quotes it or names one of those constants.
     */
    fun aliases(value: String, paths: Collection<String>): Set<String> {
        val literal = value.removeSuffix("/").replace("*", "")
        val found = mutableSetOf<String>()
        paths.forEach { path ->
            constants[path].orEmpty().forEach { (name, held) -> if (held == literal) found += name }
        }
        paths.forEach { path ->
            val text = sources[path] ?: return@forEach
            Regex("""\b(?:fun|val|var)\s+(?:<[^>]*>\s*)?(\w+)""").findAll(text).forEach { match ->
                val chunk = indented(text, match.range.first, match.range.first)
                if (quoted(value) in chunk || found.any { Regex("""\b$it\b""").containsMatchIn(chunk) }) found += match.groupValues[1]
            }
        }
        return found
    }

    /**
     * Whether [body] removes the key [name]: `remove(…)` of it, an alias of it set to an empty
     * value, or for a family `startsWith(…)` of it beside a removal.
     */
    private fun removes(body: String, name: String, aliases: Set<String>): Boolean {
        val names = (aliases.map { Regex.escape(it) + "\\b" } + Regex.escape(quoted(name))).joinToString("|")
        val emptied = """(?:null|false|0L?|0f|""|emptySet\(\)|emptyList\(\)|emptyMap\(\))"""
        val assigned = aliases.joinToString("|") { Regex.escape(it) }.ifEmpty { "(?!)" }
        return Regex("""remove\(\s*(?:$names)""").containsMatchIn(body) ||
            Regex("""\b(?:$assigned)\s*=\s*$emptied""").containsMatchIn(body) ||
            ('*' in name && Regex("""startsWith\(\s*(?:$names)""").containsMatchIn(body) && "remove" in body)
    }

    /**
     * Whether a statement of [body] that names [name] or one of [aliases] deletes: the statement
     * of the innermost block around the name that is no lambda taking parameters, a chain broken
     * over lines included. It deletes when it calls `delete()` or `deleteRecursively()`, or a
     * function of [source]'s among [aliases] that does.
     */
    private fun deletes(clearer: String, name: String, aliases: Set<String>, source: String): Boolean {
        fun deleting(text: String) = "delete()" in text || "deleteRecursively()" in text
        val pattern = Regex((aliases.map { "\\b" + Regex.escape(it) + "\\b" } + Regex.escape(quoted(name))).joinToString("|"))
        return pattern.findAll(clearer).any { mention ->
            var open = mention.range.first
            var depth = 0
            while (open > 0) {
                open--
                when (clearer[open]) {
                    '}' -> depth++
                    '{' -> if (depth > 0) depth-- else if (!Regex("""^\{[^{}]*->""").containsMatchIn(clearer.substring(open))) break
                }
            }
            val close = matching(clearer, open, '{', '}') ?: clearer.length
            val statement = statement(clearer, open + 1, close, mention.range.first)
            deleting(statement) || aliases.any { alias ->
                Regex("""\b$alias\(""").containsMatchIn(statement) && body(source, alias)?.let(::deleting) == true
            }
        }
    }

    /** The statement of the block from [start] to [end] that holds [at]. */
    private fun statement(body: String, start: Int, end: Int, at: Int): String {
        val breaks = mutableListOf(start)
        var depth = 0
        for (index in start until end) {
            when (body[index]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                '\n' -> if (depth == 0) {
                    val next = body.substring(index + 1, end).trimStart()
                    if (!next.startsWith(".") && !next.startsWith("?.")) breaks += index + 1
                }
            }
        }
        val from = breaks.last { it <= at }
        val to = breaks.firstOrNull { it > at } ?: end
        return body.substring(from, to)
    }

    // ---- the check ----

    /** Every store the sources declare, as its row would name it: a file an account clears whole, without its keys. */
    val declared: Set<Pair<String, String>> by lazy {
        val whole = rows.filter { it.store == "prefs" && it.scope in ACCOUNT_SCOPES }.map { it.name }.toSet()
        prefsFiles.keys.map { "prefs" to it }.toSet() +
            cacheFiles.map { "cache" to it } +
            databases.map { "database" to it } +
            keys.filterKeys { it !in whole }.flatMap { (file, keys) -> keys.map { file to it.name } }
    }

    /**
     * What is wrong with the inventory: a store or key this cannot read, a store with no row, a
     * row with nothing declared, a scope it does not know, a file outliving the accounts whose keys
     * are not listed, and an account's row that names nothing that clears it, or a function that
     * no longer does.
     */
    fun problems(): List<String> {
        val errors = mutableListOf<String>()
        // Read first: each reading notes what it cannot read.
        val declared = declared
        errors += unreadable.distinct()
        rows.groupBy { it.store to it.name }.filterValues { it.size > 1 }.keys.forEach { (store, name) ->
            errors += "$store `$name` has more than one row"
        }
        val listed = rows.map { it.store to it.name }.toSet()
        (declared - listed).sortedBy { it.toString() }.forEach { (store, name) ->
            errors += "$store `$name` has no row in docs/account-state.md"
        }
        (listed - declared).sortedBy { it.toString() }.forEach { (store, name) ->
            errors += "$store `$name` is listed but no longer declared"
        }
        rows.forEach { row ->
            val label = "${row.store} `${row.name}`"
            when {
                row.scope !in SCOPES -> errors += "$label has unknown scope ${row.scope}"
                row.scope == "keys" && row.store != "prefs" -> errors += "$label: only a file's keys are listed apart"
                row.scope == "device" && row.store == "prefs" -> errors += "$label: a file outliving the accounts lists its keys (scope keys)"
                row.scope in ACCOUNT_SCOPES -> clearingProblem(row)?.let { errors += "$label$it" }
            }
        }
        return errors
    }

    private fun clearingProblem(row: Row): String? {
        val clearer = row.clearer ?: return " belongs to an account but names nothing that clears it"
        val path = sourceNamed(clearer.file) ?: return ": no single source is named ${clearer.file}"
        val body = body(sources.getValue(path), clearer.function) ?: return ": no `${clearer.function}` in ${clearer.file}"
        val clears = when {
            clearer.via != null -> clearer.via in body
            row.store == "prefs" -> path in prefsFiles[row.name].orEmpty() && (
                ".clear()" in body ||
                    keys[row.name].orEmpty().all { removes(body, it.name, aliases(it.name, prefsFiles.getValue(row.name))) }
                )
            row.store == "cache" -> deletes(body, row.name, aliases(row.name, listOf(dataCache)), sources.getValue(dataCache))
            row.store == "database" -> false
            else -> removes(body, row.name, aliases(row.name, prefsFiles[row.store].orEmpty()))
        }
        return if (clears) null else ": `${clearer.function}` in ${clearer.file} does not clear it"
    }

    companion object {
        /** The scopes a row may give; see the inventory's opening. */
        val SCOPES = setOf("school", "mail", "library", "owed", "device", "keys")

        /** The scopes a sign-out clears, whose rows name what clears them. */
        val ACCOUNT_SCOPES = setOf("school", "mail", "library", "owed")

        val appDir: File by lazy { locateApp() }

        val inventoryFile: File get() = File(appDir.parentFile, "docs/account-state.md")

        /** The phone app's Kotlin sources, main, both flavors and debug, by path under `app/src`. */
        fun appSources(): Map<String, String> = listOf("main", "play", "fdroid", "debug").flatMap { set ->
            File(appDir, "src/$set/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
                .map { it.relativeTo(File(appDir, "src")).invariantSeparatorsPath to it.readText() }
                .toList()
        }.toMap()

        /** The app's sources and the inventory as they are in the repository. */
        val app: AccountStateInventory by lazy { AccountStateInventory(appSources(), inventoryFile.readText()) }

        private fun locateApp(): File {
            var dir: File? = File("").absoluteFile
            while (dir != null) {
                val here = dir
                if (File(here, "app/src/main/java").isDirectory) return File(here, "app")
                if (File(here, "src/main/java").isDirectory) return here
                dir = here.parentFile
            }
            throw AssertionError("Could not locate the :app module from ${File("").absolutePath}")
        }
    }
}
