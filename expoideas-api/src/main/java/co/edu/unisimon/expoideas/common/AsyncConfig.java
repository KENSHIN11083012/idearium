package co.edu.unisimon.expoideas.common;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita {@code @Async}: los correos salen en otro hilo, después del commit,
 * para que quien pidió la operación no espere al servidor SMTP. Cuántos hilos y
 * cuánta cola hay se fija en application.properties ({@code spring.task.execution}).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Qué pasa si la cola se llena (el SMTP lleva rato sin responder y siguen
     * llegando envíos): lo hace el hilo que lo pidió. Esa petición tarda más,
     * pero el aviso no se descarta ni la operación, que ya se confirmó, termina
     * en error por un correo.
     */
    @Bean
    ThreadPoolTaskExecutorCustomizer callerSendsWhenTheQueueIsFull() {
        return executor -> executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
