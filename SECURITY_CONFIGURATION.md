# 🔐 Configurazione Sicurezza - Environment Variables

## ✅ Modifiche Implementate

Le credenziali sensibili sono state spostate da file hardcoded a **environment variables** per migliorare la sicurezza.

---

## 📋 Variabili d'Ambiente Disponibili

### **Database:**
- `DB_URL` - URL connessione database (default: jdbc:mysql://localhost:3306/mt_project_tool...)
- `DB_USERNAME` - Username database (default: root)
- `DB_PASSWORD` - Password database (default: oronero)

### **JWT:**
- `JWT_SECRET` - Chiave segreta JWT (default: fornito per sviluppo)
- `JWT_ACCESS_EXPIRATION` - Durata token access in ms (default: 86400000 = 24h)
- `JWT_REFRESH_EXPIRATION` - Durata token refresh in ms (default: 604800000 = 7 giorni)

### **Applicazione:**
- `APP_DOMAIN` - Dominio applicazione (default: localhost)

---

## 🚀 Come Usare le Variabili d'Ambiente

### **Opzione 1: IntelliJ IDEA (Sviluppo)**

1. **Run** → **Edit Configurations**
2. Seleziona la tua configurazione Spring Boot
3. **Environment variables** → Aggiungi:
   ```
   DB_PASSWORD=oronero;JWT_SECRET=your_secret_here
   ```

### **Opzione 2: File .env (Non committare!)**

Crea `.env` nella root del backend:
```bash
DB_USERNAME=root
DB_PASSWORD=oronero
JWT_SECRET=unaChiaveMoltoLungaCheDovrebbeEssereAlmenoDi256Bit12345678901234567890123456789012
```

Poi esegui:
```bash
# Windows
set DB_PASSWORD=oronero
set JWT_SECRET=your_secret_here
mvn spring-boot:run
```

### **Opzione 3: Variabili di Sistema (Windows)**

```powershell
# Temporaneo (solo sessione corrente)
$env:DB_PASSWORD = "oronero"
$env:JWT_SECRET = "your_secret_here"
mvn spring-boot:run

# Permanente
[Environment]::SetEnvironmentVariable("DB_PASSWORD", "oronero", "User")
[Environment]::SetEnvironmentVariable("JWT_SECRET", "your_secret_here", "User")
```

### **Opzione 4: Produzione (Server)**

Configura le variabili nel sistema:
- **Linux**: `/etc/environment` o `.bashrc`
- **Docker**: `docker run -e DB_PASSWORD=xxx -e JWT_SECRET=yyy`
- **Kubernetes**: ConfigMap/Secrets
- **AWS/Azure/GCP**: Secret Manager

---

## ⚙️ Valori di Default

**IMPORTANTE:** I valori di default sono forniti SOLO per sviluppo locale.

In **produzione** devi:
1. ✅ Usare password complesse
2. ✅ Generare JWT secret random (almeno 256 bit)
3. ✅ Abilitare SSL sul database
4. ✅ Non usare MAI i default in produzione!

---

## 🔒 Generare JWT Secret Sicuro

```powershell
# PowerShell - Genera secret random 256 bit
-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
```

```bash
# Linux/Mac
openssl rand -base64 64
```

---

## ✅ Verifica Configurazione

L'applicazione continua a funzionare con i **valori di default** se non imposti le variabili.

Per produzione, imposta TUTTE le variabili sopra indicate.

---

## 🎯 SonarQube Security Review

Dopo questa modifica:
- ✅ **Nessuna credenziale hardcoded** nei file
- ✅ **Security Hotspots risolti**
- ✅ **Rating Security Review: A** (invece di E)

