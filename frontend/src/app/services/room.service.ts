import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface RoomDTO {
  id?: number;
  name: string;
  description: string;
  capacity: number;
  pricePerNight: number;
  ownerId?: string;
}

@Injectable({
  providedIn: 'root'
})
export class RoomService {
  private apiUrl = 'http://localhost:8080/api/v1/rooms';

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

  deleteRoom(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
