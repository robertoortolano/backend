# Aggiornamento Sistema Ruoli ItemTypeSet - Riepilogo Modifiche

## Modifiche Implementate

### 1. Enum ItemTypeSetRoleType
**File**: `backend/src/main/java/com/example/demo/enums/ItemTypeSetRoleType.java`

**Modifiche**:
- Aggiunto `FIELD_EDITOR` (sostituisce il vecchio `EDITOR` generico)
- Mantenuti `WORKER`, `OWNER`, `CREATOR`, `EXECUTOR`
- Aggiunti `EDITOR` e `VIEWER` per le coppie (FieldConfiguration, WorkflowStatus)

**Nuovi valori**:
```java
WORKER,         // Per ogni ItemType che compone l'ItemTypeSet
OWNER,          // Per ogni WorkflowStatus di ogni Workflow associato agli ItemType
FIELD_EDITOR,   // Per ogni FieldConfiguration di ogni FieldSet dell'ItemTypeSet
CREATOR,        // Per ogni Workflow associato agli ItemType
EXECUTOR,       // Per ogni Transition di ogni Workflow
EDITOR,         // Per ogni coppia (FieldConfiguration, WorkflowStatus) in ogni ItemTypeConfiguration
VIEWER          // Per ogni coppia (FieldConfiguration, WorkflowStatus) in ogni ItemTypeConfiguration
```

### 2. Entity ItemTypeSetRole
**File**: `backend/src/main/java/com/example/demo/entity/ItemTypeSetRole.java`

**Modifiche**:
- Aggiunti campi per supportare le coppie (FieldConfiguration, WorkflowStatus):
  - `secondaryEntityType` (String)
  - `secondaryEntityId` (Long)
- Aggiornati commenti per includere "ItemTypeConfiguration"
- Aggiunti metodi di utilità:
  - `isForItemTypeConfiguration()`
  - `isForFieldConfigurationWorkflowStatusPair()`
  - `isForFieldConfiguration()`

### 3. DTO ItemTypeSetRoleDTO
**File**: `backend/src/main/java/com/example/demo/dto/ItemTypeSetRoleDTO.java`

**Modifiche**:
- Aggiunti campi `secondaryEntityType` e `secondaryEntityId`

### 4. DTO ItemTypeSetRoleCreateDTO
**File**: `backend/src/main/java/com/example/demo/dto/ItemTypeSetRoleCreateDTO.java`

**Modifiche**:
- Aggiunti campi `secondaryEntityType` e `secondaryEntityId`

### 5. Mapper ItemTypeSetRoleMapper
**File**: `backend/src/main/java/com/example/demo/mapper/ItemTypeSetRoleMapper.java`

**Modifiche**:
- Aggiornati metodi `toDTO()` e `toEntity()` per gestire i nuovi campi

### 6. Service ItemTypeSetRoleService
**File**: `backend/src/main/java/com/example/demo/service/ItemTypeSetRoleService.java`

**Modifiche principali**:
- Aggiornato `createRolesForItemTypeSet()` per chiamare i nuovi metodi
- Sostituito `createEditorAndViewerRoles()` con `createFieldEditorRoles()`
- Aggiunto `createEditorAndViewerRolesForPairs()` per le coppie
- Aggiunto metodo `createRole()` per creazione manuale

**Nuova logica di creazione**:
1. **WORKER**: Per ogni ItemType nelle configurazioni
2. **OWNER**: Per ogni WorkflowStatus in ogni Workflow
3. **FIELD_EDITOR**: Per ogni FieldConfiguration in ogni FieldSet
4. **CREATOR**: Per ogni Workflow associato
5. **EXECUTOR**: Per ogni Transition in ogni Workflow
6. **EDITOR/VIEWER**: Per ogni coppia (FieldConfiguration, WorkflowStatus) in ogni ItemTypeConfiguration

### 7. Controller ItemTypeSetRoleController
**File**: `backend/src/main/java/com/example/demo/controller/ItemTypeSetRoleController.java`

**Modifiche**:
- Aggiunto endpoint `POST /api/itemtypeset-roles` per creazione manuale ruoli
- Aggiunto import per `ItemTypeSetRoleCreateDTO`

## Nuove Funzionalità

### Creazione Automatica Ruoli
Il sistema ora crea automaticamente 7 tipologie di ruoli quando viene creato un ItemTypeSet:

1. **WORKER**: Uno per ogni ItemType
2. **OWNER**: Uno per ogni WorkflowStatus
3. **FIELD_EDITOR**: Uno per ogni FieldConfiguration
4. **CREATOR**: Uno per ogni Workflow
5. **EXECUTOR**: Uno per ogni Transition
6. **EDITOR**: Uno per ogni coppia (FieldConfiguration, WorkflowStatus)
7. **VIEWER**: Uno per ogni coppia (FieldConfiguration, WorkflowStatus)

### Creazione Manuale Ruoli
Nuovo endpoint per creare ruoli manualmente con supporto per le coppie:

```http
POST /api/itemtypeset-roles
Content-Type: application/json

{
    "roleType": "EDITOR",
    "name": "Editor for Priority in In Progress",
    "description": "Editor role for Priority field in In Progress status",
    "itemTypeSetId": 1,
    "relatedEntityType": "ItemTypeConfiguration",
    "relatedEntityId": 1,
    "secondaryEntityType": "FieldConfiguration",
    "secondaryEntityId": 5,
    "tenantId": 1
}
```

## Differenze Chiave dalla Versione Precedente

1. **FIELD_EDITOR vs EDITOR**: 
   - FIELD_EDITOR è indipendente dallo stato del workflow
   - EDITOR è specifico per coppie (FieldConfiguration, WorkflowStatus)

2. **Coppie (FieldConfiguration, WorkflowStatus)**:
   - EDITOR e VIEWER ora sono legati a coppie specifiche
   - Utilizzano `secondaryEntityType` e `secondaryEntityId` per la FieldConfiguration

3. **Granularità Maggiore**:
   - Controllo più fine sui permessi per campo e stato
   - Ruoli specifici per ogni combinazione rilevante

## Compatibilità

- Le API esistenti rimangono compatibili
- I ruoli esistenti continuano a funzionare
- Nuovi ruoli vengono creati automaticamente per nuovi ItemTypeSet

## Test

Tutti i test esistenti continuano a passare. Il sistema è stato testato con:
- Creazione automatica ruoli
- Creazione manuale ruoli
- Gestione Grant
- API endpoints

## Documentazione

Creati due nuovi file di documentazione:
- `ItemTypeSetRoleSystemV2.md`: Documentazione completa del nuovo sistema
- `ItemTypeSetRoleSystemUpdateSummary.md`: Questo riepilogo delle modifiche
