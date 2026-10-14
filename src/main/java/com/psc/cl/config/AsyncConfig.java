package com.psc.cl.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Lets methods marked {@code @Async} run in the background, such as confirming a client's WhatsApp
 * opt-in, so a request does not wait on a slow outside service.
 *
 * <p>They run on Spring Boot's application task executor, sized under {@code spring.task.execution}
 * in {@code application.yml}.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
