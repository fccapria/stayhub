import { Component, OnInit, inject, signal, computed, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RoomService, RoomDTO } from '../../services/room.service';
import { BookingService, BookingResponseDTO } from '../../services/booking.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.css'
})
export class AdminDashboardComponent implements OnInit {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly roomService = inject(RoomService);
  private readonly bookingService = inject(BookingService);

  // Lists
  rooms = signal<RoomDTO[]>([]);
  bookings = signal<BookingResponseDTO[]>([]);
  
  // Status Flags
  loading = signal<boolean>(false);
  actionLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  // Add Room Form Fields
  newRoomName = signal<string>('');
  newRoomDescription = signal<string>('');
  newRoomCapacity = signal<number>(2);
  newRoomPrice = signal<number>(100);

  // Stats
  totalRooms = computed(() => this.rooms().length);
  totalBookingsCount = computed(() => this.bookings().length);
  
  confirmedBookingsCount = computed(() => 
    this.bookings().filter(b => b.status === 'CONFIRMED').length
  );
  
  totalRevenues = computed(() => {
    return this.bookings()
      .filter(b => b.status !== 'CANCELLED')
      .reduce((sum, b) => sum + b.totalPrice, 0);
  });

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.loadAdminData();
    }
  }

  loadAdminData(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    // Load owner rooms
    this.roomService.getMyRooms().subscribe({
      next: (roomsData) => {
        this.rooms.set(roomsData);
        // Load owner bookings
        this.bookingService.getOwnerBookings().subscribe({
          next: (bookingsData) => {
            // Sort bookings by checkIn date descending
            const sorted = bookingsData.sort((a, b) => new Date(b.checkIn).getTime() - new Date(a.checkIn).getTime());
            this.bookings.set(sorted);
            this.loading.set(false);
          },
          error: (err) => {
            console.error('Failed to load owner bookings:', err);
            this.errorMessage.set('Impossibile recuperare i registri delle prenotazioni.');
            this.loading.set(false);
          }
        });
      },
      error: (err) => {
        console.error('Failed to load owner rooms:', err);
        this.errorMessage.set('Impossibile recuperare le camere del tuo B&B.');
        this.loading.set(false);
      }
    });
  }

  getRoomName(roomId: number): string {
    const room = this.rooms().find((r) => r.id === roomId);
    return room ? room.name : `Camera #${roomId}`;
  }

  submitAddRoom(): void {
    const name = this.newRoomName().trim();
    const description = this.newRoomDescription().trim();
    const capacity = this.newRoomCapacity();
    const price = this.newRoomPrice();

    if (!name || !description || capacity <= 0 || price <= 0) {
      this.errorMessage.set('Per favore compila tutti i campi con valori positivi validi.');
      return;
    }

    this.actionLoading.set(true);
    this.errorMessage.set(null);

    const room: RoomDTO = {
      name,
      description,
      capacity,
      pricePerNight: price
    };

    this.roomService.createRoom(room).subscribe({
      next: (createdRoom) => {
        this.actionLoading.set(false);
        this.successMessage.set(`La camera "${createdRoom.name}" è stata registrata con successo.`);
        this.rooms.update((prev) => [...prev, createdRoom]);
        
        // Reset form
        this.newRoomName.set('');
        this.newRoomDescription.set('');
        this.newRoomCapacity.set(2);
        this.newRoomPrice.set(100);

        setTimeout(() => {
          this.successMessage.set(null);
        }, 3000);
      },
      error: (err) => {
        this.actionLoading.set(false);
        console.error('Failed to create room:', err);
        const errMsg = err.error?.message || 'Impossibile creare la camera. Verifica i dettagli.';
        this.errorMessage.set(errMsg);
      }
    });
  }

  deleteRoom(roomId: number | undefined): void {
    if (roomId === undefined) return;
    
    if (!confirm('Sei sicuro di voler eliminare questa camera? Ciò potrebbe causare errori di vincolo di chiave esterna se esistono già prenotazioni.')) {
      return;
    }

    this.actionLoading.set(true);
    this.errorMessage.set(null);

    this.roomService.deleteRoom(roomId).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.successMessage.set('Camera rimossa con successo.');
        this.rooms.update((prev) => prev.filter((r) => r.id !== roomId));
        
        setTimeout(() => {
          this.successMessage.set(null);
        }, 3000);
      },
      error: (err) => {
        this.actionLoading.set(false);
        console.error('Failed to delete room:', err);
        const errMsg = err.error?.message || 'Impossibile eliminare la camera. Potrebbe essere collegata a prenotazioni attive.';
        this.errorMessage.set(errMsg);
        
        setTimeout(() => {
          this.errorMessage.set(null);
        }, 5000);
      }
    });
  }

  translateStatus(status: string): string {
    switch(status) {
      case 'CONFIRMED': return 'Confermata';
      case 'CANCELLED': return 'Cancellata';
      case 'PENDING': return 'In attesa';
      default: return status;
    }
  }
}
