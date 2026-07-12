package gg.modl.backend.staff.service;

import gg.modl.backend.database.mongo.repository.StaffMongoRepository;
import gg.modl.backend.email.EmailAddressUtil;
import gg.modl.backend.infrastructure.exception.ConflictException;
import gg.modl.backend.server.data.Server;
import gg.modl.backend.settings.data.SupportedLanguages;
import gg.modl.backend.staff.data.Staff;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffProfileService {
    private final StaffMongoRepository staffRepository;
    private final StaffLookupCache staffLookupCache;

    public Optional<Staff> updateOrCreateProfileUsername(Server server, String email, String newUsername,
                                                         boolean createIfNotExists, String newLanguage, String newDateFormat) {
        Staff staff = staffRepository.findByEmailIgnoreCase(server, email).orElse(null);

        if (staff == null) {
            return Optional.empty();
        }

        boolean hasChanges = false;
        if (newUsername != null && !newUsername.equals(staff.getUsername())) {
            if (staffRepository.existsByUsernameExcludingId(server, newUsername, staff.getId())) {
                throw new ConflictException("Username already in use");
            }
            staff.setUsername(newUsername);
            hasChanges = true;
        }

        if (SupportedLanguages.isSupported(newLanguage)) {
            staff.setLanguage(newLanguage);
            hasChanges = true;
        }

        if (newDateFormat != null && List.of(Staff.DEFAULT_DATE_FORMAT, "MM/DD/YYYY", "DD/MM/YYYY", "YYYY-MM-DD").contains(newDateFormat)) {
            staff.setDateFormat(newDateFormat);
            hasChanges = true;
        }

        if (hasChanges) {
            staff.setUpdatedAt(new Date());
            staff = staffRepository.saveEntity(server, staff);
            staffLookupCache.evict(server, email);
        }

        return Optional.of(staff);
    }

    public boolean isStaffEmailInUse(Server server, String newEmail, String excludingCurrentEmail) {
        return staffRepository.existsByEmailIgnoreCaseExcluding(server, EmailAddressUtil.normalize(newEmail), excludingCurrentEmail);
    }

    public Optional<Staff> applyStaffEmailChange(Server server, String currentEmail, String newEmail) {
        Staff staff = staffRepository.findByEmailIgnoreCase(server, currentEmail).orElse(null);
        if (staff == null) {
            return Optional.empty();
        }

        staff.setEmail(EmailAddressUtil.normalize(newEmail));
        staff.setUpdatedAt(new Date());
        staff = staffRepository.saveEntity(server, staff);

        staffLookupCache.evict(server, currentEmail);
        staffLookupCache.evict(server, staff.getEmail());
        return Optional.of(staff);
    }
}
