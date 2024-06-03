package com.daelim.service
import com.daelim.model.domain.User
import com.daelim.model.dto.OrderCreatedEvent
import com.daelim.repository.UserRepository
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.security.Key
import java.util.*

@Service
class UserService(
    @Value("\${jwt.secret}") secretKey: String,
    @Value("\${jwt.access_token_expiration_time}") accessTokenExpTime: Long,
    @Value("\${jwt.refresh_token_expiration_time}") refreshTokenExpTime: Long,
    private val userRepository: UserRepository
) {
    private var key: Key? = null
    private var accessTokenExpTime: Long = 0
    private var refreshTokenExpTime: Long = 0

    init {
        val keyBytes = Decoders.BASE64.decode(secretKey)
        this.key = Keys.hmacShaKeyFor(keyBytes)
        this.accessTokenExpTime = accessTokenExpTime
        this.refreshTokenExpTime = refreshTokenExpTime
    }

    private val passwordEncoder = BCryptPasswordEncoder()

    fun registerUser(username: String, password: String): User {
        print("username: $username, password: $password")
        val hashedPassword = passwordEncoder.encode(password)
        val user = User(username = username, password = hashedPassword)
        return userRepository.save(user)
    }

    fun validateUser(username: String, password: String): Boolean {
        val user = userRepository.findByUsername(username)
        return user != null && passwordEncoder.matches(password, user.password)
    }

    fun generateToken(user: User): String {
        val expirationTime = 1000 * 60 * 60 * 24 // 24 hours
        return Jwts.builder()
            .setSubject(user.username)
            .setClaims(mapOf("userId" to user.id))
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + expirationTime))
            .signWith(key, io.jsonwebtoken.SignatureAlgorithm.HS256)
            .compact()
    }

    fun login(username: String, password: String): String? {
        val user = userRepository.findByUsername(username)
        return if (user != null && passwordEncoder.matches(password, user.password)) {
            generateToken(user)
        } else null
    }

    @KafkaListener(topics = ["order-created"], groupId = "user-service")
    fun handleOrderCreated(event: OrderCreatedEvent) {
        val user = userRepository.findById(event.userId)
        user.ifPresent {
            println("User ${it.username} created an order ${event.orderId} for ${event.quantity} of ${event.product}")
            // 추가 로직: 이메일 알림 보내기 등
        }
    }
}