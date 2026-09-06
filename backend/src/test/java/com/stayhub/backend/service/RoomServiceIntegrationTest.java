package com.stayhub.backend.service;

import com.stayhub.backend.config.TestSecurityConfig;
import com.stayhub.backend.dto.BookingRequestDTO;
import com.stayhub.backend.dto.PaymentDetailsDTO;
import com.stayhub.backend.dto.RoomDTO;
import com.stayhub.backend.entity.BookingStatus;
import com.stayhub.backend.entity.PaymentMethod;
import com.stayhub.backend.entity.Role;
import com.stayhub.backend.entity.Room;
import com.stayhub.backend.entity.User;
import com.stayhub.backend.repository.BookingRepository;
import com.stayhub.backend.repository.PaymentRepository;
import com.stayhub.backend.repository.RoomRepository;
import com.stayhub.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class RoomServiceIntegrationTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private User hostUser;
    private User guestUser;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        hostUser = User.builder()
                .id("host-1")
                .email("host1@stayhub.com")
                .firstName("Host")
                .lastName("One")
                .role(Role.HOST)
                .build();
        userRepository.save(hostUser);

        guestUser = User.builder()
                .id("guest-1")
                .email("guest1@stayhub.com")
                .firstName("Guest")
                .lastName("One")
                .role(Role.CUSTOMER)
                .build();
        userRepository.save(guestUser);
    }

    @Test
    void testHardDelete_WhenNoBookingsExist() {
        RoomDTO newRoom = RoomDTO.builder()
                .name("Villa Panoramica")
                .description("Splendida villa affacciata sul golfo con piscina privata")
                .capacity(4)
                .pricePerNight(new BigDecimal("250.00"))
                .build();

        RoomDTO created = roomService.createRoom(newRoom, hostUser.getId());
        assertNotNull(created.getId());
        assertTrue(created.getActive());
        assertEquals(0L, created.getActiveBookingsCount());

        // Hard delete
        roomService.deleteRoom(created.getId(), hostUser.getId(), false);

        assertFalse(roomRepository.findById(created.getId()).isPresent(), "La camera senza prenotazioni deve essere eliminata fisicamente");
    }

    @Test
    void testDecommissioning_WithActiveBookings_GrandfatheredStays() {
        // 1. Host crea la camera
        RoomDTO newRoom = RoomDTO.builder()
                .name("Attico Vista Duomo")
                .description("Attico panoramico con terrazza privata nel centro storico")
                .capacity(2)
                .pricePerNight(new BigDecimal("180.00"))
                .build();
        RoomDTO created = roomService.createRoom(newRoom, hostUser.getId());
        Long roomId = created.getId();

        // 2. Ospite prenota per date future
        LocalDate futureCheckIn = LocalDate.now().plusDays(5);
        LocalDate futureCheckOut = LocalDate.now().plusDays(8);

        BookingRequestDTO bookingReq = BookingRequestDTO.builder()
                .roomId(roomId)
                .checkIn(futureCheckIn)
                .checkOut(futureCheckOut)
                .paymentMethod(PaymentMethod.CLASSIC_CARD)
                .paymentDetails(PaymentDetailsDTO.builder()
                        .cardNumber("4000123456780000")
                        .cardHolder("Guest One")
                        .cvv("123")
                        .expirationDate("12/28")
                        .build())
                .build();

        bookingService.createBooking(guestUser.getId(), bookingReq);

        // 3. Host decide di dismettere / eliminare la camera
        roomService.deleteRoom(roomId, hostUser.getId(), false);

        // 4. Verifica stato entità nel DB
        Room dbRoom = roomRepository.findById(roomId).orElseThrow();
        assertFalse(dbRoom.isActive(), "La camera dismessa deve avere active = false nel DB");

        // 5. Verifica che la camera sia SPORITA dal catalogo pubblico
        List<RoomDTO> publicCatalog = roomService.getAllRooms();
        boolean existsInCatalog = publicCatalog.stream().anyMatch(r -> r.getId().equals(roomId));
        assertFalse(existsInCatalog, "La camera in dismissione non deve più comparire nel catalogo pubblico");

        // 6. Verifica che l'host veda la camera come 'In dismissione' con 1 soggiorno residuo garantito
        List<RoomDTO> hostRooms = roomService.getRoomsByOwnerId(hostUser.getId());
        assertEquals(1, hostRooms.size(), "L'host deve continuare a vedere l'alloggio fino all'esaurimento dei soggiorni");
        RoomDTO hostRoomDto = hostRooms.get(0);
        assertEquals(roomId, hostRoomDto.getId());
        assertFalse(hostRoomDto.getActive(), "Il flag active deve essere false");
        assertEquals(1L, hostRoomDto.getActiveBookingsCount(), "Deve esserci esattamente 1 soggiorno attivo residuo");

        // 7. La prenotazione dell'ospite rimane valida e confermata
        long confirmedBookings = bookingRepository.countActiveFutureBookings(roomId, LocalDate.now());
        assertEquals(1L, confirmedBookings, "Il soggiorno dell'ospite rimane valido e confermato");
    }
}
