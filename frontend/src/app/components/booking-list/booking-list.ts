import { Component, OnInit, OnDestroy, HostListener, inject, signal, effect, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { BookingService, BookingResponseDTO } from '../../services/booking.service';
import { RoomService, RoomDTO } from '../../services/room.service';

@Component({
  selector: 'app-booking-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './booking-list.html',
  styleUrl: './booking-list.css'
})
export class BookingListComponent implements OnInit, OnDestroy {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly bookingService = inject(BookingService);
  private readonly roomService = inject(RoomService);

  // Signals
  bookings = signal<BookingResponseDTO[]>([]);
  roomsMap = signal<Map<number, RoomDTO>>(new Map());
  loading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  // Modal confirmation
  bookingToCancel = signal<BookingResponseDTO | null>(null);
  cancelLoading = signal<boolean>(false);
  downloadingReceiptId = signal<number | null>(null);

  constructor() {
    effect(() => {
      const isModalOpen = this.bookingToCancel() !== null;
      if (isPlatformBrowser(this.platformId)) {
        if (isModalOpen) {
          document.body.classList.add('modal-open');
        } else {
          document.body.classList.remove('modal-open');
        }
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscapePress(): void {
    if (this.bookingToCancel() && !this.cancelLoading()) {
      this.closeCancelModal();
    }
  }

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      this.loadData();
    }
  }

  ngOnDestroy(): void {
    if (isPlatformBrowser(this.platformId)) {
      document.body.classList.remove('modal-open');
    }
  }

  loadData(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    // Fetch rooms first to map names, then fetch bookings
    this.roomService.getAllRooms().subscribe({
      next: (rooms) => {
        const map = new Map<number, RoomDTO>();
        rooms.forEach((r) => {
          if (r.id) map.set(r.id, r);
        });
        this.roomsMap.set(map);
        this.fetchBookings();
      },
      error: (err) => {
        console.error('Failed to load rooms map:', err);
        // Continue anyway, we just won't display room names nicely
        this.fetchBookings();
      }
    });
  }

  fetchBookings(): void {
    this.bookingService.getMyBookings().subscribe({
      next: (data) => {
        // Sort bookings: active/confirmed first, then by date descending
        const sorted = data.sort((a, b) => new Date(b.checkIn).getTime() - new Date(a.checkIn).getTime());
        this.bookings.set(sorted);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load bookings:', err);
        this.errorMessage.set('Impossibile caricare le tue prenotazioni. Assicurati di aver effettuato l\'accesso.');
        this.loading.set(false);
      }
    });
  }

  getRoomName(roomId: number): string {
    const room = this.roomsMap().get(roomId);
    return room ? room.name : `Camera #${roomId}`;
  }

  getRoomDescription(roomId: number): string {
    const room = this.roomsMap().get(roomId);
    return room ? room.description : 'Alloggio';
  }

  getReceiptUrl(bookingId: number): string {
    return this.bookingService.getReceiptDownloadUrl(bookingId);
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
        this.errorMessage.set('Impossibile scaricare la ricevuta. Assicurati di essere autenticato.');
        setTimeout(() => this.errorMessage.set(null), 4000);
      }
    });
  }

  confirmCancelBooking(booking: BookingResponseDTO): void {
    this.bookingToCancel.set(booking);
  }

  closeCancelModal(): void {
    this.bookingToCancel.set(null);
  }

  executeCancelBooking(): void {
    const booking = this.bookingToCancel();
    if (!booking) return;

    this.cancelLoading.set(true);
    this.bookingService.cancelBooking(booking.id).subscribe({
      next: () => {
        this.cancelLoading.set(false);
        this.successMessage.set('Prenotazione annullata con successo.');
        this.closeCancelModal();
        this.fetchBookings(); // refresh list
        
        setTimeout(() => {
          this.successMessage.set(null);
        }, 3000);
      },
      error: (err) => {
        this.cancelLoading.set(false);
        console.error('Cancellation failed:', err);
        const errMsg = err.error?.message || 'Impossibile annullare questa prenotazione. Contatta il supporto.';
        this.errorMessage.set(errMsg);
        this.closeCancelModal();

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

  translatePaymentStatus(status: string): string {
    switch(status) {
      case 'PAID': return 'Pagato';
      case 'PENDING': return 'In attesa';
      case 'REFUNDED': return 'Rimborsato';
      case 'FAILED': return 'Fallito';
      case 'COMPLETED': return 'Completato';
      default: return status;
    }
  }
}
