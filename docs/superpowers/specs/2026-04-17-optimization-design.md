# Design: Ottimizzazione e Modernizzazione — social-media-backend

**Data:** 2026-04-17  
**Approccio:** Component by component (Approccio B)  
**Scope:** Backend (Java/Spring Boot) + Frontend (React) dove ci sono impatti API  
**Vincoli:** Java 21, Spring Boot 3.4.x, test esistenti devono restare verdi, breaking changes OK con deploy coordinato

---

## Contesto

Il backend è un monolite Spring Boot 3.3.5 / Java 17 deployato su Koyeb con PostgreSQL (Neon), Redis (Upstash) e Cloudinary. Il frontend è in React con services JS che chiamano le API REST.

Problemi principali identificati:
- **Performance:** N+1 queries in `PostServiceImpl.mapToResponse` (fino a 120+ query per pagina)
- **SOLID:** `PostServiceImpl` con 11 dipendenze (God class, viola SRP)
- **Duplicazione:** Due classi JWT (`JwtUtil` + `JwtTokenProvider`) che fanno la stessa cosa
- **Multi-istanza:** `onlineUsers` in JVM memory in `MessagingServiceImpl` — si rompe con più istanze Koyeb
- **API deprecated:** `SignatureAlgorithm.HS256` deprecato in jjwt 0.12.x
- **Java obsoleto:** Java 17 invece di Java 21 (LTS con virtual threads)
- **Pattern minori:** log con string concatenation, `Collectors.toList()` invece di `.toList()`, doppi lookup repository

---

## Fase 0 — Infrastructure

### Obiettivo
Aggiornare il runtime a Java 21 e Spring Boot 3.4.x, abilitare virtual threads.

### Modifiche

**`pom.xml`:**
- `java.version`: `17` → `21`
- `maven.compiler.source/target`: `17` → `21`
- Spring Boot parent: `3.3.5` → `3.4.x` (ultima stabile)

**`application.properties` (tutti i profili):**
```properties
spring.threads.virtual.enabled=true
```

### Impatto frontend
Nessuno. Il cambio è puramente infrastrutturale.

### Test
- `./mvnw compile -q` deve passare senza errori
- `./mvnw test` deve restare verde

---

## Fase 1 — Auth

### Obiettivo
Eliminare la duplicazione tra `JwtUtil` e `JwtTokenProvider`, creare un unico `JwtService`, fixare l'API deprecated.

### Problema attuale
Esistono due classi che generano/validano JWT con la stessa configurazione:
- `JwtUtil` — usato da `AuthServiceImpl`, `OAuth2SuccessHandler`
- `JwtTokenProvider` — usato da `JwtAuthenticationFilter`, `WebSocketConfig`

Entrambe leggono `jwt.secret` / `jwt.expiration` e producono token compatibili. Questo viola DRY e rende difficile modificare la logica JWT in un unico posto.

### Soluzione
Creare `JwtService` che unifica entrambe le responsabilità:
- `generateToken(String username)` — genera token JWT
- `extractUsername(String token)` — nome unico (i chiamanti che usavano `getUsernameFromToken` vengono aggiornati)
- `validateToken(String token, UserDetails userDetails)` — valida token + verifica scadenza
- `isTokenExpired(String token)` — helper interno

Eliminare `JwtUtil` e `JwtTokenProvider`. Tutti i chiamanti vengono aggiornati per usare il metodo `extractUsername` unificato.

**Fix API deprecated:**
```java
// Prima (deprecated in jjwt 0.12.x):
.signWith(key, SignatureAlgorithm.HS256)

// Dopo:
.signWith(key, Jwts.SIG.HS256)
```

### File modificati (backend)
- `DELETE` `security/JwtUtil.java`
- `DELETE` `security/JwtTokenProvider.java`
- `CREATE` `security/JwtService.java`
- `UPDATE` `auth/service/impl/AuthServiceImpl.java`
- `UPDATE` `auth/OAuth2SuccessHandler.java`
- `UPDATE` `security/JwtAuthenticationFilter.java`
- `UPDATE` `config/WebSocketConfig.java`

### Impatto frontend
Nessuno. Il formato del token JWT non cambia.

### Test
- `AuthControllerIntegrationTest` deve restare verde
- Tutti i test che usano JWT authentication devono restare verdi

---

## Fase 2 — Post

### Obiettivo
1. Eliminare il problema N+1 queries in `mapToResponse`
2. Spacchettare `PostServiceImpl` (God class con 11 dipendenze)
3. Rimuovere doppio lookup `userRepository.findById` in `create()`
4. Completare la migrazione multi-immagine (rimuovere campo deprecated `imageUrl` da `Post`)

### 2a — Fix N+1 queries

**Problema attuale:**
Ogni chiamata a `mapToResponse(post, userId)` esegue:
1. `likeRepository.countByPostId(post.getId())`
2. `commentRepository.countByPostId(post.getId())`
3. `likeRepository.existsByUserIdAndPostId(currentUserId, post.getId())`
4. `bookmarkRepository.existsByUserIdAndPostId(currentUserId, post.getId())`
5. `pollRepository.findByPostIdWithOptions(post.getId())`
6. `pollVoteRepository.findVotedOptionId(poll.getId(), currentUserId)`

Per una pagina da 20 post: **fino a 120 query SQL** per singola richiesta GET /api/posts.

**Soluzione:**
Creare una query JPQL/native che recupera tutti i conteggi per un set di post IDs in una singola roundtrip, restituendo una projection `PostStatsProjection`:

```java
public interface PostStatsProjection {
    Long getPostId();
    Long getLikeCount();
    Long getCommentCount();
    Boolean getIsLiked();      // solo se currentUserId != null
    Boolean getIsBookmarked(); // solo se currentUserId != null
}
```

La `mapToResponse` non esegue più query — riceve i dati pre-caricati da una mappa `Map<Long, PostStatsProjection>`.

### 2b — Spacchettare PostServiceImpl

**Problema attuale:**
`PostServiceImpl` ha 11 dipendenze iniettate via costruttore. Viola il principio SRP (Single Responsibility Principle): una classe deve avere un solo motivo per cambiare.

**Soluzione — 3 service specializzati:**

| Service | Responsabilità | Dipendenze principali |
|---------|---------------|----------------------|
| `PostCrudService` | create, read (getById), update, delete | PostRepository, UserRepository, StorageService, MentionService |
| `PostFeedService` | getAll, getByAuthorId, getFeed, getExplorePosts, searchByHashtag, countByAuthor | PostRepository, FollowRepository |
| `PostStatsService` | mapToResponse, caricamento stats batch (like/comment/bookmark/poll) | LikeRepository, CommentRepository, BookmarkRepository, PollRepository, PollVoteRepository |

L'interfaccia `PostService` viene **divisa in 3 interfacce** corrispondenti: `PostCrudService`, `PostFeedService`, `PostStatsService`. Il `PostController` viene aggiornato per iniettare le 3 interfacce separate. Non vengono aggiunti endpoint nuovi — solo la struttura interna cambia.

### 2c — Fix doppio lookup

```java
// Prima (in create() e createWithImage()):
User author = userRepository.findById(userId).orElseThrow(...);
// ...
mentionService.processMentions(..., userRepository.findById(userId).map(u -> u.getUsername()).orElse(""));

// Dopo:
User author = userRepository.findById(userId).orElseThrow(...);
// ...
mentionService.processMentions(..., author.getUsername()); // usa l'oggetto già caricato
```

### 2d — Migrazione campo imageUrl

Rimuovere il campo `@Deprecated String imageUrl` dall'entità `Post`.

**Impatto frontend:** Il campo `imageUrl` nella `PostResponse` verrà rimosso. Il frontend deve usare `images[0]?.imageUrl` oppure `imageUrls[0]`. 

**File frontend da aggiornare:**
- Cercare tutti i componenti che leggono `post.imageUrl` direttamente e sostituire con `post.images?.[0]?.imageUrl ?? post.imageUrls?.[0] ?? null`

### File modificati (backend)
- `UPDATE` `post/entity/Post.java` — rimuovere imageUrl deprecated
- `UPDATE` `post/service/PostService.java` — interfaccia invariata
- `DELETE` `post/service/PostServiceImpl.java`
- `CREATE` `post/service/impl/PostCrudServiceImpl.java`
- `CREATE` `post/service/impl/PostFeedServiceImpl.java`
- `CREATE` `post/service/impl/PostStatsServiceImpl.java`
- `UPDATE` `post/repository/PostRepository.java` — aggiungere query batch stats
- `CREATE` `post/dto/PostStatsProjection.java`

### Impatto frontend
- Rimuovere uso di `post.imageUrl` (campo rimosso) → usare `post.images?.[0]?.imageUrl`
- `post.imageUrls` rimane per compatibilità durante la transizione

---

## Fase 3 — Messaging

### Obiettivo
1. Spostare `onlineUsers` da JVM memory a Redis
2. Fix N+1 in `mapToMessageDTO`
3. Fix triple lookup in `toggleReaction`

### 3a — Online users in Redis

**Problema attuale:**
```java
private final Set<Long> onlineUsers = java.util.concurrent.ConcurrentHashMap.newKeySet();
```
Questo Set è in memoria JVM. Con più istanze Koyeb, ogni istanza ha la sua copia — un utente connesso all'istanza A risulta offline per i client sull'istanza B.

**Soluzione:**
Usare Redis con chiavi `online:<userId>` e TTL di 5 minuti (refreshato da heartbeat WebSocket):
```java
// setOnline:
redisTemplate.opsForValue().set("online:" + userId, "1", Duration.ofMinutes(5));

// setOffline:
redisTemplate.delete("online:" + userId);

// isOnline:
return Boolean.TRUE.equals(redisTemplate.hasKey("online:" + userId));
```

### 3b — Fix N+1 in getMessages

**Problema attuale:**
`getMessages()` chiama `mapToMessageDTO()` per ogni messaggio, e ogni chiamata esegue `reactionRepository.findByMessageId(msg.getId())`. Per una conversazione con 50 messaggi = 50 query aggiuntive.

**Soluzione:**
Pre-caricare tutte le reactions in una singola query per lista di messageIds, poi passare la mappa pre-caricata a `mapToMessageDTO`.

### 3c — Fix triple lookup in toggleReaction

```java
// Prima: messageRepository.findById() chiamato 3 volte
messageRepository.findById(messageId)...       // riga 184
Message msg = messageRepository.findById(messageId).get(); // riga 198
Message updated = messageRepository.findById(messageId)... // riga 204

// Dopo: caricare una volta, riutilizzare
Message msg = messageRepository.findById(messageId).orElseThrow(...);
// operazioni su msg...
// alla fine, msg è già l'oggetto aggiornato (o ricaricato una sola volta)
```

### File modificati (backend)
- `UPDATE` `messaging/service/impl/MessagingServiceImpl.java`
- `UPDATE` `messaging/repository/MessageRepository.java` — aggiungere query batch reactions

### Impatto frontend
- L'API `/online-status` risponde uguale — nessuna modifica frontend necessaria

---

## Fase 4 — Notification

### Obiettivo
Revisione query, aggiunta paginazione, ottimizzazione WebSocket push.

### Modifiche
- Aggiungere paginazione a `GET /api/notifications` (attualmente carica tutte)
- Verificare presenza di N+1 queries nel `NotificationServiceImpl`
- Assicurarsi che il push WebSocket non causi query extra al DB

### Impatto frontend
- Se viene aggiunta paginazione, aggiornare `notificationService.js` per supportare `page` / `size` params

---

## Fase 5 — Cross-cutting

### Obiettivo
Fix pattern minori trasversali a tutto il codebase.

### 5a — Log format
```java
// Prima (anti-pattern: string concat valutata sempre, anche se log disabilitato):
log.info("{}", "📋 Feed per user " + userId + " - Segue " + followedUserIds.size() + " utenti");

// Dopo (corretto: lazy evaluation, parametri passati separatamente):
log.info("Feed per user {} - Segue {} utenti", userId, followedUserIds.size());
```

### 5b — Stream.toList()
```java
// Prima (Java 8 pattern):
.collect(Collectors.toList())

// Dopo (Java 16+, lista immutabile, più efficiente):
.toList()
```

### 5c — Caching con @Cacheable
Aggiungere cache per hot reads che non cambiano frequentemente:
- `GET /api/users/{username}` — profilo utente (TTL 5 min)
- `GET /api/posts/{id}` — post singolo (TTL 2 min, invalidato su update/delete)
- Conteggi follow (TTL 1 min)

Usare Spring Cache con Redis come backend (già disponibile nel progetto).

### Impatto frontend
Nessuno. Le risposte sono identiche, solo più veloci.

---

## Impatti frontend riassunti

| Fase | Cambio API | File frontend da aggiornare |
|------|-----------|----------------------------|
| Fase 2d | `post.imageUrl` rimosso da `PostResponse` | Componenti che leggono `post.imageUrl` direttamente |
| Fase 4 | Paginazione notifiche (se aggiunta) | `notificationService.js` |
| Resto | Nessun cambio API visibile | — |

---

## Ordine di esecuzione e test

Ogni fase segue questo ciclo:
1. Implementare le modifiche backend
2. `./mvnw test` — tutti i test devono restare verdi
3. Aggiornare frontend dove necessario
4. `./mvnw clean package -DskipTests` — build deve passare
5. Deploy opzionale su Koyeb per validare in produzione
