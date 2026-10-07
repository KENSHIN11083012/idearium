package co.edu.unisimon.expoideas.notifications;

import co.edu.unisimon.expoideas.common.AccountAnonymizedEvent;
import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import co.edu.unisimon.expoideas.common.Masked;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/**
 * Envío de correos en texto plano. El servidor SMTP llega por las variables
 * {@code SPRING_MAIL_*}; sin él, Spring no crea el {@link JavaMailSender} y aquí
 * solo queda constancia en el log: la plataforma funciona igual, sin avisos.
 *
 * <p>Quien llama decide qué hacer si el envío falla; {@link NotificationListener}
 * lo registra y sigue, porque un aviso perdido no debe deshacer la operación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final ObjectProvider<JavaMailSender> senders;
    private final ExpoideasProperties properties;

    /** Si hay un servidor de correo configurado. */
    public boolean isConfigured() {
        return sender() != null;
    }

    /**
     * El servidor de correo, si hay uno con dirección. Docker Compose pasa {@code SPRING_MAIL_HOST}
     * vacía cuando no se define {@code MAIL_HOST}, y con eso Spring crea el sender igual: uno sin
     * dirección no es un servidor, y tratarlo como tal hacía fallar cada aviso.
     */
    private JavaMailSender sender() {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender instanceof JavaMailSenderImpl impl
                && (impl.getHost() == null || impl.getHost().isBlank())) {
            return null;
        }
        return sender;
    }

    /**
     * Si se le puede mandar a alguien un enlace a la plataforma: hace falta el
     * servidor de correo y una URL pública (APP_URL) con la que armarlo.
     *
     * <p>La URL se comprueba porque de aquí depende que el registro exija verificar
     * el correo: con una URL que no abre (el valor de ejemplo del .env sin
     * cambiar), nadie podría verificar y nadie podría usar su cuenta.
     */
    public boolean canSendLinks() {
        return isConfigured() && isPublicUrl(properties.appUrl());
    }

    private static boolean isPublicUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url.strip());
            return uri.getHost() != null && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Envía un correo. Si no hay servidor configurado, no hace nada más que avisar en el log.
     *
     * @throws org.springframework.mail.MailException si el servidor rechaza el envío
     */
    public void send(String to, String subject, String body) {
        if (to.endsWith(AccountAnonymizedEvent.ALIAS_DOMAIN)) {
            // Una cuenta anonimizada sigue en su equipo, pero ya no hay a quién escribirle.
            log.debug("\"{}\" no se envía: la cuenta destinataria fue anonimizada", subject);
            return;
        }
        JavaMailSender sender = sender();
        if (sender == null) {
            log.warn("Sin servidor de correo configurado: no se envía \"{}\" a {}", subject, Masked.email(to));
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
        log.info("Correo \"{}\" enviado a {}", subject, Masked.email(to));
    }
}
