package in.gov.health104.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the 104 Health Helpline CRM backend.
 * - @EnableScheduling : runs the grievance escalation engine on a cron.
 * - @EnableAsync      : SMS/Email/Call notifications are sent off the request thread.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class Crm104Application {
    public static void main(String[] args) {
        SpringApplication.run(Crm104Application.class, args);
    }
}
