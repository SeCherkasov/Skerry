package app.skerry.server

import app.skerry.server.auth.TokenService
import app.skerry.server.model.ErrorResponse
import app.skerry.server.routes.adminRoutes
import app.skerry.server.routes.authRoutes
import app.skerry.server.routes.deviceRoutes
import app.skerry.server.routes.pairingClaimRoute
import app.skerry.server.routes.pairingStartRoute
import app.skerry.server.routes.syncWebSocket
import app.skerry.server.routes.vaultRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

/** Версия сервера для /healthz и админ-консоли. */
const val SERVER_VERSION = "0.1.0"

val JWTPrincipal.accountId: String get() = payload.subject
val JWTPrincipal.deviceId: String get() = payload.getClaim(TokenService.CLAIM_DEVICE).asString()

/**
 * Принципал в маршруте под `authenticate("auth-jwt")`. Кидает явную ошибку вместо `!!`: при
 * случайном переносе маршрута из-под `authenticate {}` это даст понятный сбой, не молчаливый NPE
 * (kotlin-ревью).
 */
fun ApplicationCall.jwtPrincipal(): JWTPrincipal =
    principal<JWTPrincipal>() ?: error("missing JWT principal — route must be under authenticate(\"auth-jwt\")")

/**
 * Устанавливает плагины и маршруты. Вынесено из [module] отдельной функцией, чтобы тесты
 * могли поднять сервер на тестовой БД через `testApplication { application { configureServer(services) } }`.
 */
fun Application.configureServer(services: Services) {
    // Forward-compat: незнакомые поля в JSON игнорируем (старый клиент ↔ новый сервер). Опечатки
    // полей при этом не ловятся — осознанное решение для версионируемого API.
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(WebSockets) { pingPeriodMillis = 30_000 }
    install(CallLogging) { level = Level.INFO }
    // CORS нужен только браузерным клиентам; нативные приложения ему не подвержены, а админ-консоль
    // — same-origin. Поэтому по умолчанию (пустой список) CORS не ставим; включается явным списком
    // хостов через SKERRY_CORS_HOSTS (security-ревью M3: не оставлять anyHost).
    if (services.config.corsHosts.isNotEmpty()) {
        install(CORS) {
            services.config.corsHosts.forEach { allowHost(it, schemes = listOf("http", "https")) }
            allowHeader(io.ktor.http.HttpHeaders.Authorization)
            allowHeader(io.ktor.http.HttpHeaders.ContentType)
            allowMethod(io.ktor.http.HttpMethod.Put)
            allowMethod(io.ktor.http.HttpMethod.Delete)
        }
    }
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "bad request"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal error"))
        }
    }
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "skerry-sync"
            verifier(services.tokens.verifier())
            validate { credential ->
                val type = credential.payload.getClaim(TokenService.CLAIM_TYPE).asString()
                val account = credential.payload.subject
                val did = credential.payload.getClaim(TokenService.CLAIM_DEVICE).asString()
                // Токен валиден только если это access-токен и устройство (в рамках аккаунта) не отозвано.
                if (type == TokenService.TYPE_ACCESS && account != null && did != null &&
                    !services.devices.isRevoked(account, did)
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    routing {
        get("/healthz") { call.respondText("ok") }

        // Корень ведёт на админ-консоль — чтобы открытие сервера в браузере не давало 404.
        get("/") { call.respondRedirect("/console/") }

        // Статическая админ-консоль (self-hosted): /console -> resources/admin/index.html.
        staticResources("/console", "admin")

        authRoutes(services)
        pairingClaimRoute(services)   // без JWT: новое устройство ещё не вошло
        adminRoutes(services)         // своя admin-аутентификация (статический токен)

        authenticate("auth-jwt") {
            vaultRoutes(services)
            deviceRoutes(services)
            pairingStartRoute(services)
            syncWebSocket(services)
        }
    }
}
