// ==============================================================================
// StayHub - Frontend Environment Configuration Template (Angular)
// ==============================================================================
// Copy this file to 'environment.ts' and 'environment.development.ts':
// cp src/environments/environment.example.ts src/environments/environment.ts
// ==============================================================================

export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api/v1',
  backendUrl: 'http://localhost:8080',
  keycloak: {
    url: 'http://localhost:9000',
    realm: 'stayhub',
    clientId: 'stayhub-frontend'
  }
};
