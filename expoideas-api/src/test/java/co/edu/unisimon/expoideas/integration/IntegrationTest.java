package co.edu.unisimon.expoideas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unisimon.expoideas.catalogs.Campus;
import co.edu.unisimon.expoideas.catalogs.CampusRepository;
import co.edu.unisimon.expoideas.catalogs.Faculty;
import co.edu.unisimon.expoideas.catalogs.FacultyRepository;
import co.edu.unisimon.expoideas.files.FileFormat;
import co.edu.unisimon.expoideas.files.FileService;
import co.edu.unisimon.expoideas.files.FileVisibility;
import co.edu.unisimon.expoideas.support.TestData;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.User;
import co.edu.unisimon.expoideas.users.UserRepository;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base de las pruebas de integración: la API completa (Tomcat, filtros de
 * seguridad, Flyway, Hibernate con ddl-auto=validate) contra un MySQL 8.4 real
 * en Docker, y peticiones HTTP de verdad.
 *
 * <p>Todas las clases *IT comparten un solo contenedor y un solo contexto de
 * Spring. Cada prueba crea sus propias cuentas con correos únicos, así que no
 * dependen del orden ni se pisan entre sí.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class IntegrationTest {

    /** Contraseña válida para las cuentas de prueba (8+ caracteres, número y símbolo). */
    protected static final String PASSWORD = TestData.PASSWORD;

    // Se arranca una vez para toda la corrida; Testcontainers lo elimina al terminar.
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    protected static final Path FILES_DIR = createTempDir();

    /** SMTP en memoria: recibe los correos que la API envía tras cada commit. */
    protected static final GreenMail MAIL = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /** El token va en el fragmento del enlace, que el navegador no envía al servidor. */
    private static final Pattern LINK_TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    private static Integer openEditionId;

    static {
        MYSQL.start();
        MAIL.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("expoideas.jwt.secret", () -> TestData.JWT_SECRET);
        registry.add("expoideas.files.directory", FILES_DIR::toString);
        registry.add("expoideas.cors.allowed-origins", () -> "http://localhost:5173");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> MAIL.getSmtp().getPort());
        registry.add("expoideas.mail.from", () -> "expoideas@pruebas.local");
        registry.add("expoideas.app-url", () -> "http://localhost:5173/expoideas");
    }

    // ── Correos ─────────────────────────────────────────────────────────────

    /**
     * Los correos recibidos por esa dirección, en orden de llegada, esperando a
     * que haya al menos {@code count}: se envían en otro hilo tras el commit.
     */
    protected static List<MimeMessage> awaitMailTo(String email, int count) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        List<MimeMessage> found = mailTo(email);
        while (found.size() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            found = mailTo(email);
        }
        assertThat(found).as("correos para %s", email).hasSizeGreaterThanOrEqualTo(count);
        return found;
    }

    /** El último correo para esa dirección cuyo asunto empieza así, esperando a que llegue. */
    protected static MimeMessage awaitMail(String email, String subjectStart) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            List<MimeMessage> matching = new java.util.ArrayList<>();
            for (MimeMessage message : mailTo(email)) {
                if (message.getSubject().startsWith(subjectStart)) {
                    matching.add(message);
                }
            }
            if (!matching.isEmpty()) {
                return matching.getLast();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No llegó a " + email + " un correo cuyo asunto empiece por \"" + subjectStart + "\"");
    }

    private static List<MimeMessage> mailTo(String email) throws Exception {
        List<MimeMessage> found = new java.util.ArrayList<>();
        for (MimeMessage message : MAIL.getReceivedMessages()) {
            boolean addressed = Arrays.stream(message.getRecipients(Message.RecipientType.TO))
                    .anyMatch(address -> address.toString().equalsIgnoreCase(email));
            if (addressed) {
                found.add(message);
            }
        }
        return found;
    }

    protected static String body(MimeMessage message) throws Exception {
        return message.getContent().toString();
    }

    /**
     * El token del enlace del último correo con ese asunto que le llegó a esa
     * dirección: lo que la persona tendría al abrir el correo y pulsar el enlace.
     */
    protected static String linkTokenFor(String email, String subjectStart) {
        try {
            Matcher link = LINK_TOKEN.matcher(body(awaitMail(email, subjectStart)));
            assertThat(link.find()).as("enlace en el correo a %s", email).isTrue();
            return link.group(1);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer el correo de " + email, e);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    protected UserRepository userRepository;

    /** Para mirar (o tocar) la base sin pasar por la API, cuando la prueba es sobre lo que la base garantiza. */
    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FileService fileService;

    @Autowired
    private TransactionTemplate transactions;

    private RestTestClient client;

    @BeforeEach
    void createClient() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    // ── Cuentas ─────────────────────────────────────────────────────────────

    /** Correo institucional que ninguna otra prueba usa. */
    protected static String uniqueEmail(String prefix) {
        return prefix + SEQUENCE.incrementAndGet() + "@unisimon.edu.co";
    }

    /**
     * Crea una cuenta al día (sin pasos de primer ingreso) directamente en la BD:
     * con nombre, con foto y, si el rol la lleva, con campus y facultad. Devuelve
     * el correo.
     */
    protected String createAccount(Role role) {
        String email = uniqueEmail(role.name().toLowerCase());
        User.UserBuilder user = User.builder()
                .firstName("Prueba")
                .lastName(role.name())
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(role)
                .dataConsent(true)
                .dataConsentAt(LocalDateTime.now());
        if (role.requiresAffiliation()) {
            user.campus(anyCampus()).faculty(anyFaculty());
        }
        givePhoto(userRepository.save(user.build()));
        return email;
    }

    /** Sube la foto de perfil de quien tiene la sesión: el último paso del primer ingreso. */
    protected Response uploadPhoto(String token) {
        return putFile("/api/v1/users/me/photo", token, FileService.FIELD, "yo.png", MediaType.IMAGE_PNG, TestData.PNG);
    }

    /** La foto de perfil es obligatoria: sin ella la cuenta tendría un paso pendiente. */
    protected void givePhoto(User user) {
        transactions.executeWithoutResult(status -> {
            User account = userRepository.findById(user.getId()).orElseThrow();
            account.setPhoto(fileService.store(
                    new MockMultipartFile(FileService.FIELD, "foto.png", "image/png", TestData.PNG),
                    FileFormat.IMAGES,
                    FileVisibility.PUBLIC,
                    account));
        });
    }

    private Campus anyCampus() {
        return campusRepository.findAll().getFirst();
    }

    /** Una facultad cualquiera; la primera prueba que la necesita la crea. */
    private Faculty anyFaculty() {
        return facultyRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> facultyRepository.save(new Faculty(null, "Facultad de pruebas")));
    }

    /** Id de la cuenta con ese correo. */
    protected int idOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    /** Crea una cuenta al día con ese rol e inicia sesión. Devuelve el token. */
    protected String loginAs(Role role) {
        return login(createAccount(role), PASSWORD);
    }

    protected String login(String email, String password) {
        return post("/api/v1/auth/login", null, Map.of("email", email, "password", password))
                .expect(200)
                .json("$.token");
    }

    /** Registro público: solo correo, contraseña y autorización de datos. Devuelve la respuesta. */
    protected Response register(String email) {
        return post("/api/v1/auth/register", null, Map.of("email", email, "password", PASSWORD, "dataConsent", true));
    }

    /** Abre el enlace de verificación que le llegó a ese correo tras registrarse. */
    protected void verifyEmail(String email) {
        post("/api/v1/auth/email-verification", null, Map.of("token", linkTokenFor(email, "Verifica tu correo")))
                .expect(204);
    }

    /**
     * El paso de perfil del primer ingreso de una cuenta registrada: nombre y
     * adscripción. Devuelve la respuesta.
     */
    protected Response completeProfile(String token, int campusId, int facultyId, Integer programId) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Estudiante");
        body.put("lastName", "De Prueba");
        body.put("campusId", campusId);
        body.put("facultyId", facultyId);
        body.put("academicProgramId", programId);
        return put("/api/v1/users/me", token, body);
    }

    // ── Catálogos ───────────────────────────────────────────────────────────

    /** Id de uno de los campus que siembran las migraciones. */
    protected int campusId() {
        return get("/api/v1/campuses", null).expect(200).json("$[0].id");
    }

    protected int createFaculty(String adminToken) {
        return post("/api/v1/faculties", adminToken, Map.of("name", "Facultad " + SEQUENCE.incrementAndGet()))
                .expect(201)
                .json("$.id");
    }

    protected int createProgram(String adminToken, int facultyId) {
        return post(
                        "/api/v1/academic-programs",
                        adminToken,
                        Map.of("name", "Programa " + SEQUENCE.incrementAndGet(), "facultyId", facultyId))
                .expect(201)
                .json("$.id");
    }

    /**
     * Si alguna prueba ya cargó tipos de prototipo, un proyecto de INNPRENDE II tiene
     * que elegir uno: el catálogo es compartido y las pruebas no dependen del orden.
     */
    protected void chooseAnyPrototypeType(Map<String, Object> projectBody) {
        List<Integer> ids = get("/api/v1/prototype-types", null).expect(200).json("$[*].id");
        if (!ids.isEmpty()) {
            projectBody.put("prototypeTypeId", ids.getFirst());
        }
    }

    // ── Ediciones ───────────────────────────────────────────────────────────

    /**
     * Edición con la inscripción y las entregas abiertas hoy. La comparten todas
     * las pruebas porque dos ediciones no pueden cruzarse en el tiempo: si cada
     * clase creara la suya, la segunda chocaría con la primera.
     */
    protected int openEdition() {
        if (openEditionId == null) {
            LocalDate today = LocalDate.now();
            openEditionId = post(
                            "/api/v1/editions",
                            loginAs(Role.MACONDOLAB),
                            Map.of(
                                    "name",
                                    "Expoideas abierta " + SEQUENCE.incrementAndGet(),
                                    "registrationOpensOn",
                                    today.minusDays(1).toString(),
                                    "registrationClosesOn",
                                    today.plusDays(10).toString(),
                                    "submissionClosesOn",
                                    today.plusDays(20).toString(),
                                    "tracks",
                                    List.of(
                                            Map.of("track", "INNPRENDE_I", "minMembers", 1, "maxMembers", 3),
                                            Map.of("track", "INNPRENDE_II", "minMembers", 2, "maxMembers", 4))))
                    .expect(201)
                    .json("$.id");
        }
        return openEditionId;
    }

    // ── Peticiones ──────────────────────────────────────────────────────────

    protected Response get(String uri, String token) {
        return send(HttpMethod.GET, uri, token, null, null);
    }

    protected Response get(String uri, String token, HttpHeaders headers) {
        return send(HttpMethod.GET, uri, token, null, headers);
    }

    protected Response post(String uri, String token, Object body) {
        return send(HttpMethod.POST, uri, token, body, null);
    }

    protected Response put(String uri, String token, Object body) {
        return send(HttpMethod.PUT, uri, token, body, null);
    }

    protected Response delete(String uri, String token) {
        return send(HttpMethod.DELETE, uri, token, null, null);
    }

    /**
     * Lanza las dos peticiones a la vez y devuelve sus respuestas, para lo que
     * solo falla cuando dos personas (o un doble clic) llegan en el mismo instante.
     */
    protected static List<Response> atTheSameTime(Supplier<Response> first, Supplier<Response> second) {
        return atTheSameTime(List.of(first, second));
    }

    /** Lo mismo con cuantas peticiones hagan falta: un salón entero pulsando el botón a la vez. */
    protected static List<Response> atTheSameTime(List<Supplier<Response>> requests) {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(requests.size())) {
            List<Future<Response>> answers = requests.stream()
                    .map(request -> pool.submit(() -> {
                        start.await();
                        return request.get();
                    }))
                    .toList();
            start.countDown();
            return answers.stream().map(IntegrationTest::answerOf).toList();
        }
    }

    private static Response answerOf(Future<Response> answer) {
        try {
            return answer.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException("La petición simultánea no terminó", e);
        }
    }

    /**
     * PUT multipart con un único archivo en la parte {@code part}, enviado como lo
     * hace un navegador: la parte no declara su largo, así que el servidor la lee
     * hasta encontrar el límite.
     */
    protected Response postFile(
            String uri, String token, String part, String filename, MediaType type, byte[] content) {
        return sendFile(HttpMethod.POST, uri, token, part, filename, type, content);
    }

    protected Response putFile(String uri, String token, String part, String filename, MediaType type, byte[] content) {
        return sendFile(HttpMethod.PUT, uri, token, part, filename, type, content);
    }

    private Response sendFile(
            HttpMethod method, String uri, String token, String part, String filename, MediaType type, byte[] content) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(type);
        partHeaders.setContentDispositionFormData(part, filename);
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add(part, new HttpEntity<>(new InputStreamResource(new ByteArrayInputStream(content)), partHeaders));
        RestTestClient.RequestHeadersSpec<?> request = client.method(method)
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart);
        return Response.of(request.exchange().returnResult(byte[].class));
    }

    protected Response send(HttpMethod method, String uri, String token, Object body, HttpHeaders headers) {
        RestTestClient.RequestBodySpec request = client.method(method).uri(uri);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (headers != null) {
            request = request.headers(h -> h.addAll(headers));
        }
        RestTestClient.RequestHeadersSpec<?> ready = body == null
                ? request
                : request.contentType(MediaType.APPLICATION_JSON).body(body);
        return Response.of(ready.exchange().returnResult(byte[].class));
    }

    /** Respuesta HTTP con el cuerpo en bruto y acceso por JSONPath. */
    protected record Response(HttpStatusCode status, HttpHeaders headers, byte[] bytes) {

        static Response of(EntityExchangeResult<byte[]> result) {
            return new Response(result.getStatus(), result.getResponseHeaders(), result.getResponseBody());
        }

        public String body() {
            return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        }

        public <T> T json(String path) {
            return JsonPath.read(body(), path);
        }

        /** Verifica el código y devuelve la respuesta, mostrando el cuerpo si falla. */
        public Response expect(int status) {
            assertThat(this.status.value()).as("%s", body()).isEqualTo(status);
            return this;
        }
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("expoideas-it-files");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
