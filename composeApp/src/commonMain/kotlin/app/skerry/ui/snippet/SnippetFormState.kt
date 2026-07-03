package app.skerry.ui.snippet

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Состояние формы создания/правки сниппета: редактируемые поля как Compose-state (по образцу
 * [app.skerry.ui.host.NewConnectionFormState]). Общее для desktop-редактора (`SnippetEditor` в
 * [SnippetsView]) и мобильного листа (`MobileSnippetEditSheet`) — одна точка правды по seed'у,
 * валидации и сборке черновика.
 *
 * [shortcut] правится только на desktop (захват аккорда); мобильный лист лишь проносит его из
 * правимого сниппета, чтобы Save не терял назначенный хоткей.
 */
@Stable
class SnippetFormState private constructor(private val editingId: String?) {
    var label: String by mutableStateOf("")
    var command: String by mutableStateOf("")

    /** Зафиксированные теги (пилюли); правятся через [addTags]/[removeTag]/[pickTag]. */
    var tags: List<String> by mutableStateOf(emptyList())
        private set

    /** Незакоммиченный ввод тега (пилюля ещё не создана); [toDraft] дофиксирует его, чтобы не потерялся. */
    var tagDraft: String by mutableStateOf("")

    var shortcut: String? by mutableStateOf(null)

    val canSave: Boolean get() = label.isNotBlank() && command.isNotBlank()

    /** Зафиксировать тег(и) из [raw] ([parseSnippetTags], дубли отбрасываются) и очистить черновик. */
    fun addTags(raw: String) {
        tags = (tags + parseSnippetTags(raw)).distinct()
        tagDraft = ""
    }

    /** Обновить черновик тега; запятая фиксирует тег(и) сразу (одиночный тег — по Enter, [addTags]). */
    fun updateTagDraft(value: String) {
        if (value.contains(',')) addTags(value) else tagDraft = value
    }

    /** Убрать тег (значение — то, что отрисовано на пилюле). */
    fun removeTag(tag: String) {
        tags = tags - tag
    }

    /** Добавить тег из type-ahead-подсказки и очистить черновик. */
    fun pickTag(tag: String) {
        tags = (tags + tag).distinct()
        tagDraft = ""
    }

    /**
     * Черновик для [SnippetManager.save]. Дослать недозафиксированный [tagDraft] (набран, но не
     * нажат Enter/запятая перед Save) — иначе тег терялся бы.
     */
    fun toDraft(): SnippetDraft = SnippetDraft(
        id = editingId,
        label = label.trim(),
        command = command,
        tags = (tags + parseSnippetTags(tagDraft)).distinct(),
        shortcut = shortcut,
    )

    companion object {
        /** Форма, предзаполненная из [entry] (правка), либо пустая (создание, `entry == null`). */
        fun fromEntry(entry: SnippetEntry?): SnippetFormState =
            SnippetFormState(entry?.id).apply {
                entry?.snippet?.let { s ->
                    label = s.label
                    command = s.command
                    tags = s.tags
                    shortcut = s.shortcut
                }
            }
    }
}
