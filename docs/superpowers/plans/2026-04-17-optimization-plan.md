# Ottimizzazione e Modernizzazione Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modernizzare il social-media-backend a Java 21 / Spring Boot 3.4.x, eliminare N+1 queries, rispettare i principi SOLID e aggiornare il frontend dove necessario.

**Architecture:** Approccio component-by-component in 6 fasi sequenziali. Ogni fase produce codice funzionante con test verdi prima di passare alla successiva. Il backend usa Spring Boot 3.4.x + Java 21 virtual threads; il frontend React viene aggiornato nelle fasi che cambiano l'API.

**Tech Stack:** Java 21, Spring Boot 3.4.x, jjwt 0.12.3, Spring Data JPA, Redis (Upstash), Cloudinary, H2 (test), MockMvc (integration tests), React (frontend)

---

## File Map

### Fase 0 — Infrastructure
| Azione | File |
|--------|------|
| MODIFY | `pom.xml` |
| MODIFY | `src/main/resources/application.properties` |

### Fase 1 — Auth / JWT
| Azione | File |
|--------|------|
| CREATE | `src/main/java/com/social/backend/security/JwtService.java` |
| DELETE | `src/main/java/com/social/backend/security/JwtUtil.java` |
| DELETE | `src/main/java/com/social/backend/security/JwtTokenProvider.java` |
| MODIFY | `src/main/java/com/social/backend/security/JwtAuthenticationFilter.java` |
| MODIFY | `src/main/java/com/social/backend/config/WebSocketConfig.java` |
| MODIFY | `src/main/java/com/social/backend/components/auth/service/impl/AuthServiceImpl.java` |
| MODIFY | `src/main/java/com/social/backend/components/auth/oauth2/OAuth2SuccessHandler.java` |

### Fase 2 — Post
| Azione | File |
|--------|------|
| CREATE | `src/main/java/com/social/backend/components/post/dto/PostCountProjection.java` |
| CREATE | `src/main/java/com/social/backend/components/post/service/PostCrudService.java` |
| CREATE | `src/main/java/com/social/backend/components/post/service/PostFeedService.java` |
| CREATE | `src/main/java/com/social/backend/components/post/service/PostStatsService.java` |
| CREATE | `src/main/java/com/social/backend/components/post/service/impl/PostCrudServiceImpl.java` |
| CREATE | `src/main/java/com/social/backend/components/post/service/impl/PostFeedServiceImpl.java` |
| CREATE | `src/main/java/com/social/backend/components/post/service/impl/PostStatsServiceImpl.java` |
| MODIFY | `src/main/java/com/social/backend/components/like/repository/LikeRepository.java` |
| MODIFY | `src/main/java/com/social/backend/components/comment/repository/CommentRepository.java` |
| MODIFY | `src/main/java/com/social/backend/components/bookmark/repository/BookmarkRepository.java` |
| MODIFY | `src/main/java/com/social/backend/components/poll/repository/PollRepository.java` |
| MODIFY | `src/main/java/com/social/backend/components/poll/repository/PollVoteRepository.java` |
| MODIFY | `src/main/java/com/social/backend/components/post/controller/PostController.java` |
| MODIFY | `src/main/java/com/social/backend/components/post/entity/Post.java` |
| MODIFY | `src/main/java/com/social/backend/components/post/dto/PostResponse.java` |
| DELETE | `src/main/java/com/social/backend/components/post/service/PostService.java` |
| DELETE | `src/main/java/com/social/backend/components/post/service/PostServiceImpl.java` |
| MODIFY (frontend) | `social-media-frontend/src/services/postService.js` |
| MODIFY (frontend) | Component files che usano `post.imageUrl` |

### Fase 3 — Messaging
| Azione | File |
|--------|------|
| MODIFY | `src/main/java/com/social/backend/components/messaging/service/impl/MessagingServiceImpl.java` |
| MODIFY | `src/main/java/com/social/backend/components/messaging/repository/MessageReactionRepository.java` |

### Fase 4 — Notification (verifica)
| Azione | File |
|--------|------|
| VERIFY | `src/main/java/com/social/backend/components/notification/service/impl/NotificationServiceImpl.java` |

### Fase 5 — Cross-cutting
| Azione | File |
|--------|------|
| MODIFY | Tutti i file con `Collectors.toList()` e log con string concat |
| MODIFY | `src/main/java/com/social/backend/config/RedisConfig.java` |
| MODIFY | Servizi con hot reads (`UserServiceImpl`, `PostCrudServiceImpl`) |

---

## Task 1: Upgrade a Java 21 + Spring Boot 3.4.x + Virtual Threads

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Aggiorna `pom.xml` — Java 21 e Spring Boot 3.4.5**

Apri `pom.xml` e sostituisci:

```xml
<!-- DA: -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
</parent>
...
<properties>
    <java.version>17</java.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    ...
</properties>

<!-- A: -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.5</version>
    <relativePath/>
</parent>
...
<properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    ...
</properties>
```

- [ ] **Step 2: Abilita virtual threads in `application.properties`**

Aggiungi alla fine di `src/main/resources/application.properties`:

```properties
# Virtual Threads (Java 21) — ogni richiesta HTTP usa un virtual thread leggero
spring.threads.virtual.enabled=true
```

- [ ] **Step 3: Verifica compilazione**

```bash
./mvnw compile -q
```

Atteso: nessun errore. Se ci sono warning su deprecazioni di API Spring Boot, annotali ma non bloccarti.

- [ ] **Step 4: Esegui tutti i test**

```bash
./mvnw test
```

Atteso: tutti i test passano. Se falliscono per cambiamenti di API Spring Boot 3.4.x, risolvi prima di proseguire.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/application.properties
git commit -m "feat(infra): upgrade to Java 21, Spring Boot 3.4.5, enable virtual threads"
```

---

## Task 2: Crea JwtService — unifica JwtUtil e JwtTokenProvider

**Files:**
- Create: `src/main/java/com/social/backend/security/JwtService.java`

- [ ] **Step 1: Crea `JwtService.java`**

```java
package com.social.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return true;
        } catch (SignatureException ex) {
            log.error("JWT signature non valida: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.error("JWT token malformato: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("JWT token scaduto: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("JWT token non supportato: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims vuoti: {}", ex.getMessage());
        }
        return false;
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Date expiry = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
        return expiry.before(new Date());
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 2: Verifica compilazione di JwtService isolato**

```bash
./mvnw compile -q
```

Atteso: si compila (JwtUtil e JwtTokenProvider esistono ancora — conflitto non ancora risolto).

---

## Task 3: Aggiorna i chiamanti per usare JwtService

**Files:**
- Modify: `src/main/java/com/social/backend/security/JwtAuthenticationFilter.java`
- Modify: `src/main/java/com/social/backend/config/WebSocketConfig.java`
- Modify: `src/main/java/com/social/backend/components/auth/service/impl/AuthServiceImpl.java`
- Modify: `src/main/java/com/social/backend/components/auth/oauth2/OAuth2SuccessHandler.java`

- [ ] **Step 1: Aggiorna `JwtAuthenticationFilter.java`**

Sostituisci `JwtTokenProvider jwtTokenProvider` con `JwtService jwtService`:

```java
// Cambia import:
// RIMUOVI: import com.social.backend.security.JwtTokenProvider;
// AGGIUNGI: (già nel package, nessun import necessario)

// Cambia field e costruttore:
private final JwtService jwtService;
private final UserDetailsService userDetailsService;

public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
    this.jwtService = jwtService;
    this.userDetailsService = userDetailsService;
}

// Nel metodo doFilterInternal, cambia:
// DA: jwtTokenProvider.validateToken(jwt)  →  jwtService.validateToken(jwt)
// DA: jwtTokenProvider.getUsernameFromToken(jwt)  →  jwtService.extractUsername(jwt)
```

- [ ] **Step 2: Aggiorna `WebSocketConfig.java`**

Trova la classe e sostituisci `JwtTokenProvider` con `JwtService`:

```java
// Cambia field:
private final JwtService jwtService;

// Nel metodo configureClientInboundChannel dove si valida il token STOMP CONNECT:
// DA: jwtTokenProvider.validateToken(token)  →  jwtService.validateToken(token)
// DA: jwtTokenProvider.getUsernameFromToken(token)  →  jwtService.extractUsername(token)
```

- [ ] **Step 3: Aggiorna `AuthServiceImpl.java`**

```java
// Cambia import:
// RIMUOVI: import com.social.backend.security.JwtUtil;
// AGGIUNGI: import com.social.backend.security.JwtService;

// Cambia field e costruttore:
private final JwtService jwtService;

public AuthServiceImpl(AuthenticationManager authenticationManager,
                       JwtService jwtService,  // <-- era JwtUtil jwtUtil
                       ...) {
    this.jwtService = jwtService;
    ...
}

// Sostituisci tutte le occorrenze:
// jwtUtil.generateToken(...)  →  jwtService.generateToken(...)
// jwtUtil.extractUsername(...)  →  jwtService.extractUsername(...)
```

- [ ] **Step 4: Aggiorna `OAuth2SuccessHandler.java`**

```java
// Cambia import:
// RIMUOVI: import com.social.backend.security.JwtUtil;
// AGGIUNGI: import com.social.backend.security.JwtService;

// Cambia field (usa @RequiredArgsConstructor — Lombok):
private final JwtService jwtService;  // era: private final JwtUtil jwtUtil;

// Sostituisci:
// jwtUtil.generateToken(user.getUsername())  →  jwtService.generateToken(user.getUsername())
```

- [ ] **Step 5: Verifica compilazione**

```bash
./mvnw compile -q
```

Atteso: nessun errore (JwtUtil e JwtTokenProvider sono ancora presenti ma non più usati).

- [ ] **Step 6: Esegui i test di autenticazione**

```bash
./mvnw test -Dtest=AuthControllerIntegrationTest,RefreshTokenIntegrationTest
```

Atteso: tutti i test passano.

---

## Task 4: Elimina JwtUtil e JwtTokenProvider

**Files:**
- Delete: `src/main/java/com/social/backend/security/JwtUtil.java`
- Delete: `src/main/java/com/social/backend/security/JwtTokenProvider.java`

- [ ] **Step 1: Verifica che nessun file importi ancora JwtUtil o JwtTokenProvider**

```bash
grep -r "JwtUtil\|JwtTokenProvider" src/main/java --include="*.java"
```

Atteso: nessun risultato (tutti i chiamanti già aggiornati nel Task 3).

- [ ] **Step 2: Elimina i file**

```bash
rm src/main/java/com/social/backend/security/JwtUtil.java
rm src/main/java/com/social/backend/security/JwtTokenProvider.java
```

- [ ] **Step 3: Compila e testa**

```bash
./mvnw compile -q && ./mvnw test
```

Atteso: compilazione OK, tutti i test passano.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(auth): unify JwtUtil+JwtTokenProvider into JwtService, fix deprecated SignatureAlgorithm"
```

---

## Task 5: Aggiungi query batch ai repository per le statistiche post

**Files:**
- Create: `src/main/java/com/social/backend/components/post/dto/PostCountProjection.java`
- Modify: `src/main/java/com/social/backend/components/like/repository/LikeRepository.java`
- Modify: `src/main/java/com/social/backend/components/comment/repository/CommentRepository.java`
- Modify: `src/main/java/com/social/backend/components/bookmark/repository/BookmarkRepository.java`
- Modify: `src/main/java/com/social/backend/components/poll/repository/PollRepository.java`
- Modify: `src/main/java/com/social/backend/components/poll/repository/PollVoteRepository.java`

- [ ] **Step 1: Crea `PostCountProjection.java`**

```java
package com.social.backend.components.post.dto;

public interface PostCountProjection {
    Long getPostId();
    Long getCount();
}
```

- [ ] **Step 2: Aggiungi metodi batch a `LikeRepository.java`**

Aggiungi questi metodi all'interfaccia esistente:

```java
@Query("SELECT l.postId AS postId, COUNT(l) AS count FROM Like l WHERE l.postId IN :postIds GROUP BY l.postId")
List<PostCountProjection> countByPostIds(@Param("postIds") List<Long> postIds);

@Query("SELECT l.postId FROM Like l WHERE l.userId = :userId AND l.postId IN :postIds")
Set<Long> findLikedPostIdsByUser(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
```

Aggiungi gli import: `import com.social.backend.components.post.dto.PostCountProjection;` e `import java.util.Set;`

- [ ] **Step 3: Aggiungi metodo batch a `CommentRepository.java`**

Aggiungi all'interfaccia esistente:

```java
@Query("SELECT c.post.id AS postId, COUNT(c) AS count FROM Comment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
List<PostCountProjection> countByPostIds(@Param("postIds") List<Long> postIds);
```

Aggiungi import: `import com.social.backend.components.post.dto.PostCountProjection;`

- [ ] **Step 4: Aggiungi metodo batch a `BookmarkRepository.java`**

Aggiungi all'interfaccia esistente:

```java
@Query("SELECT b.post.id FROM Bookmark b WHERE b.user.id = :userId AND b.post.id IN :postIds")
Set<Long> findBookmarkedPostIdsByUser(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
```

Aggiungi import: `import java.util.Set;`

- [ ] **Step 5: Aggiungi metodo batch a `PollRepository.java`**

Aggiungi all'interfaccia esistente:

```java
@Query("SELECT p FROM Poll p JOIN FETCH p.options WHERE p.post.id IN :postIds")
List<Poll> findByPostIdsWithOptions(@Param("postIds") List<Long> postIds);
```

- [ ] **Step 6: Aggiungi metodo batch a `PollVoteRepository.java`**

Aggiungi all'interfaccia esistente:

```java
@Query("SELECT pv.poll.id AS pollId, pv.option.id AS optionId FROM PollVote pv WHERE pv.poll.id IN :pollIds AND pv.user.id = :userId")
List<PollVoteProjection> findVotedOptionsByPollIds(@Param("pollIds") List<Long> pollIds, @Param("userId") Long userId);
```

E crea l'interfaccia projection nello stesso package di `PollVoteRepository` (o nel dto package poll):

```java
// src/main/java/com/social/backend/components/poll/dto/PollVoteProjection.java
package com.social.backend.components.poll.dto;

public interface PollVoteProjection {
    Long getPollId();
    Long getOptionId();
}
```

- [ ] **Step 7: Verifica compilazione**

```bash
./mvnw compile -q
```

Atteso: nessun errore. Se Poll/PollVote hanno nomi di campo diversi, adatta le query JPQL di conseguenza.

---

## Task 6: Crea PostStatsService — caricamento statistiche in batch

**Files:**
- Create: `src/main/java/com/social/backend/components/post/service/PostStatsService.java`
- Create: `src/main/java/com/social/backend/components/post/service/impl/PostStatsServiceImpl.java`

- [ ] **Step 1: Crea interfaccia `PostStatsService.java`**

```java
package com.social.backend.components.post.service;

import com.social.backend.components.post.dto.PostResponse;
import com.social.backend.components.post.entity.Post;
import org.springframework.data.domain.Page;

import java.util.List;

public interface PostStatsService {
    Page<PostResponse> enrichPage(Page<Post> posts, Long currentUserId);
    PostResponse enrichOne(Post post, Long currentUserId);
}
```

- [ ] **Step 2: Crea `PostStatsServiceImpl.java`**

```java
package com.social.backend.components.post.service.impl;

import com.social.backend.components.bookmark.repository.BookmarkRepository;
import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.like.repository.LikeRepository;
import com.social.backend.components.poll.dto.PollVoteProjection;
import com.social.backend.components.poll.repository.PollRepository;
import com.social.backend.components.poll.repository.PollVoteRepository;
import com.social.backend.components.post.dto.PostCountProjection;
import com.social.backend.components.post.dto.PostImageDto;
import com.social.backend.components.post.dto.PostResponse;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.entity.PostImage;
import com.social.backend.components.post.service.PostStatsService;
import com.social.backend.components.poll.entity.Poll;
import com.social.backend.components.poll.dto.PollResponse;
import com.social.backend.components.poll.dto.PollOptionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostStatsServiceImpl implements PostStatsService {

    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final BookmarkRepository bookmarkRepository;
    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> enrichPage(Page<Post> posts, Long currentUserId) {
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        if (postIds.isEmpty()) return posts.map(p -> enrichOne(p, currentUserId));

        // Batch: 1 query per likeCount, 1 per commentCount
        Map<Long, Long> likeCounts = toCountMap(likeRepository.countByPostIds(postIds));
        Map<Long, Long> commentCounts = toCountMap(commentRepository.countByPostIds(postIds));

        // Batch: 1 query per liked, 1 per bookmarked (solo se autenticato)
        Set<Long> likedIds = currentUserId != null
                ? likeRepository.findLikedPostIdsByUser(currentUserId, postIds) : Set.of();
        Set<Long> bookmarkedIds = currentUserId != null
                ? bookmarkRepository.findBookmarkedPostIdsByUser(currentUserId, postIds) : Set.of();

        // Batch: 1 query per tutti i poll della pagina
        Map<Long, Poll> pollsByPostId = pollRepository.findByPostIdsWithOptions(postIds)
                .stream().collect(Collectors.toMap(p -> p.getPost().getId(), p -> p));

        // Batch: 1 query per i voti dell'utente su tutti i poll della pagina
        List<Long> pollIds = pollsByPostId.values().stream().map(Poll::getId).toList();
        Map<Long, Long> votedOptionByPollId = currentUserId != null && !pollIds.isEmpty()
                ? toVotedMap(pollVoteRepository.findVotedOptionsByPollIds(pollIds, currentUserId))
                : Map.of();

        return posts.map(post -> mapToResponse(
                post, currentUserId,
                likeCounts.getOrDefault(post.getId(), 0L),
                commentCounts.getOrDefault(post.getId(), 0L),
                likedIds.contains(post.getId()),
                bookmarkedIds.contains(post.getId()),
                pollsByPostId.get(post.getId()),
                votedOptionByPollId
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse enrichOne(Post post, Long currentUserId) {
        long likeCount = likeRepository.countByPostId(post.getId());
        long commentCount = commentRepository.countByPostId(post.getId());
        boolean liked = currentUserId != null && likeRepository.existsByUserIdAndPostId(currentUserId, post.getId());
        boolean bookmarked = currentUserId != null && bookmarkRepository.existsByUserIdAndPostId(currentUserId, post.getId());
        Poll poll = pollRepository.findByPostIdWithOptions(post.getId()).orElse(null);
        Map<Long, Long> votedMap = (poll != null && currentUserId != null)
                ? toVotedMap(pollVoteRepository.findVotedOptionsByPollIds(List.of(poll.getId()), currentUserId))
                : Map.of();
        return mapToResponse(post, currentUserId, likeCount, commentCount, liked, bookmarked, poll, votedMap);
    }

    private PostResponse mapToResponse(Post post, Long currentUserId,
                                        long likeCount, long commentCount,
                                        boolean liked, boolean bookmarked,
                                        Poll poll, Map<Long, Long> votedOptionByPollId) {
        PollResponse pollData = null;
        if (poll != null) {
            long total = poll.getOptions().stream()
                    .mapToLong(o -> o.getVoteCount() != null ? o.getVoteCount() : 0L).sum();
            Long votedOptionId = votedOptionByPollId.get(poll.getId());
            pollData = PollResponse.builder()
                    .id(poll.getId())
                    .question(poll.getQuestion())
                    .options(poll.getOptions().stream().map(o -> PollOptionResponse.builder()
                            .id(o.getId()).text(o.getText())
                            .voteCount(o.getVoteCount() != null ? o.getVoteCount() : 0L)
                            .percentage(total > 0 ? (o.getVoteCount() != null ? o.getVoteCount() : 0L) * 100.0 / total : 0)
                            .build()).toList())
                    .totalVotes(total)
                    .votedOptionId(votedOptionId)
                    .expired(poll.isExpired())
                    .expiresAt(poll.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant())
                    .build();
        }

        List<PostImageDto> imageDtos = post.getImages().stream()
                .sorted(Comparator.comparingInt(PostImage::getDisplayOrder))
                .map(img -> PostImageDto.builder()
                        .id(img.getId()).imageUrl(img.getImageUrl()).displayOrder(img.getDisplayOrder())
                        .build())
                .toList();

        List<String> imageUrls = imageDtos.stream().map(PostImageDto::getImageUrl).toList();

        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .images(imageDtos)
                .imageUrls(imageUrls)
                .authorId(post.getAuthor().getId())
                .authorUsername(post.getAuthor().getUsername())
                .authorAvatarUrl(post.getAuthor().getAvatarUrl())
                .createdAt(post.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .updatedAt(post.getUpdatedAt() != null
                        ? post.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
                .likeCount(likeCount)
                .commentCount(commentCount)
                .viewCount(post.getViewCount())
                .liked(liked)
                .bookmarked(bookmarked)
                .poll(pollData)
                .build();
    }

    private Map<Long, Long> toCountMap(List<PostCountProjection> projections) {
        return projections.stream()
                .collect(Collectors.toMap(PostCountProjection::getPostId, PostCountProjection::getCount));
    }

    private Map<Long, Long> toVotedMap(List<PollVoteProjection> projections) {
        return projections.stream()
                .collect(Collectors.toMap(PollVoteProjection::getPollId, PollVoteProjection::getOptionId));
    }
}
```

- [ ] **Step 3: Verifica compilazione**

```bash
./mvnw compile -q
```

Atteso: nessun errore. Se `Poll`, `PollResponse`, `PollOptionResponse` hanno package diversi, aggiusta gli import.

---

## Task 7: Crea PostCrudService e PostFeedService

**Files:**
- Create: `src/main/java/com/social/backend/components/post/service/PostCrudService.java`
- Create: `src/main/java/com/social/backend/components/post/service/PostFeedService.java`
- Create: `src/main/java/com/social/backend/components/post/service/impl/PostCrudServiceImpl.java`
- Create: `src/main/java/com/social/backend/components/post/service/impl/PostFeedServiceImpl.java`

- [ ] **Step 1: Crea interfaccia `PostCrudService.java`**

```java
package com.social.backend.components.post.service;

import com.social.backend.components.post.dto.CreatePostRequest;
import com.social.backend.components.post.dto.PostResponse;
import com.social.backend.components.post.dto.UpdatePostRequest;
import com.social.backend.components.post.entity.Post;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PostCrudService {
    PostResponse create(Long userId, CreatePostRequest request);
    PostResponse createWithImage(Long userId, CreatePostRequest request, MultipartFile image);
    PostResponse createWithImages(Long userId, CreatePostRequest request, List<MultipartFile> images);
    Post findById(Long postId);
    PostResponse getById(Long postId, Long currentUserId);
    PostResponse update(Long currentUserId, Long postId, UpdatePostRequest request);
    void delete(Long currentUserId, Long postId);
    void incrementViewCount(Long postId, Long currentUserId);
    PostResponse addImageToPost(Long userId, Long postId, MultipartFile image);
    PostResponse addImagesToPost(Long userId, Long postId, List<MultipartFile> images);
    void removeImageFromPost(Long userId, Long postId, Long imageId);
}
```

- [ ] **Step 2: Crea interfaccia `PostFeedService.java`**

```java
package com.social.backend.components.post.service;

import com.social.backend.components.post.dto.PostResponse;
import org.springframework.data.domain.Page;

public interface PostFeedService {
    Page<PostResponse> getAll(int page, int size, Long currentUserId);
    Page<PostResponse> getByAuthorId(Long authorId, int page, int size, Long currentUserId);
    Page<PostResponse> getFeed(Long userId, int page, int size);
    Page<PostResponse> getExplorePosts(Long currentUserId, int page, int size);
    Page<PostResponse> searchByHashtag(String tag, int page, int size, Long currentUserId);
    int countByAuthor(Long authorId);
}
```

- [ ] **Step 3: Crea `PostCrudServiceImpl.java`**

```java
package com.social.backend.components.post.service.impl;

import com.social.backend.common.exception.ForbiddenException;
import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.comment.repository.CommentRepository;
import com.social.backend.components.like.repository.LikeRepository;
import com.social.backend.components.mention.MentionService;
import com.social.backend.components.notification.repository.NotificationRepository;
import com.social.backend.components.poll.repository.PollRepository;
import com.social.backend.components.poll.repository.PollVoteRepository;
import com.social.backend.components.post.dto.CreatePostRequest;
import com.social.backend.components.post.dto.PostResponse;
import com.social.backend.components.post.dto.UpdatePostRequest;
import com.social.backend.components.post.entity.Post;
import com.social.backend.components.post.entity.PostImage;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.post.service.PostCrudService;
import com.social.backend.components.post.service.PostStatsService;
import com.social.backend.components.storage.service.StorageService;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class PostCrudServiceImpl implements PostCrudService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final MentionService mentionService;
    private final PostStatsService postStatsService;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final NotificationRepository notificationRepository;
    private final PollRepository pollRepository;
    private final PollVoteRepository pollVoteRepository;

    @Value("${storage.local.posts-dir:uploads/posts}")
    private String postsDir;

    public PostCrudServiceImpl(PostRepository postRepository,
                               UserRepository userRepository,
                               StorageService storageService,
                               MentionService mentionService,
                               PostStatsService postStatsService,
                               CommentRepository commentRepository,
                               LikeRepository likeRepository,
                               NotificationRepository notificationRepository,
                               PollRepository pollRepository,
                               PollVoteRepository pollVoteRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.mentionService = mentionService;
        this.postStatsService = postStatsService;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.notificationRepository = notificationRepository;
        this.pollRepository = pollRepository;
        this.pollVoteRepository = pollVoteRepository;
    }

    @Override
    @Transactional
    public PostResponse create(Long userId, CreatePostRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + userId));
        Post post = postRepository.save(Post.builder().content(request.getContent()).author(author).build());
        mentionService.processMentions(request.getContent(), userId, post.getId(), null, author.getUsername());
        log.info("Post creato - ID: {}", post.getId());
        return postStatsService.enrichOne(post, userId);
    }

    @Override
    @Transactional
    public PostResponse createWithImage(Long userId, CreatePostRequest request, MultipartFile image) {
        if ((request.getContent() == null || request.getContent().trim().isEmpty()) &&
                (image == null || image.isEmpty())) {
            throw new IllegalArgumentException("Il post deve avere almeno un contenuto testuale o un'immagine");
        }
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato con ID: " + userId));
        String imageUrl = null;
        if (image != null && !image.isEmpty()) {
            String fileName = storageService.store(image, postsDir);
            imageUrl = storageService.getFileUrl(fileName, postsDir);
        }
        Post post = postRepository.save(Post.builder()
                .content(request.getContent()).imageUrl(imageUrl).author(author).build());
        mentionService.processMentions(request.getContent(), userId, post.getId(), null, author.getUsername());
        return postStatsService.enrichOne(post, userId);
    }

    @Override
    @Transactional
    public PostResponse createWithImages(Long userId, CreatePostRequest request, List<MultipartFile> images) {
        if (images != null && images.size() > 5) {
            throw new IllegalArgumentException("Massimo 5 immagini per post");
        }
        if ((request.getContent() == null || request.getContent().trim().isEmpty()) &&
                (images == null || images.isEmpty())) {
            throw new IllegalArgumentException("Il post deve avere contenuto o almeno un'immagine");
        }
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utente non trovato"));
        Post post = postRepository.save(Post.builder().content(request.getContent()).author(author).build());
        if (images != null && !images.isEmpty()) {
            for (int i = 0; i < images.size(); i++) {
                String fileName = storageService.store(images.get(i), postsDir);
                String imageUrl = storageService.getFileUrl(fileName, postsDir);
                post.addImage(imageUrl, i);
            }
            post = postRepository.save(post);
        }
        mentionService.processMentions(request.getContent(), userId, post.getId(), null, author.getUsername());
        return postStatsService.enrichOne(post, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Post findById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post non trovato con ID: " + postId));
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getById(Long postId, Long currentUserId) {
        return postStatsService.enrichOne(findById(postId), currentUserId);
    }

    @Override
    @Transactional
    public PostResponse update(Long currentUserId, Long postId, UpdatePostRequest request) {
        Post post = findById(postId);
        if (!post.getAuthor().getId().equals(currentUserId)) {
            throw new ForbiddenException("Non puoi modificare un post di un altro utente");
        }
        post.setContent(request.getContent());
        return postStatsService.enrichOne(postRepository.save(post), currentUserId);
    }

    @Override
    @Transactional
    public void delete(Long currentUserId, Long postId) {
        Post post = findById(postId);
        if (!post.getAuthor().getId().equals(currentUserId)) {
            throw new ForbiddenException("Non puoi eliminare questo post");
        }
        commentRepository.deleteByPostId(postId);
        likeRepository.deleteByPostId(postId);
        notificationRepository.deleteByPostId(postId);
        pollRepository.findByPostIdWithOptions(postId).ifPresent(poll -> {
            pollVoteRepository.deleteByPollId(poll.getId());
            pollRepository.delete(poll);
        });
        postRepository.delete(post);
        log.info("Post eliminato - ID: {}", postId);
    }

    @Override
    @Transactional
    public void incrementViewCount(Long postId, Long currentUserId) {
        Post post = findById(postId);
        if (!post.getAuthor().getId().equals(currentUserId)) {
            postRepository.incrementViewCount(postId);
        }
    }

    @Override
    @Transactional
    public PostResponse addImageToPost(Long userId, Long postId, MultipartFile image) {
        Post post = findById(postId);
        if (!post.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Non sei autorizzato a modificare questo post");
        }
        String fileName = storageService.store(image, postsDir);
        post.setImageUrl(storageService.getFileUrl(fileName, postsDir));
        return postStatsService.enrichOne(postRepository.save(post), userId);
    }

    @Override
    @Transactional
    public PostResponse addImagesToPost(Long userId, Long postId, List<MultipartFile> images) {
        Post post = findById(postId);
        if (!post.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Non puoi modificare un post di un altro utente");
        }
        int current = post.getImages().size();
        if (current + images.size() > 5) {
            throw new IllegalArgumentException("Massimo 5 immagini per post. Hai già " + current);
        }
        for (MultipartFile img : images) {
            String fileName = storageService.store(img, postsDir);
            post.addImage(storageService.getFileUrl(fileName, postsDir), current++);
        }
        return postStatsService.enrichOne(postRepository.save(post), userId);
    }

    @Override
    @Transactional
    public void removeImageFromPost(Long userId, Long postId, Long imageId) {
        Post post = findById(postId);
        if (!post.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Non puoi modificare un post di un altro utente");
        }
        PostImage imageToRemove = post.getImages().stream()
                .filter(img -> img.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Immagine non trovata con ID: " + imageId));
        try {
            storageService.delete(extractPublicId(imageToRemove.getImageUrl()));
        } catch (Exception e) {
            log.error("Errore eliminazione da storage: {}", e.getMessage());
        }
        post.getImages().remove(imageToRemove);
        List<PostImage> remaining = post.getImages().stream()
                .sorted(Comparator.comparingInt(PostImage::getDisplayOrder))
                .toList();
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setDisplayOrder(i);
        }
        postRepository.save(post);
    }

    private String extractPublicId(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("http")) return imageUrl;
        int uploadIndex = imageUrl.indexOf("/upload/");
        if (uploadIndex == -1) return imageUrl;
        String after = imageUrl.substring(uploadIndex + 8);
        if (after.startsWith("v") && after.contains("/")) {
            after = after.substring(after.indexOf("/") + 1);
        }
        int lastDot = after.lastIndexOf(".");
        return lastDot > 0 ? after.substring(0, lastDot) : after;
    }
}
```

- [ ] **Step 4: Crea `PostFeedServiceImpl.java`**

```java
package com.social.backend.components.post.service.impl;

import com.social.backend.common.exception.ResourceNotFoundException;
import com.social.backend.components.follow.repository.FollowRepository;
import com.social.backend.components.post.dto.PostResponse;
import com.social.backend.components.post.repository.PostRepository;
import com.social.backend.components.post.service.PostFeedService;
import com.social.backend.components.post.service.PostStatsService;
import com.social.backend.components.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostFeedServiceImpl implements PostFeedService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PostStatsService postStatsService;

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getAll(int page, int size, Long currentUserId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return postStatsService.enrichPage(postRepository.findAllWithAuthor(pageable), currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getByAuthorId(Long authorId, int page, int size, Long currentUserId) {
        if (!userRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("Utente non trovato con ID: " + authorId);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return postStatsService.enrichPage(
                postRepository.findByAuthorIdWithAuthor(authorId, pageable), currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getFeed(Long userId, int page, int size) {
        List<Long> followedIds = followRepository.findFollowingIdsByUserId(userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (followedIds.isEmpty()) return Page.empty(pageable);
        return postStatsService.enrichPage(
                postRepository.findByAuthorIdInWithAuthor(followedIds, pageable), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getExplorePosts(Long currentUserId, int page, int size) {
        Set<Long> excluded = followRepository.findByFollowerId(currentUserId)
                .stream().map(f -> f.getFollowed().getId()).collect(Collectors.toSet());
        excluded.add(currentUserId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<com.social.backend.components.post.entity.Post> posts = excluded.isEmpty()
                ? postRepository.findAllWithAuthor(pageable)
                : postRepository.findByAuthorIdNotInWithAuthor(excluded, pageable);
        return postStatsService.enrichPage(posts, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> searchByHashtag(String tag, int page, int size, Long currentUserId) {
        String cleanTag = tag.startsWith("#") ? tag.substring(1) : tag;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return postStatsService.enrichPage(postRepository.findByHashtag(cleanTag, pageable), currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public int countByAuthor(Long authorId) {
        if (!userRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("Utente non trovato con ID: " + authorId);
        }
        return postRepository.countByAuthorId(authorId);
    }
}
```

- [ ] **Step 5: Verifica compilazione**

```bash
./mvnw compile -q
```

---

## Task 8: Aggiorna PostController e rimuovi PostService/PostServiceImpl

**Files:**
- Modify: `src/main/java/com/social/backend/components/post/controller/PostController.java`
- Delete: `src/main/java/com/social/backend/components/post/service/PostService.java`
- Delete: `src/main/java/com/social/backend/components/post/service/PostServiceImpl.java`

- [ ] **Step 1: Aggiorna `PostController.java`**

Sostituisci il campo `PostService postService` con i tre service specializzati:

```java
private final PostCrudService postCrudService;
private final PostFeedService postFeedService;
private final LikeService likeService;

public PostController(PostCrudService postCrudService,
                      PostFeedService postFeedService,
                      LikeService likeService) {
    this.postCrudService = postCrudService;
    this.postFeedService = postFeedService;
    this.likeService = likeService;
}
```

Aggiorna tutti i metodi sostituendo `postService.` con il service corretto:
- `postService.create/createWithImage/createWithImages/getById/update/delete/incrementViewCount/addImageToPost/addImagesToPost/removeImageFromPost` → `postCrudService.*`
- `postService.getAll/getByAuthorId/getFeed/getExplorePosts/searchByHashtag/countByAuthor` → `postFeedService.*`

Rimuovi l'import di `PostService`.

- [ ] **Step 2: Verifica che nessun altro file usi PostService/PostServiceImpl**

```bash
grep -r "PostService\b\|PostServiceImpl" src/main/java --include="*.java" | grep -v "PostCrudService\|PostFeedService\|PostStatsService"
```

Atteso: nessun risultato.

- [ ] **Step 3: Elimina i file deprecati**

```bash
rm src/main/java/com/social/backend/components/post/service/PostService.java
rm src/main/java/com/social/backend/components/post/service/PostServiceImpl.java
```

- [ ] **Step 4: Compila e testa**

```bash
./mvnw compile -q && ./mvnw test
```

Atteso: tutti i test passano.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(post): fix N+1 queries with batch stats, split PostServiceImpl into 3 SOLID services"
```

---

## Task 9: Rimuovi campo imageUrl deprecated da Post + aggiorna frontend

**Files:**
- Modify: `src/main/java/com/social/backend/components/post/entity/Post.java`
- Modify: `src/main/java/com/social/backend/components/post/dto/PostResponse.java`
- Modify (frontend): cerca `post\.imageUrl` nel frontend e aggiorna

> ⚠️ **IMPORTANTE — DB Migration**: Prima di fare deploy in produzione, esegui questa query sul database PostgreSQL Neon:
> ```sql
> ALTER TABLE post DROP COLUMN IF EXISTS image_url;
> ```
> Eseguila dalla console Neon **dopo** che il nuovo codice è stato deployato e verificato che nessun dato venga perso.

- [ ] **Step 1: Rimuovi `imageUrl` da `Post.java`**

In `Post.java`, rimuovi:
```java
// DA RIMUOVERE:
@Column(name = "image_url", columnDefinition = "TEXT")
@Deprecated
private String imageUrl;
```

- [ ] **Step 2: Rimuovi `imageUrl` da `PostResponse.java`**

Apri `PostResponse.java` e rimuovi il campo `@Deprecated String imageUrl`. Mantieni `imageUrls` (lista) e `images` (lista di oggetti).

Poi aggiorna `PostController.java` righe 219-220 (metodo `addImageToPost`) che usa ancora `post.getImageUrl()`:
```java
// DA:
.fileName(post.getImageUrl().substring(post.getImageUrl().lastIndexOf("/") + 1))
.fileUrl(post.getImageUrl())

// A:
String firstUrl = (post.getImageUrls() != null && !post.getImageUrls().isEmpty())
        ? post.getImageUrls().get(0) : "";
.fileName(firstUrl.substring(firstUrl.lastIndexOf("/") + 1))
.fileUrl(firstUrl)
```

- [ ] **Step 3: Rimuovi le ultime riferenze in `PostCrudServiceImpl`**

In `PostCrudServiceImpl.createWithImage` rimuovi `.imageUrl(imageUrl)` dal builder (il campo non esiste più) — usa invece `addImage()`:
```java
// DA:
Post post = postRepository.save(Post.builder()
        .content(request.getContent()).imageUrl(imageUrl).author(author).build());

// A:
Post post = Post.builder().content(request.getContent()).author(author).build();
post = postRepository.save(post);
if (imageUrl != null) {
    post.addImage(imageUrl, 0);
    post = postRepository.save(post);
}
```

Rimuovi anche `.imageUrl(...)` in `addImageToPost` sostituendo con `addImage(url, 0)` o aggiornando l'immagine esistente tramite `images`.

- [ ] **Step 4: Cerca usi di `post.imageUrl` nel frontend**

```bash
grep -r "\.imageUrl\b\|imageUrl" "C:/Users/cdattilo/OneDrive - Capgemini/Desktop/social-media-app/social-media-frontend/src" --include="*.js" --include="*.jsx" --include="*.tsx" -l
```

- [ ] **Step 5: Aggiorna i componenti frontend trovati**

Per ogni occorrenza di `post.imageUrl`, sostituisci con:
```js
post.images?.[0]?.imageUrl ?? post.imageUrls?.[0] ?? null
```

- [ ] **Step 6: Compila e testa**

```bash
./mvnw compile -q && ./mvnw test
```

Atteso: tutti i test passano.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(post): remove deprecated imageUrl field, migrate to multi-image only"
```

---

## Task 10: Sposta online users da JVM memory a Redis

**Files:**
- Modify: `src/main/java/com/social/backend/components/messaging/service/impl/MessagingServiceImpl.java`

- [ ] **Step 1: Aggiorna `MessagingServiceImpl.java`**

Rimuovi il campo:
```java
// DA RIMUOVERE:
private final Set<Long> onlineUsers = java.util.concurrent.ConcurrentHashMap.newKeySet();
```

Aggiungi `StringRedisTemplate` come dipendenza:
```java
private final StringRedisTemplate redisTemplate;

public MessagingServiceImpl(ConversationRepository conversationRepository,
                            MessageRepository messageRepository,
                            MessageReactionRepository reactionRepository,
                            UserRepository userRepository,
                            StorageService storageService,
                            StringRedisTemplate redisTemplate) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
    this.reactionRepository = reactionRepository;
    this.userRepository = userRepository;
    this.storageService = storageService;
    this.redisTemplate = redisTemplate;
}
```

Sostituisci i metodi online:
```java
private static final String ONLINE_KEY_PREFIX = "online:";
private static final java.time.Duration ONLINE_TTL = java.time.Duration.ofMinutes(5);

public void setOnline(Long userId) {
    redisTemplate.opsForValue().set(ONLINE_KEY_PREFIX + userId, "1", ONLINE_TTL);
}

public void setOffline(Long userId) {
    redisTemplate.delete(ONLINE_KEY_PREFIX + userId);
}

public boolean isOnline(Long userId) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(ONLINE_KEY_PREFIX + userId));
}
```

Aggiungi `@Slf4j` alla classe.

- [ ] **Step 2: Verifica compilazione**

```bash
./mvnw compile -q
```

- [ ] **Step 3: Esegui i test**

```bash
./mvnw test
```

Atteso: tutti i test passano (Redis è mockato in `TestRedisConfig` nel profilo test).

---

## Task 11: Fix N+1 in getMessages e triple lookup in toggleReaction

**Files:**
- Modify: `src/main/java/com/social/backend/components/messaging/service/impl/MessagingServiceImpl.java`
- Modify: `src/main/java/com/social/backend/components/messaging/repository/MessageReactionRepository.java`

- [ ] **Step 1: Aggiungi query batch a `MessageReactionRepository.java`**

Aggiungi all'interfaccia esistente:
```java
@Query("SELECT r FROM MessageReaction r WHERE r.message.id IN :messageIds")
List<MessageReaction> findByMessageIds(@Param("messageIds") List<Long> messageIds);
```

- [ ] **Step 2: Aggiorna `getMessages` in `MessagingServiceImpl.java`**

```java
@Override
@Transactional(readOnly = true)
public List<MessageDTO> getMessages(Long conversationId, Long userId) {
    Conversation conv = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversazione non trovata"));
    if (!conv.getUser1().getId().equals(userId) && !conv.getUser2().getId().equals(userId))
        throw new ForbiddenException("Non puoi leggere questa conversazione");

    List<Message> messages = messageRepository.findAllByConversationId(conversationId);
    if (messages.isEmpty()) return List.of();

    // Batch: carica tutte le reactions in una sola query
    List<Long> messageIds = messages.stream().map(Message::getId).toList();
    Map<Long, List<MessageReaction>> reactionsByMessage = reactionRepository
            .findByMessageIds(messageIds)
            .stream()
            .collect(Collectors.groupingBy(r -> r.getMessage().getId()));

    return messages.stream()
            .map(m -> mapToMessageDTO(m, userId, reactionsByMessage.getOrDefault(m.getId(), List.of())))
            .toList();
}
```

- [ ] **Step 3: Aggiorna `mapToMessageDTO` per accettare la lista pre-caricata**

```java
private MessageDTO mapToMessageDTO(Message msg, Long currentUserId, List<MessageReaction> reactionList) {
    Map<String, Long> reactions = reactionList.stream()
            .collect(Collectors.groupingBy(MessageReaction::getEmoji, Collectors.counting()));
    String myReaction = reactionList.stream()
            .filter(r -> r.getUser().getId().equals(currentUserId))
            .map(MessageReaction::getEmoji)
            .findFirst().orElse(null);
    return MessageDTO.builder()
            .id(msg.getId())
            .conversationId(msg.getConversation().getId())
            .senderId(msg.getSender().getId())
            .senderUsername(msg.getSender().getUsername())
            .senderAvatarUrl(msg.getSender().getAvatarUrl())
            .content(msg.isDeletedForAll() ? null : msg.getContent())
            .imageUrl(msg.isDeletedForAll() ? null : msg.getImageUrl())
            .audioUrl(msg.isDeletedForAll() ? null : msg.getAudioUrl())
            .replyToId(msg.getReplyToId())
            .replyToContent(msg.getReplyToContent())
            .replyToSenderUsername(msg.getReplyToSenderUsername())
            .isRead(msg.isRead())
            .deletedForAll(msg.isDeletedForAll())
            .expiresAt(msg.getExpiresAt())
            .createdAt(msg.getCreatedAt())
            .reactions(reactions)
            .myReaction(myReaction)
            .build();
}

// Mantieni il vecchio metodo (per toggleReaction e sendMessage) come wrapper:
private MessageDTO mapToMessageDTO(Message msg, Long currentUserId) {
    List<MessageReaction> reactionList = reactionRepository.findByMessageId(msg.getId());
    return mapToMessageDTO(msg, currentUserId, reactionList);
}
```

- [ ] **Step 4: Fix triple lookup in `toggleReaction`**

```java
@Override
@Transactional
public MessageDTO toggleReaction(Long messageId, Long userId, String emoji) {
    Message msg = messageRepository.findById(messageId)
            .orElseThrow(() -> new ResourceNotFoundException("Messaggio non trovato"));
    User user = getUser(userId);

    Optional<MessageReaction> existing = reactionRepository.findByMessageIdAndUserId(messageId, userId);
    if (existing.isPresent()) {
        if (existing.get().getEmoji().equals(emoji)) {
            reactionRepository.deleteByMessageIdAndUserId(messageId, userId);
        } else {
            reactionRepository.updateEmoji(messageId, userId, emoji);
        }
    } else {
        reactionRepository.save(MessageReaction.builder()
                .message(msg).user(user).emoji(emoji).build());
    }
    // Una sola chiamata finale — non rilegge dal DB inutilmente
    return mapToMessageDTO(msg, userId);
}
```

- [ ] **Step 5: Compila e testa**

```bash
./mvnw compile -q && ./mvnw test
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(messaging): move online status to Redis, fix N+1 in getMessages, fix triple lookup in toggleReaction"
```

---

## Task 12: Verifica Fase 4 — Notification (già paginata)

**Files:**
- Verify: `NotificationController.java`, `NotificationServiceImpl.java`, `notificationService.js`

- [ ] **Step 1: Verifica che la paginazione sia già implementata**

Backend: `NotificationController` già espone `?page=0&size=20`.
Frontend: `notificationService.js` già passa `{ params: { page, size } }`.

Esegui i test:
```bash
./mvnw test
```

Atteso: tutti i test passano. Nessuna modifica necessaria.

---

## Task 13: Cross-cutting — Fix log format e Stream.toList()

**Files:**
- Modify: Tutti i file con `log.info("{}", "..." + var)` e `.collect(Collectors.toList())`

- [ ] **Step 1: Fix log statements — cerca i file**

```bash
grep -r 'log\.\(info\|error\|warn\|debug\)("{}", "' src/main/java --include="*.java" -l
```

- [ ] **Step 2: Fix ogni occorrenza trovata**

Pattern da sostituire (esempi rappresentativi):

```java
// DA:
log.info("{}", " Post creato - ID: " + savedPost.getId());
log.info("{}", "📋 Feed per user " + userId + " - Segue " + followedUserIds.size() + " utenti");
log.error("{}", "⚠️ Errore: " + e.getMessage());

// A:
log.info("Post creato - ID: {}", savedPost.getId());
log.info("Feed per user {} - Segue {} utenti", userId, followedUserIds.size());
log.error("Errore: {}", e.getMessage());
```

- [ ] **Step 3: Fix Collectors.toList() — cerca i file**

```bash
grep -r "Collectors\.toList()" src/main/java --include="*.java" -l
```

- [ ] **Step 4: Sostituisci ogni `collect(Collectors.toList())` con `.toList()`**

```java
// DA:
.collect(Collectors.toList())

// A:
.toList()
```

> Nota: `.toList()` restituisce una lista immutabile. Se il codice chiama `.add()` o `.remove()` sulla lista risultante, usa `new ArrayList<>(stream.toList())` invece.

- [ ] **Step 5: Compila e testa**

```bash
./mvnw compile -q && ./mvnw test
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: fix log format anti-pattern, replace Collectors.toList() with Stream.toList()"
```

---

## Task 14: Cross-cutting — Aggiungi @Cacheable per hot reads

**Files:**
- Modify: `src/main/java/com/social/backend/config/RedisConfig.java` (o crea `CacheConfig.java`)
- Modify: `src/main/java/com/social/backend/components/user/service/impl/UserServiceImpl.java`
- Modify: `src/main/java/com/social/backend/components/post/service/impl/PostCrudServiceImpl.java`

- [ ] **Step 1: Aggiungi `implements Serializable` ai DTO che verranno messi in cache**

`PostResponse`, `PostImageDto`, `PollResponse`, `PollOptionResponse` e `UserResponse` devono implementare `Serializable` affinché Redis possa serializzarli. Per ciascuno, aggiungi:

```java
// Aggiungi alle classi DTO:
import java.io.Serializable;

public class PostResponse implements Serializable {
    // ... campi invariati
}
```

Fai lo stesso per `PostImageDto`, `PollResponse`, `PollOptionResponse`, `UserResponse`.

- [ ] **Step 2: Fix type mismatch likeCount/commentCount in PostStatsServiceImpl**

`PostResponse` ha `Integer likeCount` e `Integer commentCount`, ma le query batch restituiscono `Long`. Nel metodo `mapToResponse` di `PostStatsServiceImpl`, converti esplicitamente:

```java
// Nella firma del metodo:
private PostResponse mapToResponse(Post post, Long currentUserId,
                                    long likeCount, long commentCount,
                                    boolean liked, boolean bookmarked,
                                    Poll poll, Map<Long, Long> votedOptionByPollId)

// Nel builder:
.likeCount((int) likeCount)
.commentCount((int) commentCount)
```

Fai lo stesso in `enrichOne` dove usi `likeRepository.countByPostId` (che restituisce `int`):
```java
long likeCount = likeRepository.countByPostId(post.getId());
long commentCount = commentRepository.countByPostId(post.getId());
```

- [ ] **Step 3: Configura Spring Cache con Redis**

Se non esiste un `CacheConfig.java`, crealo:

```java
package com.social.backend.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        "users", defaultConfig.entryTtl(Duration.ofMinutes(5)),
                        "posts", defaultConfig.entryTtl(Duration.ofMinutes(2)),
                        "followCounts", defaultConfig.entryTtl(Duration.ofMinutes(1))
                ))
                .build();
    }
}
```

- [ ] **Step 4: Aggiungi `@Cacheable` a `UserServiceImpl` — metodo `getUserByUsername`**

```java
@Cacheable(value = "users", key = "#username")
public UserResponse getUserByUsername(String username) {
    // ... codice esistente invariato
}
```

Aggiungi `@CacheEvict` su `updateUser`:
```java
@CacheEvict(value = "users", key = "#username")
public UserResponse updateUser(String username, ...) {
    // ... codice esistente invariato
}
```

- [ ] **Step 5: Aggiungi `@Cacheable` a `PostCrudServiceImpl` — metodo `getById`**

```java
@Cacheable(value = "posts", key = "#postId + '_' + #currentUserId")
@Transactional(readOnly = true)
public PostResponse getById(Long postId, Long currentUserId) {
    return postStatsService.enrichOne(findById(postId), currentUserId);
}
```

Aggiungi `@CacheEvict` su `update` e `delete`:
```java
@CacheEvict(value = "posts", allEntries = true)
@Transactional
public PostResponse update(...) { ... }

@CacheEvict(value = "posts", allEntries = true)
@Transactional
public void delete(...) { ... }
```

- [ ] **Step 6: Compila e testa**

```bash
./mvnw compile -q && ./mvnw test
```

Atteso: tutti i test passano (Redis mockato in profilo test gestisce `@Cacheable` come no-op o mock).

- [ ] **Step 7: Commit finale**

```bash
git add -A
git commit -m "feat(cache): add Redis @Cacheable for user profiles and post reads"
```

---

## Checklist finale

- [ ] `./mvnw test` — tutti i test verdi
- [ ] `./mvnw clean package -DskipTests` — build completo OK
- [ ] Frontend aggiornato per rimozione `post.imageUrl`
- [ ] Query DB per drop `image_url` pronta per esecuzione su Neon prima/dopo deploy
- [ ] Virtual threads abilitati (`spring.threads.virtual.enabled=true`)
- [ ] Nessuna occorrenza di `JwtUtil` o `JwtTokenProvider` nel codice
- [ ] Nessuna occorrenza di `PostServiceImpl` nel codice
- [ ] `onlineUsers` non più in JVM memory
