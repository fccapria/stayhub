import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PaymentDetailsDTO {
  cardHolder?: string;
  cardNumber?: string;
  cvv?: string;
  expirationDate?: string;
  paypalEmail?: string;
  paypalOrderId?: string;
}

export interface BookingRequestDTO {
  roomId: number;
  checkIn: string;
  checkOut: string;
  paymentMethod: 'PAYPAL' | 'CLASSIC_CARD';
  paymentDetails: PaymentDetailsDTO;
}

export interface BookingResponseDTO {
  id: number;
  userId: string;
  roomId: number;
  checkIn: string;
  checkOut: string;
  totalPrice: number;
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED';
  paymentStatus: 'PENDING' | 'COMPLETED' | 'FAILED';
  transactionReference: string;
}

@Injectable({
  providedIn: 'root'
})
export class BookingService {
  private apiUrl = 'http://localhost:8080/api/v1/bookings';

  constructor(private http: HttpClient) {}

  createBooking(request: BookingRequestDTO): Observable<BookingResponseDTO> {
    return this.http.post<BookingResponseDTO>(this.apiUrl, request);
  }

  getMyBookings(): Observable<BookingResponseDTO[]> {
    return this.http.get<BookingResponseDTO[]>(`${this.apiUrl}/my`);
  }

  getAllBookings(): Observable<BookingResponseDTO[]> {
    return this.http.get<BookingResponseDTO[]>(this.apiUrl);
  }

  getOwnerBookings(): Observable<BookingResponseDTO[]> {
    return this.http.get<BookingResponseDTO[]>(`${this.apiUrl}/owner`);
  }

  cancelBooking(id: number): Observable<BookingResponseDTO> {
    return this.http.put<BookingResponseDTO>(`${this.apiUrl}/${id}/cancel`, {});
  }

  getReceiptDownloadUrl(bookingId: number): string {
    return `http://localhost:8080/api/v1/exports/receipt?bookingId=${bookingId}`;
  }
}
