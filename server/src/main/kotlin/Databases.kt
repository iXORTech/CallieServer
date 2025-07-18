package dev.ixor

import com.sksamuel.cohort.*
import com.sksamuel.cohort.cpu.*
import com.sksamuel.cohort.memory.*
import freemarker.cache.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.freemarker.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.sse.*
import io.ktor.server.webjars.*
import io.ktor.server.websocket.*
import io.ktor.sse.*
import io.ktor.websocket.*
import java.time.Duration
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.css.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*

fun Application.configureDatabases() {
    val development = environment.config.propertyOrNull("ktor.development")?.getString()?.toBoolean() ?: false
    val database = if (development) {
        log.warn("Running in development mode, using H2 database.")
        Database.connect(
            driver = "org.h2.Driver",
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            user = "root",
            password = "",
        )
    } else {
        val dbDriver = environment.config.property("database.driverClassName").getString()
        val dbUrl = environment.config.property("database.jdbcUrl").getString()
        val dbUser = environment.config.property("database.username").getString()
        val dbPassword = environment.config.property("database.password").getString()
        Database.connect(
            driver = dbDriver,
            url = dbUrl,
            user = dbUser,
            password = dbPassword,
        )
    }

    val userService = UserService(database)
    routing {
        // Create user
        post("/users") {
            val user = call.receive<ExposedUser>()
            val id = userService.create(user)
            call.respond(HttpStatusCode.Created, id)
        }

        // Read user
        get("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val user = userService.read(id)
            if (user != null) {
                call.respond(HttpStatusCode.OK, user)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // Update user
        put("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val user = call.receive<ExposedUser>()
            userService.update(id, user)
            call.respond(HttpStatusCode.OK)
        }

        // Delete user
        delete("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            userService.delete(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}
