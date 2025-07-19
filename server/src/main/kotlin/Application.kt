package dev.ixor

import dev.ixor.plugins.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSecurity()
    configureRouting()
    configureMonitoring()
    configureSerialization()
    configureTemplating()
    configureDatabases()
    configureSockets()
}
