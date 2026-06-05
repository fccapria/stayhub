import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import Keycloak from 'keycloak-js';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private keycloak: Keycloak | null = null;
  private isInit = false;

  constructor(@Inject(PLATFORM_ID) private platformId: Object) {}

  async init(): Promise<boolean> {
    if (!isPlatformBrowser(this.platformId)) {
      return true;
    }

    if (this.isInit) return true;

    this.keycloak = new Keycloak({
      url: 'http://localhost:9000',
      realm: 'stayhub',
      clientId: 'stayhub-frontend'
    });

    try {
      const authenticated = await this.keycloak.init({
        checkLoginIframe: false
      });
      
      this.isInit = true;

      if (authenticated) {
        console.log('User authenticated successfully', this.keycloak.token);
        await this.syncUser();
      }
      return authenticated;
    } catch (error) {
      console.error('Keycloak initialization failed completely', error);
      return false;
    }
  }

  login(): void {
    this.keycloak?.login();
  }

  logout(): void {
    this.keycloak?.logout({ redirectUri: window.location.origin });
  }

  isAuthenticated(): boolean {
    return this.keycloak?.authenticated || false;
  }

  getToken(): string | null {
    return this.keycloak?.token || null;
  }

  getUsername(): string | null {
    return this.keycloak?.tokenParsed?.['preferred_username'] || null;
  }

  getEmail(): string | null {
    return this.keycloak?.tokenParsed?.['email'] || null;
  }

  getUserId(): string | null {
    return this.keycloak?.subject || null;
  }

  hasRole(role: string): boolean {
    return this.keycloak?.hasRealmRole(role) || false;
  }

  private async syncUser(): Promise<void> {
    const token = this.getToken();
    if (!token) return;

    try {
      const response = await fetch('http://localhost:8080/api/v1/users/sync', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
      if (!response.ok) {
        console.error('Failed to sync user with backend');
      }
    } catch (e) {
      console.error('Error syncing user with backend', e);
    }
  }
}
