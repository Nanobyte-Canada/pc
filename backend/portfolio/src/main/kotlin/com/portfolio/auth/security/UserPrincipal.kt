package com.portfolio.auth.security

import com.portfolio.auth.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class UserPrincipal(
    val id: Long,
    val email: String,
    private val passwordHash: String?,
    private val authorities: Collection<GrantedAuthority>,
    private val enabled: Boolean = true,
    private val accountNonLocked: Boolean = true
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> = authorities

    override fun getPassword(): String? = passwordHash

    override fun getUsername(): String = email

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = accountNonLocked

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = enabled

    companion object {
        fun from(user: User, roles: List<String>): UserPrincipal {
            val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }

            return UserPrincipal(
                id = user.id,
                email = user.email,
                passwordHash = user.passwordHash,
                authorities = authorities,
                enabled = user.emailVerified,
                accountNonLocked = !user.isLocked()
            )
        }
    }
}
