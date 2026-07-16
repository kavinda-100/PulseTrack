package com.kavinda.auth_service.mappers;

import com.kavinda.auth_service.entity.AppUser;
import com.kavinda.auth_service.entity.Permission;
import com.kavinda.auth_service.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class UserAuthorityMapper {

    public Set<GrantedAuthority> map(AppUser user) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        for (Role role : user.getRoles()) {
            authorities.add(
                    new SimpleGrantedAuthority(
                            "ROLE_" + role.getName().name()
                    )
            );

            for (Permission permission : role.getPermissions()) {
                authorities.add(
                        new SimpleGrantedAuthority(
                                permission.getName().authority()
                        )
                );
            }
        }

        return Set.copyOf(authorities);
    }
}
