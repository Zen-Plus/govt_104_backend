package in.gov.health104.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Beneficiary demographics captured at call triage. */
@Entity
@Table(name = "caller")
@Getter @Setter
public class Caller {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private Integer age;
    private String sex;
    private String phone;
    private String email;
    private String district;
    private String block;
    private String village;
    private String address;
    private LocalDateTime createdAt = LocalDateTime.now();
}
