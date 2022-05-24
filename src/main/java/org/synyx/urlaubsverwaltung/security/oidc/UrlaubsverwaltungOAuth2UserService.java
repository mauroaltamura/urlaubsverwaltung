package org.synyx.urlaubsverwaltung.security.oidc;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UrlaubsverwaltungOAuth2UserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    // TODO make this constants configurable from application.properties
    private static final String GROUP_PERMISSION = "urlaubsverwaltung_user";
    private static final String PERMISSION_CLAIM = "groups";
    private final OidcUserService delegate;

    public UrlaubsverwaltungOAuth2UserService(OidcUserService delegate) {
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        OidcUser oidcUser = delegate.loadUser(userRequest);

        List<GrantedAuthority> fromGroupsClaim = parseAuthoritiesFromGroupsClaim(oidcUser.getClaims());

        checkUserHasApplicationPermission(fromGroupsClaim);

        List<GrantedAuthority> combinedAuthorities = Stream.concat(oidcUser.getAuthorities().stream(), fromGroupsClaim.stream()).collect(Collectors.toList());

        return new DefaultOidcUser(combinedAuthorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }

    private void checkUserHasApplicationPermission(List<GrantedAuthority> fromGroupsClaim) {
        if (fromGroupsClaim.stream().noneMatch(simpleGrantedAuthority -> simpleGrantedAuthority.getAuthority().equalsIgnoreCase(GROUP_PERMISSION))) {
            throw new OAuth2AuthenticationException("user has no permission to access application=urlaubsverwaltung!");
        }
    }

    private List<GrantedAuthority> parseAuthoritiesFromGroupsClaim(Map<String, Object> claims) {

        if (!claims.containsKey(PERMISSION_CLAIM)) {
            throw new OAuth2AuthenticationException(String.format("claim=%s is missing!", PERMISSION_CLAIM));
        }

        List<GrantedAuthority> groups = extractFromList(claims, PERMISSION_CLAIM)
            .stream()
            .map(role -> new SimpleGrantedAuthority(String.valueOf(role)))
            .collect(Collectors.toList());

        return groups;
    }

    private List<String> extractFromList(Map<String, Object> myMap, String key) {
        Object roles = myMap.get(key);
        if (roles instanceof List) {
            return (List) roles;
        }
        return Collections.emptyList();
    }
}
