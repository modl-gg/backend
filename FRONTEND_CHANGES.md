Use ENDPOINTS.md file for extra context.
1. change landing page registration to new constraints
```
public record RegisterRequest(@Email @NotBlank String email,
                                  @Size(min = 3, max = 50) @NotBlank @Pattern(regexp = "^[a-zA-Z0-9 -]+$") String serverName,
                                  @Size(min = 3, max = 20) @NotBlank @Pattern(regexp = "^[a-z0-9-]+$") String customDomain,
                                  @NotBlank String turnstileToken) {}
```
2. 