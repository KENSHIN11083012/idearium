package co.edu.unisimon.expoideas.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import co.edu.unisimon.expoideas.support.TestData;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Sin SMTP no pasa nada; con SMTP el correo sale con el remitente configurado. */
class MailServiceTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    @Test
    void withoutAMailServerNothingIsSentAndNothingBreaks() {
        MailService service = new MailService(provider(null), TestData.properties(Path.of("x")));

        assertThat(service.isConfigured()).isFalse();
        assertThatCode(() -> service.send("ana@unisimon.edu.co", "Hola", "Cuerpo"))
                .doesNotThrowAnyException();
    }

    @Test
    void aSenderWithoutAHostIsNotAMailServer() {
        // Así llega cuando Docker Compose pasa MAIL_HOST vacía: Spring crea el sender igual.
        JavaMailSenderImpl withoutHost = new JavaMailSenderImpl();
        withoutHost.setHost("");
        MailService service = new MailService(provider(withoutHost), TestData.properties(Path.of("x")));

        assertThat(service.isConfigured()).isFalse();
        assertThat(service.canSendLinks()).isFalse();
        assertThatCode(() -> service.send("ana@unisimon.edu.co", "Hola", "Cuerpo"))
                .doesNotThrowAnyException();
    }

    @Test
    void linksNeedAMailServerAndAPublicUrlThatActuallyOpens() {
        JavaMailSender sender = mock(JavaMailSender.class);

        assertThat(serviceWithAppUrl(sender, "https://idearium.unisimon.edu.co/expoideas")
                        .canSendLinks())
                .isTrue();
        assertThat(serviceWithAppUrl(sender, "http://localhost:8080/expoideas").canSendLinks())
                .isTrue();
        // Sin URL, sin servidor, o con el valor de ejemplo del .env sin cambiar: no se pide verificar el correo.
        assertThat(serviceWithAppUrl(sender, "").canSendLinks()).isFalse();
        assertThat(serviceWithAppUrl(null, "https://idearium.unisimon.edu.co/expoideas")
                        .canSendLinks())
                .isFalse();
        assertThat(serviceWithAppUrl(sender, "https://<dominio>.unisimon.edu.co/expoideas")
                        .canSendLinks())
                .isFalse();
        assertThat(serviceWithAppUrl(sender, "idearium.unisimon.edu.co").canSendLinks())
                .isFalse();
    }

    private static MailService serviceWithAppUrl(JavaMailSender sender, String appUrl) {
        ExpoideasProperties base = TestData.properties(Path.of("x"));
        return new MailService(
                provider(sender),
                new ExpoideasProperties(
                        base.jwt(), base.cors(), base.files(), base.mail(), base.login(), base.admin(), appUrl));
    }

    @Test
    void sendsPlainTextFromTheConfiguredSender() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailService service = new MailService(provider(sender), TestData.properties(Path.of("x")));

        service.send("ana@unisimon.edu.co", "Hola", "Cuerpo");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("expoideas@pruebas.local");
        assertThat(message.getValue().getTo()).containsExactly("ana@unisimon.edu.co");
        assertThat(message.getValue().getSubject()).isEqualTo("Hola");
        assertThat(message.getValue().getText()).isEqualTo("Cuerpo");
    }

    @Test
    void datesReadNaturallyInSpanish() {
        LocalDateTime when = LocalDateTime.of(2026, 11, 20, 14, 5);

        assertThat(NotificationListener.formatDate(when)).isEqualTo("Viernes 20 de noviembre de 2026");
        assertThat(NotificationListener.formatTime(when)).isEqualTo("2:05 p. m.");
    }
}
