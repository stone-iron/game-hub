package com.stoneiron.gamehub.member.jpa;

import com.stoneiron.gamehub.member.SocialAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "social_accounts", uniqueConstraints =
        @UniqueConstraint(name = "uk_social_provider_subject", columnNames = {"provider", "subject"}))
public class SocialAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    protected SocialAccountEntity() {
    }

    static SocialAccountEntity from(SocialAccount account) {
        SocialAccountEntity entity = new SocialAccountEntity();
        entity.provider = account.provider();
        entity.subject = account.subject();
        entity.memberId = account.memberId();
        return entity;
    }

    SocialAccount toSocialAccount() {
        return new SocialAccount(provider, subject, memberId);
    }
}
