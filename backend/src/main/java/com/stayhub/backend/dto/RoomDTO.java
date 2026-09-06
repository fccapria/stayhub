package com.stayhub.backend.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomDTO {
    private Long id;
    private String name;
    private String description;
    private Integer capacity;
    private BigDecimal pricePerNight;
    private String ownerId;
    private String imageUrl;
    private Boolean active;
    private Long activeBookingsCount;
}
