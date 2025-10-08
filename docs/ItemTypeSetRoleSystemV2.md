# Sistema Ruoli ItemTypeSet - Versione 2.0

## Panoramica

Il sistema di ruoli per ItemTypeSet è stato aggiornato per supportare 7 tipologie di ruoli specifiche, con particolare attenzione alle coppie (FieldConfiguration, WorkflowStatus) per i ruoli EDITOR e VIEWER.

## Tipologie Ruoli

### 1. WORKER
- **Scopo**: Uno per ogni ItemType nell'ItemTypeSet
- **Entità correlata**: ItemType
- **Descrizione**: Ruolo per lavorare con un tipo di item specifico

### 2. OWNER
- **Scopo**: Uno per ogni WorkflowStatus di ogni Workflow associato
- **Entità correlata**: WorkflowStatus
- **Descrizione**: Ruolo di proprietà per uno stato specifico del workflow

### 3. FIELD_EDITOR
- **Scopo**: Uno per ogni FieldConfiguration di ogni FieldSet
- **Entità correlata**: FieldConfiguration
- **Descrizione**: Ruolo per modificare una configurazione di campo specifica

### 4. CREATOR
- **Scopo**: Uno per ogni Workflow associato
- **Entità correlata**: Workflow
- **Descrizione**: Ruolo per creare elementi nel workflow

### 5. EXECUTOR
- **Scopo**: Uno per ogni Transition di ogni Workflow
- **Entità correlata**: Transition
- **Descrizione**: Ruolo per eseguire transizioni specifiche

### 6. EDITOR
- **Scopo**: Uno per ogni coppia (FieldConfiguration, WorkflowStatus) in ogni ItemTypeConfiguration
- **Entità correlate**: ItemTypeConfiguration + FieldConfiguration (secondary)
- **Descrizione**: Ruolo per modificare un campo specifico in uno stato specifico del workflow

### 7. VIEWER
- **Scopo**: Uno per ogni coppia (FieldConfiguration, WorkflowStatus) in ogni ItemTypeConfiguration
- **Entità correlate**: ItemTypeConfiguration + FieldConfiguration (secondary)
- **Descrizione**: Ruolo per visualizzare un campo specifico in uno stato specifico del workflow

## Struttura Dati

### ItemTypeSetRole Entity

```java
@Entity
public class ItemTypeSetRole {
    private Long id;
    private ItemTypeSetRoleType roleType;
    private String name;
    private String description;
    private ItemTypeSet itemTypeSet;
    private String relatedEntityType;      // Tipo entità principale
    private Long relatedEntityId;          // ID entità principale
    private String secondaryEntityType;    // Tipo entità secondaria (per coppie)
    private Long secondaryEntityId;        // ID entità secondaria (per coppie)
    private Tenant tenant;
    private Set<ItemTypeSetRoleGrant> grants;
}
```

### Campi per Coppie

Per i ruoli EDITOR e VIEWER:
- `relatedEntityType`: "ItemTypeConfiguration"
- `relatedEntityId`: ID dell'ItemTypeConfiguration
- `secondaryEntityType`: "FieldConfiguration"
- `secondaryEntityId`: ID della FieldConfiguration

## API Endpoints

### Creazione Automatica Ruoli
```
POST /api/itemtypeset-roles/create-for-itemtypeset/{itemTypeSetId}?tenantId={tenantId}
```

### Creazione Manuale Ruolo
```
POST /api/itemtypeset-roles
Content-Type: application/json

{
    "roleType": "EDITOR",
    "name": "Editor for Field X in Status Y",
    "description": "Editor role for specific field in specific status",
    "itemTypeSetId": 1,
    "relatedEntityType": "ItemTypeConfiguration",
    "relatedEntityId": 1,
    "secondaryEntityType": "FieldConfiguration",
    "secondaryEntityId": 5,
    "tenantId": 1
}
```

### Ottenere Ruoli per ItemTypeSet
```
GET /api/itemtypeset-roles/itemtypeset/{itemTypeSetId}?tenantId={tenantId}
```

### Ottenere Ruoli per Tipo
```
GET /api/itemtypeset-roles/type/{roleType}?itemTypeSetId={itemTypeSetId}&tenantId={tenantId}
```

## Logica di Creazione Automatica

Quando viene creato un ItemTypeSet, il sistema crea automaticamente:

1. **WORKER**: Per ogni ItemType nelle configurazioni
2. **OWNER**: Per ogni WorkflowStatus in ogni Workflow
3. **FIELD_EDITOR**: Per ogni FieldConfiguration in ogni FieldSet
4. **CREATOR**: Per ogni Workflow associato
5. **EXECUTOR**: Per ogni Transition in ogni Workflow
6. **EDITOR/VIEWER**: Per ogni coppia (FieldConfiguration, WorkflowStatus) in ogni ItemTypeConfiguration

## Esempi di Nomi Ruoli

- `"Worker for Bug"`
- `"Owner for In Progress in Bug Workflow"`
- `"Field Editor for Priority"`
- `"Creator for Bug Workflow"`
- `"Executor for Start Work"`
- `"Editor for Priority in In Progress"`
- `"Viewer for Description in Done"`

## Note Importanti

- I ruoli EDITOR e VIEWER sono specifici per le coppie (FieldConfiguration, WorkflowStatus)
- FIELD_EDITOR è indipendente dallo stato del workflow
- Tutti i ruoli sono validi solo nel contesto dell'ItemTypeSet
- Ogni ruolo può avere multiple Grant assegnate
- Le Grant definiscono chi può assumere il ruolo




