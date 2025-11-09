package dev.ixor.database

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ExposedUser(
    val id: Int? = null,
    val username: String,
    val passwordHash: String,
    val clientTokens: List<String> = emptyList(),
    val sessionTokens: List<String> = emptyList()
)

class UserService(database: Database) {
    object Users : Table() {
        val id = integer("id").autoIncrement()
        val username = varchar("username", length = 50).uniqueIndex()
        val passwordHash = varchar("password_hash", length = 255)

        override val primaryKey = PrimaryKey(id)
    }

    object ClientTokens : Table() {
        val id = integer("id").autoIncrement()
        val userId = integer("user_id").references(Users.id)
        val token = varchar("token", length = 255)
        val createdAt = long("created_at")

        override val primaryKey = PrimaryKey(id)
    }

    object SessionTokens : Table() {
        val id = integer("id").autoIncrement()
        val userId = integer("user_id").references(Users.id)
        val token = varchar("token", length = 255)
        val createdAt = long("created_at")
        val expiresAt = long("expires_at")

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users, ClientTokens, SessionTokens)
        }
    }

    suspend fun create(user: ExposedUser): Int = dbQuery {
        Users.insert {
            it[Users.username] = user.username
            it[Users.passwordHash] = user.passwordHash
        }[Users.id]
    }

    suspend fun read(id: Int): ExposedUser? {
        return dbQuery {
            Users.selectAll()
                .where { Users.id eq id }
                .map { ExposedUser(it[Users.id], it[Users.username], it[Users.passwordHash]) }
                .singleOrNull()
        }
    }

    suspend fun update(id: Int, user: ExposedUser) {
        dbQuery {
            Users.update({ Users.id eq id }) {
                it[Users.username] = user.username
                it[Users.passwordHash] = user.passwordHash
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            Users.deleteWhere { Users.id.eq(id) }
        }
    }

    suspend fun findByUsername(username: String): ExposedUser? {
        return dbQuery {
            Users.selectAll()
                .where { Users.username eq username }
                .map { ExposedUser(it[Users.id], it[Users.username], it[Users.passwordHash]) }
                .singleOrNull()
        }
    }

    suspend fun addClientToken(userId: Int, token: String) {
        dbQuery {
            ClientTokens.insert {
                it[ClientTokens.userId] = userId
                it[ClientTokens.token] = token
                it[ClientTokens.createdAt] = System.currentTimeMillis()
            }
        }
    }

    suspend fun validateClientToken(userId: Int, token: String): Boolean {
        return dbQuery {
            ClientTokens.selectAll()
                .where { (ClientTokens.userId eq userId) and (ClientTokens.token eq token) }
                .count() > 0
        }
    }

    suspend fun addSessionToken(userId: Int, token: String, expirationTime: Long) {
        dbQuery {
            SessionTokens.insert {
                it[SessionTokens.userId] = userId
                it[SessionTokens.token] = token
                it[SessionTokens.createdAt] = System.currentTimeMillis()
                it[SessionTokens.expiresAt] = expirationTime
            }
        }
    }

    suspend fun validateSessionToken(token: String): Int? {
        return dbQuery {
            SessionTokens.selectAll()
                .where { (SessionTokens.token eq token) and (SessionTokens.expiresAt greater System.currentTimeMillis()) }
                .map { it[SessionTokens.userId] }
                .singleOrNull()
        }
    }

    suspend fun removeSessionToken(token: String) {
        dbQuery {
            SessionTokens.deleteWhere { SessionTokens.token eq token }
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
