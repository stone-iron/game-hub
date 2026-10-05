package com.stoneiron.gamehub.member;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class InMemorySocialAccountStore implements SocialAccountStore {

    private final Map<String, SocialAccount> accounts = new ConcurrentHashMap<>();

    @Override
    public Optional<SocialAccount> find(String provider, String subject) {
        return Optional.ofNullable(accounts.get(provider + ":" + subject));
    }

    @Override
    public void save(SocialAccount account) {
        accounts.putIfAbsent(account.provider() + ":" + account.subject(), account);
    }
}
