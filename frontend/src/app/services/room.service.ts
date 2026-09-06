import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export interface RoomDTO {
  id?: number;
  name: string;
  description: string;
  capacity: number;
  pricePerNight: number;
  ownerId?: string;
  imageUrl?: string;
  active?: boolean;
  activeBookingsCount?: number;
}

@Injectable({
  providedIn: 'root'
})
export class RoomService {
  private apiUrl = `${environment.apiUrl}/rooms`;

  constructor(private http: HttpClient) {}

  getAllRooms(): Observable<RoomDTO[]> {
    return this.http.get<RoomDTO[]>(this.apiUrl);
  }

  getMyRooms(): Observable<RoomDTO[]> {
    return this.http.get<RoomDTO[]>(`${this.apiUrl}/my`);
  }

  getRoomById(id: number): Observable<RoomDTO> {
    return this.http.get<RoomDTO>(`${this.apiUrl}/${id}`);
  }

  createRoom(room: RoomDTO): Observable<RoomDTO> {
    return this.http.post<RoomDTO>(this.apiUrl, room);
  }

  uploadRoomImage(roomId: number, file: File): Observable<RoomDTO> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<RoomDTO>(`${this.apiUrl}/${roomId}/image`, formData);
  }

  getRoomImageUrl(imageUrl?: string): string | null {
    if (!imageUrl) return null;
    if (imageUrl.startsWith('http')) return imageUrl;
    return `${environment.backendUrl}${imageUrl}`;
  }

  deleteRoom(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getOccupiedDates(id: number): Observable<{ checkIn: string; checkOut: string }[]> {
    return this.http.get<{ checkIn: string; checkOut: string }[]>(`${this.apiUrl}/${id}/occupied`);
  }
}
