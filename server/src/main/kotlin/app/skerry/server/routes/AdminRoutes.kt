package app.skerry.server.routes

import app.skerry.server.SERVER_VERSION
import app.skerry.server.Services
import app.skerry.server.model.ErrorResponse
import app.skerry.server.model.HealthResponse
import app.skerry.server.model.StatsResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.security.MessageDigest

/**
 * Админ-эндпоинты для self-hosted консоли. `/admin/health` открыт (liveness), `/admin/stats`
 * отдаёт только агрегаты и закрыт статическим [config.adminToken] — отдельная admin-роль
 * (`docs/skerry-sync-design.md` §3). Содержимое аккаунтов недоступно по определению.
 */
fun Route.adminRoutes(services: Services) {
    get("/admin/health") {
        call.respond(HealthResponse("ok", SERVER_VERSION))
    }

    get("/admin/stats") {
        val token = services.config.adminToken
        val provided = call.request.headers["X-Admin-Token"]
        // Constant-time сравнение: не даём по таймингу побайтно подобрать долгоживущий токен.
        val authorized = token.isNotBlank() && provided != null &&
            MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), token.toByteArray(Charsets.UTF_8))
        if (!authorized) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("admin token required"))
            return@get
        }
        val c = services.stats.counts()
        call.respond(StatsResponse(c.accounts, c.devices, c.records, c.pairingSessions))
    }
}
