package com.stoneiron.gamehub.member.jpa;

import com.stoneiron.gamehub.member.SocialAccount;
import com.stoneiron.gamehub.member.SocialAccountStore;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaSocialAccountStore implements SocialAccountStore {

    private final SocialAccountJpaRepository repository;

    public JpaSocialAccountStore(SocialAccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SocialAccount> find(String provider, String subject) {
        return repository.findByProviderAndSubject(provider, subject).map(SocialAccountEntity::toSocialAccount);
    }

    @Override
    public void save(SocialAccount account) {
        repository.saveAndFlush(SocialAccountEntity.from(account));
    }
}
