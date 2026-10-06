package co.edu.unisimon.expoideas.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import co.edu.unisimon.expoideas.support.TestData;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.servlet.HandlerExceptionResolver;

class JwtAuthenticationFilterTest {

    private static final String EMAIL = "ana@unisimon.edu.co";

    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private HandlerExceptionResolver exceptionResolver;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        ExpoideasProperties properties = TestData.properties(Path.of("uploads"));
        jwtService = new JwtService(properties);
        userDetailsService = mock(UserDetailsService.class);
        exceptionResolver = mock(HandlerExceptionResolver.class);
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService, exceptionResolver);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rolesComeFromTheDatabaseNotFromTheToken() throws Exception {
        String tokenFromWhenItWasAdmin = jwtService.generateToken(principal(Role.ADMIN));
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(principal(Role.STUDENT));

        Authentication authentication = filter(tokenFromWhenItWasAdmin);

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    void validTokenAuthenticatesTheRequest() throws Exception {
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(principal(Role.ADMIN));

        Authentication authentication = filter(" " + jwtService.generateToken(principal(Role.ADMIN)) + " ");

        assertThat(authentication.getName()).isEqualTo(EMAIL);
        assertThat(authentication.getPrincipal()).isInstanceOf(UserPrincipal.class);
    }

    @Test
    void tamperedTokenDoesNotAuthenticate() throws Exception {
        String token = jwtService.generateToken(principal(Role.STUDENT));

        assertThat(filter(token.substring(0, token.length() - 2) + "xx")).isNull();
    }

    @Test
    void expiredTokenDoesNotAuthenticate() throws Exception {
        String expired = Jwts.builder()
                .subject(EMAIL)
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TestData.JWT_SECRET)))
                .compact();

        assertThat(filter(expired)).isNull();
    }

    @Test
    void aTokenFromBeforeThePasswordChangeDoesNotAuthenticate() throws Exception {
        User account = TestData.user(1, EMAIL, Role.STUDENT);
        String before = jwtService.generateToken(new UserPrincipal(account));
        account.closeSessions();
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(new UserPrincipal(account));

        assertThat(filter(before)).isNull();
        assertThat(filter(jwtService.generateToken(new UserPrincipal(account)))).isNotNull();
    }

    @Test
    void aSuspendedAccountDoesNotAuthenticateEvenWithAValidToken() throws Exception {
        User account = TestData.user(1, EMAIL, Role.STUDENT);
        account.setEnabled(false);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(new UserPrincipal(account));

        assertThat(filter(jwtService.generateToken(new UserPrincipal(account)))).isNull();
    }

    @Test
    void aTokenIssuedBeforeSessionVersionsExistedStillWorks() throws Exception {
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(principal(Role.STUDENT));
        String withoutVersion = Jwts.builder()
                .subject(EMAIL)
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TestData.JWT_SECRET)))
                .compact();

        // Desplegar esta versión no debe cerrarle la sesión a nadie.
        assertThat(filter(withoutVersion)).isNotNull();
    }

    @Test
    void anAccountThatNoLongerExistsDoesNotAuthenticateAndTheRequestGoesOn() throws Exception {
        when(userDetailsService.loadUserByUsername(EMAIL)).thenThrow(new UsernameNotFoundException("no está"));
        MockFilterChain chain = new MockFilterChain();

        assertThat(filter(jwtService.generateToken(principal(Role.STUDENT)), chain))
                .isNull();
        // Sigue sin sesión: las rutas protegidas responderán 401 y las públicas, lo suyo.
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(exceptionResolver);
    }

    @Test
    void aDatabaseOutageIsNotMistakenForAnExpiredSession() throws Exception {
        DataAccessResourceFailureException outage = new DataAccessResourceFailureException("sin conexión");
        when(userDetailsService.loadUserByUsername(EMAIL)).thenThrow(outage);
        MockFilterChain chain = new MockFilterChain();

        assertThat(filter(jwtService.generateToken(principal(Role.STUDENT)), chain))
                .isNull();
        // Con un 401 la app cerraría la sesión de todos: el fallo se responde aquí y la petición no sigue.
        verify(exceptionResolver).resolveException(any(), any(), isNull(), eq(outage));
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void theTokenCarriesTheRoleForTheFrontend() {
        String token = jwtService.generateToken(principal(Role.MACONDOLAB));

        String role = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TestData.JWT_SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
        assertThat(role).isEqualTo("MACONDOLAB");
    }

    private Authentication filter(String token) throws Exception {
        return filter(token, new MockFilterChain());
    }

    private Authentication filter(String token, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/users");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static UserPrincipal principal(Role role) {
        return new UserPrincipal(TestData.user(1, EMAIL, role));
    }
}
