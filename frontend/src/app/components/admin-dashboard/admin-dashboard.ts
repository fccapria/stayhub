import { Component, OnInit, inject, signal, computed, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RoomService, RoomDTO } from '../../services/room.service';
import { BookingService, BookingResponseDTO } from '../../services/booking.service';
import { AuthService } from '../../services/auth.service';

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
  protected readonly authService = inject(AuthService);

  get isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  get username(): string | null {
    return this.authService.getUsername();
  }

  // Lists
  rooms = signal<RoomDTO[]>([]);
  bookings = signal<BookingResponseDTO[]>([]);
  
  // Status Flags
  loading = signal<boolean>(false);
  actionLoading = signal<boolean>(false);
  downloadingReceiptId = signal<number | null>(null);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  // Add Room Form Fields
  newRoomName = signal<string>('');
  newRoomDescription = signal<string>('');
  newRoomCapacity = signal<number>(2);
  newRoomPrice = signal<number>(100);
  selectedFile = signal<File | null>(null);
  selectedFilePreview = signal<string | null>(null);
  uploadingRoomId = signal<number | null>(null);

  // Add Room Form Touched States
  newRoomNameTouched = signal<boolean>(false);
  newRoomDescriptionTouched = signal<boolean>(false);
  newRoomCapacityTouched = signal<boolean>(false);
  newRoomPriceTouched = signal<boolean>(false);

  // Add Room Form Validations
  isNewRoomNameValid = computed(() => this.newRoomName().trim().length >= 3);
  isNewRoomDescriptionValid = computed(() => this.newRoomDescription().trim().length >= 10);
  isNewRoomCapacityValid = computed(() => {
    const c = this.newRoomCapacity();
    return c !== null && c !== undefined && !isNaN(c) && c >= 1 && c <= 50;
  });
  isNewRoomPriceValid = computed(() => {
    const p = this.newRoomPrice();
    return p !== null && p !== undefined && !isNaN(p) && p >= 1 && p <= 100000;
  });

  isRoomFormValid = computed(() => 
    this.isNewRoomNameValid() &&
    this.isNewRoomDescriptionValid() &&
    this.isNewRoomCapacityValid() &&
    this.isNewRoomPriceValid()
  );

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
    this.newRoomNameTouched.set(true);
    this.newRoomDescriptionTouched.set(true);
    this.newRoomCapacityTouched.set(true);
    this.newRoomPriceTouched.set(true);

    if (!this.isRoomFormValid()) {
      this.errorMessage.set('Per favore compila tutti i campi con valori validi prima di creare la camera.');
      return;
    }

    const name = this.newRoomName().trim();
    const description = this.newRoomDescription().trim();
    const capacity = this.newRoomCapacity();
    const price = this.newRoomPrice();

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
        const fileToUpload = this.selectedFile();
        if (fileToUpload && createdRoom.id) {
          this.roomService.uploadRoomImage(createdRoom.id, fileToUpload).subscribe({
            next: (updatedWithImage) => {
              this.rooms.update((prev) => prev.map((r) => r.id === updatedWithImage.id ? updatedWithImage : r));
            },
            error: (imgErr) => console.error('Failed to upload image after creation:', imgErr)
          });
        }

        this.actionLoading.set(false);
        this.successMessage.set(`La camera "${createdRoom.name}" è stata registrata con successo.`);
        this.rooms.update((prev) => [...prev, createdRoom]);
        
        // Reset form & touched states
        this.newRoomName.set('');
        this.newRoomDescription.set('');
        this.newRoomCapacity.set(2);
        this.newRoomPrice.set(100);
        this.newRoomNameTouched.set(false);
        this.newRoomDescriptionTouched.set(false);
        this.newRoomCapacityTouched.set(false);
        this.newRoomPriceTouched.set(false);
        this.selectedFile.set(null);
        this.selectedFilePreview.set(null);

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

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      if (file.size > 5 * 1024 * 1024) {
        this.errorMessage.set('L\'immagine non deve superare i 5MB.');
        input.value = '';
        return;
      }
      this.selectedFile.set(file);
      const reader = new FileReader();
      reader.onload = () => {
        this.selectedFilePreview.set(reader.result as string);
      };
      reader.readAsDataURL(file);
    }
  }

  removeSelectedFile(inputElem?: HTMLInputElement): void {
    this.selectedFile.set(null);
    this.selectedFilePreview.set(null);
    if (inputElem) inputElem.value = '';
  }

  triggerTableUpload(roomId: number | undefined): void {
    if (!roomId) return;
    const fileInput = document.getElementById('tableFileInput-' + roomId) as HTMLInputElement;
    if (fileInput) {
      fileInput.click();
    }
  }

  onTableFileSelected(event: Event, room: RoomDTO): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0 || !room.id) return;
    const file = input.files[0];
    if (file.size > 5 * 1024 * 1024) {
      this.errorMessage.set('L\'immagine non deve superare i 5MB.');
      input.value = '';
      return;
    }

    this.uploadingRoomId.set(room.id);
    this.errorMessage.set(null);
    this.roomService.uploadRoomImage(room.id, file).subscribe({
      next: (updatedRoom) => {
        this.uploadingRoomId.set(null);
        this.successMessage.set(`Foto aggiornata con successo per "${room.name}".`);
        this.rooms.update((prev) => prev.map((r) => r.id === updatedRoom.id ? updatedRoom : r));
        input.value = '';
        setTimeout(() => this.successMessage.set(null), 3000);
      },
      error: (err) => {
        this.uploadingRoomId.set(null);
        console.error('Failed to upload image:', err);
        this.errorMessage.set('Impossibile caricare la foto. Verifica che il formato sia JPG, PNG o WEBP.');
        input.value = '';
        setTimeout(() => this.errorMessage.set(null), 5000);
      }
    });
  }

  getRoomThumbnail(room: RoomDTO): string {
    if (room.imageUrl) {
      const url = this.roomService.getRoomImageUrl(room.imageUrl);
      if (url) return url;
    }
    const name = room.name.toLowerCase();
    if (name.includes('suite') || name.includes('penthouse') || name.includes('presidential')) {
      return 'assets/suite_room.png';
    }
    return 'assets/deluxe_room.png';
  }

  deleteRoom(roomId: number | undefined): void {
    if (roomId === undefined) return;

    const targetRoom = this.rooms().find((r) => r.id === roomId);
    const activeCount = targetRoom?.activeBookingsCount || 0;

    let confirmMsg = 'Sei sicuro di voler eliminare questa camera dal catalogo?';
    if (activeCount > 0) {
      confirmMsg = `Attenzione: questo alloggio ha ${activeCount} soggiorno/i attivo/i o futuro/i confermato/i.\n\nRitirando la camera, essa verrà rimossa immediatamente dal catalogo pubblico (nessun nuovo ospite potrà prenotarla), ma i soggiorni già confermati rimarranno garantiti fino alla loro regolare conclusione.\n\nVuoi procedere con il ritiro dell'alloggio?`;
    }

    if (!confirm(confirmMsg)) {
      return;
    }

    this.actionLoading.set(true);
    this.errorMessage.set(null);

    this.roomService.deleteRoom(roomId).subscribe({
      next: () => {
        this.actionLoading.set(false);
        const successMsg = activeCount > 0
          ? 'Alloggio ritirato dal catalogo pubblico. I soggiorni confermati rimangono garantiti.'
          : 'Alloggio eliminato con successo dal catalogo.';
        this.successMessage.set(successMsg);

        // Aggiorna la dashboard per riflettere lo stato aggiornato
        this.loadAdminData();

        setTimeout(() => {
          this.successMessage.set(null);
        }, 4000);
      },
      error: (err) => {
        this.actionLoading.set(false);
        console.error('Failed to delete room:', err);
        const errMsg = err.error?.message || 'Impossibile completare l\'operazione sulla camera.';
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

  downloadReceipt(bookingId: number): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.downloadingReceiptId.set(bookingId);
    this.bookingService.downloadReceipt(bookingId).subscribe({
      next: (blob) => {
        this.downloadingReceiptId.set(null);
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `receipt-booking-${bookingId}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.downloadingReceiptId.set(null);
        console.error('Failed to download receipt:', err);
        this.errorMessage.set('Impossibile scaricare la ricevuta della prenotazione #' + bookingId);
        setTimeout(() => this.errorMessage.set(null), 4000);
      }
    });
  }
}
