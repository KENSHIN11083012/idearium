package co.edu.unisimon.expoideas.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un enlace de un solo uso enviado al correo de una cuenta. Se guarda el hash
 * del token, nunca el token: el valor en claro solo existe en el correo.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "account_tokens")
public class AccountToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AccountToken of(User user, AccountTokenPurpose purpose, String tokenHash, LocalDateTime now) {
        AccountToken token = new AccountToken();
        token.setUser(user);
        token.setPurpose(purpose);
        token.setTokenHash(tokenHash);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(purpose.validity()));
        return token;
    }

    /** Si todavía se puede usar: nadie lo usó y no ha vencido. */
    public boolean isUsableAt(LocalDateTime now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(LocalDateTime now) {
        usedAt = now;
    }
}
