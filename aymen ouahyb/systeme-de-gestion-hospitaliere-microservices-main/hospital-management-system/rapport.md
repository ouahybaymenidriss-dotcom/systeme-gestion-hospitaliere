# Rapport - Mini Projet Microservices

## Système de Gestion Hospitalière

---

## 1. Introduction

Ce projet met en œuvre une application de gestion hospitalière utilisant une architecture à base de **microservices** avec Spring Boot et Spring Cloud. L'objectif est de démontrer les concepts fondamentaux de cette architecture : découverte de services, configuration centralisée, passerelle API, communication inter-services, et tolérance aux pannes.

---

## 2. Architecture Technique

### 2.1 Technologies utilisées

| Composant       | Technologie              | Version          |
| --------------- | ------------------------ | ---------------- |
| Framework       | Spring Boot              | 4.0.3            |
| Cloud           | Spring Cloud             | 2025.1.0         |
| Gateway         | Spring Cloud Gateway MVC | (webmvc)         |
| Discovery       | Netflix Eureka           | via Spring Cloud |
| Config          | Spring Cloud Config      | native           |
| Communication   | OpenFeign                | via Spring Cloud |
| Résilience      | Resilience4j             | 2.3.0            |
| Base de données | H2 (in-memory)           | 2.4.x            |
| Build           | Maven                    | 3.9+             |
| Runtime         | Java                     | 21               |

### 2.2 Microservices

Le système est composé de **6 microservices** :

1. **Config Server (port 8888)** — Fournit la configuration centralisée à tous les services via le profil `native` (fichiers YAML stockés localement).

2. **Eureka Server (port 8761)** — Annuaire de services permettant la découverte dynamique et l'enregistrement automatique de chaque microservice.

3. **API Gateway (port 8080)** — Point d'entrée unique de l'application. Route les requêtes vers les services appropriés en utilisant `GatewayRouterFunctions` et `HandlerFunctions.http()` de Spring Cloud Gateway MVC.

4. **Patient Service (port 8081)** — Gestion CRUD des patients (nom, prénom, date de naissance, contact). Exposé sous `/api/patients`.

5. **Appointment Service (port 8082)** — Gestion des rendez-vous. Utilise **OpenFeign** pour vérifier l'existence du patient avant la création d'un rendez-vous. Exposé sous `/api/appointments`.

6. **Medical Record Service (port 8083)** — Gestion des dossiers médicaux. Utilise **OpenFeign** pour vérifier l'existence du patient. Exposé sous `/api/medical-records`.

### 2.3 Diagramme d'architecture

```
Client → API Gateway (8080) → Eureka Discovery → Services métiers
                                                     ├── Patient Service (8081)
                                                     ├── Appointment Service (8082)
                                                     └── Medical Record Service (8083)
         Config Server (8888) ← Tous les services se configurent ici
```

---

## 3. Entités et Modèles de données

### Patient

| Champ         | Type      | Description             |
| ------------- | --------- | ----------------------- |
| id            | Long      | Identifiant auto-généré |
| nom           | String    | Nom du patient          |
| prenom        | String    | Prénom du patient       |
| dateNaissance | LocalDate | Date de naissance       |
| contact       | String    | Coordonnées             |

### Appointment (Rendez-vous)

| Champ     | Type          | Description                                  |
| --------- | ------------- | -------------------------------------------- |
| id        | Long          | Identifiant auto-généré                      |
| patientId | Long          | Référence au patient (clé étrangère logique) |
| date      | LocalDateTime | Date et heure du rendez-vous                 |

### MedicalRecord (Dossier médical)

| Champ       | Type          | Description             |
| ----------- | ------------- | ----------------------- |
| id          | Long          | Identifiant auto-généré |
| patientId   | Long          | Référence au patient    |
| diagnostics | String        | Diagnostic médical      |
| createdAt   | LocalDateTime | Date de création        |

---

## 4. Communication Inter-services

### 4.1 OpenFeign

Les services `appointment-service` et `medical-record-service` utilisent **OpenFeign** pour appeler le `patient-service` via Eureka :

```java
@FeignClient(name = "patient-service")
public interface PatientServiceClient {
    @GetMapping("/api/patients/{id}")
    PatientDTO getPatientById(@PathVariable("id") Long id);
}
```

Lors de la création d'un rendez-vous ou d'un dossier médical, le service vérifie l'existence du patient avant de persister la donnée.

### 4.2 API Gateway — Routage

L'API Gateway utilise la DSL Java de Spring Cloud Gateway MVC :

```java
@Bean
public RouterFunction<ServerResponse> patientRoute() {
    return route("patient-service")
            .route(path("/api/patients/**"), HandlerFunctions.http())
            .before(BeforeFilterFunctions.uri("http://localhost:8081"))
            .build();
}
```

---

## 5. Tolérance aux pannes (Resilience4j)

### 5.1 Configuration

```yaml
resilience4j:
  circuitbreaker:
    instances:
      patientService:
        slidingWindowSize: 10
        permittedNumberOfCallsInHalfOpenState: 3
        waitDurationInOpenState: 5000ms
        failureRateThreshold: 50
  retry:
    instances:
      patientService:
        maxAttempts: 3
        waitDuration: 2000ms
```

### 5.2 Annotations

```java
@PostMapping
@CircuitBreaker(name = "patientService", fallbackMethod = "fallbackCreateAppointment")
@Retry(name = "patientService")
public Appointment createAppointment(@RequestBody Appointment appointment) { ... }

public Appointment fallbackCreateAppointment(Appointment appointment, Throwable throwable) {
    throw new RuntimeException("Service patient temporairement indisponible...");
}
```

### 5.3 Résultat du test

| Scénario         | patient-service | Résultat                                          |
| ---------------- | --------------- | ------------------------------------------------- |
| Création RDV     | ✅ UP           | 200 OK — RDV créé avec succès                     |
| Création RDV     | ❌ DOWN         | 500 — Fallback : « Service patient indisponible » |
| Création dossier | ✅ UP           | 200 OK — Dossier créé                             |
| Création dossier | ❌ DOWN         | 500 — Fallback : « Service patient indisponible » |

---

## 6. Tests effectués

### 6.1 Vérification des services

- ✅ Tous les 6 services démarrent correctement
- ✅ Tous les services s'enregistrent sur Eureka
- ✅ Le Config Server distribue les configurations

### 6.2 Tests fonctionnels via API Gateway

- ✅ `POST /api/patients` → Création patient (200 OK)
- ✅ `GET /api/patients` → Liste des patients (200 OK)
- ✅ `POST /api/appointments` → Création RDV avec vérification patient (200 OK)
- ✅ `POST /api/medical-records` → Création dossier avec vérification patient (200 OK)

### 6.3 Tests de résilience

- ✅ Arrêt du `patient-service` → Fallback Resilience4j activé
- ✅ Circuit Breaker + Retry fonctionnels
- ✅ Message d'erreur contrôlé retourné au client

---

## 7. Difficultés rencontrées

1. **Spring Cloud Gateway MVC vs WebFlux** : Spring Boot 4.x utilise par défaut Gateway MVC (Tomcat) au lieu de WebFlux (Netty). La configuration YAML des routes ne fonctionnait pas avec la version MVC.
   - **Solution** : Remplacement de la configuration YAML par du code Java utilisant `GatewayRouterFunctions.route()` et `HandlerFunctions.http()` avec `BeforeFilterFunctions.uri()`.

2. **Dépendance Config Server manquante** : L'API Gateway ne récupérait pas sa configuration du Config Server car la dépendance `spring-cloud-starter-config` était absente.
   - **Solution** : Ajout de la dépendance dans le `pom.xml`.

3. **Compilation Java** : Le compilateur Eclipse dans Maven causait des conflits de version.
   - **Solution** : Utilisation de Docker avec `maven:3.9-eclipse-temurin-17`.

---

## 8. Conclusion

Ce projet démontre la mise en œuvre complète d'une architecture microservices avec Spring Cloud, incluant la configuration centralisée, la découverte de services, le routage API, la communication inter-services via OpenFeign, et la tolérance aux pannes avec Resilience4j. L'ensemble fonctionne de manière cohérente et les mécanismes de résilience protègent efficacement le système en cas de défaillance d'un service.
