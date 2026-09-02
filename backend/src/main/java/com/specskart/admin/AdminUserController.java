package com.specskart.admin;

import com.specskart.auth.User;
import com.specskart.auth.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Staff directory — used by the CRM "Assign employee" control. Readable by ADMIN and AGENT. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository users;

    public AdminUserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return users.findAll().stream()
                .filter(User::isActive)
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(), "name", u.getFullName(),
                        "email", u.getEmail(), "role", u.getRole().name()))
                .toList();
    }
}
