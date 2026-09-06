import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import Keycloak from 'keycloak-js';
import { environment } from '../../environments/environment';

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
      url: environment.keycloak.url,
      realm: environment.keycloak.realm,
      clientId: environment.keycloak.clientId
    });

    try {
      const authenticated = await this.keycloak.init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
        checkLoginIframe: false,
        pkceMethod: 'S256'
      });
      
      this.isInit = true;

      // Aggiornamento automatico del token prima della scadenza
      this.keycloak.onTokenExpired = () => {
        this.keycloak?.updateToken(30).then((refreshed) => {
          if (refreshed) {
            console.log('Keycloak token refreshed successfully');
          }
        }).catch((err) => {
          console.warn('Failed to refresh Keycloak token:', err);
        });
      };

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
    const token = this.keycloak?.tokenParsed;
    if (!token) return null;
    const name = token['name'];
    if (name && typeof name === 'string' && name.trim().length > 0) {
      return name.trim();
    }
    const given = token['given_name'];
    const family = token['family_name'];
    if (given || family) {
      const full = `${given || ''} ${family || ''}`.trim();
      if (full.length > 0) return full;
    }
    return token['preferred_username'] || null;
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

  isHost(): boolean {
    return this.hasRole('HOST');
  }

  isAdmin(): boolean {
    return this.hasRole('ADMIN');
  }

  isHostOrAdmin(): boolean {
    return this.hasRole('HOST') || this.hasRole('ADMIN');
  }

  async refreshToken(): Promise<boolean> {
    if (!this.keycloak) return false;
    try {
      const refreshed = await this.keycloak.updateToken(-1);
      return !!refreshed;
    } catch (e) {
      console.error('Failed to force token refresh:', e);
      return false;
    }
  }

  async becomeHost(): Promise<boolean> {
    const token = this.getToken();
    if (!token) return false;

    try {
      const response = await fetch(`${environment.apiUrl}/users/become-host`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
      if (!response.ok) {
        throw new Error('Failed to become host: ' + response.statusText);
      }
      // Aggiorna il token Keycloak per caricare immediatamente il ruolo HOST
      await this.refreshToken();
      return true;
    } catch (e) {
      console.error('Error promoting user to host:', e);
      return false;
    }
  }

  private async syncUser(): Promise<void> {
    const token = this.getToken();
    if (!token) return;

    try {
      const response = await fetch(`${environment.apiUrl}/users/sync`, {
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
