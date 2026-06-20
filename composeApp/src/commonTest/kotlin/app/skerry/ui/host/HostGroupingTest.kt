package app.skerry.ui.host

import app.skerry.shared.host.Host
import kotlin.test.Test
import kotlin.test.assertEquals

private fun host(id: String, label: String = id, group: String? = null) =
    Host(id = id, label = label, address = "$id.example.com", username = "root", group = group)

class HostGroupingTest {

    @Test
    fun empty_input_yields_no_folders() {
        assertEquals(emptyList(), groupHostsByFolder(emptyList()))
    }

    @Test
    fun groups_by_folder_preserving_first_seen_order() {
        val hosts = listOf(
            host("a", group = "Prod"),
            host("b", group = "Lab"),
            host("c", group = "Prod"),
        )
        val folders = groupHostsByFolder(hosts)
        assertEquals(listOf("Prod", "Lab"), folders.map { it.name })
        assertEquals(listOf("a", "c"), folders[0].hosts.map { it.id })
        assertEquals(listOf("b"), folders[1].hosts.map { it.id })
    }

    @Test
    fun null_group_falls_into_ungrouped_bucket_in_order() {
        val hosts = listOf(
            host("loose", group = null),
            host("p", group = "Prod"),
        )
        val folders = groupHostsByFolder(hosts, ungroupedLabel = "Ungrouped")
        assertEquals(listOf("Ungrouped", "Prod"), folders.map { it.name })
        assertEquals(listOf("loose"), folders[0].hosts.map { it.id })
    }
}
