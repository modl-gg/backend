package gg.modl.backend.admin.service;

import gg.modl.backend.admin.data.AdminUser;
import gg.modl.backend.database.mongo.repository.AdminUserMongoRepository;
import java.util.Date;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {
    private final AdminUserMongoRepository adminUserRepository;

    public Optional<AdminUser> findByEmail(String email) {
        return adminUserRepository.findByEmailIgnoreCase(email);
    }

    public void updateLastActivity(String email, String clientIp) {
        adminUserRepository.updateLastActivity(email, clientIp, new Date());
    }
}
