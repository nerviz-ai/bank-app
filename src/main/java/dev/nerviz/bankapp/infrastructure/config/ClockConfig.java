package dev.nerviz.bankapp.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** "Today" is the UTC calendar date — {@.claude/rules/architecture-ddd.md} § Domain: Clock injected. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
