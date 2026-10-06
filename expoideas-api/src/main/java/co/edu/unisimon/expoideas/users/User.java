package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.catalogs.AcademicProgram;
import co.edu.unisimon.expoideas.catalogs.Campus;
import co.edu.unisimon.expoideas.catalogs.Faculty;
import co.edu.unisimon.expoideas.files.StoredFile;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.NamedSubgraph;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Cuenta de la plataforma: identidad, rol, adscripción académica y estado del
 * primer ingreso.
 *
 * <p>Sin @Data a propósito: equals/hashCode/toString sobre relaciones lazy
 * dispararían consultas por accidente.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
@NamedEntityGraph(
        name = User.WITH_PROFILE,
        attributeNodes = {
            @NamedAttributeNode("campus"),
            @NamedAttributeNode("faculty"),
            @NamedAttributeNode(value = "academicProgram", subgraph = "program"),
            @NamedAttributeNode("photo")
        },
        subgraphs = @NamedSubgraph(name = "program", attributeNodes = @NamedAttributeNode("faculty")))
public class User {

    /** Grafo con todo lo que muestra el perfil: evita una consulta por relación al armar la respuesta. */
    public static final String WITH_PROFILE = "User.withProfile";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Nulo hasta que la persona completa su perfil en el primer ingreso. */
    @Column(length = 100)
    private String firstName;

    /** Ambos apellidos. Nulo hasta completar el perfil. */
    @Column(length = 100)
    private String lastName;

    /** Institucional (@unisimon.edu.co), salvo los jurados externos. Es el usuario del login. */
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /**
     * Se registró cuando la plataforma podía enviar correos y todavía no abre el
     * enlace de verificación. Mientras tanto solo puede completar su primer ingreso.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean emailVerificationPending = false;

    /** Cuándo demostró que el correo es suyo. Nulo si nunca se le pidió o aún no lo hace. */
    private LocalDateTime emailVerifiedAt;

    /** Hash BCrypt. La API responde con DTOs; @JsonIgnore es solo una red por si acaso. */
    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    /** La contraseña la puso la gestión (cuenta creada o restablecida): es temporal. */
    @Builder.Default
    @Column(nullable = false)
    private boolean mustChangePassword = false;

    /**
     * La versión que deben traer los tokens de sesión de esta cuenta para valer.
     * Subirla ({@link #closeSessions()}) deja fuera a todos los emitidos antes.
     */
    @Builder.Default
    @Column(nullable = false)
    private int tokenVersion = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role = Role.STUDENT;

    /**
     * El rol que el listado de la cátedra le asigna y que la gestión aún no ha
     * confirmado. Nulo si no hay nada por confirmar.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Role pendingRole;

    /** En false la cuenta está suspendida: ni inicia sesión ni usa la que tuviera abierta. */
    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /** Intentos fallidos de inicio de sesión seguidos; un ingreso correcto los borra. */
    @Builder.Default
    @Column(nullable = false)
    private int failedLogins = 0;

    /** Hasta cuándo no se le deja entrar por acumular intentos fallidos. Nulo si no está bloqueada. */
    private LocalDateTime lockedUntil;

    /** Autorización de tratamiento de datos personales (Ley 1581 de 2012). */
    @Builder.Default
    @Column(nullable = false)
    private boolean dataConsent = false;

    private LocalDateTime dataConsentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private Campus campus;

    /** Se guarda aparte del programa porque un docente pertenece a una facultad sin estar en un programa. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Faculty faculty;

    /** Opcional; si existe, pertenece a {@link #faculty}. */
    @ManyToOne(fetch = FetchType.LAZY)
    private AcademicProgram academicProgram;

    /** Foto de perfil: un archivo público subido por la propia persona. */
    @ManyToOne(fetch = FetchType.LAZY)
    private StoredFile photo;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Lo que se muestra de una cuenta que todavía no puso su nombre. */
    public static final String NAMELESS = "Sin nombre";

    /**
     * Nombre para mostrar a los demás: "Ana María Pérez Gómez". Si aún no completó
     * su perfil, {@value #NAMELESS}: nunca un trozo de su correo, que se enseñaría
     * en listas donde el correo no tiene por qué verse.
     */
    public String fullName() {
        return hasName() ? firstName + " " + lastName : NAMELESS;
    }

    /** El nombre para saludarla en un correo que va a ella; null si aún no lo puso. */
    public String nameForGreeting() {
        return hasName() ? fullName() : null;
    }

    /** Lo que la cuenta debe resolver antes de usar la plataforma, en el orden en que se pide. */
    public List<OnboardingStep> pendingSteps() {
        List<OnboardingStep> steps = new ArrayList<>();
        if (emailVerificationPending) {
            steps.add(OnboardingStep.VERIFY_EMAIL);
        }
        if (mustChangePassword) {
            steps.add(OnboardingStep.CHANGE_PASSWORD);
        }
        if (!dataConsent) {
            steps.add(OnboardingStep.DATA_CONSENT);
        }
        // Sin tocar la facultad: basta con saber si está, para no disparar la carga lazy.
        if (!hasName() || (role.requiresAffiliation() && faculty == null)) {
            steps.add(OnboardingStep.COMPLETE_PROFILE);
        }
        return steps;
    }

    private boolean hasName() {
        return firstName != null && !firstName.isBlank() && lastName != null && !lastName.isBlank();
    }

    /** Deja constancia de la autorización de datos; si ya estaba, conserva la fecha original. */
    public void giveDataConsent(LocalDateTime now) {
        if (!dataConsent) {
            dataConsent = true;
            dataConsentAt = now;
        }
    }

    /**
     * El correo es suyo: abrió un enlace que solo llega a ese buzón. Con eso recibe
     * el rol que el listado de la cátedra le tuviera reservado, sin esperar a la gestión.
     */
    public void verifyEmail(LocalDateTime now) {
        emailVerificationPending = false;
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
        }
        if (pendingRole != null) {
            role = pendingRole;
            pendingRole = null;
        }
    }

    /** Deja sin efecto todos los tokens de sesión emitidos hasta ahora. */
    public void closeSessions() {
        tokenVersion++;
    }

    /** Corta el acceso sin eliminar la cuenta; lo que tuviera abierto se cierra. */
    public void suspend() {
        enabled = false;
        closeSessions();
    }

    public void reactivate() {
        enabled = true;
    }

    /**
     * Un intento fallido más. Al llegar a {@code maxAttempts} seguidos la cuenta
     * queda bloqueada hasta {@code lockUntil} y la cuenta de fallos empieza de nuevo.
     */
    public void registerFailedLogin(int maxAttempts, LocalDateTime lockUntil) {
        failedLogins++;
        if (failedLogins >= maxAttempts) {
            lockedUntil = lockUntil;
            failedLogins = 0;
        }
    }

    public boolean isLockedAt(LocalDateTime now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public boolean hasLoginFailures() {
        return failedLogins > 0 || lockedUntil != null;
    }

    /** Borra los intentos fallidos y el bloqueo: entró bien, o la gestión le restableció la contraseña. */
    public void clearLoginFailures() {
        failedLogins = 0;
        lockedUntil = null;
    }
}
