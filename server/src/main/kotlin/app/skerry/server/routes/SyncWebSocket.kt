package app.skerry.server.routes

import app.skerry.server.Services
import app.skerry.server.accountId
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.collect

/**
 * WS push (`docs/skerry-sync-design.md` §3): сервер шлёт лишь новый курсор аккаунта — сигнал
 * «появились изменения, сделай дельта-pull». Никакого содержимого в кадрах.
 */
fun Route.syncWebSocket(services: Services) {
    webSocket("/sync") {
        val principal = call.principal<JWTPrincipal>() ?: return@webSocket
        val accountId = principal.accountId
        services.notifier.forAccount(accountId).collect { cursor ->
            send(Frame.Text(cursor.toString()))
        }
    }
}
