package co.edu.unisimon.expoideas.security;

import co.edu.unisimon.expoideas.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carga la cuenta de la BD como {@link UserPrincipal}; el username es el correo.
 * Con este bean y el PasswordEncoder de {@link SecurityConfig}, Spring Security
 * arma solo el proveedor de autenticación del login.
 */
@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserPrincipal loadUserByUsername(String email) {
        return userRepository
                .findByEmail(email)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
