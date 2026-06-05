package org.example.carshering.entity.rent;

import jakarta.persistence.*;
import lombok.*;
import org.example.carshering.entity.user.Client;
import org.example.carshering.entity.cars.Car;

import java.time.LocalDateTime;

@Entity
@Table(name = "contract", schema = "car_rental")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "client")
@Builder
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_start",  nullable = false)
    private LocalDateTime dataStart;

    @Column(name = "data_end",   nullable = false)
    private LocalDateTime dataEnd;

    @Column(name = "total_cost", nullable = false)
    private Double totalCost;

    private String comment;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    @Column(name = "duration_minutes", nullable = false)
    private Long durationMinutes;

// todo комментарии

    @ManyToOne
    @JoinColumn(name = "state_id", nullable = false)
    private RentalState state;
}
