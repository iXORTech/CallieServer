package dev.ixor.routes.security

import dev.ixor.database.ExposedUser
import dev.ixor.database.UserService
import dev.ixor.models.LoginRequest
import dev.ixor.models.LoginResponse
import dev.ixor.models.RegisterRequest
import dev.ixor.models.RegisterResponse
import dev.ixor.plugins.DatabaseServices
import dev.ixor.utils.HashUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.registerAuthenticationRoutes() {
    val userService = DatabaseServices.userService ?: throw IllegalStateException("UserService not initialized")
    
    routing {
        route("/api/v1/auth") {
            post("/login") {
                try {
                    val loginRequest = call.receive<LoginRequest>()
                    
                    // Validate request
                    if (loginRequest.machineId.isBlank() || loginRequest.username.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, LoginResponse(false, message = "Missing required fields"))
                        return@post
                    }
                    
                    if (loginRequest.authType !in listOf("PASSWD", "TOKEN")) {
                        call.respond(HttpStatusCode.BadRequest, LoginResponse(false, message = "Invalid authType"))
                        return@post
                    }
                    
                    // Find user by username
                    val user = userService.findByUsername(loginRequest.username)
                    if (user == null) {
                        call.respond(HttpStatusCode.Unauthorized, LoginResponse(false, message = "Invalid credentials"))
                        return@post
                    }
                    
                    var isAuthenticated = false
                    var clientToken: String? = null
                    
                    when (loginRequest.authType) {
                        "PASSWD" -> {
                            if (loginRequest.password == null) {
                                call.respond(HttpStatusCode.BadRequest, LoginResponse(false, message = "Password required for PASSWD auth"))
                                return@post
                            }
                            
                            // Verify password
                            isAuthenticated = HashUtils.verifyPassword(loginRequest.password, user.passwordHash)
                            
                            if (isAuthenticated) {
                                // Generate and store client token
                                clientToken = HashUtils.generateToken(user.username, user.passwordHash, loginRequest.machineId)
                                userService.addClientToken(user.id!!, clientToken)
                            }
                        }
                        
                        "TOKEN" -> {
                            if (loginRequest.clientToken == null) {
                                call.respond(HttpStatusCode.BadRequest, LoginResponse(false, message = "Client token required for TOKEN auth"))
                                return@post
                            }
                            
                            // Validate stored client token
                            val isValidStoredToken = userService.validateClientToken(user.id!!, loginRequest.clientToken)
                            
                            // Also validate that the token matches what we would generate
                            val expectedToken = HashUtils.generateToken(user.username, user.passwordHash, loginRequest.machineId)
                            val isValidGeneratedToken = loginRequest.clientToken == expectedToken
                            
                            isAuthenticated = isValidStoredToken && isValidGeneratedToken
                            
                            if (isAuthenticated) {
                                clientToken = loginRequest.clientToken // Return the same client token
                            }
                        }
                    }
                    
                    if (isAuthenticated) {
                        // Generate session token
                        val sessionToken = HashUtils.generateSessionToken()
                        val expirationTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours
                        
                        userService.addSessionToken(user.id!!, sessionToken, expirationTime)
                        
                        call.respond(
                            HttpStatusCode.OK,
                            LoginResponse(
                                success = true,
                                sessionToken = sessionToken,
                                clientToken = clientToken
                            )
                        )
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, LoginResponse(false, message = "Invalid credentials"))
                    }
                    
                } catch (e: Exception) {
                    application.log.error("Login error", e)
                    call.respond(HttpStatusCode.InternalServerError, LoginResponse(false, message = "Internal server error"))
                }
            }
            
            post("/register") {
                try {
                    val registerRequest = call.receive<RegisterRequest>()
                    
                    // Validate request
                    if (registerRequest.username.isBlank() || registerRequest.password.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, RegisterResponse(false, "Username and password are required"))
                        return@post
                    }
                    
                    if (registerRequest.username.length < 3 || registerRequest.password.length < 6) {
                        call.respond(HttpStatusCode.BadRequest, RegisterResponse(false, "Username must be at least 3 characters, password at least 6 characters"))
                        return@post
                    }
                    
                    // Check if username already exists
                    val existingUser = userService.findByUsername(registerRequest.username)
                    if (existingUser != null) {
                        call.respond(HttpStatusCode.Conflict, RegisterResponse(false, "Username already exists"))
                        return@post
                    }
                    
                    // Hash password and create user
                    val passwordHash = HashUtils.hashPassword(registerRequest.password)
                    val newUser = ExposedUser(
                        username = registerRequest.username,
                        passwordHash = passwordHash
                    )
                    
                    userService.create(newUser)
                    call.respond(HttpStatusCode.Created, RegisterResponse(true, "User registered successfully"))
                    
                } catch (e: Exception) {
                    application.log.error("Registration error", e)
                    call.respond(HttpStatusCode.InternalServerError, RegisterResponse(false, "Internal server error"))
                }
            }
        }
    }
}
