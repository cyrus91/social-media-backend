package com.social.backend.components.post.entity;

import com.social.backend.components.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "post",
        indexes = {
                @Index(name = "idx_posts_author", columnList = "author_id"),
                @Index(name = "idx_posts_created_at", columnList = "created_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String content;

    // ✅ DEPRECATO: Manteniamo per retrocompatibilità (migrazione graduale)
    @Column(name = "image_url", columnDefinition = "TEXT")
    @Deprecated
    private String imageUrl;

    // ✅ NUOVO: Relazione con immagini multiple
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<PostImage> images = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Long viewCount = 0L;

    @Column(name = "hidden", nullable = false)
    @Builder.Default
    private Boolean hidden = false;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ✅ HELPER METHOD: Aggiungi immagine
    public void addImage(String imageUrl, int order) {
        PostImage postImage = PostImage.builder()
                .post(this)
                .imageUrl(imageUrl)
                .displayOrder(order)
                .build();
        images.add(postImage);
    }
}