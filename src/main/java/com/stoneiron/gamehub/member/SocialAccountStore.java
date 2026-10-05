package com.stoneiron.gamehub.member;

import java.util.Optional;

public interface SocialAccountStore {

    Optional<SocialAccount> find(String provider, String subject);

    void save(SocialAccount account);
}
