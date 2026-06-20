package app.skerry.ui.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewConnectionFormStateTest {

    @Test
    fun defaults_port_22_and_blank_rest() {
        val f = NewConnectionFormState()
        assertEquals("22", f.port)
        assertFalse(f.canSave) // name/address/username пустые
    }

    @Test
    fun requires_name_address_username_and_valid_port() {
        val f = NewConnectionFormState().apply {
            name = "prod-web-01"; address = "192.168.1.45"; username = "root"
        }
        assertTrue(f.canSave)
        f.username = "   "
        assertFalse(f.canSave)
    }

    @Test
    fun invalid_or_out_of_range_port_blocks_save() {
        val f = NewConnectionFormState().apply {
            name = "h"; address = "a"; username = "u"
        }
        f.port = "abc"; assertFalse(f.canSave)
        f.port = "0"; assertFalse(f.canSave)
        f.port = "70000"; assertFalse(f.canSave)
        f.port = "2222"; assertTrue(f.canSave)
    }

    @Test
    fun toDraft_trims_and_maps_blank_group_to_null() {
        val f = NewConnectionFormState().apply {
            name = "  prod  "; address = " 10.0.0.1 "; port = " 2222 "; username = " root "; group = "  "
        }
        val draft = f.toDraft(id = "keep-me")
        assertEquals("keep-me", draft.id)
        assertEquals("prod", draft.label)
        assertEquals("10.0.0.1", draft.address)
        assertEquals(2222, draft.port)
        assertEquals("root", draft.username)
        assertNull(draft.group)
    }

    @Test
    fun toDraft_keeps_non_blank_group() {
        val f = NewConnectionFormState().apply {
            name = "h"; address = "a"; username = "u"; group = "Production"
        }
        assertEquals("Production", f.toDraft().group)
        assertNull(f.toDraft().id)
    }
}
