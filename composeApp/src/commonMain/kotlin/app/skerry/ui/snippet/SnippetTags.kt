package app.skerry.ui.snippet

/**
 * Парсинг строки тегов сниппета в список: разделители — запятая/пробел/перевод строки/таб,
 * ведущий `#` снимается, пустые и дубли отбрасываются, порядок сохраняется. Общий для desktop
 * ([SnippetsView]) и mobile (`MobileSnippetsView`) редакторов; регистр тегов не канонизируется
 * (в отличие от тегов хоста), см. [snippetTagSuggestions].
 */
fun parseSnippetTags(text: String): List<String> =
    text.split(',', ' ', '\n', '\t')
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotEmpty() }
        .distinct()
