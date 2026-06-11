# StayHub - Roadmap & TODO List

Questo file contiene l'elenco delle funzionalità e delle migliorie suggerite per portare il progetto StayHub ad un livello professionale e pronto per la produzione.

## 📋 Lista delle Cose da Fare

- [ ] **1. Esportazione Ricevute in PDF**
  - Sostituire l'attuale esportazione in CSV implementata in `ReceiptExportServlet.java` con un formato PDF elegante e formattato.
  - Usare librerie come *OpenPDF*, *iText* o *Thymeleaf-to-PDF* nel backend.
  
- [ ] **2. Gestione Foto Camere (Upload)**
  - Consentire all'amministratore di caricare immagini reali per le camere dalla dashboard amministrativa.
  - Implementare un endpoint di upload nel backend per salvare i file (localmente o su cloud storage come AWS S3) e aggiornare il riferimento nel database.
  
- [ ] **3. Calendario Interattivo di Prenotazione (Frontend UX)**
  - Sostituire i campi data di testo nella modale di prenotazione con un calendario interattivo (ad es. *Angular Material Datepicker* o *FullCalendar*).
  - Mostrare visivamente le date già prenotate e renderle non selezionabili per evitare errori di prenotazione a livello di interfaccia.
  
- [ ] **4. Sistema di Recensioni e Valutazioni (Review System)**
  - Aggiungere una sezione commenti e stelle (da 1 a 5) per ogni stanza.
  - Consentire la pubblicazione delle recensioni solo agli utenti registrati il cui soggiorno è già concluso.
  - Visualizzare il punteggio medio delle camere nella lista principale delle stanze.

- [ ] **5. Notifiche Email Automatiche**
  - Integrare *Spring Boot Starter Mail* nel backend.
  - Inviare un'email di riepilogo al cliente subito dopo la conferma della prenotazione (e a transazione completata su carta o PayPal).
  - Allegare la fattura in PDF generata automaticamente.

---
*StayHub Project Roadmap - 2026*
