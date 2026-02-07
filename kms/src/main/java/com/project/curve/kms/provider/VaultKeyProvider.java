package com.project.curve.kms.provider;

import com.project.curve.core.key.KeyProvider;
import com.project.curve.core.key.KeyProviderException;
import com.project.curve.kms.model.KeyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

import java.util.regex.Pattern;

@RequiredArgsConstructor
public class VaultKeyProvider implements KeyProvider {

    private final VaultTemplate vaultTemplate;
    private final String mountPath; // e.g., "secret" or "transit"

    // Pattern to prevent path traversal attacks (only allow alphanumeric, underscore, hyphen)
    private static final Pattern VALID_KEY_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    @Override
    public String getDataKey(String keyId) {
        // Validate keyId to prevent path traversal attacks
        if (keyId == null || keyId.isBlank()) {
            throw new KeyProviderException("KeyId cannot be null or empty");
        }

        if (!VALID_KEY_ID_PATTERN.matcher(keyId).matches()) {
            throw new KeyProviderException(
                    "Invalid keyId format: '" + keyId + "'. " +
                    "Only alphanumeric characters, underscores, and hyphens are allowed"
            );
        }

        String path = String.format("%s/data/%s", mountPath, keyId);

        VaultResponseSupport<KeyResponse> response = vaultTemplate.read(path, KeyResponse.class);

        if (response != null && response.getData() != null) {
            String key = response.getData().key();
            if (key == null || key.isBlank()) {
                throw new KeyProviderException("Key value is null or empty in Vault for keyId: " + keyId);
            }
            return key;
        }
        throw new KeyProviderException("Key not found in Vault: " + keyId);
    }
}
