package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.ConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite y canjea los enlaces de un solo uso que se envían por correo. El token
 * en claro solo sale de aquí hacia el correo: en la base queda su hash.
 */
@Component
@RequiredArgsConstructor
class AccountTokens {

    /** 256 bits al azar: no se adivina ni probando todas las combinaciones. */
    private static final int TOKEN_BYTES = 32;

    private final AccountTokenRepository tokenRepository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * Crea un enlace nuevo de ese tipo para la cuenta y devuelve el token en
     * claro. Los anteriores del mismo tipo dejan de servir.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String issue(User user, AccountTokenPurpose purpose) {
        tokenRepository.deleteByUserIdAndPurpose(user.getId(), purpose);
        // El borrado tiene que llegar a la base antes del alta: comparten la cuenta y el propósito.
        tokenRepository.flush();

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(AccountToken.of(user, purpose, hash(token), now()));
        return token;
    }

    /**
     * Canjea un enlace: lo marca como usado y devuelve su cuenta.
     *
     * @throws ConflictException si no existe, ya se usó, venció o es de otro tipo
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public User consume(String token, AccountTokenPurpose purpose) {
        LocalDateTime now = now();
        AccountToken found = tokenRepository
                .findByTokenHashAndPurpose(hash(token.strip()), purpose)
                .filter(candidate -> candidate.isUsableAt(now))
                // Un solo mensaje para todos los casos: no se le dice a quien prueba cuál acertó.
                .orElseThrow(() -> new ConflictException("El enlace no es válido o ya venció. Pide uno nuevo."));
        found.markUsed(now);
        return found.getUser();
    }

    /** Si a esa cuenta se le envió un enlace de ese tipo hace menos de {@code window}. */
    @Transactional(readOnly = true)
    public boolean issuedWithin(User user, AccountTokenPurpose purpose, Duration window) {
        return tokenRepository
                .findFirstByUserIdAndPurposeOrderByCreatedAtDescIdDesc(user.getId(), purpose)
                .map(last -> last.getCreatedAt().isAfter(now().minus(window)))
                .orElse(false);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
