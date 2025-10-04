# Sistema Ruoli per ItemTypeSet

## Panoramica

Il sistema implementa un meccanismo di ruoli specifici per ogni `ItemTypeSet` che permette di gestire i permessi in modo granulare all'interno del contesto di un ItemTypeSet specifico.

## Architettura

### Entity Principali

1. **ItemTypeSetRole**: Rappresenta un ruolo specifico per un ItemTypeSet
2. **ItemTypeSetRoleGrant**: Associa un Grant esistente a un ruolo specifico
3. **ItemTypeSet**: Modificato per includere la relazione con i ruoli

### Tipi di Ruoli

Il sistema crea automaticamente i seguenti tipi di ruoli per ogni ItemTypeSet:

1. **WORKER**: Uno per ogni ItemType che compone l'ItemTypeSet
2. **OWNER**: Uno per ogni WorkflowStatus di ogni Workflow associato agli ItemType
3. **EDITOR**: Uno per ogni FieldConfiguration di ogni FieldSet dell'ItemTypeSet
4. **VIEWER**: Uno per ogni FieldConfiguration di ogni FieldSet dell'ItemTypeSet
5. **CREATOR**: Uno per ogni Workflow associato agli ItemType
6. **EXECUTOR**: Uno per ogni Transition di ogni Workflow

## Utilizzo

### 1. Creazione Automatica dei Ruoli

Quando viene creato un nuovo ItemTypeSet, il sistema crea automaticamente tutti i ruoli necessari:

```java
// Chiamata API per creare i ruoli
POST /api/itemtypeset-roles/create-for-itemtypeset/{itemTypeSetId}?tenantId={tenantId}
```

### 2. Assegnazione di Grants

I grants esistenti possono essere assegnati ai ruoli specifici:

```java
// DTO per assegnare un grant
ItemTypeSetRoleGrantCreateDTO createDTO = ItemTypeSetRoleGrantCreateDTO.builder()
    .itemTypeSetRoleId(roleId)
    .grantId(grantId)
    .tenantId(tenantId)
    .build();

// Chiamata API
POST /api/itemtypeset-roles/assign-grant
```

### 3. Consultazione dei Ruoli

```java
// Ottieni tutti i ruoli per un ItemTypeSet
GET /api/itemtypeset-roles/itemtypeset/{itemTypeSetId}?tenantId={tenantId}

// Ottieni ruoli per tipo specifico
GET /api/itemtypeset-roles/itemtypeset/{itemTypeSetId}/type/{roleType}?tenantId={tenantId}
```

## Struttura Dati

### ItemTypeSetRole

```java
{
    "id": 1,
    "roleType": "WORKER",
    "name": "Worker for ItemType Name",
    "description": "Worker role for ItemType: ItemType Name",
    "itemTypeSetId": 1,
    "relatedEntityType": "ItemType",
    "relatedEntityId": 5,
    "tenantId": 1,
    "grants": [...]
}
```

### ItemTypeSetRoleGrant

```java
{
    "id": 1,
    "itemTypeSetRoleId": 1,
    "grantId": 3,
    "tenantId": 1,
    "grantedUserIds": [1, 2],
    "grantedGroupIds": [1],
    "negatedUserIds": [],
    "negatedGroupIds": []
}
```

## Vantaggi

1. **Granularità**: I ruoli sono specifici per ogni ItemTypeSet
2. **Flessibilità**: Ogni ruolo può avere grants personalizzati
3. **Automatizzazione**: La creazione dei ruoli è automatica
4. **Tracciabilità**: Ogni ruolo è collegato a un'entità specifica
5. **Isolamento**: I ruoli sono validi solo nel contesto dell'ItemTypeSet

## Esempi di Utilizzo

### Scenario: Progetto Software

Un ItemTypeSet per un progetto software potrebbe avere:

- **WORKER** per "Bug", "Feature", "Task" (ItemType)
- **OWNER** per "In Progress", "Review", "Done" (WorkflowStatus)
- **EDITOR/VIEWER** per "Description", "Priority", "Assignee" (FieldConfiguration)
- **CREATOR** per "Development Workflow" (Workflow)
- **EXECUTOR** per "Start Work", "Submit Review", "Close" (Transition)

Ogni ruolo può avere grants specifici che determinano chi può svolgere quella funzione nel contesto di quel particolare ItemTypeSet.
