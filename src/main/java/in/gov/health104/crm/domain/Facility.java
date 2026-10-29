package in.gov.health104.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Directory entry (hospital, blood bank, diagnostic centre) used for information calls and GIS lookup. */
@Entity
@Table(name = "facility")
@Getter @Setter
public class Facility {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String type;
    private String district;
    private String address;
    private String phone;
    private Double latitude;
    private Double longitude;
    private String services;
    private String hospitalType;
    private String pin;
    private Integer noOfBeds;
    private boolean active = true;
}
